package kr.kyg.ijplugin.egovframe.crud

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import kr.kyg.ijplugin.egovframe.SymlinkTestSupport
import org.junit.jupiter.api.Test
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class CrudWriteAdapterTest {

  @Test
  fun `writes only the selected subset and reports overwritten targets in enum order`() = withTemporaryDirectory { root ->
    val existing = root.resolve("existing/default.txt")
    Files.createDirectories(existing.parent)
    Files.writeString(existing, "old")
    val plan = GenerationPlan.create(
      listOf(
        rendered(CrudArtifact.VO, "new/vo.txt", "vo"),
        rendered(CrudArtifact.DEFAULT_VO, "existing/default.txt", "default"),
        rendered(CrudArtifact.CONTROLLER, "new/controller.txt", "controller"),
      ),
      root,
    )

    val result = CrudWriteAdapter().write(plan.select(linkedSetOf(CrudArtifact.CONTROLLER, CrudArtifact.DEFAULT_VO)))

    val defaultTarget = root.resolve("existing/default.txt").toAbsolutePath().normalize()
    val controllerTarget = root.resolve("new/controller.txt").toAbsolutePath().normalize()
    assertEquals(listOf(defaultTarget, controllerTarget), result.written)
    assertEquals(listOf(defaultTarget), result.overwritten)
    assertTrue(result.cleanupFailures.isEmpty())
    assertEquals("default", Files.readString(defaultTarget))
    assertEquals("controller", Files.readString(controllerTarget))
    assertFalse(Files.exists(root.resolve("new/vo.txt")))
  }

  @Test
  fun `rejects target creation or deletion after planning without mutation`() = withTemporaryDirectory { root ->
    val newPlan = GenerationPlan.create(listOf(rendered(CrudArtifact.VO, "target.txt", "generated")), root)
    val target = root.resolve("target.txt")
    Files.writeString(target, "concurrent")

    assertStale { CrudWriteAdapter().write(newPlan.select(setOf(CrudArtifact.VO))) }
    assertEquals("concurrent", Files.readString(target))
    assertEquals(listOf("target.txt"), childNames(root))

    val collisionPlan = GenerationPlan.create(listOf(rendered(CrudArtifact.VO, "target.txt", "generated")), root)
    Files.delete(target)

    assertStale { CrudWriteAdapter().write(collisionPlan.select(setOf(CrudArtifact.VO))) }
    assertTrue(childNames(root).isEmpty())
  }

  @Test
  fun `rejects target type changes after planning without mutation`() = withTemporaryDirectory { root ->
    val target = root.resolve("target.txt")
    Files.writeString(target, "old")
    val directoryPlan = GenerationPlan.create(listOf(rendered(CrudArtifact.VO, "target.txt", "generated")), root)
    Files.delete(target)
    Files.createDirectory(target)

    assertStale { CrudWriteAdapter().write(directoryPlan.select(setOf(CrudArtifact.VO))) }
    assertTrue(Files.isDirectory(target))

    Files.delete(target)
    Files.writeString(target, "old")
    val symlinkPlan = GenerationPlan.create(listOf(rendered(CrudArtifact.VO, "target.txt", "generated")), root)
    val source = root.resolve("source.txt")
    Files.writeString(source, "source")
    Files.delete(target)
    createSymbolicLinkOrSkip(target, source)

    assertStale { CrudWriteAdapter().write(symlinkPlan.select(setOf(CrudArtifact.VO))) }
    assertTrue(Files.isSymbolicLink(target))
  }

  @Test
  fun `rejects a changed parent real path without mutation`() = withTemporaryDirectory { root ->
    val firstDestination = Files.createDirectory(root.resolve("first"))
    val secondDestination = Files.createDirectory(root.resolve("second"))
    val link = root.resolve("src")
    createSymbolicLinkOrSkip(link, firstDestination)
    val plan = GenerationPlan.create(listOf(rendered(CrudArtifact.VO, "src/generated.txt", "generated")), root)
    Files.delete(link)
    createSymbolicLinkOrSkip(link, secondDestination)

    assertStale { CrudWriteAdapter().write(plan.select(setOf(CrudArtifact.VO))) }
    assertFalse(Files.exists(firstDestination.resolve("generated.txt")))
    assertFalse(Files.exists(secondDestination.resolve("generated.txt")))
  }

  @Test
  fun `rolls back the current attempted entry after a move changes the target then fails`() =
    withTemporaryDirectory { root ->
      val collision = root.resolve("existing/second.txt")
      Files.createDirectories(collision.parent)
      Files.writeString(collision, "original")
      val plan = GenerationPlan.create(
        listOf(
          rendered(CrudArtifact.VO, "created/first.txt", "first-generated"),
          rendered(CrudArtifact.DEFAULT_VO, "existing/second.txt", "second-generated"),
        ),
        root,
      )
      val fileOps = FailingCrudFileOps(stageMoveFailureAfter = 2)

      assertThrows(IOException::class.java) {
        CrudWriteAdapter(fileOps).write(plan.select(setOf(CrudArtifact.VO, CrudArtifact.DEFAULT_VO)))
      }

      assertFalse(Files.exists(root.resolve("created/first.txt")))
      assertFalse(Files.exists(root.resolve("created")))
      assertEquals("original", Files.readString(collision))
      assertTrue(temporaryFiles(root).isEmpty())
    }

  @Test
  fun `cleans only directories actually created before a directory creation failure`() = withTemporaryDirectory { root ->
    val plan = GenerationPlan.create(
      listOf(rendered(CrudArtifact.VO, "one/two/generated.txt", "generated")),
      root,
    )
    val fileOps = FailingCrudFileOps(directoryFailureAt = 2)

    assertThrows(IOException::class.java) {
      CrudWriteAdapter(fileOps).write(plan.select(setOf(CrudArtifact.VO)))
    }

    assertFalse(Files.exists(root.resolve("one")))
    assertTrue(childNames(root).isEmpty())
  }

  @Test
  fun `preserves the backup and suppresses its path when rollback restore fails`() = withTemporaryDirectory { root ->
    val collision = root.resolve("existing/second.txt")
    Files.createDirectories(collision.parent)
    Files.writeString(collision, "original")
    val plan = GenerationPlan.create(
      listOf(
        rendered(CrudArtifact.VO, "created/first.txt", "first-generated"),
        rendered(CrudArtifact.DEFAULT_VO, "existing/second.txt", "second-generated"),
      ),
      root,
    )
    val fileOps = FailingCrudFileOps(stageMoveFailureAfter = 2, failRestore = true)

    val error = assertThrows(IOException::class.java) {
      CrudWriteAdapter(fileOps).write(plan.select(setOf(CrudArtifact.VO, CrudArtifact.DEFAULT_VO)))
    }

    val restoreFailure = error.suppressed.single { it.message!!.startsWith("Failed to restore CRUD file") }
    val backup = Path.of(restoreFailure.message!!.substringAfter("backup preserved at "))
    assertTrue(Files.isRegularFile(backup))
    assertEquals("original", Files.readString(backup))
    assertEquals("second-generated", Files.readString(collision))
    assertFalse(Files.exists(root.resolve("created")))
  }

  @Test
  fun `returns cleanup failures after commit without rolling back generated targets`() = withTemporaryDirectory { root ->
    val target = root.resolve("existing.txt")
    Files.writeString(target, "original")
    val plan = GenerationPlan.create(
      listOf(rendered(CrudArtifact.VO, "existing.txt", "generated")),
      root,
    )
    val fileOps = FailingCrudFileOps(failBackupDelete = true)

    val result = CrudWriteAdapter(fileOps).write(plan.select(setOf(CrudArtifact.VO)))

    assertEquals("generated", Files.readString(target))
    assertEquals(1, result.cleanupFailures.size)
    assertTrue(result.cleanupFailures.single().fileName.toString().endsWith(".backup"))
    assertTrue(Files.isRegularFile(result.cleanupFailures.single()))
  }

  private fun rendered(artifact: CrudArtifact, relativePath: String, content: String) = RenderedCrudArtifact(
    artifact = artifact,
    relativePath = relativePath,
    fileName = relativePath.substringAfterLast('/'),
    language = "text",
    content = content,
  )

  private fun assertStale(block: () -> Unit) {
    val error = assertThrows(IllegalStateException::class.java, block)
    assertEquals("CRUD generation plan is stale; review file collisions and try again.", error.message)
  }

  private fun childNames(root: Path): List<String> =
    Files.list(root).use { paths -> paths.map { it.fileName.toString() }.sorted().toList() }

  private fun temporaryFiles(root: Path): List<Path> =
    Files.walk(root).use { paths ->
      paths.filter { path ->
        val name = path.fileName?.toString().orEmpty()
        name.endsWith(".stage") || name.endsWith(".backup")
      }.toList()
    }

  private fun createSymbolicLinkOrSkip(link: Path, target: Path) =
    SymlinkTestSupport.createSymbolicLinkOrSkip(link, target)

  private fun withTemporaryDirectory(block: (Path) -> Unit) {
    val root = Files.createTempDirectory("egovframe-crud-write-")
    try {
      block(root)
    } finally {
      Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }
  }

  private class FailingCrudFileOps(
    private val directoryFailureAt: Int? = null,
    private val stageMoveFailureAfter: Int? = null,
    private val failRestore: Boolean = false,
    private val failBackupDelete: Boolean = false,
  ) : CrudFileOps by NioCrudFileOps {
    private var directoryCalls = 0
    private var stageMoveCalls = 0

    override fun createDirectory(path: Path) {
      directoryCalls += 1
      if (directoryCalls == directoryFailureAt) throw IOException("Injected directory creation failure: $path")
      NioCrudFileOps.createDirectory(path)
    }

    override fun moveReplacing(source: Path, target: Path) {
      when {
        source.fileName.toString().endsWith(".stage") -> {
          stageMoveCalls += 1
          NioCrudFileOps.moveReplacing(source, target)
          if (stageMoveCalls == stageMoveFailureAfter) throw IOException("Injected post-move failure: $target")
        }
        source.fileName.toString().endsWith(".backup") && failRestore ->
          throw IOException("Injected restore failure: $target")
        else -> NioCrudFileOps.moveReplacing(source, target)
      }
    }

    override fun deleteIfExists(path: Path): Boolean {
      if (failBackupDelete && path.fileName.toString().endsWith(".backup")) {
        throw IOException("Injected backup cleanup failure: $path")
      }
      return NioCrudFileOps.deleteIfExists(path)
    }
  }
}
