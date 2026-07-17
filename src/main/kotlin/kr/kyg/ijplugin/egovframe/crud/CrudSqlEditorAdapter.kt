package kr.kyg.ijplugin.egovframe.crud

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.util.Disposer
import kr.kyg.ijplugin.egovframe.ddl.DdlSyntaxDiagnostics
import java.awt.Color
import java.awt.Font
import javax.swing.JComponent

/**
 * Lightweight SQL editor adapter using IDEA Community platform API only.
 * Provides per-dialect syntax highlighting and diagnostic markers.
 * Does NOT depend on com.intellij.sql, database plugin, or any installable DB plugin.
 */
internal class CrudSqlEditorAdapter(
  private val model: CrudEditorModel,
) : Disposable {

  private val document: Document = EditorFactory.getInstance().createDocument("")
  private val editor: EditorEx

  private val sqlKeywords = setOf(
    "CREATE", "TABLE", "IF", "NOT", "EXISTS", "PRIMARY", "KEY", "FOREIGN", "REFERENCES",
    "CONSTRAINT", "UNIQUE", "INDEX", "DEFAULT", "NULL", "AUTO_INCREMENT", "SERIAL",
    "BIGSERIAL", "SMALLSERIAL", "CHECK", "COMMENT", "ON", "COLUMN", "IS", "ENUM", "SET",
    "VARCHAR", "CHAR", "TEXT", "INT", "INTEGER", "BIGINT", "SMALLINT", "TINYINT",
    "DECIMAL", "NUMERIC", "FLOAT", "REAL", "DOUBLE", "BOOLEAN", "BIT",
    "DATE", "TIME", "DATETIME", "TIMESTAMP", "MEDIUMTEXT", "BLOB", "ENGINE",
    "CHARSET", "COLLATE", "UNSIGNED",
  )

  init {
    val fileType = FileTypeManager.getInstance().getFileTypeByExtension("sql")
      .takeIf { it !is PlainTextFileType } ?: PlainTextFileType.INSTANCE
    editor = EditorFactory.getInstance().createEditor(document, null, fileType, false) as EditorEx

    editor.settings.apply {
      isLineNumbersShown = true
      isWhitespacesShown = false
      isFoldingOutlineShown = false
      additionalLinesCount = 2
    }

    document.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        model.setSqlText(document.text)
        applyHighlighting()
      }
    }, this)

    model.addChangeListener {
      if (document.text != model.sqlText) {
        com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction {
          document.setText(model.sqlText)
        }
      }
      applyDiagnosticMarkers()
    }
  }

  val component: JComponent get() = editor.component

  fun setText(text: String) {
    com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction {
      document.setText(text)
    }
  }

  fun getText(): String = document.text

  override fun dispose() {
    EditorFactory.getInstance().releaseEditor(editor)
  }

  private fun applyHighlighting() {
    val markup = editor.markupModel
    markup.removeAllHighlighters()

    val text = document.text
    if (text.isEmpty()) return

    val keywordAttrs = TextAttributes().apply {
      foregroundColor = Color(0x00, 0x00, 0xCC)
      fontType = Font.BOLD
    }
    val stringAttrs = TextAttributes().apply {
      foregroundColor = Color(0x00, 0x80, 0x00)
    }
    val commentAttrs = TextAttributes().apply {
      foregroundColor = Color(0x80, 0x80, 0x80)
      fontType = Font.ITALIC
    }

    var i = 0
    while (i < text.length) {
      val ch = text[i]
      // String literals
      if (ch == '\'' || ch == '"' || ch == '`') {
        val start = i
        i += 1
        while (i < text.length) {
          if (text[i] == '\\' && i + 1 < text.length) { i += 2; continue }
          if (text[i] == ch) {
            if (i + 1 < text.length && text[i + 1] == ch) { i += 2; continue }
            i += 1
            break
          }
          i += 1
        }
        markup.addRangeHighlighter(start, i.coerceAtMost(text.length), HighlighterLayer.SYNTAX, stringAttrs, HighlighterTargetArea.EXACT_RANGE)
        continue
      }
      // SQL line comments
      if (ch == '-' && i + 1 < text.length && text[i + 1] == '-') {
        val start = i
        while (i < text.length && text[i] != '\n') i += 1
        markup.addRangeHighlighter(start, i, HighlighterLayer.SYNTAX, commentAttrs, HighlighterTargetArea.EXACT_RANGE)
        continue
      }
      // Keywords
      if (ch.isLetter()) {
        val start = i
        while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_')) i += 1
        val word = text.substring(start, i).uppercase()
        if (word in sqlKeywords) {
          markup.addRangeHighlighter(start, i, HighlighterLayer.SYNTAX, keywordAttrs, HighlighterTargetArea.EXACT_RANGE)
        }
        continue
      }
      i += 1
    }
  }

  private fun applyDiagnosticMarkers() {
    val markup = editor.markupModel
    // Remove existing error markers (keep syntax highlighting)
    val errorAttrs = TextAttributes().apply {
      effectColor = Color.RED
      effectType = com.intellij.openapi.editor.markup.EffectType.WAVE_UNDERSCORE
    }

    // Remove old diagnostic markers
    markup.allHighlighters.forEach { highlighter ->
      if (highlighter.layer == HighlighterLayer.ERROR) {
        markup.removeHighlighter(highlighter)
      }
    }

    val result = model.diagnosticResult
    if (result is DdlSyntaxDiagnostics.DiagnosticResult.Error) {
      for (diagnostic in result.diagnostics) {
        val offset = diagnostic.offset.coerceIn(0, document.textLength)
        val end = (offset + 1).coerceAtMost(document.textLength)
        if (offset < end) {
          markup.addRangeHighlighter(offset, end, HighlighterLayer.ERROR, errorAttrs, HighlighterTargetArea.EXACT_RANGE)
        }
      }
    }
  }
}
