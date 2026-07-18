package kr.kyg.ijplugin.egovframe.project

import kr.kyg.ijplugin.egovframe.assets.TemplateCatalog
import kr.kyg.ijplugin.egovframe.assets.TemplateStore
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

class ProjectCatalogGenerationContractTest {

  @Test
  fun `all 22 bundled project ZIPs extract and follow the 18 POM 4 no-POM contract`() {
    assertEquals(22, TemplateCatalog.projects.size)
    assertEquals(18, TemplateCatalog.projects.count { it.pomFile.isNotBlank() })
    assertEquals(4, TemplateCatalog.projects.count { it.pomFile.isBlank() })

    withTemporaryDirectory { root ->
      val store = TemplateStore(root.resolve("cache")) { url ->
        throw AssertionError("Bundled project contract must not access the network: $url")
      }

      TemplateCatalog.projects.forEachIndexed { index, template ->
        val zip = store.ensure(template.fileName)
        val bundledPom = ZipFile(zip.toFile()).use { archive ->
          archive.getEntry("pom.xml")?.let { entry -> archive.getInputStream(entry).use { it.readBytes() } }
        }
        val output = Files.createDirectories(root.resolve("output-$index"))
        val projectName = "contract-$index"
        val generated = ProjectGenerator.generate(
          outputDirectory = output,
          zipPath = zip,
          config = ProjectGenerator.ProjectConfig(
            projectName = projectName,
            groupId = "com.example",
            artifactId = projectName,
            template = template,
          ),
        )

        assertTrue(Files.isDirectory(generated), "Project root missing for ${template.displayName}")
        Files.list(generated).use { entries ->
          assertTrue(entries.findAny().isPresent, "Extracted project is empty for ${template.displayName}")
        }
        val pom = generated.resolve("pom.xml")
        if (template.pomFile.isBlank()) {
          if (bundledPom == null) {
            assertFalse(Files.exists(pom), "No-POM template unexpectedly created pom.xml: ${template.displayName}")
          } else {
            assertArrayEquals(
              bundledPom,
              Files.readAllBytes(pom),
              "No-POM template must preserve the pom.xml bundled inside its ZIP: ${template.displayName}",
            )
          }
        } else {
          assertTrue(Files.isRegularFile(pom), "POM template did not generate pom.xml: ${template.displayName}")
          val pomText = Files.readString(pom)
          assertTrue(pomText.contains("com.example"), "groupId token was not replaced for ${template.displayName}")
          assertTrue(pomText.contains(projectName), "artifactId token was not replaced for ${template.displayName}")
        }

        deleteRecursively(output)
      }
    }
  }

  private fun withTemporaryDirectory(block: (Path) -> Unit) {
    val root = Files.createTempDirectory("egov-project-catalog-contract-")
    try {
      block(root)
    } finally {
      deleteRecursively(root)
    }
  }

  private fun deleteRecursively(root: Path) {
    if (!Files.exists(root)) return
    Files.walk(root).use { paths ->
      paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
    }
  }
}
