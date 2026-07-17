package kr.kyg.intellij.plugin.egovframe.crud

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDate

/**
 * For each CRUD entry in `golden/index.json`, calls [CrudGenerator.prepare]
 * with the golden date, serializes `PreparedCrud.context` to a Gson [JsonElement],
 * and deep-compares against the golden `context.json` (key-order independent).
 */
class CrudContextEquivalenceTest {

  companion object {
    private val cl: ClassLoader = CrudContextEquivalenceTest::class.java.classLoader

    private fun resource(path: String): String {
      val stream = cl.getResourceAsStream(path)
      assertNotNull(stream, "Missing classpath resource: $path")
      return stream!!.reader().readText()
    }

    @JvmStatic
    fun data(): Collection<Arguments> {
      val indexStream = cl.getResourceAsStream("golden/index.json")
      assertNotNull(indexStream, "golden/index.json must be on the classpath")
      val index = JsonParser.parseString(indexStream!!.reader().readText()).asJsonObject
      val crud = index.getAsJsonArray("crud")
      return crud.map { entry ->
        val obj = entry.asJsonObject
        Arguments.of(
          obj["case"].asString,
          obj["ddl"].asString,
          obj["context"].asString,
        )
      }
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("data")
  fun `context matches golden file`(
    caseName: String,
    ddlPath: String,
    contextPath: String,
  ) {
    val ddl = resource(ddlPath)
    val expectedJson = resource(contextPath)

    val expectedElement: JsonElement = JsonParser.parseString(expectedJson)
    val expectedObj = expectedElement.asJsonObject

    // Extract date and packageName from the golden context itself
    val date = expectedObj["date"].asString
    val packageName = expectedObj["packageName"].asString

    val prepared = CrudGenerator.prepare(ddl, packageName, LocalDate.parse(date))

    val gson = Gson()
    val actualElement: JsonElement = gson.toJsonTree(prepared.context)

    assertEquals(
      expectedElement,
      actualElement,
      "Golden mismatch for '$caseName'",
    )
  }
}
