package kr.kyg.ijplugin.egovframe.crud

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.GridLayout
import javax.swing.JComponent
import javax.swing.JPanel

class CrudFileSelectionDialog(
  project: Project,
  private val files: List<CrudGenerator.RenderedFile>,
) : DialogWrapper(project) {

  private val selections = files.associateWith { JBCheckBox(it.info.outputPath, true) }

  init {
    title = "Select CRUD files"
    init()
  }

  override fun createCenterPanel(): JComponent {
    val panel = JPanel(GridLayout(0, 1, 0, 4)).apply {
      border = JBUI.Borders.empty(8)
      selections.values.forEach(::add)
    }
    return JBScrollPane(panel).apply { preferredSize = JBUI.size(720, 420) }
  }

  fun selectedFiles(): List<CrudGenerator.RenderedFile> =
    files.filter { selections.getValue(it).isSelected }
}
