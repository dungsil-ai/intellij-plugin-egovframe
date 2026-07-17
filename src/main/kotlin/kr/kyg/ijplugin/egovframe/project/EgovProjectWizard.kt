package kr.kyg.ijplugin.egovframe.project

import com.intellij.icons.AllIcons
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Panel
import kr.kyg.ijplugin.egovframe.EgovNotifications
import kr.kyg.ijplugin.egovframe.assets.ProjectTemplate
import kr.kyg.ijplugin.egovframe.assets.TemplateCatalog
import kr.kyg.ijplugin.egovframe.assets.TemplateStoreService
import kr.kyg.ijplugin.egovframe.settings.EgovSettings
import kr.kyg.ijplugin.egovframe.settings.EgovBundle
import java.nio.file.Path
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JTextArea

class EgovProjectWizard : GeneratorNewProjectWizard {
  override val id: String = "kr.kyg.ijplugin.egovframe.wizard"
  override val name: String = "eGovFrame"
  override val icon = AllIcons.Nodes.Module
  override val description: String get() = EgovBundle.message("wizard.description")

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
  private val initialOutputPath = baseData.path
  private val model = ProjectWizardModel(
    settings.defaultGroupId,
    settings.defaultArtifactId,
    initialOutputPath,
  )
  private val categories = listOf("All") + TemplateCatalog.projectCategories()
  private val categoryCombo = JComboBox(categories.toTypedArray())
  private val templateCombo = JComboBox<ProjectTemplate>()
  private val groupIdField = JBTextField(model.groupId)
  private val artifactIdField = JBTextField(model.artifactId)
  private val descriptionArea = JTextArea(4, 50)
  private val availabilityLabel = JBLabel()
  private var groupIdRow: com.intellij.ui.dsl.builder.Row? = null
  private var artifactIdRow: com.intellij.ui.dsl.builder.Row? = null
  private var applyingTemplateDefaults = false

  init {
    descriptionArea.isEditable = false
    descriptionArea.lineWrap = true
    descriptionArea.wrapStyleWord = true
    templateCombo.renderer = ProjectTemplateRenderer()
    categoryCombo.addActionListener { updateTemplates() }
    templateCombo.addActionListener { updateTemplateDetails() }
    baseData.nameProperty.afterChange { name ->
      if (!applyingTemplateDefaults) {
        model.deriveFromProjectName(name)
        groupIdField.text = model.groupId
        artifactIdField.text = model.artifactId
      }
    }
    baseData.pathProperty.afterChange { path ->
      if (!applyingTemplateDefaults) model.outputPath = path
    }
    updateTemplates()
  }

  override fun setupUI(builder: Panel) {
    builder.row(EgovBundle.message("wizard.label.category")) { cell(categoryCombo) }
    builder.row(EgovBundle.message("wizard.label.template")) { cell(templateCombo) }
    builder.row(EgovBundle.message("wizard.label.availability")) { cell(availabilityLabel) }
    builder.row(EgovBundle.message("wizard.label.description")) { cell(descriptionArea) }
    builder.row(EgovBundle.message("wizard.label.groupId")) { cell(groupIdField) }.also { groupIdRow = it }
    builder.row(EgovBundle.message("wizard.label.artifactId")) { cell(artifactIdField) }.also { artifactIdRow = it }
    updateMavenFieldVisibility()
  }

  private fun updateMavenFieldVisibility() {
    val visible = model.hasPom
    groupIdRow?.visible(visible)
    artifactIdRow?.visible(visible)
  }

