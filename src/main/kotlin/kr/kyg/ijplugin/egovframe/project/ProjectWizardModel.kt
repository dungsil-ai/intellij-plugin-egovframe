package kr.kyg.ijplugin.egovframe.project

import kr.kyg.ijplugin.egovframe.assets.ProjectTemplate

/**
 * Pure state model for the eGovFrame New Project wizard.
 * Testable without IntelliJ dependencies.
 */
class ProjectWizardModel(
  private val defaultGroupId: String = "egovframework.com",
  private val defaultArtifactId: String = "egovframe-project",
  private val initialOutputPath: String = "",
) {
  var projectName: String = ""
  var groupId: String = defaultGroupId
  var artifactId: String = defaultArtifactId
  var outputPath: String = initialOutputPath
  var template: ProjectTemplate? = null
    private set

  /** The 4 templates that ship without a POM and need no Maven fields. */
  val hasPom: Boolean get() = template?.pomFile?.isNotBlank() == true

  /**
   * Selects a template and restores the exact upstream defaults.
   * Project-name edits derive Maven coordinates only after this reset.
   */
  fun selectTemplate(newTemplate: ProjectTemplate) {
    template = newTemplate
    projectName = newTemplate.projectName
    groupId = defaultGroupId
    artifactId = defaultArtifactId
    outputPath = initialOutputPath
  }

  /**
   * Derives groupId and artifactId from the current project name by splitting on the last dot.
   * A blank value restores defaults; a name without a dot keeps the default groupId.
   */
  fun deriveFromProjectName(name: String) {
    projectName = name
    val dotIndex = name.lastIndexOf('.')
    if (dotIndex > 0 && dotIndex < name.length - 1) {
      groupId = name.substring(0, dotIndex)
      artifactId = name.substring(dotIndex + 1)
    } else if (name.isNotBlank()) {
      groupId = defaultGroupId
      artifactId = deriveArtifactId(name)
    } else {
      groupId = defaultGroupId
      artifactId = defaultArtifactId
    }
  }

  /** Validate Maven fields only when the template has a POM. */
  fun validate(): List<String> {
    val errors = mutableListOf<String>()
    if (!ProjectGenerator.PROJECT_NAME_REGEX.matches(projectName)) {
      errors += "Project name can only contain letters, numbers, hyphens, underscores, and single dots between segments"
    }
    if (hasPom) {
      if (!ProjectGenerator.GROUP_ID_REGEX.matches(groupId)) errors += "Invalid Maven groupId: $groupId"
      if (!ProjectGenerator.ARTIFACT_ID_REGEX.matches(artifactId)) errors += "Invalid Maven artifactId: $artifactId"
    }
    if (template == null) errors += "Select an eGovFrame project template"
    return errors
  }

  private fun deriveArtifactId(name: String): String {
    val candidate = name.substringAfterLast('.').lowercase()
      .replace(Regex("[^a-z0-9-]"), "-")
      .replace(Regex("-+"), "-")
      .trimStart('-').trimEnd('-')
    return candidate.ifEmpty { defaultArtifactId }
  }
}
