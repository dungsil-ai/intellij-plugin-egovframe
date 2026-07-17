package kr.kyg.ijplugin.egovframe.render

import com.github.jknack.handlebars.*
import com.github.jknack.handlebars.context.JavaBeanValueResolver
import com.github.jknack.handlebars.context.MapValueResolver
import com.github.jknack.handlebars.context.MethodValueResolver
import java.security.MessageDigest
import java.util.*

/**
 * Handlebars renderer compatible with the pinned VS Code Initializr templates.
 *
 * A renderer is intentionally built for every [render] call, matching upstream
 * `Handlebars.compile` usage and preventing helpers or inline partials from
 * leaking across independently rendered templates.
 */
object EgovHandlebars {

  /** Resolves Java collection and array sizes as JavaScript's `.length`. */
  private object LengthValueResolver : ValueResolver {
    override fun resolve(context: Any?, name: String): Any {
      if (name != "length") return ValueResolver.UNRESOLVED
      return when (context) {
        is Collection<*> -> context.size
        is Array<*> -> context.size
        is BooleanArray -> context.size
        is ByteArray -> context.size
        is CharArray -> context.size
        is DoubleArray -> context.size
        is FloatArray -> context.size
        is IntArray -> context.size
        is LongArray -> context.size
        is ShortArray -> context.size
        else -> ValueResolver.UNRESOLVED
      }
    }

    override fun resolve(context: Any?): Any = ValueResolver.UNRESOLVED

    override fun propertySet(context: Any?): Set<Map.Entry<String, Any>> = emptySet()
  }

  /**
   * Renders one code or configuration template. [context] is deliberately
   * mutable: `setVar` updates this root map, which makes values immediately
   * visible through `@root` from nested blocks.
   */
  fun render(templateText: String, context: MutableMap<String, Any?>): String {
    val handlebars = newHandlebars(context)
    val template = handlebars.compileInline(normalizeKnownTemplate(templateText))
    return template.apply(newContext(context))
  }

  private fun normalizeKnownTemplate(templateText: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
      .digest(templateText.toByteArray(Charsets.UTF_8))
      .joinToString("") { byte -> "%02x".format(byte) }
    val resourcePath = NORMALIZED_TEMPLATE_PATHS.getProperty(digest) ?: return templateText
    return requireNotNull(EgovHandlebars::class.java.classLoader.getResourceAsStream(resourcePath)) {
      "Missing normalized Handlebars resource: $resourcePath"
    }.bufferedReader(Charsets.UTF_8).use { it.readText() }
  }

  private val NORMALIZED_TEMPLATE_PATHS: Properties by lazy {
    Properties().apply {
      requireNotNull(EgovHandlebars::class.java.classLoader.getResourceAsStream(NORMALIZED_TEMPLATE_INDEX)) {
        "Missing Handlebars normalization index: $NORMALIZED_TEMPLATE_INDEX"
      }.use(::load)
    }
  }

  private const val NORMALIZED_TEMPLATE_INDEX = "egovframe/handlebars-normalized.properties"

  /**
   * Ports configGenerator.ts#renderTemplate: expands local `#parse("...")`
   * directives when available, enriches the user form data, then renders.
   */
  fun renderConfig(
    templateText: String,
    formData: Map<String, Any?>,
    defaultPackageName: String,
    includeResolver: (String) -> String?,
  ): String {
    var expandedTemplate = normalizeKnownTemplate(templateText)
    val parseDirective = Regex("""#parse\(\"(.+)\"\)""")
    parseDirective.findAll(templateText).forEach { match ->
      includeResolver(match.groupValues[1])?.let { included ->
        expandedTemplate = expandedTemplate.replace(match.value, normalizeKnownTemplate(included))
      }
    }

    val context = LinkedHashMap<String, Any?>(formData.size + 1)
    context.putAll(formData)
    context["defaultPackageName"] = defaultPackageName
    return render(expandedTemplate, context)
  }

  /**
   * Compatibility factory for callers that need a raw engine. Its `setVar`
   * helper follows the Handlebars data-root convention; [render] is preferred
   * for root-map mutation parity.
   */
  fun create(): Handlebars = newHandlebars(null)

