package kr.kyg.ijplugin.egovframe.render

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GoldenInventoryTest {

  @Test
  fun `golden CRUD inventory matches baseline`() {
    val crudCases = index().getAsJsonArray("crud")
    assertEquals(4, crudCases.size(), "CRUD context count")
    val outputs = crudCases.sumOf { it.asJsonObject.getAsJsonObject("outputs").size() }
    assertEquals(44, outputs, "CRUD output count")
  }

  @Test
  fun `golden config inventory matches baseline`() {
    val configCases = index().getAsJsonArray("config")
    assertEquals(29, configCases.size(), "config context count")
    val outputs = configCases.sumOf { it.asJsonObject.getAsJsonObject("outputs").size() }
    assertEquals(68, outputs, "config output count")
  }

  private fun index() = JsonParser.parseString(
    javaClass.classLoader.getResourceAsStream("golden/index.json")
      ?.bufferedReader(Charsets.UTF_8)
      ?.use { it.readText() }
      ?: throw AssertionError("Missing golden/index.json"),
  ).asJsonObject
}
