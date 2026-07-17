package kr.kyg.ijplugin.egovframe.crud

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.IOException
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
  private val fixedClock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC)

  @Test
  fun `custom workflow writes once then opens every generated file`() = withTemporaryDirectory { dir ->
    val first = dir.resolve("greeting.hbs")
    val second = dir.resolve("nested.name.hbs")
    Files.writeString(first, "Hello {{tableName}}")
    Files.writeString(second, "Package {{packageName}}")
    val events = mutableListOf<String>()
    val writer = CrudAuxiliaryWriter { files ->
      events += "write:${files.joinToString { it.target.fileName.toString() }}"
      CrudAuxiliaryWriteResult(files.map { it.target }, emptyList(), emptyList())
    }
    val workflow = CrudAuxiliaryWorkflow(
      writer = writer,
      runWriteCommand = { action ->
        events += "command:start"
        action().also { events += "command:end" }
      },
      openInEditor = { events += "open:${it.fileName}" },
    )

    val result = workflow.renderCustom(prepared(), listOf(first, second))

    assertEquals(listOf("greeting.generated", "nested.name.generated"), result.written.map { it.fileName.toString() })
    assertEquals(
      listOf(
        "command:start",
        "write:greeting.generated, nested.name.generated",
        "command:end",
        "open:greeting.generated",
        "open:nested.name.generated",
      ),
      events,
    )
  }

  @Test
  fun `custom workflow rejects mixed parents and non hbs inputs before writing`() = withTemporaryDirectory { dir ->
    val other = Files.createDirectory(dir.resolve("other"))
    val writer = CrudAuxiliaryWriter { throw AssertionError("writer must not be called") }
    val workflow = CrudAuxiliaryWorkflow(writer, { it() }, {}) { "" }

    assertThrows(IllegalArgumentException::class.java) {
      workflow.renderCustom(prepared(), listOf(dir.resolve("one.hbs"), other.resolve("two.hbs")))
    }
    assertThrows(IllegalArgumentException::class.java) {
      workflow.renderCustom(prepared(), listOf(dir.resolve("one.txt")))
    }
  }

  @Test
  fun `context export uses fixed upstream filename and opens after success`() = withTemporaryDirectory { dir ->
    val events = mutableListOf<String>()
    val writer = CrudAuxiliaryWriter { files ->
      events += "write"
      CrudAuxiliaryWriteResult(files.map { it.target }, emptyList(), emptyList())
    }
    val workflow = CrudAuxiliaryWorkflow(writer, { it() }, { events += "open:${it.fileName}" })

    val result = workflow.exportContext(prepared(), dir)

    assertEquals("SampleItem_TemplateContext.json", result.written.single().fileName.toString())
    assertEquals(listOf("write", "open:SampleItem_TemplateContext.json"), events)
  }

  @Test
  fun `transactional writer restores overwritten file and removes new file on failure`() = withTemporaryDirectory { dir ->
    val first = dir.resolve("first.generated")
    val second = dir.resolve("second.generated")
    Files.writeString(first, "old")
    val writer = TransactionalCrudAuxiliaryWriter(FailSecondStageMoveOps())

    assertThrows(IOException::class.java) {
      writer.write(
        listOf(
          CrudAuxiliaryFile(first, "new-first"),
          CrudAuxiliaryFile(second, "new-second"),
        ),
      )
    }

    assertEquals("old", Files.readString(first))
    assertFalse(Files.exists(second))
  }

  @Test
  fun `transactional writer rejects symlink and non regular targets`() = withTemporaryDirectory { dir ->
    val writer = TransactionalCrudAuxiliaryWriter()
    val directoryTarget = Files.createDirectory(dir.resolve("directory.generated"))
    assertThrows(IllegalArgumentException::class.java) {
      writer.write(listOf(CrudAuxiliaryFile(directoryTarget, "content")))
    }

    val real = dir.resolve("real.generated")
    Files.writeString(real, "old")
    val link = dir.resolve("link.generated")
    val linked = runCatching { Files.createSymbolicLink(link, real.fileName) }.isSuccess
    assumeTrue(linked, "Symbolic links are unavailable in this Windows environment")
    assertThrows(IllegalArgumentException::class.java) {
      writer.write(listOf(CrudAuxiliaryFile(link, "content")))
    }
  }

  @Test
  fun `generation preflight follows upstream package and output rules`() {
    CrudGeneration.preflightGeneration("egovframework..sample", "C:/output")
    assertThrows(IllegalArgumentException::class.java) { CrudGeneration.preflightGeneration("", "C:/output") }
    assertThrows(IllegalArgumentException::class.java) { CrudGeneration.preflightGeneration("Egov.sample", "C:/output") }
    assertThrows(IllegalArgumentException::class.java) { CrudGeneration.preflightGeneration("egov.sample.", "C:/output") }
    assertThrows(IllegalArgumentException::class.java) { CrudGeneration.preflightGeneration("egov.sample", "") }
  }

  private fun prepared(): PreparedCrud = (
    CrudGeneration(fixedClock).prepare(ddl, "egovframework.example.sample") as CrudPreparation.Ready
  ).prepared

  private fun withTemporaryDirectory(block: (Path) -> Unit) {
    val root = Files.createTempDirectory("egovframe-crud-auxiliary-")
    try {
      block(root)
    } finally {
      Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }
  }

  private class FailSecondStageMoveOps : CrudFileOps by NioCrudFileOps {
    private var stageMoves = 0

    override fun moveReplacing(source: Path, target: Path) {
      if (source.fileName.toString().endsWith(".stage")) {
        stageMoves += 1
        if (stageMoves == 2) throw IOException("simulated second commit failure")
      }
      NioCrudFileOps.moveReplacing(source, target)
    }
  }
}
