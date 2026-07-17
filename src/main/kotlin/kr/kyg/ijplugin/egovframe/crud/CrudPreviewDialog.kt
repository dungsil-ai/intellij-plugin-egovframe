package kr.kyg.ijplugin.egovframe.crud

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea

class CrudPreviewDialog(
  project: Project,
  private val files: List<CrudGenerator.RenderedFile>,
) : DialogWrapper(project) {

  private val fileCombo = JComboBox(files.map { it.info.fileName }.toTypedArray())
  private val preview = JTextArea()

  init {
    title = "CRUD Preview"
    preview.isEditable = false
    preview.font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, preview.font.size)
    fileCombo.addActionListener { updatePreview() }
    init()
    updatePreview()
  }

  override fun createCenterPanel(): JComponent = JPanel(BorderLayout(0, 8)).apply {
    border = JBUI.Borders.empty(8)
    add(fileCombo, BorderLayout.NORTH)
    add(JBScrollPane(preview), BorderLayout.CENTER)
    preferredSize = JBUI.size(900, 680)
  }

  private fun updatePreview() {
    val index = fileCombo.selectedIndex.coerceAtLeast(0)
    preview.text = files.getOrNull(index)?.content.orEmpty()
    preview.caretPosition = 0
  }
}
