package kr.kyg.ijplugin.egovframe.crud

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/** Compares the production CRUD context JSON with every committed golden context. */
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

    val clock = Clock.fixed(LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    val prepared = (CrudGeneration(clock).prepare(ddl, packageName) as CrudPreparation.Ready).prepared
    val actualElement: JsonElement = JsonParser.parseString(prepared.contextJson())

    assertEquals(
      expectedElement,
      actualElement,
      "Golden mismatch for '$caseName'",
    )
  }
}
