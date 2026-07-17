package kr.kyg.ijplugin.egovframe.settings

import java.util.Locale
import java.util.ResourceBundle

/**
 * Central locale-aware message resolver.
 *
 * Resolves keys from `messages.EgovBundle` using the language persisted in
 * [EgovSettings]. The bundle is cached per language and replaced only when
 * the configured language changes.
 */
object EgovBundle {

  private const val BUNDLE_BASE = "messages.EgovBundle"

  @Volatile
  private var cached: Pair<String, ResourceBundle>? = null

  /** Resolves [key] using the currently configured language. */
  fun message(key: String): String = bundle().getString(key)

  /** Resolves [key], returning [defaultValue] only while a new key is being developed. */
  fun messageOrDefault(key: String, defaultValue: String): String =
    bundle().takeIf { it.containsKey(key) }?.getString(key) ?: defaultValue

  /**
   * Resolves with simple positional substitution (`{0}`, `{1}`, …).
   */
  fun message(key: String, vararg args: Any?): String {
    val pattern = bundle().getString(key)
    return args.foldIndexed(pattern) { i, acc, arg -> acc.replace("{$i}", arg.toString()) }
  }

  /**
   * Returns the [ResourceBundle] for the currently configured language.
   */
  fun bundle(): ResourceBundle {
    val lang = currentLanguage()
    val entry = cached
    if (entry != null && entry.first == lang) return entry.second
    val locale = if (lang == "ko") Locale.KOREAN else Locale.ENGLISH
    val bundle = ResourceBundle.getBundle(BUNDLE_BASE, locale)
    cached = lang to bundle
    return bundle
  }

  /**
   * Returns the configured language tag (`"en"` or `"ko"`).
   * Falls back to `"en"` when settings are unavailable (e.g. in tests).
   */
  fun currentLanguage(): String = try {
    EgovSettings.getInstance().state.language
  } catch (_: Exception) {
    "en"
  }

  /**
   * Resolves a bundle for a specific language, ignoring the current setting.
   * Used by tests to verify parity.
   */
  fun bundleFor(language: String): ResourceBundle {
    val locale = if (language == "ko") Locale.KOREAN else Locale.ENGLISH
    return ResourceBundle.getBundle(BUNDLE_BASE, locale)
  }

  /** Invalidates the cached bundle so the next call picks up a language change. */
  internal fun invalidateCache() {
    cached = null
  }
}
