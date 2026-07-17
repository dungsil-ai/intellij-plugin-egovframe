package kr.kyg.ijplugin.egovframe.crud

import com.intellij.openapi.Disposable
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import kr.kyg.ijplugin.egovframe.EgovNotifications
import kr.kyg.ijplugin.egovframe.ddl.DdlSyntaxDiagnostics
import kr.kyg.ijplugin.egovframe.settings.EgovSettings
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridLayout
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JFileChooser
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.Timer
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.filechooser.FileNameExtensionFilter

class CrudPanel(private val project: Project) : JPanel(BorderLayout(8, 8)), Disposable {

  private val editorModel = CrudEditorModel()
  private val crudGeneration = CrudGeneration()
  private val crudWriteAdapter = CrudWriteAdapter()
  private val editorAdapter = CrudSqlEditorAdapter(editorModel)
  private val packageField = JBTextField(EgovSettings.getInstance().state.defaultPackageName)
  private val outputFolderField = JBTextField(project.basePath.orEmpty())
  private val autoPreview = JBCheckBox("Auto preview", false)
  private val statusLabel = JBLabel("Enter a CREATE TABLE statement.")
  private val erdSummary = JBTextArea()
  private val validationTimer = Timer(500) { validateAndRefresh(openPreview = autoPreview.isSelected) }
  private val dialectCombo = JComboBox(SqlDialect.entries.toTypedArray())
  private val sampleCombo = JComboBox<CrudSampleCatalog.Sample>()
  private val directInputItem = "Direct input"
  private var updatingSample = false

  init {
    border = JBUI.Borders.empty(10)
    validationTimer.isRepeats = false

    dialectCombo.selectedItem = editorModel.dialect
    dialectCombo.renderer = javax.swing.DefaultListCellRenderer().let { renderer ->
      object : javax.swing.ListCellRenderer<Any?> {
        override fun getListCellRendererComponent(
          list: javax.swing.JList<out Any?>?,
          value: Any?,
          index: Int,
          isSelected: Boolean,
          cellHasFocus: Boolean,
        ) = renderer.getListCellRendererComponent(
          list, (value as? SqlDialect)?.displayName ?: value, index, isSelected, cellHasFocus,
        )
      }
    }
    dialectCombo.addActionListener {
      val selected = dialectCombo.selectedItem as? SqlDialect ?: return@addActionListener
      editorModel.switchDialect(selected)
      refreshSampleCombo()
      restartValidation()
    }

    refreshSampleCombo()
    sampleCombo.addActionListener {
      if (updatingSample) return@addActionListener
      val selected = sampleCombo.selectedItem
      if (selected is CrudSampleCatalog.Sample) {
        editorModel.selectSample(selected)
        restartValidation()
      } else {
        editorModel.clearSample()
        restartValidation()
      }
    }
    sampleCombo.renderer = javax.swing.DefaultListCellRenderer().let { renderer ->
      object : javax.swing.ListCellRenderer<Any?> {
        override fun getListCellRendererComponent(
          list: javax.swing.JList<out Any?>?,
          value: Any?,
          index: Int,
          isSelected: Boolean,
          cellHasFocus: Boolean,
        ) = renderer.getListCellRendererComponent(
          list,
          when (value) {
            is CrudSampleCatalog.Sample -> value.name
            else -> directInputItem
          },
          index, isSelected, cellHasFocus,
        )
      }
    }

    packageField.document.addDocumentListener(object : DocumentListener {
      override fun insertUpdate(event: DocumentEvent) = restartValidation()
      override fun removeUpdate(event: DocumentEvent) = restartValidation()
      override fun changedUpdate(event: DocumentEvent) = restartValidation()
    })
    editorModel.addChangeListener {
      restartValidation()
    }

    val dialectRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
      add(JBLabel("Dialect"))
      add(dialectCombo)
      add(JBLabel("Sample"))
      add(sampleCombo)
    }

    val inputs = JPanel(GridLayout(0, 1, 0, 6)).apply {
      add(dialectRow)
      add(labeledField("Package", packageField))
      add(labeledOutputFolder())
    }

