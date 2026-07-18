package kr.kyg.ijplugin.egovframe.settings

import com.intellij.openapi.project.Project

/**
 * Pure policy for action availability based on project presence.
 *
 * Config/CRUD generation requires an open project. The New Project wizard
 * is available from the Welcome screen (projectless) by design.
 */
object ActionAvailabilityPolicy {

  /**
   * Returns `true` when Config/CRUD tool-window actions should be enabled.
   */
  fun isConfigCrudAvailable(project: Project?): Boolean = project != null

  /**
   * Returns a user-facing explanation of why an action is disabled,
   * resolved from the current bundle language.
   */
  fun disabledReason(): String = EgovBundle.message("action.requires.project")

  fun descriptionFor(available: Boolean, defaultDescription: String?): String? =
    if (available) defaultDescription else disabledReason()
}
