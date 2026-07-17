package kr.kyg.intellij.plugin.egovframe.project

import kr.kyg.intellij.plugin.egovframe.assets.ProjectTemplate
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
}
