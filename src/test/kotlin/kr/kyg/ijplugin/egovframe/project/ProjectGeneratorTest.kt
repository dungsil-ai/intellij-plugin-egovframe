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
  fun `generateWithProgress cleans up on failure for new directory`() {
    val output = Files.createTempDirectory("egov-cleanup-test")
    val zip = output.resolve("template.zip")
    ZipOutputStream(Files.newOutputStream(zip)).use { stream ->
      stream.putNextEntry(ZipEntry("README.md"))
      stream.write("hello".toByteArray())
      stream.closeEntry()
    }
    // Reference a nonexistent POM to trigger failure after extraction
    val template = ProjectTemplate("Test", "template.zip", "nonexistent-pom.xml", "d", "Boot", "cleanup-test")
    val config = ProjectGenerator.ProjectConfig("cleanup-test", "com.example", "demo", template)

    val result = ProjectGenerator.generateWithProgress(output, zip, config)

    assertTrue(result is GenerationResult.Failure, "Should fail due to missing POM template")
    assertFalse(Files.exists(output.resolve("cleanup-test")), "Newly created project dir should be cleaned up")
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
  fun `generateWithProgress preserves pre-existing empty directory on failure`() {
    val output = Files.createTempDirectory("egov-preserve-test")
    val projectDir = output.resolve("preserve-test")
    Files.createDirectories(projectDir)
    val zip = output.resolve("template.zip")
    ZipOutputStream(Files.newOutputStream(zip)).use { stream ->
      stream.putNextEntry(ZipEntry("README.md"))
      stream.write("hello".toByteArray())
      stream.closeEntry()
    }
    // Reference a nonexistent POM to trigger failure after extraction
    val template = ProjectTemplate("Test", "template.zip", "nonexistent-pom.xml", "d", "Boot", "preserve-test")
    val config = ProjectGenerator.ProjectConfig("preserve-test", "com.example", "demo", template)

    val result = ProjectGenerator.generateWithProgress(output, zip, config, allowExistingEmptyDirectory = true)

    assertTrue(result is GenerationResult.Failure, "Should fail due to missing POM template")
    assertTrue(Files.exists(projectDir), "Pre-existing directory should be preserved")
  }

  @Test
  fun `cleanup failure is suppressed without masking the generation failure`() {
    val original = IllegalStateException("generation failed")
    val cleanup = IOException("cleanup failed")

    ProjectGenerator.cleanupAfterFailure(Path.of("unused"), original) { throw cleanup }

    assertSame(cleanup, original.suppressed.single())
    assertEquals("generation failed", original.message)
  }

  @Test
  fun `error messages fall back to the exception type`() {
    assertEquals("IllegalStateException", IllegalStateException().messageOrTypeName())
    assertEquals("IllegalStateException", IllegalStateException("   ").messageOrTypeName())
    assertEquals("specific", IllegalStateException("specific").messageOrTypeName())
  }

}
