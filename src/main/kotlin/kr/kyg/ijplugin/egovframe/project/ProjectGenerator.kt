package kr.kyg.ijplugin.egovframe.project

import kr.kyg.ijplugin.egovframe.assets.EgovAssets
import kr.kyg.ijplugin.egovframe.assets.ProjectTemplate
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

enum class ProjectGenerationStage(val messageKey: String) {
  RESOLVE_TEMPLATE("wizard.progress.resolveTemplate"),
  EXTRACT("wizard.progress.extract"),
  WRITE_POM("wizard.progress.writePom"),
  LINK_MAVEN("wizard.progress.linkMaven"),
  CONFIGURE_JDK("wizard.progress.configureJdk"),
  COMPLETE("wizard.progress.complete"),
}

fun interface GenerationProgress {
  fun update(stage: ProjectGenerationStage)
}

sealed class GenerationResult {
  data class Success(val projectRoot: Path) : GenerationResult()
  data class Failure(
    val stage: ProjectGenerationStage,
    val error: String,
    val cause: Throwable? = null,
  ) : GenerationResult()
}

internal interface ProjectFileOps {
  fun createTempDirectory(directory: Path, prefix: String): Path
  fun exists(path: Path): Boolean
  fun list(directory: Path): List<Path>
  fun moveNoReplace(source: Path, target: Path)
  fun deleteRecursively(directory: Path)
}

internal object NioProjectFileOps : ProjectFileOps {
  override fun createTempDirectory(directory: Path, prefix: String): Path = Files.createTempDirectory(directory, prefix)

  override fun exists(path: Path): Boolean = Files.exists(path, LinkOption.NOFOLLOW_LINKS)

  override fun list(directory: Path): List<Path> = Files.list(directory).use { paths -> paths.toList() }

  override fun moveNoReplace(source: Path, target: Path) {
    Files.move(source, target)
  }

  override fun deleteRecursively(directory: Path) {
    if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return
    Files.walk(directory).use { paths ->
      paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
    }
  }
}

object ProjectGenerator {
  private const val STAGING_PREFIX = ".egov-project-stage-"

  data class ProjectConfig(
    val projectName: String,
    val groupId: String,
    val artifactId: String,
    val template: ProjectTemplate,
    val version: String = "1.0.0",
    val url: String = "https://www.egovframe.go.kr",
  )

  /**
   * Generates a project in a sibling staging directory and publishes it only after extraction succeeds.
   */
  fun generateWithProgress(
    outputDirectory: Path,
    zipPath: Path,
    config: ProjectConfig,
    allowExistingEmptyDirectory: Boolean = false,
    progress: GenerationProgress = GenerationProgress { },
  ): GenerationResult = generateWithProgress(
    outputDirectory,
    zipPath,
    config,
    allowExistingEmptyDirectory,
    progress,
    NioProjectFileOps,
  )

  internal fun generateWithProgress(
    outputDirectory: Path,
    zipPath: Path,
    config: ProjectConfig,
    allowExistingEmptyDirectory: Boolean,
    progress: GenerationProgress,
    fileOps: ProjectFileOps,
  ): GenerationResult {
    validate(config)
    val base = outputDirectory.toAbsolutePath().normalize()
    val projectRoot = base.resolve(config.projectName).normalize()
    require(projectRoot.startsWith(base) && projectRoot != base) { "Resolved project path escapes the output directory" }

    Files.createDirectories(base)
    val preExisting = Files.exists(projectRoot, LinkOption.NOFOLLOW_LINKS)
    if (preExisting) {
      require(canReuseProjectDirectory(projectRoot, allowExistingEmptyDirectory)) {
        "Project directory already exists: $projectRoot"
      }
    }

    val stagingRoot = fileOps.createTempDirectory(base, STAGING_PREFIX)
    var stage = ProjectGenerationStage.EXTRACT
    return try {
      progress.update(stage)
      extractZip(zipPath, stagingRoot)

      if (config.template.pomFile.isNotBlank()) {
        stage = ProjectGenerationStage.WRITE_POM
        progress.update(stage)
        val pomTemplate = EgovAssets.resourceText("${EgovAssets.POM_DIR}/${config.template.pomFile}")
        Files.writeString(stagingRoot.resolve("pom.xml"), replacePomTokens(pomTemplate, config), Charsets.UTF_8)
      }

      commitStagedProject(stagingRoot, projectRoot, preExisting, fileOps)
      GenerationResult.Success(projectRoot)
    } catch (error: Exception) {
      cleanupAfterFailure(stagingRoot, error, fileOps::deleteRecursively)
      GenerationResult.Failure(stage, error.message ?: "Project generation failed", error)
    }
  }

