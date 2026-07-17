package kr.kyg.ijplugin.egovframe.assets

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

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
  fun `18 templates have a POM and 4 do not`() {
    val withPom = TemplateCatalog.projects.count { it.pomFile.isNotBlank() }
    val withoutPom = TemplateCatalog.projects.count { it.pomFile.isBlank() }
    assertEquals(18, withPom)
    assertEquals(4, withoutPom)
  }

  @Test
  fun `every catalog project fileName exists in manifest zips`() {
    val manifest = AssetManifest.instance
    TemplateCatalog.projects.forEach { template ->
      assertTrue(manifest.zips.containsKey(template.fileName), "Missing ZIP in manifest: ${template.fileName}")
    }
  }

  @Test
  fun `all 22 manifest zips correspond to catalog entries`() {
    val catalogFileNames = TemplateCatalog.projects.map(ProjectTemplate::fileName).toSet()
    AssetManifest.instance.zips.keys.forEach { zipName ->
      assertTrue(catalogFileNames.contains(zipName), "Manifest ZIP not in catalog: $zipName")
    }
  }

  @Test
  fun `upstream catalog references absent time based rolling java template`() {
    val reference = TemplateCatalog.configs.single { it.templateFile == "timeBasedRollingFile.hbs" }.javaConfigTemplate
    assertEquals("timeBasedRollingFile-java.hbs", reference)
    assertFalse(
      EgovAssets::class.java.classLoader.getResource("${EgovAssets.CONFIG_DIR}/logging/$reference") != null,
      "Pinned upstream catalog references a Java template absent from the bundled upstream assets",
    )
  }
}
