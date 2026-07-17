package kr.kyg.ijplugin.egovframe.render


/**
 * Renders bundled `.hbs` sources with upstream-compatible semantics.
 *
 * Mirrors `configGenerator.ts#renderTemplate`: a simple `#parse("relative")`
 * include mechanism is applied before compilation (currently unused by the
 * pinned assets but kept for parity), then the template is compiled inline.
 */
class TemplateRenderer {

  /**
   * @param templateSource raw template text
   * @param includeResolver resolves a `#parse("name")` reference to its text, or null when absent
   */
  fun render(
    templateSource: String,
    context: Map<String, Any?>,
    includeResolver: (String) -> String? = { null },
  ): String = EgovHandlebars.renderConfig(
    templateText = templateSource,
    formData = context,
    defaultPackageName = context["defaultPackageName"]?.toString() ?: DEFAULT_PACKAGE_NAME,
    includeResolver = includeResolver,
  )

  private companion object {
    const val DEFAULT_PACKAGE_NAME = "egovframework.example.sample"
  }
}
