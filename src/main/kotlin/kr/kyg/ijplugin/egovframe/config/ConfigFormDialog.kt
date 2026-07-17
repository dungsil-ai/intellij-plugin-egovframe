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
import kr.kyg.ijplugin.egovframe.settings.EgovBundle
import java.awt.Component
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.nio.file.Path
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.DefaultListCellRenderer
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JList
import javax.swing.JRadioButton
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

internal object ConfigFormControlListener {
    fun install(control: JComponent, onChange: () -> Unit) {
        when (control) {
            is JComboBox<*> -> control.addActionListener { onChange() }
            is JBTextField -> control.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(event: DocumentEvent) = onChange()
                override fun removeUpdate(event: DocumentEvent) = onChange()
                override fun changedUpdate(event: DocumentEvent) = onChange()
            })
        }
    }
}

internal fun createSelectControl(field: FieldDef, defaultValue: Any?, onChange: () -> Unit): JComboBox<String> {
    val labelsByValue = field.options.associate { it.value to it.label }
    return JComboBox(field.options.map { it.value }.toTypedArray()).apply {
        renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): Component = super.getListCellRendererComponent(
                list,
                labelsByValue[value?.toString()] ?: value,
                index,
                isSelected,
                cellHasFocus,
            )
        }
        selectedItem = defaultValue?.toString() ?: field.options.firstOrNull()?.value
        ConfigFormControlListener.install(this, onChange)
    }
}
internal fun validateOutputFolderPath(value: String, component: JComponent): ValidationInfo? = when {
    value.isBlank() -> ValidationInfo("Select an output folder", component)
    else -> runCatching { Path.of(value) }.exceptionOrNull()?.let {
        ValidationInfo("Invalid output path", component)
    }
}



