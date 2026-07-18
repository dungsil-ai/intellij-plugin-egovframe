package kr.kyg.ijplugin.egovframe.crud

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.ui.JBUI
import kr.kyg.ijplugin.egovframe.settings.EgovBundle
import java.awt.BorderLayout
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

internal class CrudPreviewDialog(
  project: Project,
  private val files: List<RenderedCrudArtifact>,
) : DialogWrapper(project) {

  private val fileCombo = JComboBox(files.map(RenderedCrudArtifact::fileName).toTypedArray())
  private val editorFactory = EditorFactory.getInstance()
  private var currentEditor: EditorEx? = null
  private val editorPanel = JPanel(BorderLayout())

  init {
    title = EgovBundle.message("crud.dialog.preview.title")
    fileCombo.addActionListener { updatePreview() }
    init()
    updatePreview()
  }

  override fun createCenterPanel(): JComponent = JPanel(BorderLayout(0, 8)).apply {
    border = JBUI.Borders.empty(8)
    add(fileCombo, BorderLayout.NORTH)
    add(editorPanel, BorderLayout.CENTER)
    preferredSize = JBUI.size(900, 680)
  }

  private fun updatePreview() {
    val index = fileCombo.selectedIndex.coerceAtLeast(0)
    val file = files.getOrNull(index) ?: return
    val fileType = CrudArtifact.fileTypeForLanguage(file.language)

    currentEditor?.let {
      editorPanel.remove(it.component)
      editorFactory.releaseEditor(it)
    }

    val document = editorFactory.createDocument(file.content)
    document.setReadOnly(true)
    val editor = editorFactory.createEditor(document, null, fileType, true) as EditorEx
    editor.settings.isLineNumbersShown = true
    editor.settings.isFoldingOutlineShown = false
    editor.settings.isAdditionalPageAtBottom = false
    currentEditor = editor
    editorPanel.add(editor.component, BorderLayout.CENTER)
    editorPanel.revalidate()
    editorPanel.repaint()
  }

  override fun dispose() {
    currentEditor?.let { editorFactory.releaseEditor(it) }
    currentEditor = null
    super.dispose()
  }
}
