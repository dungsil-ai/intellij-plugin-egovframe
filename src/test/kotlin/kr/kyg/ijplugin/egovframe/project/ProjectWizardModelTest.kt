package kr.kyg.ijplugin.egovframe.project

import kr.kyg.ijplugin.egovframe.assets.ProjectTemplate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ProjectWizardModelTest {

  private val pomTemplate = ProjectTemplate(
    displayName = "Boot Web Project",
    fileName = "egovframe-boot-web.zip",
    pomFile = "egovframe-boot-web-pom.xml",
    description = "test",
    category = "Boot",
    projectName = "egov-boot-web",
  )

  private val noPomTemplate = ProjectTemplate(
    displayName = "MSA Frontend",
    fileName = "egovframe-msa-portal-frontend.zip",
    pomFile = "",
    description = "test",
    category = "MSA",
    projectName = "egov-msa-frontend",
  )

  @Test
  fun `selectTemplate resets fields and output to upstream defaults`() {
    val model = ProjectWizardModel("egovframework.com", "egovframe-project", "C:/initial")
    model.groupId = "custom.group"
    model.artifactId = "custom-artifact"
    model.outputPath = "C:/changed"

    model.selectTemplate(pomTemplate)

    assertEquals("egov-boot-web", model.projectName)
    assertEquals("egovframework.com", model.groupId)
    assertEquals("egovframe-project", model.artifactId)
    assertEquals("C:/initial", model.outputPath)
  }

  @Test
  fun `selectTemplate resets when switching between templates`() {
    val model = ProjectWizardModel(initialOutputPath = "C:/initial")
    model.selectTemplate(pomTemplate)
    model.groupId = "modified.group"
    model.artifactId = "modified-artifact"
    model.outputPath = "C:/changed"

    model.selectTemplate(noPomTemplate)

    assertEquals("egov-msa-frontend", model.projectName)
    assertEquals("egovframework.com", model.groupId)
    assertEquals("egovframe-project", model.artifactId)
    assertEquals("C:/initial", model.outputPath)
  }

  @Test
  fun `deriveFromProjectName splits on last dot`() {
    val model = ProjectWizardModel()
    model.selectTemplate(pomTemplate)

    model.deriveFromProjectName("com.example.demo")

    assertEquals("com.example", model.groupId)
    assertEquals("demo", model.artifactId)
  }

  @Test
  fun `deriveFromProjectName with no dot uses default groupId`() {
    val model = ProjectWizardModel("egovframework.com", "egovframe-project")
    model.selectTemplate(pomTemplate)

    model.deriveFromProjectName("simple-project")

    assertEquals("egovframework.com", model.groupId)
    assertEquals("simple-project", model.artifactId)
  }

  @Test
  fun `deriveFromProjectName with blank resets to defaults`() {
    val model = ProjectWizardModel("egovframework.com", "egovframe-project")
    model.selectTemplate(pomTemplate)

    model.deriveFromProjectName("")

    assertEquals("egovframework.com", model.groupId)
    assertEquals("egovframe-project", model.artifactId)
  }

  @Test
  fun `hasPom is true for templates with pomFile`() {
    val model = ProjectWizardModel()
    model.selectTemplate(pomTemplate)
    assertTrue(model.hasPom)
  }

  @Test
  fun `hasPom is false for no-POM templates`() {
    val model = ProjectWizardModel()
    model.selectTemplate(noPomTemplate)
    assertFalse(model.hasPom)
  }

  @Test
  fun `validate skips Maven fields for no-POM templates`() {
    val model = ProjectWizardModel()
    model.selectTemplate(noPomTemplate)
    model.groupId = "INVALID_GROUP"
    model.artifactId = "INVALID_ARTIFACT"

    val errors = model.validate()

    assertTrue(errors.isEmpty(), "No-POM template should not validate Maven fields: $errors")
  }

  @Test
  fun `validate reports Maven field errors for POM templates`() {
    val model = ProjectWizardModel()
    model.selectTemplate(pomTemplate)
    model.groupId = "INVALID"
    model.artifactId = "ALSO_INVALID"

    val errors = model.validate()

    assertTrue(errors.any { it.contains("groupId") }, "Should report groupId error")
    assertTrue(errors.any { it.contains("artifactId") }, "Should report artifactId error")
  }

  @Test
  fun `validate succeeds with valid fields`() {
    val model = ProjectWizardModel()
    model.selectTemplate(pomTemplate)
    model.groupId = "com.example"
    model.artifactId = "demo-app"

    assertEquals(emptyList<String>(), model.validate())
  }
}
