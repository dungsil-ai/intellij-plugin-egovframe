package kr.kyg.intellij.plugin.egovframe.config

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager

class OpenConfigToolWindowAction : AnAction() {
  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    ToolWindowManager.getInstance(project).getToolWindow("eGovFrame")?.show()
  }
}