    val buttons = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
      add(JButton("Validate").apply { addActionListener { validateAndRefresh(false) } })
      add(JButton("Preview").apply { addActionListener { preview() } })
      add(JButton("Generate").apply { addActionListener { generate() } })
      add(JButton("Render .hbs...").apply { addActionListener { renderCustomTemplate() } })
      add(JButton("Save context JSON...").apply { addActionListener { saveContextJson() } })
      add(autoPreview)
    }

    val left = JPanel(BorderLayout(0, 8)).apply {
      add(inputs, BorderLayout.NORTH)
      add(editorAdapter.component, BorderLayout.CENTER)
      add(JPanel(BorderLayout()).apply {
        add(buttons, BorderLayout.NORTH)
        add(statusLabel, BorderLayout.SOUTH)
      }, BorderLayout.SOUTH)
    }

    erdSummary.isEditable = false
    erdSummary.font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, erdSummary.font.size)
    val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, JBScrollPane(erdSummary)).apply {
      resizeWeight = 0.68
      dividerLocation = 720
    }
    add(split, BorderLayout.CENTER)
  }

  private fun refreshSampleCombo() {
    updatingSample = true
    try {
      val model = DefaultComboBoxModel<Any>()
      model.addElement(directInputItem)
      CrudSampleCatalog.forDialect(editorModel.dialect).forEach { model.addElement(it) }
      @Suppress("UNCHECKED_CAST")
      (sampleCombo as JComboBox<Any>).model = model
      sampleCombo.selectedItem = editorModel.selectedSample ?: directInputItem
    } finally {
      updatingSample = false
    }
  }

  private fun validateAndRefresh(openPreview: Boolean) {
    editorModel.markInputSettled()
    val diag = editorModel.diagnosticResult
    if (diag is DdlSyntaxDiagnostics.DiagnosticResult.Error) {
      val first = diag.diagnostics.first()
      statusLabel.text = "${first.message} (line ${first.line}, column ${first.column})"
      erdSummary.text = ""
      return
    }
    runCatching { preparation() }
      .onSuccess { preparation ->
        when (preparation) {
          is CrudPreparation.Ready -> {
            val prepared = preparation.prepared
            statusLabel.text = "Valid: ${prepared.summary.dbTableName} (${prepared.summary.columnCount} columns)"
            erdSummary.text = prepared.erdText
            if (openPreview) preview(prepared)
          }
          is CrudPreparation.Rejected -> showRejected(preparation)
        }
      }
      .onFailure { statusLabel.text = it.message ?: "Invalid DDL" }
  }

  private fun preview() {
    runCatching { requireReady() }
      .onSuccess { prepared -> if (prepared != null) preview(prepared) }
      .onFailure { notifyError(it, "CRUD preview failed") }
  }

  private fun preview(prepared: PreparedCrud) {
    runCatching { prepared.artifacts }
      .onSuccess { CrudPreviewDialog(project, it).show() }
      .onFailure { notifyError(it, "CRUD preview failed") }
  }

  private fun generate() {
    val plan = try {
      val prepared = requireReady() ?: return
      prepared.plan(Path.of(outputFolderField.text.trim()))
    } catch (error: Exception) {
      notifyError(error, "CRUD generation failed")
      return
    }

    val selection = CrudFileSelectionDialog(project, plan)
    if (!selection.showAndGet()) return
    val selected = selection.selectedArtifacts()
    if (selected.isEmpty()) return

    try {
      val writePlan = plan.select(selected)
      var result: CrudWriteResult? = null
      WriteCommandAction.runWriteCommandAction(project) {
        result = crudWriteAdapter.write(writePlan)
      }
      val generated = requireNotNull(result)
      generated.written.take(3).forEach { path ->
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)?.let { file ->
          FileEditorManager.getInstance(project).openFile(file, true)
        }
      }
      if (generated.overwritten.isNotEmpty()) {
        EgovNotifications.warning(project, "Overwrote ${generated.overwritten.size} CRUD file(s).")
      }
      EgovNotifications.info(project, "Generated ${generated.written.size} CRUD file(s).")
      if (generated.cleanupFailures.isNotEmpty()) {
        EgovNotifications.warning(
          project,
          "Generated files, but could not remove temporary files: ${generated.cleanupFailures.joinToString()}",
        )
      }
    } catch (error: Exception) {
      notifyError(error, "CRUD generation failed")
    }
  }

  private fun renderCustomTemplate() {
    val chooser = JFileChooser().apply {
      dialogTitle = "Select Handlebars template"
      fileFilter = FileNameExtensionFilter("Handlebars template (*.hbs)", "hbs")
    }
    if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return

    runCatching {
      val prepared = requireReady() ?: return@runCatching
      val content = prepared.renderCustom(Files.readString(chooser.selectedFile.toPath()))
      val output = chooser.selectedFile.toPath().resolveSibling("${chooser.selectedFile.name}.generated")
      WriteCommandAction.runWriteCommandAction(project) {
        Files.writeString(output, content, Charsets.UTF_8)
      }
      LocalFileSystem.getInstance().refreshAndFindFileByNioFile(output)
      EgovNotifications.info(project, "Rendered custom template: $output")
    }.onFailure { notifyError(it, "Template rendering failed") }
  }

  private fun saveContextJson() {
    runCatching {
      val prepared = requireReady() ?: return@runCatching
      val chooser = JFileChooser().apply {
        dialogTitle = "Save TemplateContext JSON"
        selectedFile = java.io.File("${prepared.summary.tableName}-context.json")
      }
      if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return@runCatching
      val output = chooser.selectedFile.toPath()
      WriteCommandAction.runWriteCommandAction(project) {
        Files.writeString(output, prepared.contextJson(), Charsets.UTF_8)
      }
      LocalFileSystem.getInstance().refreshAndFindFileByNioFile(output)
      EgovNotifications.info(project, "Saved TemplateContext JSON: ${chooser.selectedFile}")
    }.onFailure { notifyError(it, "Context export failed") }
  }

  private fun preparation(): CrudPreparation =
    crudGeneration.prepare(editorModel.sqlText, packageField.text.trim())

  private fun requireReady(): PreparedCrud? = when (val preparation = preparation()) {
    is CrudPreparation.Ready -> preparation.prepared
    is CrudPreparation.Rejected -> {
      showRejected(preparation)
      EgovNotifications.error(project, preparation.message)
      null
    }
  }

  private fun showRejected(rejected: CrudPreparation.Rejected) {
    statusLabel.text = rejected.message
    erdSummary.text = rejected.erdText
  }

  private fun notifyError(error: Throwable, fallback: String) {
    val messages = buildList {
      add(error.message ?: fallback)
      addAll(error.suppressed.mapNotNull { it.message })
    }
    EgovNotifications.error(project, messages.joinToString("; "))
  }

  private fun restartValidation() {
    validationTimer.restart()
  }

  private fun labeledField(label: String, field: JBTextField): JPanel = JPanel(BorderLayout(8, 0)).apply {
    add(JBLabel(label), BorderLayout.WEST)
    add(field, BorderLayout.CENTER)
  }

  private fun labeledOutputFolder(): JPanel = JPanel(BorderLayout(8, 0)).apply {
    add(JBLabel("Output folder"), BorderLayout.WEST)
    add(outputFolderField, BorderLayout.CENTER)
    add(JButton("Browse...").apply { addActionListener { chooseOutputFolder() } }, BorderLayout.EAST)
  }

  private fun chooseOutputFolder() {
    val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
    val initialPath = outputFolderField.text.ifBlank { project.basePath.orEmpty() }
    val initial = runCatching { LocalFileSystem.getInstance().findFileByNioFile(Path.of(initialPath)) }.getOrNull()
    FileChooser.chooseFile(descriptor, project, initial)?.let { outputFolderField.text = it.path }
  }

  override fun dispose() {
    validationTimer.stop()
    Disposer.dispose(editorAdapter)
  }
}
