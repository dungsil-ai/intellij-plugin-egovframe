package kr.kyg.ijplugin.egovframe.crud

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import kr.kyg.ijplugin.egovframe.SymlinkTestSupport
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class CrudGenerationPlanTest {

  @Test
  fun `plans all artifacts without collisions under a fresh root`() = withTemporaryDirectory { root ->
    val plan = prepared().plan(root)

    assertEquals(11, plan.artifacts.size)
    assertTrue(plan.artifacts.none(PlannedCrudArtifact::collision))
    assertTrue(plan.artifacts.all { it.target.startsWith(root.toAbsolutePath().normalize()) })
  }

  @Test
  fun `allows an output root that does not exist yet`() = withTemporaryDirectory { root ->
    val outputRoot = root.resolve("new-project")

    val plan = prepared().plan(outputRoot)

    assertFalse(Files.exists(outputRoot))
    assertEquals(outputRoot.toAbsolutePath().normalize(), plan.outputRoot)
    assertTrue(plan.artifacts.none(PlannedCrudArtifact::collision))
  }

  @Test
  fun `records regular file collisions and treats selection as overwrite approval`() = withTemporaryDirectory { root ->
    val target = root.resolve("src/main/java/egovframework/example/sample/service/SampleItemVO.java")
    Files.createDirectories(target.parent)
    Files.writeString(target, "existing")

    val plan = prepared().plan(root)
    val entry = plan.artifacts.single { it.artifact == CrudArtifact.VO }
    val writePlan = plan.select(setOf(CrudArtifact.VO))

    assertTrue(entry.collision)
    assertEquals(listOf(entry), writePlan.artifacts)
  }

  @Test
  fun `rejects empty and unknown selections`() = withTemporaryDirectory { root ->
    val complete = prepared().plan(root)
    val emptyError = assertThrows(IllegalArgumentException::class.java) { complete.select(emptySet()) }
    assertEquals("Select at least one CRUD artifact", emptyError.message)

    val partial = GenerationPlan(
      artifacts = complete.artifacts.filter { it.artifact == CrudArtifact.VO },
      outputRoot = complete.outputRoot,
      projectedRootRealPath = complete.projectedRootRealPath,
    )
    val unknownError = assertThrows(IllegalArgumentException::class.java) {
      partial.select(setOf(CrudArtifact.SERVICE, CrudArtifact.CONTROLLER))
    }
    assertEquals(
      "Selected CRUD artifacts are not part of this generation plan: CONTROLLER, SERVICE",
      unknownError.message,
    )
  }

  @Test
  fun `sorts selected artifacts in enum declaration order`() = withTemporaryDirectory { root ->
    val plan = prepared().plan(root)

    val selected = plan.select(linkedSetOf(CrudArtifact.JSP_REGISTER, CrudArtifact.VO, CrudArtifact.MAPPER_XML))

    assertEquals(
      listOf(CrudArtifact.VO, CrudArtifact.MAPPER_XML, CrudArtifact.JSP_REGISTER),
      selected.artifacts.map(PlannedCrudArtifact::artifact),
    )
  }

  @Test
  fun `rejects lexical escape`() = withTemporaryDirectory { root ->
    val error = assertThrows(IllegalArgumentException::class.java) {
      GenerationPlan.create(listOf(rendered("../escape.txt")), root)
    }

    assertTrue(error.message!!.startsWith("CRUD output escapes the project root: "))
  }

  @Test
  @Tag("symlink")
  fun `allows a parent symlink whose real target remains inside the root`() = withTemporaryDirectory { root ->
    val destination = Files.createDirectories(root.resolve("actual-src"))
    createSymbolicLinkOrSkip(root.resolve("src"), destination)

    val plan = GenerationPlan.create(listOf(rendered("src/main/generated.txt")), root)

    assertTrue(plan.artifacts.single().expectedParentRealPath.startsWith(root.toRealPath()))
  }

  @Test
  @Tag("symlink")
  fun `rejects a parent symlink whose real target escapes the root`() = withTemporaryDirectory { root ->
    val project = Files.createDirectory(root.resolve("project"))
    val outside = Files.createDirectory(root.resolve("outside"))
    createSymbolicLinkOrSkip(project.resolve("src"), outside)

    val error = assertThrows(IllegalArgumentException::class.java) {
      GenerationPlan.create(listOf(rendered("src/main/generated.txt")), project)
    }

    assertTrue(error.message!!.startsWith("CRUD output escapes the project root: "))
  }

  @Test
  @Tag("symlink")
  fun `rejects an existing target symbolic link`() = withTemporaryDirectory { root ->
    val source = root.resolve("source.txt")
    Files.writeString(source, "source")
    val target = root.resolve("src/generated.txt")
    Files.createDirectories(target.parent)
    createSymbolicLinkOrSkip(target, source)

    val error = assertThrows(IllegalArgumentException::class.java) {
      GenerationPlan.create(listOf(rendered("src/generated.txt")), root)
    }

    assertEquals("CRUD output target must not be a symbolic link: ${target.toAbsolutePath().normalize()}", error.message)
  }

  @Test
  fun `rejects an existing non-regular target`() = withTemporaryDirectory { root ->
    val target = Files.createDirectories(root.resolve("src/generated.txt"))

    val error = assertThrows(IllegalArgumentException::class.java) {
      GenerationPlan.create(listOf(rendered("src/generated.txt")), root)
    }

    assertEquals("CRUD output target is not a regular file: ${target.toAbsolutePath().normalize()}", error.message)
  }

  @Test
  fun `rejects an output root or nearest ancestor that is not a directory`() = withTemporaryDirectory { root ->
    val fileRoot = root.resolve("project-file")
    Files.writeString(fileRoot, "not a directory")

    val rootError = assertThrows(IllegalArgumentException::class.java) {
      GenerationPlan.create(listOf(rendered("generated.txt")), fileRoot)
    }
    assertEquals("CRUD output root is not a directory: $fileRoot", rootError.message)

    val descendant = fileRoot.resolve("missing")
    val ancestorError = assertThrows(IllegalArgumentException::class.java) {
      GenerationPlan.create(listOf(rendered("generated.txt")), descendant)
    }
    assertEquals("CRUD output root is not a directory: $descendant", ancestorError.message)
  }

  private fun prepared(): PreparedCrud {
    val generation = CrudGeneration(Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC))
    return (generation.prepare(DDL, "egovframework.example.sample") as CrudPreparation.Ready).prepared
  }

  private fun rendered(relativePath: String) = RenderedCrudArtifact(
    artifact = CrudArtifact.VO,
    relativePath = relativePath,
    fileName = relativePath.substringAfterLast('/'),
    language = "text",
    content = "generated",
  )

  private fun createSymbolicLinkOrSkip(link: Path, target: Path) =
    SymlinkTestSupport.createSymbolicLinkOrSkip(link, target)

  private fun withTemporaryDirectory(block: (Path) -> Unit) {
    val root = Files.createTempDirectory("egovframe-crud-plan-")
    try {
      block(root)
    } finally {
      Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }
  }

  private companion object {
    val DDL = """
      CREATE TABLE sample_item (
        item_id INT PRIMARY KEY,
        item_name VARCHAR(100) NOT NULL
      );
    """.trimIndent()
  }
}