  override fun setupProject(project: Project) {
    val template = templateCombo.selectedItem as? ProjectTemplate
      ?: throw IllegalStateException(EgovBundle.message("wizard.error.noTemplate"))
    val config = ProjectGenerator.ProjectConfig(
      projectName = baseData.name,
      groupId = groupIdField.text.trim(),
      artifactId = artifactIdField.text.trim(),
      template = template,
    )
    ProjectGenerator.validate(config)

    val store = ApplicationManager.getApplication().getService(TemplateStoreService::class.java).store
    var generation: GenerationResult? = null
    var workflowFailure: Pair<ProjectGenerationStage, Throwable>? = null
    var mavenWarning: String? = null
    var jdkMissing = false
    var currentStage = ProjectGenerationStage.RESOLVE_TEMPLATE

    val completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(
      {
        val indicator = ProgressManager.getInstance().progressIndicator
        fun report(stage: ProjectGenerationStage) {
          currentStage = stage
          indicator?.text = stage.label
        }

        try {
          report(ProjectGenerationStage.RESOLVE_TEMPLATE)
          val zipPath = store.ensure(template.fileName)
          val result = ProjectGenerator.generateWithProgress(
            outputDirectory = Path.of(baseData.path),
            zipPath = zipPath,
            config = config,
            allowExistingEmptyDirectory = true,
            progress = GenerationProgress(::report),
          )
          generation = result

          if (result is GenerationResult.Success) {
            if (template.pomFile.isNotBlank()) {
              report(ProjectGenerationStage.LINK_MAVEN)
              val linker = project.getService(MavenProjectLinker::class.java)
              if (linker == null) {
                mavenWarning = "Maven integration is unavailable. Import pom.xml manually."
              } else {
                runCatching { linker.link(project, result.projectRoot.resolve("pom.xml")) }
                  .onFailure {
                    mavenWarning = "Maven project linking failed: ${it.messageOrTypeName()}. Import pom.xml manually."
                  }
              }
            }

            report(ProjectGenerationStage.CONFIGURE_JDK)
            val jdk17 = ProjectJdkTable.getInstance().allJdks.firstOrNull { sdk ->
              sdk.versionString?.contains(Regex("(?:^|\\D)17(?:\\D|$)")) == true
            }
            if (jdk17 == null) {
              jdkMissing = true
            } else {
              WriteCommandAction.runWriteCommandAction(project) {
                ProjectRootManager.getInstance(project).projectSdk = jdk17
              }
            }
            report(ProjectGenerationStage.COMPLETE)
          }
        } catch (error: Throwable) {
          workflowFailure = currentStage to error
        }
      },
      "Creating eGovFrame Project",
      true,
      project,
    )

    if (!completed) {
      EgovNotifications.warning(project, "eGovFrame project creation was cancelled.")
      return
    }
    workflowFailure?.let { (stage, error) ->
      EgovNotifications.error(project, "${stage.label} failed: ${error.messageOrTypeName()}")
      return
    }

    when (val result = generation) {
      is GenerationResult.Failure -> {
        EgovNotifications.error(project, "${result.stage.label} failed: ${result.error}")
      }
      is GenerationResult.Success -> {
        mavenWarning?.let { EgovNotifications.warning(project, it) }
        if (jdkMissing) {
          EgovNotifications.warning(project, EgovBundle.message("wizard.jdk17.notFound"))
        }
        EgovNotifications.info(project, EgovBundle.message("wizard.project.created", result.projectRoot))
      }
      null -> EgovNotifications.error(project, "eGovFrame project generation did not produce a result.")
    }
  }

  private fun updateTemplates() {
    val category = categoryCombo.selectedItem?.toString().orEmpty()
    val templates = TemplateCatalog.projects.filter { category == "All" || it.category == category }
    templateCombo.model = DefaultComboBoxModel(templates.toTypedArray())
    updateTemplateDetails()
  }

  private fun updateTemplateDetails() {
    val template = templateCombo.selectedItem as? ProjectTemplate ?: return
    applyingTemplateDefaults = true
    try {
      model.selectTemplate(template)
      baseData.name = model.projectName
      baseData.path = model.outputPath
      groupIdField.text = model.groupId
      artifactIdField.text = model.artifactId
    } finally {
      applyingTemplateDefaults = false
    }
    descriptionArea.text = template.description
    availabilityLabel.text = EgovBundle.message("wizard.availability.offline")
    updateMavenFieldVisibility()
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

internal fun Throwable.messageOrTypeName(): String =
  message?.takeIf(String::isNotBlank) ?: javaClass.simpleName.takeIf(String::isNotBlank) ?: javaClass.name
