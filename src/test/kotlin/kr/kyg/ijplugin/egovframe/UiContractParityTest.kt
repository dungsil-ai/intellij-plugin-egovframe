package kr.kyg.ijplugin.egovframe

import com.google.gson.JsonParser
import kr.kyg.ijplugin.egovframe.assets.TemplateCatalog
import kr.kyg.ijplugin.egovframe.config.ConfigFormRegistry
import kr.kyg.ijplugin.egovframe.config.ConfigGenerator
import kr.kyg.ijplugin.egovframe.crud.CrudEditorModel
import kr.kyg.ijplugin.egovframe.crud.CrudGeneration
import kr.kyg.ijplugin.egovframe.crud.CrudPreparation
import kr.kyg.ijplugin.egovframe.crud.CrudSampleCatalog
import kr.kyg.ijplugin.egovframe.crud.SqlDialect
import kr.kyg.ijplugin.egovframe.ddl.DdlSyntaxDiagnostics
import kr.kyg.ijplugin.egovframe.project.ProjectGenerationStage
import kr.kyg.ijplugin.egovframe.project.ProjectWizardModel
import kr.kyg.ijplugin.egovframe.settings.ActionAvailabilityPolicy
import kr.kyg.ijplugin.egovframe.settings.EgovSettings
import kr.kyg.ijplugin.egovframe.settings.SettingsValidator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UiContractParityTest {

  @Test
  fun `config form definitions expose the exact 21 entry 45 variant contract`() {
    assertEquals(21, ConfigFormRegistry.all().size)
    assertEquals(45, ConfigFormRegistry.all().sumOf { it.activeTypes.size })

    val javaDefaults = mapOf(
      "Cache > New Ehcache Configuration" to "EgovEhcacheSpringConfig",
      "Datasource > New Datasource" to "EgovDataSourceConfig",
      "Datasource > New JNDI Datasource" to "EgovJndiDatasourceConfig",
      "Property > New Property" to "EgovPropertiesConfig",
    )
    javaDefaults.forEach { (displayName, expected) ->
      val template = TemplateCatalog.configs.single { it.displayName == displayName }
      val definition = ConfigGenerator.definition(template)
      assertTrue(ConfigGenerator.GenerationType.JAVA in definition.generationTypes)
      assertEquals(
        expected,
        definition.initialFormData(ConfigGenerator.GenerationType.JAVA)[template.fileNameProperty],
      )
    }

    val timeBased = TemplateCatalog.configs.single {
      it.displayName == "Logging > New Time-Based Rolling File Appender"
    }
    assertFalse(ConfigGenerator.GenerationType.JAVA in ConfigGenerator.definition(timeBased).generationTypes)
    val jdbc = TemplateCatalog.configs.single { it.displayName == "Logging > New JDBC Appender" }
    assertEquals(
      listOf(ConfigGenerator.GenerationType.XML, ConfigGenerator.GenerationType.YAML),
      ConfigGenerator.definition(jdbc).generationTypes,
    )
  }

  @Test
  fun `CRUD editor model exposes three dialects ten samples direct input and positioned diagnostics`() {
    assertEquals(setOf("mysql", "pgsql", "generic"), SqlDialect.entries.map { it.id }.toSet())
    assertEquals(10, CrudSampleCatalog.all().size)
    assertEquals(5, CrudSampleCatalog.forDialect(SqlDialect.MYSQL).size)
    assertEquals(5, CrudSampleCatalog.forDialect(SqlDialect.POSTGRESQL).size)
    assertTrue(CrudSampleCatalog.forDialect(SqlDialect.GENERIC).isEmpty())

    val model = CrudEditorModel()
    val sample = CrudSampleCatalog.forDialect(SqlDialect.MYSQL).first()
    model.selectSample(sample)
    assertEquals(sample, model.selectedSample)
    model.switchDialect(SqlDialect.GENERIC)
    assertNull(model.selectedSample)
    assertTrue(model.availableSamples.isEmpty())
    model.clearSample()
    assertEquals("", model.sqlText)
    val diagnostics = DdlSyntaxDiagnostics.diagnose(
      "\n\nSELECT 1;",
      SqlDialect.GENERIC,
    ) as DdlSyntaxDiagnostics.DiagnosticResult.Error
    assertEquals(3, diagnostics.diagnostics.first().line)
    assertTrue(diagnostics.diagnostics.first().column >= 1)
  }

  @Test
  fun `CRUD context export keeps the upstream JSON shape and fixed filename`() {
    val preparation = CrudGeneration().prepare(
      "CREATE TABLE sample_item (item_id INT PRIMARY KEY);",
      "egovframework.example.sample",
    ) as CrudPreparation.Ready
    val prepared = preparation.prepared
    val context = JsonParser.parseString(prepared.contextJson()).asJsonObject

    assertEquals("SampleItem_TemplateContext.json", prepared.contextFileName)
    assertFalse(context.has("packagePath"))
    assertEquals(
      setOf(
        "tableName",
        "dbTableName",
        "attributes",
        "pkAttributes",
        "packageName",
        "className",
        "classNameFirstCharLower",
        "author",
        "date",
        "version",
      ),
      context.keySet(),
    )
  }

  @Test
  fun `project wizard reset no-POM and progress contracts remain observable`() {
    val pomTemplate = TemplateCatalog.projects.first { it.pomFile.isNotBlank() }
    val noPomTemplate = TemplateCatalog.projects.first { it.pomFile.isBlank() }
    val model = ProjectWizardModel("com.example", "default-artifact", "C:/initial")

    model.selectTemplate(pomTemplate)
    assertEquals("com.example", model.groupId)
    assertEquals("default-artifact", model.artifactId)
    assertEquals("C:/initial", model.outputPath)
    assertTrue(model.hasPom)

    model.deriveFromProjectName("com.acme.demo")
    assertEquals("com.acme", model.groupId)
    assertEquals("demo", model.artifactId)

    model.selectTemplate(noPomTemplate)
    model.groupId = "INVALID"
    model.artifactId = "INVALID"
    assertFalse(model.hasPom)
    assertTrue(model.validate().isEmpty())

    assertEquals(
      setOf(
        ProjectGenerationStage.RESOLVE_TEMPLATE,
        ProjectGenerationStage.EXTRACT,
        ProjectGenerationStage.WRITE_POM,
        ProjectGenerationStage.LINK_MAVEN,
        ProjectGenerationStage.CONFIGURE_JDK,
        ProjectGenerationStage.COMPLETE,
      ),
      ProjectGenerationStage.entries.toSet(),
    )
  }

  @Test
  fun `settings language validation metadata and projectless policy remain available`() {
    val defaults = EgovSettings.SettingsState(language = "ko")
    assertTrue(SettingsValidator.validate(defaults).isValid)
    assertFalse(ActionAvailabilityPolicy.isConfigCrudAvailable(null))

    val invalid = EgovSettings.SettingsState(
      defaultGroupId = "com..example",
      defaultArtifactId = "INVALID",
      defaultPackageName = "com.123",
      language = "en",
    )
    assertFalse(SettingsValidator.validate(invalid).isValid)

    val pluginXml = javaClass.getResource("/META-INF/plugin.xml")!!.readText()
    assertTrue(pluginXml.contains("kr.kyg.ijplugin.egovframe.settings.AboutEgovAction"))
    assertTrue(pluginXml.contains("action.open.config"))
    assertTrue(pluginXml.contains("applicationConfigurable"))
  }
}
