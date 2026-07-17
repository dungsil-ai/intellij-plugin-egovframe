package kr.kyg.ijplugin.egovframe.assets

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipInputStream

@Tag("remoteZip")
class RemoteZipTest {

  @Test
  fun `every remote ZIP downloads with correct SHA and manifest size and is readable`() {
    val remoteZips = AssetManifest.instance.zips.filterValues { !it.bundled }
    assertEquals(20, remoteZips.size, "remote ZIP count")

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

  private fun cachedDownload(name: String, url: String, expectedSha: String): ByteArray {
    val cached = cacheDir.resolve(expectedSha).resolve(name)
    if (Files.isRegularFile(cached)) {
      val bytes = Files.readAllBytes(cached)
      if (sha256(bytes).equals(expectedSha, ignoreCase = true)) return bytes
    }
    val bytes = URI(url).toURL().openStream().use { it.readBytes() }
    Files.createDirectories(cached.parent)
    Files.write(cached, bytes)
    return bytes
  }

  private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { "%02x".format(it.toInt() and 0xff) }

  private companion object {
    val cacheDir: Path = Path.of(
      System.getProperty(
        "egovframe.test.remoteZip.cacheDir",
        "${System.getProperty("java.io.tmpdir")}/egovframe-remote-zip-cache",
      ),
    )
  }
}
