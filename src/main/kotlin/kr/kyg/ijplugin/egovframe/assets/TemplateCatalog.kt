package kr.kyg.ijplugin.egovframe.assets

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class ProjectTemplate(
  val displayName: String,
  val fileName: String,
  val pomFile: String,
  val description: String,
  val category: String,
  val projectName: String,
)

data class ConfigTemplate(
  val displayName: String,
  val templateFolder: String,
  val templateFile: String,
  val webView: String,
  val fileNameProperty: String,
  val javaConfigTemplate: String,
  val yamlTemplate: String,
  val propertiesTemplate: String,
  val description: String,
)

object TemplateCatalog {
  private val gson = Gson()

  val projects: List<ProjectTemplate> by lazy {
    gson.fromJson(
      EgovAssets.resourceText(EgovAssets.PROJECT_CATALOG),
      object : TypeToken<List<ProjectTemplate>>() {}.type,
    )
  }

  val configs: List<ConfigTemplate> by lazy {
    gson.fromJson(
      EgovAssets.resourceText(EgovAssets.CONFIG_CATALOG),
      object : TypeToken<List<ConfigTemplate>>() {}.type,
    )
  }

  val configDefaults: Map<String, Map<String, Any?>> by lazy {
    gson.fromJson(
      EgovAssets.resourceText("egovframe/config-defaults.json"),
      object : TypeToken<Map<String, Map<String, Any?>>>() {}.type,
    )
  }

  fun projectCategories(): List<String> = projects.map(ProjectTemplate::category).distinct()
}
