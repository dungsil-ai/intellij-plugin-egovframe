package kr.kyg.intellij.plugin.egovframe.crud

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.LocalDate

/**
 * For each CRUD entry in `golden/index.json`, calls [CrudGenerator.prepare]
 * with the golden date, serializes `PreparedCrud.context` to a Gson [JsonElement],
 * and deep-compares against the golden `context.json` (key-order independent).
 */
@RunWith(Parameterized::class)
class CrudContextEquivalenceTest(
  private val caseName: String,
  private val ddlPath: String,
  private val contextPath: String,
) {

  companion object {
    private val cl: ClassLoader = CrudContextEquivalenceTest::class.java.classLoader

    private fun resource(path: String): String {
      val stream = cl.getResourceAsStream(path)
      assertNotNull("Missing classpath resource: $path", stream)
      return stream!!.reader().readText()
    }

    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun data(): Collection<Array<String>> {
      val indexStream = cl.getResourceAsStream("golden/index.json")
      assertNotNull("golden/index.json must be on the classpath", indexStream)
      val index = JsonParser.parseString(indexStream!!.reader().readText()).asJsonObject
      val crud = index.getAsJsonArray("crud")
      return crud.map { entry ->
        val obj = entry.asJsonObject
        arrayOf(
          obj["case"].asString,
          obj["ddl"].asString,
          obj["context"].asString,
        )
      }
    }
  }

  @Test
  fun `context matches golden file`() {
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
      "Golden mismatch for '$caseName'",
      expectedElement,
      actualElement,
    )
  }
}
