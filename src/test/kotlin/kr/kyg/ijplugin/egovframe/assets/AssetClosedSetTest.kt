package kr.kyg.ijplugin.egovframe.assets

import kr.kyg.ijplugin.egovframe.crud.CrudArtifact

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AssetClosedSetTest {

  @Test
  fun `asset counts match the pinned baseline`() {
    val manifest = AssetManifest.instance
    val assets = manifest.assets.keys
    assertEquals(11, assets.count { it.startsWith("${EgovAssets.CODE_DIR}/") && it.endsWith(".hbs") }, "code HBS count")
    assertEquals(52, assets.count { it.startsWith("${EgovAssets.CONFIG_DIR}/") && it.endsWith(".hbs") }, "config HBS count")
    assertEquals(18, assets.count { it.startsWith("${EgovAssets.POM_DIR}/") && it.endsWith(".xml") }, "POM count")
    assertEquals(22, manifest.zips.size, "ZIP count")
    assertEquals(21, TemplateCatalog.configs.size, "config entry count")
  }

  @Test
  fun `all code HBS resources are exactly the eleven CRUD artifact templates`() {
    val manifestCode = AssetManifest.instance.assets.keys
      .filter { it.startsWith("${EgovAssets.CODE_DIR}/") && it.endsWith(".hbs") }
      .map { it.removePrefix("${EgovAssets.CODE_DIR}/") }
      .toSet()
    val artifactCode = CrudArtifact.entries.map { it.templateFile }.toSet()

    assertEquals(11, artifactCode.size)
    assertEquals(artifactCode, manifestCode)
  }

  // --- ZIP ↔ catalog bidirectional ---

  @Test
  fun `every catalog project has a manifest ZIP entry`() {
    val manifestZips = AssetManifest.instance.zips.keys
    val catalogZips = TemplateCatalog.projects.map(ProjectTemplate::fileName).toSet()
    val missing = catalogZips - manifestZips
    assertTrue(missing.isEmpty(), "Catalog projects without manifest ZIP: $missing")
  }

  @Test
  fun `every manifest ZIP has a catalog project entry`() {
    val manifestZips = AssetManifest.instance.zips.keys
    val catalogZips = TemplateCatalog.projects.map(ProjectTemplate::fileName).toSet()
    val orphan = manifestZips - catalogZips
    assertTrue(orphan.isEmpty(), "Manifest ZIPs without catalog project: $orphan")
  }

  // --- POM ↔ resource bidirectional ---

  @Test
  fun `every catalog nonblank POM resolves to a bundled resource`() {
    val pomFiles = TemplateCatalog.projects.map(ProjectTemplate::pomFile).filter(String::isNotBlank)
    assertEquals(18, pomFiles.size, "nonblank POM count")
    pomFiles.forEach { pom ->
      assertNotNull(
        EgovAssets::class.java.classLoader.getResource("${EgovAssets.POM_DIR}/$pom"),
        "Catalog POM missing from resources: $pom",
      )
    }
  }

  @Test
  fun `every manifest POM is referenced by a catalog project`() {
    val manifestPoms = AssetManifest.instance.assets.keys
      .filter { it.startsWith("${EgovAssets.POM_DIR}/") && it.endsWith(".xml") }
      .map { it.removePrefix("${EgovAssets.POM_DIR}/") }
      .toSet()
    val catalogPoms = TemplateCatalog.projects.map(ProjectTemplate::pomFile).filter(String::isNotBlank).toSet()
    val orphan = manifestPoms - catalogPoms
    assertTrue(orphan.isEmpty(), "Manifest POMs without catalog reference: $orphan")
  }

  // --- Config entry slot ↔ resource bidirectional ---

  @Test
  fun `config entries produce 51 nonblank template slots with 50 resolvable and 1 known phantom`() {
    val knownPhantom = setOf("logging/timeBasedRollingFile-java.hbs")

    val allSlots = mutableListOf<String>()
    TemplateCatalog.configs.forEach { config ->
      allSlots.add("${config.templateFolder}/${config.templateFile}")
      if (config.javaConfigTemplate.isNotBlank()) allSlots.add("${config.templateFolder}/${config.javaConfigTemplate}")
      if (config.yamlTemplate.isNotBlank()) allSlots.add("${config.templateFolder}/${config.yamlTemplate}")
      if (config.propertiesTemplate.isNotBlank()) allSlots.add("${config.templateFolder}/${config.propertiesTemplate}")
    }
    assertEquals(51, allSlots.size, "total nonblank template slots")

    val resolved = allSlots.filter { slot ->
      EgovAssets::class.java.classLoader.getResource("${EgovAssets.CONFIG_DIR}/$slot") != null
    }
    val unresolved = allSlots.toSet() - resolved.toSet()
    assertEquals(50, resolved.size, "resolvable slots")
    assertEquals(knownPhantom, unresolved, "unresolved slots must match known phantom allowlist")
  }

  // --- Config HBS orphan allowlist ---

  @Test
  fun `all 52 config HBS files are either catalog-referenced or in the known orphan allowlist`() {
    val knownOrphans = setOf(
      "cache/cache-java.hbs",
      "logging/dailyRollingFile-java.hbs",
    )

    val catalogSlots = mutableSetOf<String>()
    TemplateCatalog.configs.forEach { config ->
      catalogSlots.add("${config.templateFolder}/${config.templateFile}")
      if (config.javaConfigTemplate.isNotBlank()) catalogSlots.add("${config.templateFolder}/${config.javaConfigTemplate}")
      if (config.yamlTemplate.isNotBlank()) catalogSlots.add("${config.templateFolder}/${config.yamlTemplate}")
      if (config.propertiesTemplate.isNotBlank()) catalogSlots.add("${config.templateFolder}/${config.propertiesTemplate}")
    }

    val resourceHbs = AssetManifest.instance.assets.keys
      .filter { it.startsWith("${EgovAssets.CONFIG_DIR}/") && it.endsWith(".hbs") }
      .map { it.removePrefix("${EgovAssets.CONFIG_DIR}/") }
      .toSet()

    assertEquals(52, resourceHbs.size, "raw config HBS count")

    val unreferenced = resourceHbs - catalogSlots
    assertEquals(knownOrphans, unreferenced, "unreferenced config HBS must match known orphan allowlist")
  }
}
