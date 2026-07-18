package kr.kyg.ijplugin.egovframe

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

object SymlinkTestSupport {
  private val strict: Boolean
    get() = System.getProperty("egovframe.test.symlink.strict")?.toBooleanStrictOrNull() == true

  fun createSymbolicLinkOrSkip(link: Path, target: Path) {
    try {
      Files.createSymbolicLink(link, target)
    } catch (error: Exception) {
      val isPermissionIssue = error is UnsupportedOperationException || error is IOException || error is SecurityException
      if (isPermissionIssue && strict) {
        throw AssertionError(
          "Symbolic link creation must succeed in strict mode (egovframe.test.symlink.strict=true): ${error.message}",
          error,
        )
      }
      assumeTrue(
        !isPermissionIssue,
        "Symbolic links are not supported in this environment: ${error.message}",
      )
      throw error
    }
  }
}
