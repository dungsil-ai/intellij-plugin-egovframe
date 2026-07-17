package kr.kyg.ijplugin.egovframe.crud

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.time.LocalDate

class CrudGeneratorTest {

  private val ddl = """
        CREATE TABLE sample_item (
          item_id INT PRIMARY KEY,
          item_name VARCHAR(100) NOT NULL
        );
    """.trimIndent()

  private val packageName = "egovframework.example.sample"

  @Test
  fun prepareBuildsContextWithExpectedKeys() {
    val prepared = CrudGenerator.prepare(ddl, packageName, LocalDate.parse("2025-01-01"))
    val ctx = prepared.context
    assertEquals("SampleItem", ctx["tableName"])
    assertEquals("sample_item", ctx["dbTableName"])
    assertEquals(packageName, ctx["packageName"])
    assertEquals("2025-01-01", ctx["date"])
    assertEquals("egovframework/example/sample", ctx["packagePath"])
  }

  @Test
  fun renderFilesProducesElevenArtifacts() {
    val prepared = CrudGenerator.prepare(ddl, packageName)
    val rendered = prepared.renderFiles()
    assertEquals(11, rendered.size)
    assertTrue(rendered.any { it.info.fileName == "SampleItemVO.java" })
    assertTrue(rendered.any { it.info.fileName == "SampleItem_SQL.xml" })
  }

  @Test
  fun generateWritesFilesToDisk() {
    val prepared = CrudGenerator.prepare(ddl, packageName)
    val rendered = prepared.renderFiles()
    val root = Files.createTempDirectory("egov-crud-test")
    val result = CrudGenerator.generate(root, rendered)
    assertEquals(11, result.written.size)
    assertTrue(result.written.all(Files::isRegularFile))
    assertTrue(result.written.any { it.endsWith("SampleItemVO.java") })
    assertTrue(result.written.any { it.endsWith("SampleItem_SQL.xml") })
  }

  @Test
  fun renderTemplateExpandsCustomHandlebars() {
    val prepared = CrudGenerator.prepare(ddl, packageName, LocalDate.parse("2025-06-01"))
    val output = prepared.renderTemplate("Table: {{dbTableName}}, Date: {{date}}")
    assertEquals("Table: sample_item, Date: 2025-06-01", output)
  }
}
