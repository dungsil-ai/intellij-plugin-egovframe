package kr.kyg.ijplugin.egovframe.settings

/**
 * Deterministic plugin metadata constants.
 *
 * Exposed via the IntelliJ Plugin Manager description and optionally through
 * a small About dialog. All values are compile-time constants so they can be
 * tested without a running IDE.
 */
object PluginMetadata {
  const val ID = "kr.kyg.ijplugin.egovframe"
  const val NAME = "eGovFrame Initializr (Community)"
  const val VERSION = "0.1.0-5.0.6"
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

  fun description(language: String): String =
    if (language == "ko") DESCRIPTION_KO else DESCRIPTION_EN
}
