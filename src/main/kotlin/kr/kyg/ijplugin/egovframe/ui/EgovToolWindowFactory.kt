package kr.kyg.ijplugin.egovframe.ui

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import kr.kyg.ijplugin.egovframe.config.ConfigPanel
import kr.kyg.ijplugin.egovframe.crud.CrudPanel
import kr.kyg.ijplugin.egovframe.settings.EgovBundle

class EgovToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val contentFactory = ContentFactory.getInstance()
    toolWindow.contentManager.addContent(contentFactory.createContent(ConfigPanel(project), EgovBundle.message("toolwindow.tab.config"), false))
    toolWindow.contentManager.addContent(contentFactory.createContent(CrudPanel(project), EgovBundle.message("toolwindow.tab.crud"), false))
  }
}
