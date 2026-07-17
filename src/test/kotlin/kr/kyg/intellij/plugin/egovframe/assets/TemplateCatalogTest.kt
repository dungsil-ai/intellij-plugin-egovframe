package kr.kyg.intellij.plugin.egovframe.assets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TemplateCatalogTest {
  @Test
  fun `catalog sizes and project category order match upstream`() {
    assertEquals(22, TemplateCatalog.projects.size)
    assertEquals(21, TemplateCatalog.configs.size)
    assertEquals(listOf("Web", "Template", "Boot", "MSA", "Mobile", "Batch", "AI"), TemplateCatalog.projectCategories())
  }

  @Test
  fun `only the four non Maven projects have an empty pom file`() {
    assertEquals(
      setOf(
        "egovframe-boot-simple-frontend.zip",
        "egovframe-msa-common-components.zip",
        "egovframe-msa-portal-backend.zip",
        "egovframe-msa-portal-frontend.zip",
      ),
      TemplateCatalog.projects.filter { it.pomFile.isEmpty() }.map(ProjectTemplate::fileName).toSet(),
    )
  }

  @Test
  fun `upstream catalog references absent time based rolling java template`() {
    val reference = TemplateCatalog.configs.single { it.templateFile == "timeBasedRollingFile.hbs" }.javaConfigTemplate
    assertEquals("timeBasedRollingFile-java.hbs", reference)
    assertFalse(
      "Pinned upstream catalog references a Java template absent from the bundled upstream assets",
      EgovAssets::class.java.classLoader.getResource("${EgovAssets.CONFIG_DIR}/logging/$reference") != null,
    )
  }
}
