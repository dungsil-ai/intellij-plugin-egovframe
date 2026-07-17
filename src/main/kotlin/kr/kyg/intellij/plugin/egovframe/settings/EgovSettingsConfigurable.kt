package kr.kyg.intellij.plugin.egovframe.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class EgovSettingsConfigurable : Configurable {

  private val groupIdField = JBTextField()
  private val artifactIdField = JBTextField()
  private val packageNameField = JBTextField()
  private var panel: JPanel? = null

  override fun getDisplayName(): String = "eGovFrame"

  override fun createComponent(): JComponent = FormBuilder.createFormBuilder()
    .addLabeledComponent(JBLabel("Default Maven groupId"), groupIdField, 1, false)
    .addLabeledComponent(JBLabel("Default Maven artifactId"), artifactIdField, 1, false)
    .addLabeledComponent(JBLabel("Default Java package"), packageNameField, 1, false)
    .addComponentFillVertically(JPanel(), 0)
    .panel.also { panel = it }

  override fun isModified(): Boolean {
    val state = EgovSettings.getInstance().state
    return groupIdField.text != state.defaultGroupId ||
      artifactIdField.text != state.defaultArtifactId ||
      packageNameField.text != state.defaultPackageName
  }

  override fun apply() {
    val state = EgovSettings.getInstance().state
    state.defaultGroupId = groupIdField.text.trim()
    state.defaultArtifactId = artifactIdField.text.trim()
    state.defaultPackageName = packageNameField.text.trim()
  }

  override fun reset() {
    val state = EgovSettings.getInstance().state
    groupIdField.text = state.defaultGroupId
    artifactIdField.text = state.defaultArtifactId
    packageNameField.text = state.defaultPackageName
  }

  override fun disposeUIResources() {
    panel = null
  }
}