  private fun newHandlebars(rootMap: MutableMap<String, Any?>?): Handlebars {
    val handlebars = Handlebars().with(EscapingStrategy.HBS4)

    handlebars.registerHelper("eq", Helper<Any?> { a, options ->
      jsStrictEquals(a, options.param<Any?>(0, null))
    })
    handlebars.registerHelper("ne", Helper<Any?> { a, options ->
      !jsStrictEquals(a, options.param<Any?>(0, null))
    })
    handlebars.registerHelper("capitalize", Helper<Any?> { value, _ ->
      val text = value?.toString() ?: ""
      if (text.isEmpty()) "" else text[0].uppercaseChar() + text.substring(1)
    })
    handlebars.registerHelper("trim", Helper<Any?> { value, _ ->
      (value?.toString() ?: "").trim()
    })
    handlebars.registerHelper("or", Helper<Any?> { first, options ->
      isJsTruthy(first) || options.params.any(::isJsTruthy)
    })
    handlebars.registerHelper("empty", Helper<Any?> { value, _ ->
      value == null || value == ""
    })
    handlebars.registerHelper("concat", Helper<Any?> { first, options ->
      buildString {
        append(jsToString(first))
        options.params.forEach { append(jsToString(it)) }
      }
    })
    handlebars.registerHelper("lowercase", Helper<Any?> { value, _ ->
      if (value is String) value.lowercase() else ""
    })
    handlebars.registerHelper("add", Helper<Any?> { first, options ->
      jsNumberToString(normalizeJsNumber(first) + normalizeJsNumber(options.param<Any?>(0, null)))
    })
    handlebars.registerHelper("error", Helper<Any?> { message, _ ->
      Handlebars.SafeString("<span class=\"error\">${jsToString(message)}</span>")
    })
    handlebars.registerHelper("setVar", Helper<Any?> { name, options ->
      val target = rootMap ?: options.data<Any?>("root") as? MutableMap<String, Any?>
      target?.put(name?.toString() ?: "", options.param<Any?>(0, null))
      ""
    })
    return handlebars
  }

  /** JavaScript `Number(value)`, preserving NaN until `|| 0` is applied. */
  private fun jsNumber(value: Any?): Double = when (value) {
    null -> 0.0
    is Number -> value.toDouble()
    is Boolean -> if (value) 1.0 else 0.0
    is String -> {
      val trimmed = value.trim()
      if (trimmed.isEmpty()) 0.0 else trimmed.toDoubleOrNull() ?: Double.NaN
    }

    else -> Double.NaN
  }

  /** JS `(Number(value) || 0)`. */
  private fun normalizeJsNumber(value: Any?): Double {
    val number = jsNumber(value)
    return if (number.isNaN() || number == 0.0) 0.0 else number
  }

  /** Formats finite integral numbers without the Java-only trailing `.0`. */
  private fun jsNumberToString(value: Double): String =
    if (value.isFinite() && value == Math.floor(value) && Math.abs(value) < 1e21) value.toLong()
      .toString() else value.toString()

  /** JavaScript strict equality for primitive values emitted by Handlebars. */
  private fun jsStrictEquals(left: Any?, right: Any?): Boolean {
    if (left == null || right == null) return left === right
    if (left is Number && right is Number) return left.toDouble() == right.toDouble()
    if (left is Number || right is Number) return false
    return left::class == right::class && left == right
  }

  /** JS truthiness: unlike Handlebars `if`, empty collections are truthy. */
  private fun isJsTruthy(value: Any?): Boolean = when (value) {
    null -> false
    is Boolean -> value
    is Number -> value.toDouble() != 0.0 && !value.toDouble().isNaN()
    is String -> value.isNotEmpty()
    else -> true
  }

  private fun jsToString(value: Any?): String = when (value) {
    null -> ""
    is Number -> jsNumberToString(value.toDouble())
    else -> value.toString()
  }

  fun newContext(model: Map<String, Any?>): Context =
    Context.newBuilder(model)
      .resolver(
        MapValueResolver.INSTANCE,
        LengthValueResolver,
        JavaBeanValueResolver.INSTANCE,
        MethodValueResolver.INSTANCE,
      )
      .build()
}
