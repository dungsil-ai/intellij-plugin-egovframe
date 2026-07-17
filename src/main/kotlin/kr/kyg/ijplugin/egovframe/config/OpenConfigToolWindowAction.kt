package kr.kyg.ijplugin.egovframe.config

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import kr.kyg.ijplugin.egovframe.settings.ActionAvailabilityPolicy

class OpenConfigToolWindowAction : AnAction() {
  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    ToolWindowManager.getInstance(project).getToolWindow("eGovFrame")?.show()
  }

  override fun update(event: AnActionEvent) {
    val available = ActionAvailabilityPolicy.isConfigCrudAvailable(event.project)
    event.presentation.isEnabled = available
    if (!available) {
      event.presentation.description = ActionAvailabilityPolicy.disabledReason()
    }
  }
}
