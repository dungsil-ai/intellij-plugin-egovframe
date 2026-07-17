package kr.kyg.ijplugin.egovframe.config

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import kr.kyg.ijplugin.egovframe.assets.ConfigTemplate
import kr.kyg.ijplugin.egovframe.assets.TemplateCatalog
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.*

class ConfigPanel(private val project: Project) : JPanel(BorderLayout(8, 8)) {

  private val categories = TemplateCatalog.configs.map(::categoryOf).distinct()
  private val categoryCombo = JComboBox(categories.toTypedArray())
  private val typeCombo = JComboBox<ConfigTemplate>()
  private val description = JTextArea(5, 60)

  init {
    border = JBUI.Borders.empty(10)

    val controls = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
      add(JBLabel("Category"))
      add(categoryCombo)
      add(JBLabel("Type"))
      add(typeCombo)
      add(JButton("Generate...").apply { addActionListener { openSelectedTemplate() } })
    }
    add(controls, BorderLayout.NORTH)

    description.isEditable = false
    description.lineWrap = true
    description.wrapStyleWord = true
    description.border = JBUI.Borders.empty(8)
    add(description, BorderLayout.CENTER)

    categoryCombo.addActionListener { updateTypes() }
    typeCombo.addActionListener { updateDescription() }
    typeCombo.renderer = TemplateRenderer()
    updateTypes()
  }

  private fun updateTypes() {
    val category = categoryCombo.selectedItem?.toString()
    val templates = TemplateCatalog.configs.filter { categoryOf(it) == category }
    typeCombo.model = DefaultComboBoxModel(templates.toTypedArray())
    updateDescription()
  }

  private fun updateDescription() {
    description.text = (typeCombo.selectedItem as? ConfigTemplate)?.description.orEmpty()
  }

  private fun openSelectedTemplate() {
    val template = typeCombo.selectedItem as? ConfigTemplate ?: return
    ConfigFormDialog(project, template).show()
  }

  private fun categoryOf(template: ConfigTemplate): String = template.displayName.substringBefore(" > ")

  private class TemplateRenderer : javax.swing.DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
      list: javax.swing.JList<*>?,
      value: Any?,
      index: Int,
      isSelected: Boolean,
      cellHasFocus: Boolean,
    ): java.awt.Component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).apply {
      if (this is javax.swing.JLabel && value is ConfigTemplate) text = value.displayName.substringAfter(" > ")
    }
  }
}
