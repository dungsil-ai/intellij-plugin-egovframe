package kr.kyg.ijplugin.egovframe.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "EgovSettings", storages = [Storage("egovframe.xml")])
@Service(Service.Level.APP)
class EgovSettings : PersistentStateComponent<EgovSettings.SettingsState> {

  data class SettingsState(
    var defaultGroupId: String = DEFAULT_GROUP_ID,
    var defaultArtifactId: String = DEFAULT_ARTIFACT_ID,
    var defaultPackageName: String = DEFAULT_PACKAGE_NAME,
    /** UI language: `"en"` or `"ko"`. */
    var language: String = DEFAULT_LANGUAGE,
  )

  private var settingsState = SettingsState()

  override fun getState(): SettingsState = settingsState

  override fun loadState(state: SettingsState) {
    XmlSerializerUtil.copyBean(state, settingsState)
    migrateIfNeeded()
  }

  private fun migrateIfNeeded() {
    settingsState = normalizedState(
      defaultGroupId = settingsState.defaultGroupId,
      defaultArtifactId = settingsState.defaultArtifactId,
      defaultPackageName = settingsState.defaultPackageName,
      language = settingsState.language,
    )
    val invalidFields = SettingsValidator.validate(settingsState).errors.mapTo(mutableSetOf()) { it.field }
    if ("defaultGroupId" in invalidFields) settingsState.defaultGroupId = DEFAULT_GROUP_ID
    if ("defaultArtifactId" in invalidFields) settingsState.defaultArtifactId = DEFAULT_ARTIFACT_ID
    if ("defaultPackageName" in invalidFields) settingsState.defaultPackageName = DEFAULT_PACKAGE_NAME
  }

  companion object {
    const val DEFAULT_GROUP_ID = "egovframework.com"
    const val DEFAULT_ARTIFACT_ID = "egovframe-project"
    const val DEFAULT_PACKAGE_NAME = "egovframework.example.sample"
    const val DEFAULT_LANGUAGE = "en"

    internal fun normalizedState(
      defaultGroupId: String,
      defaultArtifactId: String,
      defaultPackageName: String,
      language: String?,
    ): SettingsState = SettingsState(
      defaultGroupId = defaultGroupId.trim(),
      defaultArtifactId = defaultArtifactId.trim(),
      defaultPackageName = defaultPackageName.trim(),
      language = language?.trim()?.takeIf { it == "en" || it == "ko" } ?: DEFAULT_LANGUAGE,
    )

    fun getInstance(): EgovSettings = ApplicationManager.getApplication().getService(EgovSettings::class.java)
  }
}
