package kr.kyg.ijplugin.egovframe.config

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import kr.kyg.ijplugin.egovframe.EgovNotifications
import kr.kyg.ijplugin.egovframe.assets.ConfigTemplate
import kr.kyg.ijplugin.egovframe.settings.EgovSettings
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

class ConfigFormDialog(
    private val project: Project,
    private val template: ConfigTemplate,
) : DialogWrapper(project) {

    private val definition = ConfigGenerator.definition(template)
    private val generationTypeCombo = JComboBox(definition.generationTypes.toTypedArray())
    private val outputFolderField = JBTextField(project.basePath.orEmpty())
    private val controls = LinkedHashMap<String, JComponent>()

    init {
        title = template.displayName
        generationTypeCombo.renderer = GenerationTypeRenderer()
        generationTypeCombo.addActionListener { applyVariantFileName() }
        init()
    }

    override fun createCenterPanel(): JComponent {
        val form = JPanel(GridBagLayout())
        form.border = JBUI.Borders.empty(8)
        var row = 0

        addRow(form, row++, "Format", generationTypeCombo)

        val outputPanel = JPanel(BorderLayout(6, 0))
        outputPanel.add(outputFolderField, BorderLayout.CENTER)
        outputPanel.add(JButton("Browse...").apply { addActionListener { chooseOutputFolder() } }, BorderLayout.EAST)
        addRow(form, row++, "Output folder", outputPanel)

        definition.visibleFields.forEach { (name, value) ->
            val control: JComponent = if (value is Boolean) {
                JBCheckBox().apply { isSelected = value }
            } else {
                JBTextField(displayValue(value))
            }
            controls[name] = control
            addRow(form, row++, humanize(name), control)
        }

        val spacer = GridBagConstraints().apply {
            gridx = 0
            gridy = row
            gridwidth = 2
            weightx = 1.0
            weighty = 1.0
            fill = GridBagConstraints.BOTH
        }
        form.add(JPanel(), spacer)
        applyVariantFileName()

        return JBScrollPane(form).apply {
            preferredSize = JBUI.size(760, 620)
            border = JBUI.Borders.empty()
        }
    }

    override fun doValidate(): ValidationInfo? {
        val outputFolder = outputFolderField.text.trim()
        if (outputFolder.isEmpty()) return ValidationInfo("Select an output folder", outputFolderField)

        val issue = ConfigGenerator.prepare(
            template = template,
            generationType = selectedGenerationType(),
            formData = collectFormData(),
            defaultPackageName = EgovSettings.getInstance().state.defaultPackageName,
        ).validate(Path.of(outputFolder)) ?: return null

        return ValidationInfo(issue.message, issue.field?.let { controls[it] })
    }

    override fun doOKAction() {
        val validation = doValidate()
        if (validation != null) {
            setErrorText(validation.message, validation.component)
            return
        }

        try {
            var generated: ConfigGenerator.GeneratedConfig? = null
            WriteCommandAction.runWriteCommandAction(project) {
                generated = ConfigGenerator.prepare(
                    template = template,
                    generationType = selectedGenerationType(),
                    formData = collectFormData(),
                    defaultPackageName = EgovSettings.getInstance().state.defaultPackageName,
                ).generate(Path.of(outputFolderField.text.trim()))
            }
            val result = requireNotNull(generated)
            LocalFileSystem.getInstance().refreshAndFindFileByNioFile(result.path)?.let {
                FileEditorManager.getInstance(project).openFile(it, true)
            }
            EgovNotifications.info(project, "Configuration file created: ${result.path}")
            super.doOKAction()
        } catch (error: Exception) {
            setErrorText(error.message ?: "Configuration generation failed")
            EgovNotifications.error(project, error.message ?: "Configuration generation failed")
        }
    }

    private fun collectFormData(): MutableMap<String, Any?> {
        val data = LinkedHashMap(definition.initialFormData(selectedGenerationType()))
        controls.forEach { (name, control) ->
            data[name] = when (control) {
                is JBCheckBox -> control.isSelected
                is JBTextField -> control.text
                else -> data[name]
            }
        }
        data["generationType"] = selectedGenerationType().id
        return data
    }

    private fun selectedGenerationType(): ConfigGenerator.GenerationType =
        generationTypeCombo.selectedItem as ConfigGenerator.GenerationType

    private fun applyVariantFileName() {
        val fileNameField = controls[definition.fileNameProperty] as? JBTextField ?: return
        val variantData = definition.initialFormData(selectedGenerationType())
        fileNameField.text = displayValue(variantData[definition.fileNameProperty])
    }

    private fun chooseOutputFolder() {
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        val initial = LocalFileSystem.getInstance().findFileByNioFile(Path.of(outputFolderField.text.ifBlank { project.basePath.orEmpty() }))
        FileChooser.chooseFile(descriptor, project, initial)?.let { outputFolderField.text = it.path }
    }

    private fun addRow(panel: JPanel, row: Int, label: String, component: JComponent) {
        panel.add(JBLabel(label), GridBagConstraints().apply {
            gridx = 0
            gridy = row
            anchor = GridBagConstraints.NORTHWEST
            insets = Insets(4, 0, 4, 12)
        })
        panel.add(component, GridBagConstraints().apply {
            gridx = 1
            gridy = row
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(2, 0, 2, 0)
        })
    }

    private fun humanize(name: String): String = name
        .removePrefix("txt")
        .removePrefix("chk")
        .removePrefix("rdo")
        .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
        .replaceFirstChar { it.uppercase() }

    private fun displayValue(value: Any?): String = when (value) {
        is Double -> if (value == Math.floor(value)) value.toLong().toString() else value.toString()
        null -> ""
        else -> value.toString()
    }

    private class GenerationTypeRenderer : javax.swing.DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: javax.swing.JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): java.awt.Component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).apply {
            if (this is javax.swing.JLabel && value is ConfigGenerator.GenerationType) text = value.label
        }
    }
}
