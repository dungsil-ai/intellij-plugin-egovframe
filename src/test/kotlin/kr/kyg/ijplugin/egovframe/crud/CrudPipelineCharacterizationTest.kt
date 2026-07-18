package kr.kyg.ijplugin.egovframe.crud

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

class CrudPipelineCharacterizationTest {

  @Test
  fun `full pipeline renders plans and writes 11 artifacts matching golden fixtures`() {
    val ddl = resourceText("/golden/crud/single_pk/ddl.sql")
    val contextJson = resourceText("/golden/crud/single_pk/context.json")
    val context: Map<String, Any> = Gson().fromJson(contextJson, object : TypeToken<Map<String, Any>>() {}.type)
    val fixtureDate = LocalDate.parse(context["date"] as String)
    val clock = Clock.fixed(fixtureDate.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    val packageName = "egovframework.example.sample"

    val generation = CrudGeneration(clock)
    val preparation = generation.prepare(ddl, packageName)
    val ready = preparation as? CrudPreparation.Ready ?: fail("Expected Ready but got: $preparation")
    val prepared = ready.prepared

    assertEquals(11, prepared.artifacts.size, "PreparedCrud must render exactly 11 artifacts")

    val root = Files.createTempDirectory("crud-characterization-")
    try {
      val plan = prepared.plan(root)
      val writePlan = plan.select(CrudArtifact.entries.toSet())
      val result = CrudWriteAdapter().write(writePlan)

      assertEquals(11, result.written.size, "CrudWriteAdapter must write exactly 11 files")

      val expectedRelativePaths = listOf(
        "src/main/java/egovframework/example/sample/service/BoardArticleVO.java",
        "src/main/java/egovframework/example/sample/service/BoardArticleDefaultVO.java",
        "src/main/java/egovframework/example/sample/web/BoardArticleController.java",
        "src/main/java/egovframework/example/sample/service/BoardArticleService.java",
        "src/main/java/egovframework/example/sample/service/impl/BoardArticleServiceImpl.java",
        "src/main/java/egovframework/example/sample/service/impl/BoardArticleMapper.java",
        "src/main/resources/mapper/BoardArticle_SQL.xml",
        "src/main/resources/templates/thymeleaf/boardArticle/boardArticleList.html",
        "src/main/resources/templates/thymeleaf/boardArticle/boardArticleRegister.html",
        "src/main/webapp/WEB-INF/jsp/egovframework/example/sample/boardArticleList.jsp",
        "src/main/webapp/WEB-INF/jsp/egovframework/example/sample/boardArticleRegister.jsp",
      )

      for (relativePath in expectedRelativePaths) {
        val target = root.resolve(relativePath)
        assertTrue(Files.isRegularFile(target), "Expected file missing: $relativePath")
      }

      for (artifact in CrudArtifact.entries) {
        val goldenName = artifact.templateFile.removeSuffix(".hbs") + ".golden"
        val goldenContent = resourceText("/golden/crud/single_pk/rendered/$goldenName")
        val rendered = prepared.artifacts.first { it.artifact == artifact }
        assertEquals(
          goldenContent,
          rendered.content,
          "Content mismatch for ${artifact.name} (golden: $goldenName)",
        )
        val target = root.resolve(rendered.relativePath)
        assertEquals(
          goldenContent,
          Files.readString(target),
          "Written file content mismatch for ${artifact.name}",
        )
      }

      // Assert no leftover .stage/.backup files
      Files.walk(root).use { paths ->
        paths.filter { Files.isRegularFile(it) }.forEach { file ->
          val name = file.fileName.toString()
          assertTrue(!name.endsWith(".stage"), "Leftover stage file: $file")
          assertTrue(!name.endsWith(".backup"), "Leftover backup file: $file")
        }
      }
    } finally {
      deleteRecursively(root)
    }
  }

  private fun resourceText(path: String): String =
    CrudPipelineCharacterizationTest::class.java.getResourceAsStream(path)?.bufferedReader()?.readText()
      ?: throw IllegalStateException("Test resource not found: $path")

  private fun deleteRecursively(root: Path) {
    if (!Files.exists(root)) return
    Files.walk(root).use { paths ->
      paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
    }
  }
}