class ConfigFormDialog(
    private val project: Project,
    private val template: ConfigTemplate,
) : DialogWrapper(project) {

    private val definition = ConfigGenerator.definition(template)
    private val spec = definition.formSpec
    private val generationTypeCombo = JComboBox(definition.generationTypes.toTypedArray())
    private val outputFolderField = JBTextField(project.basePath.orEmpty())
    private val controls = LinkedHashMap<String, JComponent>()
    private val radioGroups = LinkedHashMap<String, List<JRadioButton>>()
    private val fieldRows = LinkedHashMap<String, List<JComponent>>()
    private var applyingControlUpdates = false

    init {
        title = template.displayName
        generationTypeCombo.renderer = GenerationTypeRenderer()
        generationTypeCombo.addActionListener {
            updateControls {
                applyVariantFileName()
                updateVisibility()
            }
        }
        init()
    }

    override fun createCenterPanel(): JComponent {
        val form = JPanel(GridBagLayout())
        form.border = JBUI.Borders.empty(8)
        var row = 0

        addRow(form, row++, EgovBundle.message("config.form.label.format"), generationTypeCombo)

        val outputPanel = JPanel(BorderLayout(6, 0))
        outputPanel.add(outputFolderField, BorderLayout.CENTER)
        outputPanel.add(JButton(EgovBundle.message("config.button.browse")).apply { addActionListener { chooseOutputFolder() } }, BorderLayout.EAST)
        addRow(form, row++, EgovBundle.message("config.form.label.outputFolder"), outputPanel)

        if (spec != null) {
            val initialData = definition.initialFormData(selectedGenerationType())
            for (field in spec.fields) {
                val defaultValue = initialData[field.key]
                val control = createControl(field, defaultValue)
                controls[field.key] = control
                val labelComponent = JBLabel(field.label)
                addRow(form, row, field.label, control)
                fieldRows[field.key] = listOf(labelComponent, control)
                form.remove(form.componentCount - 2) // remove the auto-added label
                form.add(labelComponent, GridBagConstraints().apply {
                    gridx = 0
                    gridy = row
                    anchor = GridBagConstraints.NORTHWEST
                    insets = Insets(4, 0, 4, 12)
                })
                row++
            }
        } else {
            val initialData = definition.initialFormData(selectedGenerationType())
            definition.visibleFields.forEach { (name, value) ->
                val control: JComponent = if (value is Boolean) {
                    JBCheckBox().apply { isSelected = value }
                } else {
                    JBTextField(displayValue(value))
                }
                controls[name] = control
                addRow(form, row++, humanize(name), control)
            }
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
        updateVisibility()

        return JBScrollPane(form).apply {
            preferredSize = JBUI.size(760, 620)
            border = JBUI.Borders.empty()
        }
    }

    private fun createControl(field: FieldDef, defaultValue: Any?): JComponent = when (field.control) {
        ControlType.TEXT -> JBTextField(displayValue(defaultValue)).apply {
            ConfigFormControlListener.install(this) { handleControlChange(field.key) }
        }
        ControlType.SELECT -> createSelectControl(field, defaultValue) { handleControlChange(field.key) }
        ControlType.RADIO -> {
            val panel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0))
            val group = ButtonGroup()
            val buttons = field.options.map { option ->
                JRadioButton(option.label).apply {
                    actionCommand = option.value
                    isSelected = option.value == defaultValue?.toString()
                    group.add(this)
                    panel.add(this)
                    addActionListener { handleControlChange(field.key) }
                }
            }
            radioGroups[field.key] = buttons
            panel
        }
        ControlType.CHECK -> JBCheckBox().apply {
            isSelected = defaultValue as? Boolean ?: false
            addActionListener { handleControlChange(field.key) }
        }
        ControlType.FILE -> {
            val textField = JBTextField(displayValue(defaultValue)).apply {
                ConfigFormControlListener.install(this) { handleControlChange(field.key) }
            }
            val panel = JPanel(BorderLayout(6, 0))
            panel.add(textField, BorderLayout.CENTER)
            panel.add(JButton("Browse...").apply {
                addActionListener { chooseFile(textField) }
            }, BorderLayout.EAST)
            panel.putClientProperty("textField", textField)
            panel
        }
    }

    private fun applyLinkedUpdates(sourceKey: String) {
        if (spec == null) return
        val state = buildFormState()
        for (link in spec.linkedUpdates) {
            if (link.sourceField == sourceKey) {
                link.update(state)
                syncStateToControls(state)
            }
        }
    }

    private fun handleControlChange(sourceKey: String) {
        updateControls {
            applyLinkedUpdates(sourceKey)
            updateVisibility()
        }
    }

    private inline fun updateControls(update: () -> Unit) {
        if (applyingControlUpdates) return
        applyingControlUpdates = true
        try {
            update()
        } finally {
            applyingControlUpdates = false
        }
    }

    private fun buildFormState(): FormState {
        val data = LinkedHashMap(definition.initialFormData(selectedGenerationType()))
        collectControlValues(data)
        return FormState(data)
    }

    private fun syncStateToControls(state: FormState) {
        for ((key, control) in controls) {
            val value = state[key]
            when (control) {
                is JBTextField -> {
                    val newText = value?.toString().orEmpty()
                    if (control.text != newText) control.text = newText
                }
                is JBCheckBox -> {
                    val newVal = value as? Boolean ?: false
                    if (control.isSelected != newVal) control.isSelected = newVal
                }
                is JComboBox<*> -> {
                    val newVal = value?.toString()
                    if (control.selectedItem != newVal) control.selectedItem = newVal
                }
                is JPanel -> {
                    val tf = control.getClientProperty("textField") as? JBTextField
                    if (tf != null) {
                        val newText = value?.toString().orEmpty()
                        if (tf.text != newText) tf.text = newText
                    }
                    val buttons = radioGroups[key]
                    if (buttons != null) {
                        val newVal = value?.toString()
                        for (btn in buttons) {
                            btn.isSelected = btn.actionCommand == newVal
                        }
                    }
                }
            }
        }
    }

    private fun updateVisibility() {
        if (spec == null) return
        val state = buildFormState()
        for (field in spec.fields) {
            if (field.key !in controls) continue
            val visible = field.visibleWhen?.invoke(state) ?: true
            fieldRows[field.key]?.forEach { it.isVisible = visible }
        }
    }

    override fun doValidate(): ValidationInfo? {
        val outputFolder = outputFolderField.text.trim()
        if (outputFolder.isEmpty()) {
            return ValidationInfo(EgovBundle.message("config.validation.outputFolder.empty"), outputFolderField)
        }
        validateOutputFolderPath(outputFolder, outputFolderField)?.let { return it }

        if (spec != null) {
            val state = buildFormState()
            val issue = spec.validate(state)
            if (issue != null) return ValidationInfo(issue.message, issue.field?.let { controls[it] })
        }

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
            EgovNotifications.info(project, EgovBundle.message("config.notification.generated", result.path))
            super.doOKAction()
        } catch (error: Exception) {
            setErrorText(error.message ?: EgovBundle.message("config.error.generation"))
            EgovNotifications.error(project, error.message ?: EgovBundle.message("config.error.generation"))
        }
    }

    private fun collectFormData(): MutableMap<String, Any?> {
        val data = LinkedHashMap(definition.initialFormData(selectedGenerationType()))
        collectControlValues(data)
        data["generationType"] = selectedGenerationType().id
        return data
    }

    private fun collectControlValues(data: MutableMap<String, Any?>) {
        controls.forEach { (name, control) ->
            data[name] = when (control) {
                is JBCheckBox -> control.isSelected
                is JBTextField -> control.text
                is JComboBox<*> -> control.selectedItem?.toString() ?: data[name]
                is JPanel -> {
                    val tf = control.getClientProperty("textField") as? JBTextField
                    if (tf != null) {
                        tf.text
                    } else {
                        val buttons = radioGroups[name]
                        buttons?.firstOrNull { it.isSelected }?.actionCommand ?: data[name]
                    }
                }
                else -> data[name]
            }
        }
    }

    private fun selectedGenerationType(): ConfigGenerator.GenerationType =
        generationTypeCombo.selectedItem as ConfigGenerator.GenerationType

    private fun applyVariantFileName() {
        val fileNameKey = definition.fileNameProperty
        val fileNameControl = controls[fileNameKey]
        val variantData = definition.initialFormData(selectedGenerationType())
        val newFileName = displayValue(variantData[fileNameKey])
        when (fileNameControl) {
            is JBTextField -> fileNameControl.text = newFileName
            is JPanel -> {
                val tf = fileNameControl.getClientProperty("textField") as? JBTextField
                tf?.let { it.text = newFileName }
            }
        }
    }

    private fun chooseOutputFolder() {
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        val initial = LocalFileSystem.getInstance().findFileByNioFile(Path.of(outputFolderField.text.ifBlank { project.basePath.orEmpty() }))
        FileChooser.chooseFile(descriptor, project, initial)?.let { outputFolderField.text = it.path }
    }

    private fun chooseFile(textField: JBTextField) {
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
        FileChooser.chooseFile(descriptor, project, null)?.let {
            textField.text = normalizeConfigLocation(it.path)
        }
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
