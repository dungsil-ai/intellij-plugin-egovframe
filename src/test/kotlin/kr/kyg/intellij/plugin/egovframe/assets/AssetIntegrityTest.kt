package kr.kyg.intellij.plugin.egovframe.assets

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AssetIntegrityTest {
  @Test
  fun `manifest is pinned to the official upstream version tag`() {
    val manifest = AssetManifest.instance
    assertEquals("v5.0.6", manifest.upstreamTag)
    manifest.zips.values.forEach { zip ->
      assertTrue(zip.mediaUrl.contains("/v5.0.6/"), "ZIP URL is not pinned to v5.0.6: ${zip.mediaUrl}")
    }
  }

  @Test
  fun `every manifest asset retains its recorded sha256`() {
    val manifest = AssetManifest.instance
    manifest.assets.forEach { (path, asset) ->
      assertEquals(asset.sha256, TemplateStore.sha256(EgovAssets.resourceBytes(path)), "SHA-256 mismatch for $path")
    }
  }

  @Test
  fun `bundled asset inventory has expected counts`() {
    val paths = AssetManifest.instance.assets.keys
    assertEquals(11, paths.count { it.startsWith("${EgovAssets.CODE_DIR}/") && it.endsWith(".hbs") })
    assertEquals(52, paths.count { it.startsWith("${EgovAssets.CONFIG_DIR}/") && it.endsWith(".hbs") })
    assertEquals(18, paths.count { it.startsWith("${EgovAssets.POM_DIR}/") && it.endsWith(".xml") })
    assertEquals(2, paths.count { it == EgovAssets.PROJECT_CATALOG || it == EgovAssets.CONFIG_CATALOG })
    assertEquals(22, AssetManifest.instance.zips.size)
    assertEquals(2, AssetManifest.instance.zips.values.count(ZipAsset::bundled))
  }

  @Test
  fun `bundled zip resources retain their recorded sha256`() {
    AssetManifest.instance.zips
      .filterValues(ZipAsset::bundled)
      .forEach { (zipName, zip) ->
        val bytes = EgovAssets.resourceBytes("${EgovAssets.EXAMPLES_DIR}/$zipName")
        assertEquals(zip.sha256, TemplateStore.sha256(bytes), "SHA-256 mismatch for bundled ZIP $zipName")
      }
  }
}
