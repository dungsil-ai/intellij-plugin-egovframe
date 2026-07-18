package kr.kyg.ijplugin.egovframe.project

import kr.kyg.ijplugin.egovframe.assets.ProjectTemplate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ProjectGeneratorTest {

  @Test
  fun extractsTemplateAndReplacesEveryPomToken() {
    val output = Files.createTempDirectory("egov-project-test")
    val zip = output.resolve("template.zip")
    ZipOutputStream(Files.newOutputStream(zip)).use { stream ->
      stream.putNextEntry(ZipEntry("src/main/resources/application.properties"))
      stream.write("spring.application.name=test".toByteArray())
      stream.closeEntry()
    }
    val template = ProjectTemplate(
      displayName = "Boot Web Project",
      fileName = "template.zip",
      pomFile = "egovframe-boot-web-pom.xml",
      description = "test",
      category = "Boot",
      projectName = "egov-boot-web",
    )
    val config = ProjectGenerator.ProjectConfig(
      projectName = "com.example.demo",
      groupId = "com.example",
      artifactId = "demo-app",
      template = template,
    )

    val result = ProjectGenerator.generateWithProgress(output, zip, config)
    val success = result as? GenerationResult.Success ?: fail("Expected success: $result")
    val projectRoot = success.projectRoot
    assertTrue(Files.isRegularFile(projectRoot.resolve("src/main/resources/application.properties")))
    val pom = Files.readString(projectRoot.resolve("pom.xml"))
    assertTrue(pom.contains("<groupId>com.example</groupId>"))
    assertTrue(pom.contains("<artifactId>demo-app</artifactId>"))
    assertTrue(pom.contains("com.example.demo"), "POM must contain the configured project name")
    assertTrue(pom.contains("1.0.0"), "POM must contain the default version")
    assertTrue(pom.contains("https://www.egovframe.go.kr"), "POM must contain the default URL")
    assertFalse(pom.contains("###"))
  }

  @Test
  fun `replacePomTokens maps all five placeholders`() {
    val template = "###NAME### ###GROUP_ID### ###ARTIFACT_ID### ###VERSION### ###URL###"
    val config = ProjectGenerator.ProjectConfig(
      projectName = "my-project",
      groupId = "org.example",
      artifactId = "my-artifact",
      template = ProjectTemplate("t", "t.zip", "pom.xml", "", "Boot", "t"),
      version = "2.0.0",
      url = "https://example.org",
    )
    val result = ProjectGenerator.replacePomTokens(template, config)
    assertEquals("my-project org.example my-artifact 2.0.0 https://example.org", result)
  }

  @Test
  fun rejectsUnsafeProjectNames() {
    val template = ProjectTemplate("test", "test.zip", "", "", "Web", "test")
    assertThrows(IllegalArgumentException::class.java) {
      ProjectGenerator.validate(ProjectGenerator.ProjectConfig("../escape", "com.example", "demo", template))
    }
  }

  @Test
  fun `validate skips Maven fields for no-POM templates`() {
    val noPomTemplate = ProjectTemplate("test", "test.zip", "", "desc", "MSA", "test-project")
    // These Maven fields are invalid but should not cause validation failure for no-POM templates
    assertDoesNotThrow {
      ProjectGenerator.validate(
        ProjectGenerator.ProjectConfig("test-project", "INVALID", "ALSO_INVALID", noPomTemplate),
      )
    }
  }

  @Test
  fun `validate enforces Maven fields for POM templates`() {
    val pomTemplate = ProjectTemplate("test", "test.zip", "some-pom.xml", "desc", "Boot", "test-project")
    assertThrows(IllegalArgumentException::class.java) {
      ProjectGenerator.validate(
        ProjectGenerator.ProjectConfig("test-project", "INVALID", "demo", pomTemplate),
      )
    }
  }

  @Test
  fun `generateWithProgress reports typed stages in order`() {
    val output = Files.createTempDirectory("egov-progress-test")
    val zip = output.resolve("template.zip")
    ZipOutputStream(Files.newOutputStream(zip)).use { stream ->
      stream.putNextEntry(ZipEntry("README.md"))
      stream.write("hello".toByteArray())
      stream.closeEntry()
    }
    val template = ProjectTemplate("Test", "template.zip", "egovframe-boot-web-pom.xml", "d", "Boot", "test-prog")
    val config = ProjectGenerator.ProjectConfig("test-prog", "com.example", "demo", template)
    val stages = mutableListOf<ProjectGenerationStage>()

    val result = ProjectGenerator.generateWithProgress(output, zip, config) { stages += it }

    assertTrue(result is GenerationResult.Success)
    assertEquals(
      listOf(ProjectGenerationStage.EXTRACT, ProjectGenerationStage.WRITE_POM),
      stages,
    )
  }

  @Test
  fun `missing POM leaves new project root and staging absent`() {
    val output = Files.createTempDirectory("egov-cleanup-test")
    val zip = zip(output, "README.md" to "hello")
    val template = ProjectTemplate("Test", "template.zip", "nonexistent-pom.xml", "d", "Boot", "cleanup-test")

    val result = ProjectGenerator.generateWithProgress(
      output,
      zip,
      ProjectGenerator.ProjectConfig("cleanup-test", "com.example", "demo", template),
    )

    assertTrue(result is GenerationResult.Failure)
    assertFalse(Files.exists(output.resolve("cleanup-test")))
    assertTrue(stageDirectories(output).isEmpty())
  }

  @Test
  fun `generateWithProgress reports failing stage`() {
    val output = Files.createTempDirectory("egov-failing-stage-test")
    val zip = output.resolve("template.zip")
    ZipOutputStream(Files.newOutputStream(zip)).use { stream ->
      stream.putNextEntry(ZipEntry("README.md"))
      stream.write("hello".toByteArray())
      stream.closeEntry()
    }
    val template = ProjectTemplate("Test", "template.zip", "nonexistent-pom.xml", "d", "Boot", "stage-test")
    val config = ProjectGenerator.ProjectConfig("stage-test", "com.example", "demo", template)

    val result = ProjectGenerator.generateWithProgress(output, zip, config)

    assertTrue(result is GenerationResult.Failure)
    assertEquals(ProjectGenerationStage.WRITE_POM, (result as GenerationResult.Failure).stage)
  }

  @Test
  fun `generateWithProgress reuses directory initialized by IntelliJ`() {
    val output = Files.createTempDirectory("egov-intellij-directory-test")
    val projectDir = output.resolve("intellij-test")
    val ideaDir = projectDir.resolve(".idea")
    Files.createDirectories(ideaDir)
    Files.writeString(ideaDir.resolve(".gitignore"), "workspace.xml\n")
    val zip = output.resolve("template.zip")
    ZipOutputStream(Files.newOutputStream(zip)).use { stream ->
      stream.putNextEntry(ZipEntry("README.md"))
      stream.write("hello".toByteArray())
      stream.closeEntry()
    }
    val template = ProjectTemplate("Test", "template.zip", "", "d", "Boot", "intellij-test")
    val config = ProjectGenerator.ProjectConfig("intellij-test", "", "", template)

    val result = ProjectGenerator.generateWithProgress(output, zip, config, allowExistingEmptyDirectory = true)

    assertTrue(result is GenerationResult.Success, "$result")
    assertTrue(Files.isRegularFile(projectDir.resolve("README.md")))
    assertTrue(Files.isRegularFile(ideaDir.resolve(".gitignore")))
    assertTrue(stageDirectories(output).isEmpty())
  }

  @Test
  fun `generateWithProgress reuses pre-existing empty directory`() {
    val output = Files.createTempDirectory("egov-empty-directory-test")
    val projectDir = Files.createDirectories(output.resolve("empty-test"))
    val zip = zip(output, "README.md" to "hello")
    val template = ProjectTemplate("Test", "template.zip", "", "d", "Boot", "empty-test")

    val result = ProjectGenerator.generateWithProgress(
      output,
      zip,
      ProjectGenerator.ProjectConfig("empty-test", "", "", template),
      allowExistingEmptyDirectory = true,
    )

    assertTrue(result is GenerationResult.Success, "$result")
    assertTrue(Files.isRegularFile(projectDir.resolve("README.md")))
    assertTrue(stageDirectories(output).isEmpty())
  }

  @Test
  fun `generateWithProgress rejects existing directory with user content`() {
    val output = Files.createTempDirectory("egov-existing-content-test")
    val projectDir = output.resolve("existing-content-test")
    Files.createDirectories(projectDir)
    Files.writeString(projectDir.resolve("README.md"), "user content")
    val template = ProjectTemplate("Test", "template.zip", "", "d", "Boot", "existing-content-test")
    val config = ProjectGenerator.ProjectConfig("existing-content-test", "", "", template)

    val error = assertThrows(IllegalArgumentException::class.java) {
      ProjectGenerator.generateWithProgress(
        output,
        output.resolve("template.zip"),
        config,
        allowExistingEmptyDirectory = true,
      )
    }

    assertTrue(error.message.orEmpty().contains("Project directory already exists"))
    assertEquals("user content", Files.readString(projectDir.resolve("README.md")))
  }

  @Test
  fun `missing POM preserves reused IntelliJ directory without publishing staged files`() {
    val output = Files.createTempDirectory("egov-preserve-test")
    val projectDir = output.resolve("preserve-test")
    val ideaIgnore = projectDir.resolve(".idea/.gitignore")
    Files.createDirectories(ideaIgnore.parent)
    Files.writeString(ideaIgnore, "workspace.xml\n")
    val original = Files.readAllBytes(ideaIgnore)
    val zip = zip(output, "README.md" to "hello")
    val template = ProjectTemplate("Test", "template.zip", "nonexistent-pom.xml", "d", "Boot", "preserve-test")

    val result = ProjectGenerator.generateWithProgress(
      output,
      zip,
      ProjectGenerator.ProjectConfig("preserve-test", "com.example", "demo", template),
      allowExistingEmptyDirectory = true,
    )

    assertTrue(result is GenerationResult.Failure)
    assertArrayEquals(original, Files.readAllBytes(ideaIgnore))
    assertEquals(listOf(".idea"), Files.list(projectDir).use { it.map { path -> path.fileName.toString() }.toList() })
    assertTrue(stageDirectories(output).isEmpty())
  }

  @Test
  fun `cleanup failure preserves staging path on the primary failure`() {
    val original = IllegalStateException("generation failed")
    val cleanup = IOException("cleanup failed")
    val staging = Path.of("unused-stage")

    ProjectGenerator.cleanupAfterFailure(staging, original) { throw cleanup }

    val suppressed = original.suppressed.single()
    assertEquals("generation failed", original.message)
    assertTrue(suppressed.message.orEmpty().contains(staging.toString()))
    assertSame(cleanup, suppressed.cause)
  }

  @Test
  fun `retry after failed generation succeeds`() {
    val output = Files.createTempDirectory("egov-retry-test")
    val zip = zip(output, "README.md" to "hello")
    val invalidTemplate = ProjectTemplate("Test", "template.zip", "missing-pom.xml", "d", "Boot", "retry")
    val validTemplate = ProjectTemplate("Test", "template.zip", "", "d", "Boot", "retry")

    assertTrue(ProjectGenerator.generateWithProgress(
      output,
      zip,
      ProjectGenerator.ProjectConfig("retry", "com.example", "demo", invalidTemplate),
    ) is GenerationResult.Failure)
    val result = ProjectGenerator.generateWithProgress(
      output,
      zip,
      ProjectGenerator.ProjectConfig("retry", "", "", validTemplate),
    )

    assertTrue(result is GenerationResult.Success)
    assertTrue(Files.exists(output.resolve("retry/README.md")))
  }

  @Test
  fun `staged idea is rejected before reused root mutation`() {
    val output = Files.createTempDirectory("egov-staged-idea-test")
    val projectRoot = initializeIdeaProject(output, "staged-idea")
    val ideaIgnore = projectRoot.resolve(".idea/.gitignore")
    val original = Files.readAllBytes(ideaIgnore)
    val zip = zip(output, ".idea/" to null, "README.md" to "hello")
    val template = ProjectTemplate("Test", "template.zip", "", "d", "Boot", "staged-idea")

    val result = ProjectGenerator.generateWithProgress(
      output,
      zip,
      ProjectGenerator.ProjectConfig("staged-idea", "", "", template),
      allowExistingEmptyDirectory = true,
    ) as GenerationResult.Failure

    assertTrue(result.error.contains(".idea"))
    assertArrayEquals(original, Files.readAllBytes(ideaIgnore))
    assertEquals(listOf(".idea"), Files.list(projectRoot).use { it.map { path -> path.fileName.toString() }.toList() })
    assertTrue(stageDirectories(output).isEmpty())
  }

  @Test
  fun `concurrent destination collision leaves no committed sibling and cleans staging`() {
    val output = Files.createTempDirectory("egov-collision-test")
    val projectRoot = initializeIdeaProject(output, "collision")
    val zip = zip(output, "alpha.txt" to "alpha", "bravo.txt" to "bravo")
    val fileOps = FailingProjectFileOps { call, _, target ->
      if (call == 2) {
        Files.writeString(target, "concurrent")
      }
    }
    val template = ProjectTemplate("Test", "template.zip", "", "d", "Boot", "collision")

    val result = ProjectGenerator.generateWithProgress(
      output, zip, ProjectGenerator.ProjectConfig("collision", "", "", template), true, GenerationProgress { }, fileOps,
    ) as GenerationResult.Failure

    assertTrue(result.cause is IOException)
    assertFalse(Files.exists(projectRoot.resolve("alpha.txt")))
    assertEquals("concurrent", Files.readString(projectRoot.resolve("bravo.txt")))
    assertTrue(stageDirectories(output).isEmpty())
  }

  @Test
  fun `second child move failure rolls prior child back and cleans staging`() {
    val output = Files.createTempDirectory("egov-rollback-test")
    val projectRoot = initializeIdeaProject(output, "rollback")
    val zip = zip(output, "alpha.txt" to "alpha", "bravo.txt" to "bravo")
    val fileOps = FailingProjectFileOps { call, _, _ ->
      if (call == 2) throw IOException("second child move failure")
    }
    val template = ProjectTemplate("Test", "template.zip", "", "d", "Boot", "rollback")

    val result = ProjectGenerator.generateWithProgress(
      output, zip, ProjectGenerator.ProjectConfig("rollback", "", "", template), true, GenerationProgress { }, fileOps,
    ) as GenerationResult.Failure

    assertEquals("second child move failure", result.cause?.message)
    assertEquals(listOf(".idea"), Files.list(projectRoot).use { it.map { path -> path.fileName.toString() }.toList() })
    assertTrue(stageDirectories(output).isEmpty())
  }

  @Test
  fun `rollback failure is suppressed on primary failure and cleanup is attempted`() {
    val output = Files.createTempDirectory("egov-rollback-suppression-test")
    val projectRoot = initializeIdeaProject(output, "rollback-suppression")
    val zip = zip(output, "alpha.txt" to "alpha", "bravo.txt" to "bravo")
    val fileOps = FailingProjectFileOps { call, source, _ ->
      if (call == 2 && source.fileName.toString() == "bravo.txt") throw IOException("second child move failure")
      if (call == 3 && source.fileName.toString() == "alpha.txt") throw IOException("rollback failure")
    }
    val template = ProjectTemplate("Test", "template.zip", "", "d", "Boot", "rollback-suppression")

    val result = ProjectGenerator.generateWithProgress(
      output, zip, ProjectGenerator.ProjectConfig("rollback-suppression", "", "", template), true, GenerationProgress { }, fileOps,
    ) as GenerationResult.Failure

    assertEquals("second child move failure", result.cause?.message)
    assertTrue(result.cause!!.suppressed.any { it.message == "rollback failure" })
    assertTrue(Files.exists(projectRoot.resolve("alpha.txt")))
    assertTrue(fileOps.cleanupAttempts > 0)
    assertTrue(stageDirectories(output).isEmpty())
  }

  @Test
  fun `generation cleanup failure is suppressed with preserved staging path`() {
    val output = Files.createTempDirectory("egov-cleanup-suppression-test")
    val zip = zip(output, "README.md" to "hello")
    val fileOps = FailingProjectFileOps { _, _, _ -> }.also { it.failCleanup = true }
    val template = ProjectTemplate("Test", "template.zip", "missing-pom.xml", "d", "Boot", "cleanup-suppression")

    val result = ProjectGenerator.generateWithProgress(
      output, zip, ProjectGenerator.ProjectConfig("cleanup-suppression", "com.example", "demo", template), false,
      GenerationProgress { }, fileOps,
    ) as GenerationResult.Failure

    val staging = fileOps.stagingRoot!!
    assertTrue(result.cause!!.suppressed.single().message.orEmpty().contains(staging.toString()))
    assertTrue(Files.exists(staging))
    assertFalse(Files.exists(output.resolve("cleanup-suppression")))
    Files.walk(staging).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
  }

  @Test
  fun `new project commits by one no replace move and existing target is refused`() {
    val output = Files.createTempDirectory("egov-new-commit-test")
    val staging = Files.createTempDirectory(output, ".egov-project-stage-")
    Files.writeString(staging.resolve("README.md"), "hello")
    val target = output.resolve("new-project")

    ProjectGenerator.commitStagedProject(staging, target, preExisting = false)

    assertTrue(Files.exists(target.resolve("README.md")))
    assertFalse(Files.exists(staging))
    val existingStaging = Files.createTempDirectory(output, ".egov-project-stage-")
    Files.createDirectories(output.resolve("existing-project"))
    assertThrows(IllegalArgumentException::class.java) {
      ProjectGenerator.commitStagedProject(existingStaging, output.resolve("existing-project"), preExisting = false)
    }
    assertTrue(Files.exists(existingStaging))
  }

  private fun zip(output: Path, vararg entries: Pair<String, String?>): Path {
    val zip = output.resolve("template-${System.nanoTime()}.zip")
    ZipOutputStream(Files.newOutputStream(zip)).use { stream ->
      entries.forEach { (name, contents) ->
        stream.putNextEntry(ZipEntry(name))
        contents?.let { stream.write(it.toByteArray()) }
        stream.closeEntry()
      }
    }
    return zip
  }

  private fun initializeIdeaProject(output: Path, name: String): Path {
    val root = output.resolve(name)
    Files.createDirectories(root.resolve(".idea"))
    Files.writeString(root.resolve(".idea/.gitignore"), "workspace.xml\n")
    return root
  }

  private fun stageDirectories(output: Path): List<Path> = Files.list(output).use { paths ->
    paths.filter { it.fileName.toString().startsWith(".egov-project-stage-") }.toList()
  }

  private class FailingProjectFileOps(
    private val beforeMove: (Int, Path, Path) -> Unit,
  ) : ProjectFileOps by NioProjectFileOps {
    var stagingRoot: Path? = null
    var cleanupAttempts = 0
    var failCleanup = false
    private var moveCalls = 0

    override fun createTempDirectory(directory: Path, prefix: String): Path =
      NioProjectFileOps.createTempDirectory(directory, prefix).also { stagingRoot = it }

    override fun moveNoReplace(source: Path, target: Path) {
      moveCalls += 1
      beforeMove(moveCalls, source, target)
      NioProjectFileOps.moveNoReplace(source, target)
    }

    override fun deleteRecursively(directory: Path) {
      cleanupAttempts += 1
      if (failCleanup) throw IOException("cleanup failure")
      NioProjectFileOps.deleteRecursively(directory)
    }
  }
  @Test
  fun `error messages fall back to the exception type`() {
    assertEquals("IllegalStateException", IllegalStateException().messageOrTypeName())
    assertEquals("IllegalStateException", IllegalStateException("   ").messageOrTypeName())
    assertEquals("specific", IllegalStateException("specific").messageOrTypeName())
  }

}
