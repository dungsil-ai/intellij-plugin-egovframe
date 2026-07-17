package kr.kyg.ijplugin.egovframe.crud

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class CrudGenerationTest {

  private val ddl = """
    CREATE TABLE sample_item (
      item_id INT PRIMARY KEY,
      item_name VARCHAR(100) NOT NULL
    );
  """.trimIndent()
  private val packageName = "egovframework.example.sample"
  private val fixedClock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC)

  @Test
  fun `prepares summary ERD custom rendering and pretty context JSON`() {
    val prepared = ready(CrudGeneration(fixedClock).prepare(ddl, packageName))

    assertEquals(CrudTableSummary("SampleItem", "sample_item", 2), prepared.summary)
    assertEquals(
      "[sample_item]\n  item_id: INT [PK]\n  item_name: VARCHAR\n\n",
      prepared.erdText,
    )
    assertEquals(
      "Table: sample_item, Date: 2025-01-01",
      prepared.renderCustom("Table: {{dbTableName}}, Date: {{date}}"),
    )

    val json = prepared.contextJson()
    assertTrue(json.endsWith("\n"))
    assertTrue(json.contains("\n  \"tableName\": \"SampleItem\","))
    assertEquals("SampleItem", JsonParser.parseString(json).asJsonObject["tableName"].asString)
    assertEquals("2025-01-01", JsonParser.parseString(json).asJsonObject["date"].asString)
  }

  @Test
  fun `context JSON matches upstream key shape without packagePath`() {
    val prepared = ready(CrudGeneration(fixedClock).prepare(ddl, packageName))
    val json = JsonParser.parseString(prepared.contextJson()).asJsonObject
    val keys = json.keySet().toList()

    assertEquals(
      listOf(
        "tableName", "dbTableName", "attributes", "pkAttributes",
        "packageName", "className", "classNameFirstCharLower",
        "author", "date", "version",
      ),
      keys,
    )
    assertNull(json.get("packagePath"))
  }

  @Test
  fun `context file name uses tableName underscore TemplateContext`() {
    val prepared = ready(CrudGeneration(fixedClock).prepare(ddl, packageName))
    assertEquals("SampleItem_TemplateContext.json", prepared.contextFileName)
  }

  @Test
  fun `owns the exact eleven artifact mappings`() {
    val tableName = "SampleItem"
    val actual = CrudArtifact.entries.map { artifact ->
      listOf(artifact.name, artifact.templateFile, artifact.relativePath(tableName, packageName), artifact.language)
    }

    assertEquals(
      listOf(
        listOf("VO", "sample-vo-template.hbs", "src/main/java/egovframework/example/sample/service/SampleItemVO.java", "java"),
        listOf(
          "DEFAULT_VO",
          "sample-default-vo-template.hbs",
          "src/main/java/egovframework/example/sample/service/SampleItemDefaultVO.java",
          "java",
        ),
        listOf(
          "CONTROLLER",
          "sample-controller-template.hbs",
          "src/main/java/egovframework/example/sample/web/SampleItemController.java",
          "java",
        ),
        listOf(
          "SERVICE",
          "sample-service-template.hbs",
          "src/main/java/egovframework/example/sample/service/SampleItemService.java",
          "java",
        ),
        listOf(
          "SERVICE_IMPL",
          "sample-service-impl-template.hbs",
          "src/main/java/egovframework/example/sample/service/impl/SampleItemServiceImpl.java",
          "java",
        ),
        listOf(
          "MAPPER_INTERFACE",
          "sample-mapper-interface-template.hbs",
          "src/main/java/egovframework/example/sample/service/impl/SampleItemMapper.java",
          "java",
        ),
        listOf("MAPPER_XML", "sample-mapper-template.hbs", "src/main/resources/mapper/SampleItem_SQL.xml", "xml"),
        listOf(
          "THYMELEAF_LIST",
          "sample-thymeleaf-list.hbs",
          "src/main/resources/templates/thymeleaf/sampleItem/sampleItemList.html",
          "html",
        ),
        listOf(
          "THYMELEAF_REGISTER",
          "sample-thymeleaf-register.hbs",
          "src/main/resources/templates/thymeleaf/sampleItem/sampleItemRegister.html",
          "html",
        ),
        listOf(
          "JSP_LIST",
          "sample-jsp-list.hbs",
          "src/main/webapp/WEB-INF/jsp/egovframework/example/sample/sampleItemList.jsp",
          "html",
        ),
        listOf(
          "JSP_REGISTER",
          "sample-jsp-register.hbs",
          "src/main/webapp/WEB-INF/jsp/egovframework/example/sample/sampleItemRegister.jsp",
          "html",
        ),
      ),
      actual,
    )
  }

  @Test
  fun `lazily caches artifacts and isolates every render context`() {
    val prepared = ready(CrudGeneration(fixedClock).prepare(ddl, packageName))
    val artifacts = prepared.artifacts
    val originalBytes = artifacts.map(RenderedCrudArtifact::content)

    assertSame(artifacts, prepared.artifacts)
    assertEquals("Mutated", prepared.renderCustom("{{setVar \"tableName\" \"Mutated\"}}{{tableName}}"))
    assertEquals("SampleItem", prepared.renderCustom("{{tableName}}"))
    assertEquals(originalBytes, prepared.artifacts.map(RenderedCrudArtifact::content))
    assertEquals("SampleItem", JsonParser.parseString(prepared.contextJson()).asJsonObject["tableName"].asString)
  }

  @Test
  fun `caches by exact DDL package and UTC date`() {
    val clock = MutableClock(Instant.parse("2025-01-01T10:00:00Z"))
    val generation = CrudGeneration(clock)

    val first = generation.prepare(ddl, packageName)
    assertSame(first, generation.prepare(ddl, packageName))

    val changedDdl = generation.prepare(ddl + "\n", packageName)
    assertNotSame(first, changedDdl)

    val changedPackage = generation.prepare(ddl + "\n", "$packageName.other")
    assertNotSame(changedDdl, changedPackage)

    clock.current = Instant.parse("2025-01-02T00:00:00Z")
    val changedDate = generation.prepare(ddl + "\n", "$packageName.other")
    assertNotSame(changedPackage, changedDate)
  }

  @Test
  fun `rejects invalid input and multi-table CRUD while preserving ERD`() {
    assertEquals(CrudPreparation.Rejected("Invalid DDL"), CrudGeneration(fixedClock).prepare("", packageName))
    assertEquals(
      CrudPreparation.Rejected("Invalid DDL"),
      CrudGeneration(fixedClock).prepare("SELECT * FROM sample_item", packageName),
    )

    val rejected = CrudGeneration(fixedClock).prepare(
      """
        CREATE TABLE first_table (id INT PRIMARY KEY);
        CREATE TABLE second_table (id INT PRIMARY KEY);
      """.trimIndent(),
      packageName,
    ) as CrudPreparation.Rejected
    assertEquals("CRUD generation requires exactly one CREATE TABLE statement.", rejected.message)
    assertFalse(rejected.erdText.isEmpty())
    assertTrue(rejected.erdText.contains("[first_table]"))
    assertTrue(rejected.erdText.contains("[second_table]"))
  }

  @Test
  fun `preflight rejects uppercase and invalid package names`() {
    assertThrows(IllegalArgumentException::class.java) {
      CrudGeneration.preflightGeneration("Egovframework.Example", "/output")
    }
    assertThrows(IllegalArgumentException::class.java) {
      CrudGeneration.preflightGeneration("egovframework..sample", "/output")
    }
    assertThrows(IllegalArgumentException::class.java) {
      CrudGeneration.preflightGeneration(".leading.dot", "/output")
    }
    assertThrows(IllegalArgumentException::class.java) {
      CrudGeneration.preflightGeneration("trailing.dot.", "/output")
    }
    assertThrows(IllegalArgumentException::class.java) {
      CrudGeneration.preflightGeneration("", "/output")
    }
  }

  @Test
  fun `preflight rejects blank and invalid output root`() {
    assertThrows(IllegalArgumentException::class.java) {
      CrudGeneration.preflightGeneration("egovframework.example.sample", "")
    }
    assertThrows(IllegalArgumentException::class.java) {
      CrudGeneration.preflightGeneration("egovframework.example.sample", "   ")
    }
  }

  @Test
  fun `preflight accepts valid package names and output root`() {
    CrudGeneration.preflightGeneration("egovframework.example.sample", "/project")
    CrudGeneration.preflightGeneration("a", "/project")
    CrudGeneration.preflightGeneration("a1", "/project")
    CrudGeneration.preflightGeneration("egovframework.example1.sample2", "/project")
  }

  private fun ready(preparation: CrudPreparation): PreparedCrud =
    (preparation as CrudPreparation.Ready).prepared

  private class MutableClock(var current: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = Clock.fixed(current, zone)

    override fun instant(): Instant = current
  }
}
