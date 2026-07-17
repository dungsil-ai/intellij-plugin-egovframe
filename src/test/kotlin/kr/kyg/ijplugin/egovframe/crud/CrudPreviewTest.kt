package kr.kyg.ijplugin.egovframe.crud

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class CrudPreviewTest {

  @Test
  fun `language to file type mapping covers all artifact languages`() {
    val languages = CrudArtifact.entries.map(CrudArtifact::language).toSet()
    assertEquals(setOf("java", "xml", "html"), languages)

    for (language in languages) {
      val fileType = CrudArtifact.fileTypeForLanguage(language)
      assertNotNull(fileType)
    }
  }

  @Test
  fun `unknown language falls back to plain text`() {
    val fileType = CrudArtifact.fileTypeForLanguage("unknown")
    assertTrue(fileType.name.contains("PLAIN_TEXT") || fileType.name.contains("Plain"))
  }

  @Test
  fun `every artifact has a known language`() {
    for (artifact in CrudArtifact.entries) {
      assertTrue(artifact.language in setOf("java", "xml", "html")) {
        "${artifact.name} has unexpected language: ${artifact.language}"
      }
    }
  }
}
