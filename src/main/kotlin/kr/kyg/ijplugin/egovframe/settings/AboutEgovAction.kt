package kr.kyg.ijplugin.egovframe.settings

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.DialogWrapper
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea

/**
 * Displays plugin metadata (version, description, repository, homepage/guide,
 * author, license, and project-required boundary) in a standard dialog.
 *
 * URLs are shown as selectable plain text so the user can copy them without
 * the plugin automatically launching a browser.
 */
class AboutEgovAction : AnAction(), DumbAware {

  override fun actionPerformed(e: AnActionEvent) {
    val dialog = object : DialogWrapper(e.project, false) {
      init {
        title = EgovBundle.message("about.title")
        isResizable = true
        init()
      }

      override fun createCenterPanel(): JComponent {
        val textArea = JTextArea(buildAboutText()).apply {
          isEditable = false
          lineWrap = true
          wrapStyleWord = true
        }
        val scrollPane = JScrollPane(textArea)
        scrollPane.preferredSize = Dimension(480, 300)
        return JPanel(BorderLayout()).apply { add(scrollPane, BorderLayout.CENTER) }
      }

      override fun createActions() = arrayOf(okAction)
    }
    dialog.show()
  }

  companion object {
    /**
     * Builds the full About text from bundle messages and [PluginMetadata].
     * Extracted for deterministic testing without a running UI.
     */
    fun buildAboutText(): String = buildString {
      appendLine(EgovBundle.message("about.version", PluginMetadata.version()))
      appendLine(EgovBundle.message("about.description"))
      appendLine()
      appendLine(EgovBundle.message("about.author", PluginMetadata.AUTHOR))
      appendLine(EgovBundle.message("about.license", PluginMetadata.LICENSE))
      appendLine(EgovBundle.message("about.repository", PluginMetadata.REPOSITORY))
      appendLine(EgovBundle.message("about.homepage", PluginMetadata.HOMEPAGE))
      appendLine(EgovBundle.message("about.guide", PluginMetadata.GUIDE))
      appendLine()
      appendLine(EgovBundle.message("about.requires.project"))
    }
  }
}
