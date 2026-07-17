package kr.kyg.intellij.plugin.egovframe.assets

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class TemplateStoreTest {
  @Test
  fun `bundled zip is copied into cache without fetching`() = withTemporaryDirectory { root ->
    val bytes = "bundled fixture".encodeToByteArray()
    val zipName = "bundled.zip"
    val store = store(root, zipName, bytes, bundled = true, fetch = { error("fetch must not run") })

    val path = store.ensure(zipName)

    assertEquals(root.resolve(TemplateStore.sha256(bytes)).resolve(zipName), path)
    assertArrayEquals(bytes, Files.readAllBytes(path))
    assertTrue(store.isAvailableOffline(zipName))
  }

  @Test
  fun `remote zip downloads once then reuses validated cache`() = withTemporaryDirectory { root ->
    val bytes = "remote fixture".encodeToByteArray()
    val zipName = "remote.zip"
    var fetchCalls = 0
    val store = store(root, zipName, bytes, bundled = false) {
      fetchCalls += 1
      bytes
    }

    assertFalse(store.isAvailableOffline(zipName))
    val first = store.ensure(zipName)
    val second = store.ensure(zipName)

    assertEquals(first, second)
    assertEquals(1, fetchCalls)
    assertArrayEquals(bytes, Files.readAllBytes(first))
    assertTrue(store.isAvailableOffline(zipName))
  }

  @Test
  fun `corrupt cache entry is replaced from its source`() = withTemporaryDirectory { root ->
    val bytes = "replacement fixture".encodeToByteArray()
    val zipName = "replace.zip"
    var fetchCalls = 0
    val store = store(root, zipName, bytes, bundled = false) {
      fetchCalls += 1
      bytes
    }
    val corruptPath = store.zipPath(zipName)
    Files.createDirectories(corruptPath.parent)
    Files.write(corruptPath, "corrupt".encodeToByteArray())

    val resolved = store.ensure(zipName)

    assertEquals(1, fetchCalls)
    assertArrayEquals(bytes, Files.readAllBytes(resolved))
  }

  @Test
  fun `sha mismatch from download includes expected and actual hashes`() = withTemporaryDirectory { root ->
    val expected = "expected fixture".encodeToByteArray()
    val actual = "corrupt fixture".encodeToByteArray()
    val zipName = "mismatch.zip"
    val store = store(root, zipName, expected, bundled = false) { actual }

    try {
      store.ensure(zipName)
      throw AssertionError("Expected IOException")
    } catch (error: IOException) {
      assertTrue(error.message!!.contains(TemplateStore.sha256(expected)))
      assertTrue(error.message!!.contains(TemplateStore.sha256(actual)))
    }
  }

  private fun store(
    root: Path,
    zipName: String,
    content: ByteArray,
    bundled: Boolean,
    fetch: (String) -> ByteArray,
  ): TemplateStore {
    val hash = TemplateStore.sha256(content)
    val manifest = AssetManifest(
      assets = emptyMap(),
      zips = mapOf(zipName to ZipAsset(hash, content.size.toLong(), "https://example.invalid/$zipName", bundled)),
      upstreamTag = "test",
      mediaUrlBase = "https://example.invalid",
    )
    return TemplateStore(root, fetch, manifest) { content }
  }

  private fun withTemporaryDirectory(block: (Path) -> Unit) {
    val root = Files.createTempDirectory("egovframe-template-store-")
    try {
      block(root)
    } finally {
      Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }
  }
}
