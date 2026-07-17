package kr.kyg.ijplugin.egovframe.crud

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.GridLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal class CrudFileSelectionDialog(
  project: Project,
  private val plan: GenerationPlan,
) : DialogWrapper(project) {

  private val selections = plan.artifacts.associateWith { entry ->
    val label = if (entry.collision) "${entry.relativePath} (existing — overwrite)" else entry.relativePath
    JBCheckBox(label, !entry.collision)
  }

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

  fun selectedArtifacts(): Set<CrudArtifact> =
    plan.artifacts.filter { selections.getValue(it).isSelected }.mapTo(linkedSetOf(), PlannedCrudArtifact::artifact)
}
