package kr.kyg.ijplugin.egovframe.settings


/**
 * Deterministic plugin metadata constants.
 *
 * Exposed via the IntelliJ Plugin Manager description and through the
 * About dialog. The version is resolved at runtime from the plugin
 * descriptor so it never drifts from `gradle.properties`.
 */
object PluginMetadata {
  const val ID = "kr.kyg.ijplugin.egovframe"
  const val NAME = "eGovFrame Initializr (Community)"
  const val DESCRIPTION_EN =
    "Korean eGovernment Standard Framework (eGovFrame) 5.0 support for IntelliJ IDEA. " +
      "Requires an open project for Config/CRUD generation."
  const val DESCRIPTION_KO =
    "IntelliJ IDEA용 전자정부프레임워크(eGovFrame) 5.0 지원 플러그인입니다. " +
      "설정/CRUD 생성에는 열린 프로젝트가 필요합니다."
  const val REPOSITORY = "https://github.com/dungsil-ai/intellij-plugin-egovframe"
  const val HOMEPAGE = "https://github.com/dungsil-ai/intellij-plugin-egovframe"
  const val GUIDE = "https://github.com/dungsil-ai/intellij-plugin-egovframe#readme"
  const val AUTHOR = "dungsil"
  const val LICENSE = "Apache-2.0"
  const val LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0"

  /**
   * Returns the version embedded into the plugin resources during `processResources`.
   * Falls back to `"dev"` only when running from incomplete development outputs.
   */
  fun version(): String = PluginMetadata::class.java.classLoader
    .getResourceAsStream("egovframe/plugin-metadata.properties")
    ?.bufferedReader()
    ?.useLines { lines ->
      lines.firstOrNull { it.startsWith("version=") }?.substringAfter("version=")
    }
    ?.takeIf(String::isNotBlank)
    ?: "dev"

  fun description(language: String): String =
    if (language == "ko") DESCRIPTION_KO else DESCRIPTION_EN
}
