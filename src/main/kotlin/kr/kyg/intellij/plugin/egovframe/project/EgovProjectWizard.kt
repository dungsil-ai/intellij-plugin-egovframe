package kr.kyg.intellij.plugin.egovframe.project

import com.intellij.icons.AllIcons
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Panel
import kr.kyg.intellij.plugin.egovframe.EgovNotifications
import kr.kyg.intellij.plugin.egovframe.assets.ProjectTemplate
import kr.kyg.intellij.plugin.egovframe.assets.TemplateCatalog
import kr.kyg.intellij.plugin.egovframe.assets.TemplateStoreService
import kr.kyg.intellij.plugin.egovframe.settings.EgovSettings
import java.nio.file.Path
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JTextArea

class EgovProjectWizard : GeneratorNewProjectWizard {
  override val id: String = "kr.kyg.intellij.plugin.egovframe.wizard"
  override val name: String = "eGovFrame"
  override val icon = AllIcons.Nodes.Module
  override val description: String = "Create a project from an official eGovFrame 5.0 template"

  override fun createStep(context: WizardContext): NewProjectWizardStep {
    lateinit var baseStep: NewProjectWizardBaseStep
    return NewProjectWizardChainStep(RootNewProjectWizardStep(context))
      .nextStep { parent -> NewProjectWizardBaseStep(parent).also { baseStep = it } }
      .nextStep(::GitNewProjectWizardStep)
      .nextStep { parent -> EgovTemplateStep(parent, baseStep) }
  }
}

private class EgovTemplateStep(
  parent: NewProjectWizardStep,
  private val baseData: NewProjectWizardBaseData,
) : AbstractNewProjectWizardStep(parent) {

  private val settings = EgovSettings.getInstance().state
  private val categories = listOf("All") + TemplateCatalog.projectCategories()
  private val categoryCombo = JComboBox(categories.toTypedArray())
  private val templateCombo = JComboBox<ProjectTemplate>()
  private val groupIdField = JBTextField(settings.defaultGroupId)
  private val artifactIdField = JBTextField(settings.defaultArtifactId)
  private val descriptionArea = JTextArea(4, 50)
  private val availabilityLabel = JBLabel()

  init {
    descriptionArea.isEditable = false
    descriptionArea.lineWrap = true
    descriptionArea.wrapStyleWord = true
    templateCombo.renderer = ProjectTemplateRenderer()
    categoryCombo.addActionListener { updateTemplates() }
    templateCombo.addActionListener { updateTemplateDetails() }
    updateTemplates()
  }

  override fun setupUI(builder: Panel) {
    if (baseData.name.isBlank() || baseData.name == "untitled") {
      (templateCombo.selectedItem as? ProjectTemplate)?.let { baseData.name = it.projectName }
    }
    builder.row("Category") { cell(categoryCombo) }
    builder.row("Template") { cell(templateCombo) }
    builder.row("Availability") { cell(availabilityLabel) }
    builder.row("Description") { cell(descriptionArea) }
    builder.row("Maven groupId") { cell(groupIdField) }
    builder.row("Maven artifactId") { cell(artifactIdField) }
  }

  override fun setupProject(project: Project) {
    val template = templateCombo.selectedItem as? ProjectTemplate
      ?: throw IllegalStateException("Select an eGovFrame project template")
    val projectName = baseData.name
    val groupId = groupIdField.text.trim()
    val artifactId = artifactIdField.text.trim()
    val config = ProjectGenerator.ProjectConfig(projectName, groupId, artifactId, template)
    ProjectGenerator.validate(config)

    val store = ApplicationManager.getApplication().getService(TemplateStoreService::class.java).store
    val zipPath = store.ensure(template.fileName)
    val projectRoot = ProjectGenerator.generate(
      outputDirectory = Path.of(baseData.path),
      zipPath = zipPath,
      config = config,
      allowExistingEmptyDirectory = true,
    )

    val pomPath = projectRoot.resolve("pom.xml")
    if (template.pomFile.isNotBlank()) {
      runCatching { project.getService(MavenProjectLinker::class.java)?.link(project, pomPath) }
    }

    val jdk17 = ProjectJdkTable.getInstance().allJdks.firstOrNull { sdk ->
      sdk.versionString?.contains(Regex("(?:^|\\D)17(?:\\D|$)")) == true
    }
    if (jdk17 != null) {
      WriteCommandAction.runWriteCommandAction(project) {
        ProjectRootManager.getInstance(project).projectSdk = jdk17
      }
    } else {
      EgovNotifications.warning(project, "JDK 17 was not found. Configure the project SDK manually.")
    }
    EgovNotifications.info(project, "eGovFrame project created: $projectRoot")
  }

  private fun updateTemplates() {
    val category = categoryCombo.selectedItem?.toString().orEmpty()
    val templates = TemplateCatalog.projects.filter { category == "All" || it.category == category }
    templateCombo.model = DefaultComboBoxModel(templates.toTypedArray())
    updateTemplateDetails()
  }

  private fun updateTemplateDetails() {
    val template = templateCombo.selectedItem as? ProjectTemplate ?: return
    descriptionArea.text = template.description
    val store = ApplicationManager.getApplication().getService(TemplateStoreService::class.java).store
    availabilityLabel.text = if (store.isAvailableOffline(template.fileName)) {
      "Ready offline"
    } else {
      "Downloads when the project is created"
    }
    if (baseData.name.isBlank() || TemplateCatalog.projects.any { it.projectName == baseData.name }) {
      baseData.name = template.projectName
    }
    if (artifactIdField.text.isBlank() || artifactIdField.text == settings.defaultArtifactId) {
      artifactIdField.text = template.projectName.substringAfterLast('.')
    }
  }

  private class ProjectTemplateRenderer : javax.swing.DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
      list: javax.swing.JList<*>?,
      value: Any?,
      index: Int,
      isSelected: Boolean,
      cellHasFocus: Boolean,
    ): java.awt.Component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).apply {
      if (this is javax.swing.JLabel && value is ProjectTemplate) text = value.displayName
    }
  }
}
