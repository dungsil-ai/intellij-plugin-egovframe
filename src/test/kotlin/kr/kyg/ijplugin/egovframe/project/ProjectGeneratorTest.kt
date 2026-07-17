package kr.kyg.ijplugin.egovframe.project

import kr.kyg.ijplugin.egovframe.assets.ProjectTemplate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
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

    val projectRoot = ProjectGenerator.generate(output, zip, config)
    assertTrue(Files.isRegularFile(projectRoot.resolve("src/main/resources/application.properties")))
    val pom = Files.readString(projectRoot.resolve("pom.xml"))
    assertTrue(pom.contains("<groupId>com.example</groupId>"))
    assertTrue(pom.contains("<artifactId>demo-app</artifactId>"))
    assertFalse(pom.contains("###"))
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
}
