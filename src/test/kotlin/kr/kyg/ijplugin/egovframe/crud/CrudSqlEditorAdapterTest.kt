package kr.kyg.ijplugin.egovframe.crud

import com.intellij.openapi.editor.markup.HighlighterLayer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class CrudSqlEditorAdapterTest {

  @Test
  fun `model-originated document updates do not become pending user input`() {
    val model = CrudEditorModel()
    val guard = DocumentUpdateGuard()
    val sample = CrudSampleCatalog.forDialect(SqlDialect.MYSQL).first()
    model.selectSample(sample)

    guard.applyModelUpdate {
      guard.onDocumentChanged {
        model.setSqlText("unexpected user input")
      }
    }

    assertEquals(sample.ddl, model.sqlText)
    assertFalse(model.isPendingInput)
  }

  @Test
  fun `user-originated document updates remain observable`() {
    val model = CrudEditorModel()
    val guard = DocumentUpdateGuard()

    guard.onDocumentChanged {
      model.setSqlText("CREATE TABLE users (id INT);")
    }

    assertEquals("CREATE TABLE users (id INT);", model.sqlText)
    assertEquals(true, model.isPendingInput)
  }

  @Test
  fun `syntax refresh preserves diagnostic marker layers`() {
    assertEquals(true, isSyntaxHighlighter(HighlighterLayer.SYNTAX))
    assertFalse(isSyntaxHighlighter(HighlighterLayer.ERROR))
  }

  @Test
  fun `EOF diagnostics map to the final visible character`() {
    assertEquals(4 to 5, diagnosticMarkerRange(documentLength = 5, diagnosticOffset = 5))
  }
}