  internal fun commitStagedProject(
    stagingRoot: Path,
    projectRoot: Path,
    preExisting: Boolean,
    fileOps: ProjectFileOps = NioProjectFileOps,
  ) {
    if (!preExisting) {
      require(!fileOps.exists(projectRoot)) { "Project directory already exists: $projectRoot" }
      fileOps.moveNoReplace(stagingRoot, projectRoot)
      return
    }

    val stagedChildren = fileOps.list(stagingRoot).sortedBy { it.fileName.toString() }
    require(stagedChildren.none { it.fileName.toString() == ".idea" }) {
      "Template staging directory must not contain .idea"
    }
    stagedChildren.forEach { source ->
      val target = projectRoot.resolve(source.fileName)
      require(!fileOps.exists(target)) { "Project directory already contains: $target" }
    }

    val movedChildren = mutableListOf<Path>()
    try {
      stagedChildren.forEach { source ->
        val target = projectRoot.resolve(source.fileName)
        fileOps.moveNoReplace(source, target)
        movedChildren.add(target)
      }
      fileOps.deleteRecursively(stagingRoot)
    } catch (failure: Exception) {
      movedChildren.asReversed().forEach { target ->
        try {
          fileOps.moveNoReplace(target, stagingRoot.resolve(target.fileName))
        } catch (rollbackFailure: Exception) {
          failure.addSuppressed(rollbackFailure)
        }
      }
      throw failure
    }
  }

  fun replacePomTokens(template: String, config: ProjectConfig): String = template
    .replace("###NAME###", config.projectName)
    .replace("###ARTIFACT_ID###", config.artifactId)
    .replace("###GROUP_ID###", config.groupId)
    .replace("###VERSION###", config.version.ifBlank { "1.0.0" })
    .replace("###URL###", config.url.ifBlank { "https://www.egovframe.go.kr" })

  fun validate(config: ProjectConfig) {
    require(PROJECT_NAME_REGEX.matches(config.projectName)) {
      "Project name can only contain letters, numbers, hyphens, underscores, and single dots between segments"
    }
    if (config.template.pomFile.isNotBlank()) {
      require(GROUP_ID_REGEX.matches(config.groupId)) { "Invalid Maven groupId: ${config.groupId}" }
      require(ARTIFACT_ID_REGEX.matches(config.artifactId)) { "Invalid Maven artifactId: ${config.artifactId}" }
    }
    require(config.template.fileName.isNotBlank()) { "Template file name is required" }
  }

  private fun canReuseProjectDirectory(directory: Path, allowExistingEmptyDirectory: Boolean): Boolean {
    if (!allowExistingEmptyDirectory || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return false
    val entries = Files.list(directory).use { it.toList() }
    return entries.isEmpty() || (entries.size == 1 && entries.single().fileName.toString() == ".idea" &&
      Files.isDirectory(entries.single(), LinkOption.NOFOLLOW_LINKS))
  }

  private fun extractZip(zipPath: Path, projectRoot: Path) {
    require(Files.isRegularFile(zipPath)) { "Template ZIP not found: $zipPath" }
    ZipInputStream(Files.newInputStream(zipPath)).use { zip ->
      while (true) {
        val entry = zip.nextEntry ?: break
        val target = projectRoot.resolve(entry.name).normalize()
        require(target.startsWith(projectRoot)) { "ZIP entry escapes project directory: ${entry.name}" }
        if (entry.isDirectory) {
          Files.createDirectories(target)
        } else {
          Files.createDirectories(target.parent)
          Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING)
        }
        zip.closeEntry()
      }
    }
  }

  internal fun cleanupAfterFailure(
    directory: Path,
    originalFailure: Throwable,
    cleanup: (Path) -> Unit = NioProjectFileOps::deleteRecursively,
  ) {
    try {
      cleanup(directory)
    } catch (cleanupFailure: Exception) {
      originalFailure.addSuppressed(IllegalStateException("Staged project preserved at $directory", cleanupFailure))
    }
  }

  internal fun cleanupDirectory(directory: Path) = NioProjectFileOps.deleteRecursively(directory)

  val PROJECT_NAME_REGEX = Regex("^[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)*$")
  val GROUP_ID_REGEX = Regex("^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*$")
  val ARTIFACT_ID_REGEX = Regex("^[a-z][a-z0-9-]*$")
}
