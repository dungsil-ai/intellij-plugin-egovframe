package kr.kyg.intellij.plugin.egovframe.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HandlebarsSyntaxTest {

  @Test
  fun rendersInlinePartial() {
    assertEquals(
      "Hello eGov",
      render("{{#*inline \"name\"}}Hello {{value}}{{/inline}}{{> name}}", mapOf("value" to "eGov")),
    )
  }

  @Test
  fun rendersElseIfChain() {
    val template = "{{#if (eq kind \"first\")}}1{{else if (eq kind \"second\")}}2{{else}}3{{/if}}"
    assertEquals("2", render(template, mapOf("kind" to "second")))
    assertEquals("3", render(template, mapOf("kind" to "other")))
  }

  @Test
  fun resolvesBracketIndexedMapProperty() {
    assertEquals(
      "firstColumn",
      render("{{attributes.[0].ccName}}", mapOf("attributes" to listOf(mapOf("ccName" to "firstColumn")))),
    )
  }

  @Test
  fun honorsWhitespaceControl() {
    assertEquals("leftXright", render("left {{~value~}} right", mapOf("value" to "X")))
  }

  @Test
  fun resolvesCollectionLengthForConditionalsAndHelpers() {
    val template = "{{#if list.length}}nonempty {{add list.length 1}}{{else}}empty{{/if}}"
    assertEquals("empty", render(template, mapOf("list" to emptyList<String>())))
    assertEquals("nonempty 3", render(template, mapOf("list" to listOf("a", "b"))))
  }

  @Test
  fun setVarChangesRootStateInsideNestedUnlessBlocks() {
    val template =
      "{{~setVar \"foundFirst\" false~}}{{#each attributes}}{{#unless isPrimaryKey}}{{#unless @root.foundFirst}}{{ccName}}{{~setVar \"foundFirst\" true~}}{{/unless}}{{/unless}}{{/each}}"
    val context = mapOf(
      "attributes" to listOf(
        mapOf("ccName" to "primary", "isPrimaryKey" to true),
        mapOf("ccName" to "first", "isPrimaryKey" to false),
        mapOf("ccName" to "second", "isPrimaryKey" to false),
      ),
    )
    assertEquals("first", render(template, context))
  }

  @Test
  fun helpersMatchJavascriptPrimitiveAndEscapingSemantics() {
    val template =
      "{{#if (eq number \"1\")}}wrong{{else}}strict{{/if}}|{{capitalize word}}|{{trim spaced}}|{{#if (or false \"\")}}wrong{{else}}false{{/if}}|{{lowercase word}}|{{add \"3\" \"1\"}}|{{value}}|{{{error value}}}"
    val actual = render(
      template,
      mapOf(
        "number" to 1,
        "word" to "eGOV",
        "spaced" to "  x  ",
        "value" to "&<>\"'`=",
      ),
    )
    assertEquals(
      "strict|EGOV|x|false|egov|4|&amp;&lt;&gt;&quot;&#x27;&#x60;&#x3D;|<span class=\"error\">&<>\"'`=</span>",
      actual,
    )
  }

  @Test
  fun configRendererAddsPackageNameAndRetainsMissingIncludes() {
    assertEquals(
      "before included egovframework.example.sample after",
      EgovHandlebars.renderConfig(
        "before #parse(\"present.hbs\") after",
        emptyMap(),
        "egovframework.example.sample",
        { if (it == "present.hbs") "included {{defaultPackageName}}" else null },
      ),
    )
    assertEquals(
      "#parse(\"absent.hbs\")",
      EgovHandlebars.renderConfig("#parse(\"absent.hbs\")", emptyMap(), "egovframework.example.sample") { null },
    )
  }

  @Test
  fun normalizedRuntimeResourcesUseReadableContentAddressedNames() {
    val classLoader = EgovHandlebars::class.java.classLoader
    val properties = java.util.Properties().apply {
      requireNotNull(classLoader.getResourceAsStream("egovframe/handlebars-normalized.properties")).use(::load)
    }

    properties.forEach { (rawDigest, rawPath) ->
      val digest = rawDigest.toString()
      assertTrue(digest.matches(Regex("[0-9a-f]{64}")), "Invalid source SHA-256 in normalization index: $digest")
      val resourcePath = rawPath.toString()
      val fileName = resourcePath.substringAfterLast('/')
      assertTrue(fileName.substringBefore('@').isNotBlank(), "Missing readable template name in $resourcePath")
      assertTrue(
        fileName.endsWith("@$digest.hbs"),
        "Normalized resource path does not include its source digest: $resourcePath",
      )
      assertNotNull(classLoader.getResource(resourcePath), "Missing normalized runtime resource: $resourcePath")
    }
  }

  private fun render(template: String, values: Map<String, Any?>): String =
    EgovHandlebars.render(template, LinkedHashMap(values))
}
