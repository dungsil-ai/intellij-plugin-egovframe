package kr.kyg.ijplugin.egovframe.settings

/**
 * Pure model validator for [EgovSettings.SettingsState] defaults.
 *
 * All rules are deterministic and side-effect-free so they can be tested
 * without an IntelliJ application context.
 */
object SettingsValidator {

  /** Strict lowercase dotted segments: `^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)*$` */
  private val GROUP_ID_REGEX = Regex("^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*$")

  /** Strict lowercase with hyphens: `^[a-z][a-z0-9-]*$` */
  private val ARTIFACT_ID_REGEX = Regex("^[a-z][a-z0-9-]*$")

  /** Package uses the same rule as groupId. */
  private val PACKAGE_REGEX = GROUP_ID_REGEX

  data class ValidationResult(
    val errors: List<ValidationError>,
  ) {
    val isValid: Boolean get() = errors.isEmpty()
  }

  data class ValidationError(
    val field: String,
    val messageKey: String,
  )

  fun validate(state: EgovSettings.SettingsState): ValidationResult {
    val errors = mutableListOf<ValidationError>()

    val groupId = state.defaultGroupId.trim()
    if (groupId.isBlank()) {
      errors += ValidationError("defaultGroupId", "settings.validation.groupId.blank")
    } else if (!GROUP_ID_REGEX.matches(groupId)) {
      errors += ValidationError("defaultGroupId", "settings.validation.groupId.invalid")
    }

    val artifactId = state.defaultArtifactId.trim()
    if (artifactId.isBlank()) {
      errors += ValidationError("defaultArtifactId", "settings.validation.artifactId.blank")
    } else if (!ARTIFACT_ID_REGEX.matches(artifactId)) {
      errors += ValidationError("defaultArtifactId", "settings.validation.artifactId.invalid")
    }

    val packageName = state.defaultPackageName.trim()
    if (packageName.isBlank()) {
      errors += ValidationError("defaultPackageName", "settings.validation.package.blank")
    } else if (!PACKAGE_REGEX.matches(packageName)) {
      errors += ValidationError("defaultPackageName", "settings.validation.package.invalid")
    }

    return ValidationResult(errors)
  }
}
