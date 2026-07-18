package kr.kyg.ijplugin.egovframe.assets

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URLConnection
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipInputStream

@Tag("remoteZip")
class RemoteZipTest {

  @Test
  fun `legacy remote media sources retain correct SHA size and ZIP readability`() {
    val remoteZips = AssetManifest.instance.zips.filterKeys { it !in ORIGINAL_BUNDLED_ZIPS }
    assertEquals(20, remoteZips.size, "legacy remote ZIP count")

    val failures = mutableListOf<String>()
    remoteZips.forEach { (name, zip) ->
      try {
        val bytes = cachedDownload(name, zip.mediaUrl, zip.sha256)

        if (bytes.size.toLong() != zip.size) {
          failures.add("$name: size mismatch (expected=${zip.size}, actual=${bytes.size})")
          return@forEach
        }

        val actualSha = sha256(bytes)
        if (!actualSha.equals(zip.sha256, ignoreCase = true)) {
          failures.add("$name: SHA-256 mismatch (expected=${zip.sha256}, actual=$actualSha)")
          return@forEach
        }

        ZipInputStream(bytes.inputStream()).use { zis ->
          var entries = 0
          while (zis.nextEntry != null) {
            entries++
            zis.closeEntry()
          }
          if (entries == 0) {
            failures.add("$name: ZIP contains no entries")
          }
        }
      } catch (e: Exception) {
        failures.add("$name: ${e.javaClass.simpleName}: ${e.message}")
      }
    }

    assertTrue(failures.isEmpty(), "Remote ZIP verification failures:\n${failures.joinToString("\n")}")
  }

  @Test
  fun `cache download configures timeouts and publishes only verified bytes`() {
    val root = Files.createTempDirectory("egovframe-remote-cache-test-")
    try {
      val expected = "complete ZIP bytes".toByteArray()
      val expectedSha = sha256(expected)
      val connection = InMemoryConnection(expected)

      val actual = cachedDownload("sample.zip", "https://example.invalid/sample.zip", expectedSha, root) {
        connection
      }

      assertTrue(expected.contentEquals(actual))
      assertEquals(CONNECT_TIMEOUT_MILLIS, connection.connectTimeout)
      assertEquals(READ_TIMEOUT_MILLIS, connection.readTimeout)
      assertTrue(expected.contentEquals(Files.readAllBytes(root.resolve(expectedSha).resolve("sample.zip"))))
    } finally {
      deleteRecursively(root)
    }
  }

  @Test
  fun `interrupted download leaves neither cache entry nor temporary file`() {
    val root = Files.createTempDirectory("egovframe-remote-cache-failure-")
    val expectedSha = sha256("complete ZIP bytes".toByteArray())
    try {
      assertThrows(IOException::class.java) {
        cachedDownload("sample.zip", "https://example.invalid/sample.zip", expectedSha, root) {
          InMemoryConnection("partial".toByteArray(), failAfter = 3)
        }
      }

      val cacheParent = root.resolve(expectedSha)
      assertFalse(Files.exists(cacheParent.resolve("sample.zip")))
      if (Files.isDirectory(cacheParent)) {
        Files.list(cacheParent).use { assertFalse(it.findAny().isPresent) }
      }
    } finally {
      deleteRecursively(root)
    }
  }

  private fun cachedDownload(
    name: String,
    url: String,
    expectedSha: String,
    root: Path = cacheDir,
    openConnection: (String) -> URLConnection = { URI(it).toURL().openConnection() },
  ): ByteArray {
    val cached = root.resolve(expectedSha).resolve(name)
    if (Files.isRegularFile(cached)) {
      val bytes = Files.readAllBytes(cached)
      if (sha256(bytes).equals(expectedSha, ignoreCase = true)) return bytes
    }

    Files.createDirectories(cached.parent)
    val temporary = Files.createTempFile(cached.parent, ".$name.", ".tmp")
    try {
      val connection = openConnection(url).apply {
        connectTimeout = CONNECT_TIMEOUT_MILLIS
        readTimeout = READ_TIMEOUT_MILLIS
        useCaches = false
      }
      connection.getInputStream().use { input ->
        Files.newOutputStream(temporary).use { output -> input.copyTo(output) }
      }
      val bytes = Files.readAllBytes(temporary)
      require(sha256(bytes).equals(expectedSha, ignoreCase = true)) {
        "Downloaded ZIP SHA-256 mismatch: $name"
      }
      try {
        Files.move(
          temporary,
          cached,
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING,
        )
      } catch (_: AtomicMoveNotSupportedException) {
        Files.move(temporary, cached, StandardCopyOption.REPLACE_EXISTING)
      }
      return bytes
    } finally {
      Files.deleteIfExists(temporary)
    }
  }

  private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { "%02x".format(it.toInt() and 0xff) }

  private fun deleteRecursively(root: Path) {
    if (!Files.exists(root)) return
    Files.walk(root).use { paths ->
      paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
    }
  }

  private class InMemoryConnection(
    private val bytes: ByteArray,
    private val failAfter: Int? = null,
  ) : URLConnection(URI("https://example.invalid").toURL()) {
    override fun connect() {
      connected = true
    }

    override fun getInputStream(): InputStream {
      connect()
      val delegate = ByteArrayInputStream(bytes)
      val limit = failAfter ?: return delegate
      return object : InputStream() {
        private var count = 0

        override fun read(): Int {
          if (count >= limit) throw IOException("interrupted download")
          return delegate.read().also { if (it >= 0) count++ }
        }
      }
    }
  }

  private companion object {
    const val CONNECT_TIMEOUT_MILLIS = 30_000
    const val READ_TIMEOUT_MILLIS = 120_000

    val ORIGINAL_BUNDLED_ZIPS = setOf(
      "egovframe-boot-web.zip",
      "egovframe-boot-simple-backend.zip",
    )

    val cacheDir: Path = Path.of(
      System.getProperty(
        "egovframe.test.remoteZip.cacheDir",
        "${System.getProperty("java.io.tmpdir")}/egovframe-remote-zip-cache",
      ),
    )
  }
}
