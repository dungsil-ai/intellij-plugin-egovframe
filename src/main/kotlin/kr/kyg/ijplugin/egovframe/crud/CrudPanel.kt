package kr.kyg.ijplugin.egovframe.crud

import com.google.gson.GsonBuilder
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.*
import com.intellij.util.ui.JBUI
import kr.kyg.ijplugin.egovframe.EgovNotifications
import kr.kyg.ijplugin.egovframe.ddl.DdlParser
import kr.kyg.ijplugin.egovframe.ddl.ErdParser
import kr.kyg.ijplugin.egovframe.settings.EgovSettings
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridLayout
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.filechooser.FileNameExtensionFilter

class CrudPanel(private val project: Project) : JPanel(BorderLayout(8, 8)) {

  private val ddlEditor = JBTextArea(18, 80)
  private val packageField = JBTextField(EgovSettings.getInstance().state.defaultPackageName)
  private val outputFolderField = JBTextField(project.basePath.orEmpty())
  private val autoPreview = JBCheckBox("Auto preview", false)
  private val statusLabel = JBLabel("Enter a CREATE TABLE statement.")
  private val erdSummary = JBTextArea()
  private val validationTimer = Timer(500) { validateAndRefresh(openPreview = autoPreview.isSelected) }

  init {
    border = JBUI.Borders.empty(10)
    validationTimer.isRepeats = false
    ddlEditor.document.addDocumentListener(object : DocumentListener {
      override fun insertUpdate(event: DocumentEvent) = restartValidation()
      override fun removeUpdate(event: DocumentEvent) = restartValidation()
      override fun changedUpdate(event: DocumentEvent) = restartValidation()
    })

    val inputs = JPanel(GridLayout(0, 1, 0, 6)).apply {
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
      add(JBScrollPane(ddlEditor), BorderLayout.CENTER)
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

  private fun validateAndRefresh(openPreview: Boolean) {
    val ddl = ddlEditor.text
    if (!DdlParser.validateDDL(ddl)) {
      statusLabel.text = "Invalid DDL"
      erdSummary.text = ""
      return
    }
    runCatching {
      val parsed = DdlParser.parseDDL(ddl)
      statusLabel.text = "Valid: ${parsed.dbTableName} (${parsed.attributes.size} columns)"
      erdSummary.text = erdText(ddl)
      if (openPreview) preview()
    }.onFailure { statusLabel.text = it.message ?: "Invalid DDL" }
  }

  private fun preview() {
    runCatching {
      val prepared = prepare()
      prepared.renderFiles()
    }
      .onSuccess { CrudPreviewDialog(project, it).show() }
      .onFailure { EgovNotifications.error(project, it.message ?: "CRUD preview failed") }
  }

  private fun generate() {
    runCatching {
      val prepared = prepare()
      prepared.renderFiles()
    }.onSuccess { files ->
      val selection = CrudFileSelectionDialog(project, files)
      if (!selection.showAndGet()) return@onSuccess
      val selected = selection.selectedFiles()
      if (selected.isEmpty()) return@onSuccess

      try {
        var result: CrudGenerator.GenerationResult? = null
        WriteCommandAction.runWriteCommandAction(project) {
          result = CrudGenerator.generate(Path.of(outputFolderField.text.trim()), selected)
        }
        val generated = requireNotNull(result)
        generated.written.take(3).forEach { path ->
          LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)?.let {
            FileEditorManager.getInstance(project).openFile(it, true)
          }
        }
        if (generated.overwritten.isNotEmpty()) {
          EgovNotifications.warning(project, "Overwrote ${generated.overwritten.size} CRUD file(s).")
        }
        EgovNotifications.info(project, "Generated ${generated.written.size} CRUD file(s).")
      } catch (error: Exception) {
        EgovNotifications.error(project, error.message ?: "CRUD generation failed")
      }
    }.onFailure { EgovNotifications.error(project, it.message ?: "CRUD generation failed") }
  }

  private fun renderCustomTemplate() {
    val chooser = JFileChooser().apply {
      dialogTitle = "Select Handlebars template"
      fileFilter = FileNameExtensionFilter("Handlebars template (*.hbs)", "hbs")
    }
    if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
    runCatching {
      val prepared = prepare()
      val content = prepared.renderTemplate(Files.readString(chooser.selectedFile.toPath()))
      val output = chooser.selectedFile.toPath().resolveSibling("${chooser.selectedFile.name}.generated")
      Files.writeString(output, content, Charsets.UTF_8)
      EgovNotifications.info(project, "Rendered custom template: $output")
    }.onFailure { EgovNotifications.error(project, it.message ?: "Template rendering failed") }
  }

  private fun saveContextJson() {
    runCatching {
      val prepared = prepare()
      val chooser = JFileChooser().apply {
        dialogTitle = "Save TemplateContext JSON"
        selectedFile = java.io.File("${prepared.parsed.tableName}-context.json")
      }
      if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return
      Files.writeString(
        chooser.selectedFile.toPath(),
        GsonBuilder().setPrettyPrinting().create().toJson(prepared.context) + "\n",
        Charsets.UTF_8,
      )
      EgovNotifications.info(project, "Saved TemplateContext JSON: ${chooser.selectedFile}")
    }.onFailure { EgovNotifications.error(project, it.message ?: "Context export failed") }
  }

  private fun prepare() = CrudGenerator.prepare(ddlEditor.text, packageField.text.trim())


  private fun restartValidation() {
    validationTimer.restart()
  }

  private fun erdText(ddl: String): String {
    val model = ErdParser.parseErdModel(ddl)
    return buildString {
      model.tables.forEach { table ->
        appendLine("[${table.name}]")
        table.columns.forEach { column ->
          val badges = buildList {
            if (column.isPrimaryKey) add("PK")
            if (column.isForeignKey) add("FK")
          }.joinToString(",")
          append("  ${column.name}: ${column.dataType}")
          if (badges.isNotEmpty()) append(" [$badges]")
          appendLine()
        }
        appendLine()
      }
      if (model.relationships.isNotEmpty()) {
        appendLine("Relationships")
        model.relationships.forEach { relation ->
          appendLine("  ${relation.fromTable}.${relation.fromColumn} -> ${relation.toTable}.${relation.toColumn}")
        }
      }
    }
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
}
