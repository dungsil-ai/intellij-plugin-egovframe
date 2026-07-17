package kr.kyg.intellij.plugin.egovframe.assets

import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest

class TemplateStore internal constructor(
  private val cacheRoot: Path,
  private val fetch: (url: String) -> ByteArray,
  private val manifest: AssetManifest,
  private val bundledResource: (path: String) -> ByteArray,
) {
  constructor(cacheRoot: Path, fetch: (url: String) -> ByteArray) : this(
    cacheRoot,
    fetch,
    AssetManifest.instance,
    EgovAssets::resourceBytes,
  )

  fun zipPath(zipName: String): Path {
    val zip = manifest.zips[zipName]
      ?: throw IllegalArgumentException("Unknown eGovFrame template ZIP: $zipName")
    return cacheRoot.resolve(zip.sha256).resolve(zipName)
  }

  @Synchronized
  @Throws(IOException::class)
  fun ensure(zipName: String): Path {
    val zip = manifest.zips[zipName]
      ?: throw IllegalArgumentException("Unknown eGovFrame template ZIP: $zipName")
    val destination = zipPath(zipName)
    if (Files.isRegularFile(destination)) {
      if (hasExpectedSha(destination, zip.sha256)) return destination
      Files.delete(destination)
    }

    val bytes = if (zip.bundled) {
      bundledResource("${EgovAssets.EXAMPLES_DIR}/$zipName")
    } else {
      fetch(zip.mediaUrl)
    }
    requireExpectedSha(bytes, zip.sha256)
    writeAtomically(destination, bytes)
    if (!hasExpectedSha(destination, zip.sha256)) {
      val actual = sha256(Files.readAllBytes(destination))
      Files.deleteIfExists(destination)
      throw IOException("SHA-256 mismatch: expected ${zip.sha256}, actual $actual")
    }
    return destination
  }

  fun isAvailableOffline(zipName: String): Boolean {
    val zip = manifest.zips[zipName] ?: return false
    if (zip.bundled) return true
    val destination = zipPath(zipName)
    return Files.isRegularFile(destination) && hasExpectedSha(destination, zip.sha256)
  }

  private fun writeAtomically(destination: Path, bytes: ByteArray) {
    Files.createDirectories(destination.parent)
    val temporary = Files.createTempFile(destination.parent, ".${destination.fileName}", ".tmp")
    try {
      Files.write(temporary, bytes)
      try {
        Files.move(temporary, destination, ATOMIC_MOVE, REPLACE_EXISTING)
      } catch (_: AtomicMoveNotSupportedException) {
        Files.move(temporary, destination, REPLACE_EXISTING)
      }
    } finally {
      Files.deleteIfExists(temporary)
    }
  }

  private fun hasExpectedSha(path: Path, expected: String): Boolean =
    sha256(Files.readAllBytes(path)).equals(expected, ignoreCase = true)

  private fun requireExpectedSha(bytes: ByteArray, expected: String) {
    val actual = sha256(bytes)
    if (!actual.equals(expected, ignoreCase = true)) {
      throw IOException("SHA-256 mismatch: expected $expected, actual $actual")
    }
  }

  companion object {
    internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
      .digest(bytes)
      .joinToString("") { "%02x".format(it.toInt() and 0xff) }
  }
}
