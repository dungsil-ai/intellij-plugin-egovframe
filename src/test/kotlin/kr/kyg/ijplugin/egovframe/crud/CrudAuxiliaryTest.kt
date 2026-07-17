package kr.kyg.ijplugin.egovframe.crud

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class CrudAuxiliaryTest {

  private val ddl = """
    CREATE TABLE sample_item (
      item_id INT PRIMARY KEY,
      item_name VARCHAR(100) NOT NULL
    );
  """.trimIndent()
  private val packageName = "egovframework.example.sample"
  private val fixedClock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC)

  @Test
  fun `context file name uses tableName_TemplateContext pattern`() {
    val prepared = prepared()
    assertEquals("SampleItem_TemplateContext.json", prepared.contextFileName)
  }

  @Test
  fun `custom render produces baseName dot generated alongside each HBS`() = withTemporaryDirectory { dir ->
    val template1 = dir.resolve("greeting.hbs")
    Files.writeString(template1, "Hello {{tableName}}")
    val template2 = dir.resolve("package.hbs")
    Files.writeString(template2, "Package: {{packageName}}")

    val prepared = prepared()
    val written = mutableListOf<Path>()
    val opened = mutableListOf<Path>()
    val ports = recordingPorts(written, opened)

    for (hbs in listOf(template1, template2)) {
      val templateText = Files.readString(hbs)
      val content = prepared.renderCustom(templateText)
      val baseName = hbs.fileName.toString().removeSuffix(".hbs")
      val output = hbs.resolveSibling("$baseName.generated")
      ports.writeFile(output, content)
      ports.openInEditor(output)
    }

    assertEquals(2, written.size)
    assertTrue(written[0].fileName.toString() == "greeting.generated")
    assertTrue(written[1].fileName.toString() == "package.generated")
    assertEquals(2, opened.size)
  }

  @Test
  fun `mixed parent folders are detected`() {
    val paths = listOf(Path.of("/a/one.hbs"), Path.of("/b/two.hbs"))
    val parents = paths.map { it.parent }.toSet()
    assertTrue(parents.size > 1)
  }

  @Test
  fun `same parent folder is accepted`() {
    val paths = listOf(Path.of("/a/one.hbs"), Path.of("/a/two.hbs"))
    val parents = paths.map { it.parent }.toSet()
    assertEquals(1, parents.size)
  }

  @Test
  fun `context export writes to directory with fixed filename`() = withTemporaryDirectory { dir ->
    val prepared = prepared()
    val output = dir.resolve(prepared.contextFileName)
    Files.writeString(output, prepared.contextJson())

    assertTrue(Files.isRegularFile(output))
    assertEquals("SampleItem_TemplateContext.json", output.fileName.toString())
    assertTrue(Files.readString(output).contains("\"tableName\": \"SampleItem\""))
  }

  private fun prepared(): PreparedCrud {
    val generation = CrudGeneration(fixedClock)
    return (generation.prepare(ddl, packageName) as CrudPreparation.Ready).prepared
  }

  private fun recordingPorts(
    writtenFiles: MutableList<Path>,
    openedFiles: MutableList<Path>,
  ): CrudAuxiliaryPorts = object : CrudAuxiliaryPorts {
    override fun chooseHbsFiles(): List<Path>? = null
    override fun chooseDirectory(title: String, initial: Path?): Path? = null
    override fun writeFile(path: Path, content: String) {
      Files.createDirectories(path.parent)
      Files.writeString(path, content, Charsets.UTF_8)
      writtenFiles.add(path)
    }
    override fun openInEditor(path: Path) {
      openedFiles.add(path)
    }
  }

  private fun withTemporaryDirectory(block: (Path) -> Unit) {
    val root = Files.createTempDirectory("egovframe-crud-auxiliary-")
    try {
      block(root)
    } finally {
      Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }
  }
}
