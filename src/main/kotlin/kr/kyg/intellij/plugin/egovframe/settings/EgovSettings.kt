package kr.kyg.intellij.plugin.egovframe.settings

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
    var defaultGroupId: String = "egovframework.com",
    var defaultArtifactId: String = "egovframe-project",
    var defaultPackageName: String = "egovframework.example.sample",
  )

  private var settingsState = SettingsState()

  override fun getState(): SettingsState = settingsState

  override fun loadState(state: SettingsState) {
    XmlSerializerUtil.copyBean(state, settingsState)
  }

  companion object {
    fun getInstance(): EgovSettings = ApplicationManager.getApplication().getService(EgovSettings::class.java)
  }
}
