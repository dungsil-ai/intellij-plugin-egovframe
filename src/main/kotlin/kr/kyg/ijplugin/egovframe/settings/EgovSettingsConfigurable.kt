package kr.kyg.ijplugin.egovframe.settings

import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.Configurable
import kr.kyg.ijplugin.egovframe.EgovNotifications
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

class EgovSettingsConfigurable : Configurable {

  private val groupIdField = JBTextField()
  private val artifactIdField = JBTextField()
  private val packageNameField = JBTextField()
  private val languageCombo = JComboBox(arrayOf("en", "ko"))
  private var panel: JPanel? = null

  override fun getDisplayName(): String = EgovBundle.message("settings.display.name")

  override fun createComponent(): JComponent = FormBuilder.createFormBuilder()
    .addLabeledComponent(JBLabel(EgovBundle.message("settings.label.language")), languageCombo, 1, false)
    .addLabeledComponent(JBLabel(EgovBundle.message("settings.label.groupId")), groupIdField, 1, false)
    .addLabeledComponent(JBLabel(EgovBundle.message("settings.label.artifactId")), artifactIdField, 1, false)
    .addLabeledComponent(JBLabel(EgovBundle.message("settings.label.package")), packageNameField, 1, false)
    .addComponentFillVertically(JPanel(), 0)
    .panel.also {
      panel = it
      reset()
    }

  override fun isModified(): Boolean {
    val state = EgovSettings.getInstance().state
    return normalizedCandidate() != state
  }

  @Throws(ConfigurationException::class)
  override fun apply() {
    val candidate = normalizedCandidate()
    val result = SettingsValidator.validate(candidate)
    if (!result.isValid) {
      val message = result.errors.joinToString("\n") { EgovBundle.message(it.messageKey) }
      throw ConfigurationException(message)
    }

    val state = EgovSettings.getInstance().state
    val languageChanged = state.language != candidate.language
    state.defaultGroupId = candidate.defaultGroupId
    state.defaultArtifactId = candidate.defaultArtifactId
    state.defaultPackageName = candidate.defaultPackageName
    state.language = candidate.language
    groupIdField.text = candidate.defaultGroupId
    artifactIdField.text = candidate.defaultArtifactId
    packageNameField.text = candidate.defaultPackageName
    languageCombo.selectedItem = candidate.language

    if (languageChanged) {
      EgovBundle.invalidateCache()
      EgovNotifications.info(null, EgovBundle.message("settings.restart.notice"))
    }
  }

  override fun reset() {
    val state = EgovSettings.getInstance().state
    groupIdField.text = state.defaultGroupId
    artifactIdField.text = state.defaultArtifactId
    packageNameField.text = state.defaultPackageName
    languageCombo.selectedItem = state.language
  }

  private fun normalizedCandidate(): EgovSettings.SettingsState = EgovSettings.normalizedState(
    defaultGroupId = groupIdField.text,
    defaultArtifactId = artifactIdField.text,
    defaultPackageName = packageNameField.text,
    language = languageCombo.selectedItem as? String,
  )

  override fun disposeUIResources() {
    panel = null
  }
}
