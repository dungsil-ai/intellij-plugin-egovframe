package kr.kyg.ijplugin.egovframe.project

import kr.kyg.ijplugin.egovframe.assets.EgovAssets
import kr.kyg.ijplugin.egovframe.assets.ProjectTemplate
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

enum class ProjectGenerationStage(val label: String) {
  RESOLVE_TEMPLATE("Loading template"),
  EXTRACT("Extracting template"),
  WRITE_POM("Writing POM"),
  LINK_MAVEN("Linking Maven project"),
  CONFIGURE_JDK("Configuring JDK 17"),
  COMPLETE("Complete"),
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

object ProjectGenerator {

  data class ProjectConfig(
    val projectName: String,
    val groupId: String,
    val artifactId: String,
    val template: ProjectTemplate,
    val version: String = "1.0.0",
    val url: String = "https://www.egovframe.go.kr",
  )

  fun generate(
    outputDirectory: Path,
    zipPath: Path,
    config: ProjectConfig,
    allowExistingEmptyDirectory: Boolean = false,
  ): Path {
    validate(config)
    val base = outputDirectory.toAbsolutePath().normalize()
    val projectRoot = base.resolve(config.projectName).normalize()
    require(projectRoot.startsWith(base) && projectRoot != base) { "Resolved project path escapes the output directory" }

    if (Files.exists(projectRoot)) {
      val canReuse = allowExistingEmptyDirectory && Files.isDirectory(projectRoot) && Files.list(projectRoot)
        .use { !it.findAny().isPresent }
      require(canReuse) { "Project directory already exists: $projectRoot" }
    } else {
      Files.createDirectories(projectRoot)
    }

    extractZip(zipPath, projectRoot)
    if (config.template.pomFile.isNotBlank()) {
      val pomTemplate = EgovAssets.resourceText("${EgovAssets.POM_DIR}/${config.template.pomFile}")
      Files.writeString(projectRoot.resolve("pom.xml"), replacePomTokens(pomTemplate, config), Charsets.UTF_8)
    }
    return projectRoot
  }

  /**
   * Generate with progress reporting and structured error handling.
   * On failure, newly created output is cleaned up; pre-existing user directories are preserved.
   */
  fun generateWithProgress(
    outputDirectory: Path,
    zipPath: Path,
    config: ProjectConfig,
    allowExistingEmptyDirectory: Boolean = false,
    progress: GenerationProgress = GenerationProgress { },
  ): GenerationResult {
    validate(config)
    val base = outputDirectory.toAbsolutePath().normalize()
    val projectRoot = base.resolve(config.projectName).normalize()
    require(projectRoot.startsWith(base) && projectRoot != base) { "Resolved project path escapes the output directory" }

    val preExisting = Files.exists(projectRoot)
    if (preExisting) {
      val canReuse = allowExistingEmptyDirectory && Files.isDirectory(projectRoot) && Files.list(projectRoot)
        .use { !it.findAny().isPresent }
      require(canReuse) { "Project directory already exists: $projectRoot" }
    } else {
      Files.createDirectories(projectRoot)
    }

    var stage = ProjectGenerationStage.EXTRACT
    return try {
      progress.update(stage)
      extractZip(zipPath, projectRoot)

      if (config.template.pomFile.isNotBlank()) {
        stage = ProjectGenerationStage.WRITE_POM
        progress.update(stage)
        val pomTemplate = EgovAssets.resourceText("${EgovAssets.POM_DIR}/${config.template.pomFile}")
        Files.writeString(projectRoot.resolve("pom.xml"), replacePomTokens(pomTemplate, config), Charsets.UTF_8)
      }

      GenerationResult.Success(projectRoot)
    } catch (error: Exception) {
      if (!preExisting) {
        cleanupAfterFailure(projectRoot, error)
      }
      GenerationResult.Failure(stage, error.message ?: "Project generation failed", error)
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
    cleanup: (Path) -> Unit = ::cleanupDirectory,
  ) {
    try {
      cleanup(directory)
    } catch (cleanupFailure: Exception) {
      originalFailure.addSuppressed(cleanupFailure)
    }
  }

  internal fun cleanupDirectory(directory: Path) {
    if (!Files.exists(directory)) return
    Files.walk(directory).use { paths ->
      paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
    }
  }

  val PROJECT_NAME_REGEX = Regex("^[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)*$")
  val GROUP_ID_REGEX = Regex("^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*$")
  val ARTIFACT_ID_REGEX = Regex("^[a-z][a-z0-9-]*$")
}
