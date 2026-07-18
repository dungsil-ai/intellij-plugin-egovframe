package kr.kyg.ijplugin.egovframe.config

import kr.kyg.ijplugin.egovframe.assets.TemplateCatalog
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ConfigGeneratorTest {

    // --- FormDefinition tests ---

    @Test
    fun definitionExcludesHiddenAndGenerationTypeKeys() {
        val template = TemplateCatalog.configs.first { it.displayName == "Cache > New Cache" }
        val def = ConfigGenerator.definition(template)
        val visibleNames = def.visibleFields.map { it.first }
        assertTrue("generationType" !in visibleNames, "generationType should be excluded")
        assertTrue(visibleNames.none { it.startsWith("_") }, "underscore-prefixed keys should be excluded")
        val initial = def.initialFormData(ConfigGenerator.GenerationType.XML)
        assertTrue("generationType" in initial, "initialFormData should contain generationType")
    }

    @Test
    fun definitionReturnsCorrectGenerationTypes() {
        val template = TemplateCatalog.configs.first { it.displayName == "Cache > New Ehcache Configuration" }
        val def = ConfigGenerator.definition(template)
        assertEquals(
            listOf(ConfigGenerator.GenerationType.XML, ConfigGenerator.GenerationType.JAVA),
            def.generationTypes,
        )
    }

    @Test
    fun initialFormDataSelectsJavaFileNameForJavaVariant() {
        val template = TemplateCatalog.configs.first { it.displayName == "ID Generation > New Sequence ID Generation" }
        val def = ConfigGenerator.definition(template)
        val javaData = def.initialFormData(ConfigGenerator.GenerationType.JAVA)
        assertEquals("EgovIdgnSequenceConfig", javaData[def.fileNameProperty])
        val xmlData = def.initialFormData(ConfigGenerator.GenerationType.XML)
        assertEquals("context-idgn-sequence", xmlData[def.fileNameProperty])
    }

    @Test
    fun initialFormDataPreservesHiddenDefaults() {
        val template = TemplateCatalog.configs.first { it.displayName == "ID Generation > New Sequence ID Generation" }
        val def = ConfigGenerator.definition(template)
        val data = def.initialFormData(ConfigGenerator.GenerationType.XML)
        assertTrue("_javaFileName" in data, "Hidden _javaFileName should be preserved")
        assertTrue("_formType" in data, "Hidden _formType should be preserved")
    }

    @Test
    fun initialFormDataSetsGenerationType() {
        val template = TemplateCatalog.configs.first { it.displayName == "Cache > New Cache" }
        val def = ConfigGenerator.definition(template)
        val xmlData = def.initialFormData(ConfigGenerator.GenerationType.XML)
        assertEquals("xml", xmlData["generationType"])
        val javaData = def.initialFormData(ConfigGenerator.GenerationType.JAVA)
        assertEquals("javaConfig", javaData["generationType"])
    }

    @Test
    fun definitionExposesFileNameProperty() {
        val template = TemplateCatalog.configs.first { it.displayName == "Cache > New Cache" }
        val def = ConfigGenerator.definition(template)
        assertEquals(template.fileNameProperty, def.fileNameProperty)
    }

    // --- PreparedConfig.validate tests ---

    @Test
    fun validateRejectsBlankFileName() {
        val template = TemplateCatalog.configs.first()
        val prepared = ConfigGenerator.prepare(
            template, ConfigGenerator.GenerationType.XML,
            mapOf(template.fileNameProperty to ""), "pkg",
        )
        val issue = prepared.validate(Path.of("build"))
        assertNotNull(issue)
        assertEquals("File name is required", issue!!.message)
    }

    @Test
    fun validateRejectsInvalidJavaPackageName() {
        val template = TemplateCatalog.configs.first { it.javaConfigTemplate.isNotBlank() }
        val prepared = ConfigGenerator.prepare(
            template, ConfigGenerator.GenerationType.JAVA,
            mapOf(template.fileNameProperty to "EgovConfig", "txtConfigPackage" to "123bad"), "pkg",
        )
        val issue = prepared.validate(Path.of("build"))
        assertNotNull(issue)
        assertEquals("Invalid Java package name", issue!!.message)
    }

    @Test
    fun validateIgnoresStaleJavaPackageForNonJavaVariants() {
        val template = TemplateCatalog.configs.first()
        val prepared = ConfigGenerator.prepare(
            template, ConfigGenerator.GenerationType.XML,
            mapOf(template.fileNameProperty to "file", "txtConfigPackage" to "123bad"), "pkg",
        )
        assertNull(prepared.validate(Path.of("build")))
    }

    @Test
    fun validateRejectsNonPascalCaseJavaFileName() {
        val template = TemplateCatalog.configs.first { it.javaConfigTemplate.isNotBlank() }
        val prepared = ConfigGenerator.prepare(
            template, ConfigGenerator.GenerationType.JAVA,
            mapOf(template.fileNameProperty to "lower-case", "txtConfigPackage" to "pkg"), "pkg",
        )
        val issue = prepared.validate(Path.of("build"))
        assertNotNull(issue)
        assertTrue(issue!!.message.contains("PascalCase"))
    }

    @Test
    fun validationMatchesUpstreamFileAndPackageRules() {
        val xmlTemplate = TemplateCatalog.configs.first()
        val javaTemplate = TemplateCatalog.configs.first { it.javaConfigTemplate.isNotBlank() }
        val output = Path.of("build")

        fun validate(template: kr.kyg.ijplugin.egovframe.assets.ConfigTemplate, type: ConfigGenerator.GenerationType, name: String, packageName: String) =
            ConfigGenerator.prepare(
                template, type, mapOf(template.fileNameProperty to name, "txtConfigPackage" to packageName), "pkg",
            ).validate(output)

        assertNull(validate(xmlTemplate, ConfigGenerator.GenerationType.XML, "file-name_2", "a..b"))
        assertNotNull(validate(xmlTemplate, ConfigGenerator.GenerationType.XML, "file.name", "pkg"))
        assertNotNull(validate(xmlTemplate, ConfigGenerator.GenerationType.XML, "file name", "pkg"))
        assertNotNull(validate(javaTemplate, ConfigGenerator.GenerationType.JAVA, "Egov_Config", "pkg"))
        assertNotNull(validate(javaTemplate, ConfigGenerator.GenerationType.JAVA, "EgovConfig", "Pkg"))
    }

    @Test
    fun validationAcceptsAnOptionalMatchingExtension() {
        val xmlTemplate = TemplateCatalog.configs.first()
        val javaTemplate = TemplateCatalog.configs.first { it.javaConfigTemplate.isNotBlank() }
        val output = Path.of("build")

        assertNull(
            ConfigGenerator.prepare(
                xmlTemplate,
                ConfigGenerator.GenerationType.XML,
                mapOf(xmlTemplate.fileNameProperty to "context-cache.xml"),
                "pkg",
            ).validate(output),
        )
        assertNull(
            ConfigGenerator.prepare(
                javaTemplate,
                ConfigGenerator.GenerationType.JAVA,
                mapOf(javaTemplate.fileNameProperty to "EgovConfig.java", "txtConfigPackage" to "pkg"),
                "pkg",
            ).validate(output),
        )
        assertNotNull(
            ConfigGenerator.prepare(
                xmlTemplate,
                ConfigGenerator.GenerationType.XML,
                mapOf(xmlTemplate.fileNameProperty to "context-cache.yaml"),
                "pkg",
            ).validate(output),
        )
    }

    @Test
    fun validatePassesForValidInput() {
        val template = TemplateCatalog.configs.first()
        val tmpDir = Files.createTempDirectory("cfggen-validate")
        try {
            val prepared = ConfigGenerator.prepare(
                template, ConfigGenerator.GenerationType.XML,
                mapOf(template.fileNameProperty to "context-test"), "pkg",
            )
            assertNull(prepared.validate(tmpDir))
        } finally {
            tmpDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun validateRejectsExistingFile() {
        val template = TemplateCatalog.configs.first()
        val tmpDir = Files.createTempDirectory("cfggen-exists")
        try {
            Files.writeString(tmpDir.resolve("context-test.xml"), "existing")
            val prepared = ConfigGenerator.prepare(
                template, ConfigGenerator.GenerationType.XML,
                mapOf(template.fileNameProperty to "context-test"), "pkg",
            )
            val issue = prepared.validate(tmpDir)
            assertNotNull(issue)
            assertTrue(issue!!.message.contains("already exists"))
        } finally {
            tmpDir.toFile().deleteRecursively()
        }
    }

    // --- sanitization through validate/generate ---

    @Test
    fun validationRejectsPathLikeFileNamesBeforeGeneration() {
        val template = TemplateCatalog.configs.first()
        val tmpDir = Files.createTempDirectory("cfggen-sanitize")
        try {
            for (name in listOf("../../etc/passwd", "../nested/context-cache")) {
                val prepared = ConfigGenerator.prepare(
                    template, ConfigGenerator.GenerationType.XML,
                    mapOf(template.fileNameProperty to name), "pkg",
                )
                assertNotNull(prepared.validate(tmpDir), name)
            }
        } finally {
            tmpDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun generateRejectsInvalidFileName() {
        val template = TemplateCatalog.configs.first()
        val tmpDir = Files.createTempDirectory("cfggen-invalid")
        try {
            val prepared = ConfigGenerator.prepare(
                template, ConfigGenerator.GenerationType.XML,
                mapOf(template.fileNameProperty to ".."), "pkg",
            )
            assertThrows(IllegalArgumentException::class.java) {
                prepared.generate(tmpDir)
            }
        } finally {
            tmpDir.toFile().deleteRecursively()
        }
    }

    // --- render and generate integration ---

    @Test
    fun renderProducesNonEmptyContent() {
        val template = TemplateCatalog.configs.first { it.displayName == "Cache > New Cache" }
        val def = ConfigGenerator.definition(template)
        val prepared = ConfigGenerator.prepare(
            template, ConfigGenerator.GenerationType.XML,
            def.initialFormData(ConfigGenerator.GenerationType.XML), "egovframework.example.sample",
        )
        val content = prepared.render()
        assertTrue(content.isNotBlank(), "Rendered content should be non-empty")
    }

    @Test
    fun generateWritesFileToTempDirectory() {
        val template = TemplateCatalog.configs.first { it.displayName == "Cache > New Cache" }
        val def = ConfigGenerator.definition(template)
        val tmpDir = Files.createTempDirectory("cfggen-generate")
        try {
            val result = ConfigGenerator.prepare(
                template, ConfigGenerator.GenerationType.XML,
                def.initialFormData(ConfigGenerator.GenerationType.XML), "egovframework.example.sample",
            ).generate(tmpDir)
            assertTrue(Files.exists(result.path), "Generated file should exist")
            assertEquals(result.content, Files.readString(result.path))
            assertTrue(result.path.startsWith(tmpDir.toAbsolutePath().normalize()), "Path should be inside output folder")
        } finally {
            tmpDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun generateRejectsExistingFile() {
        val template = TemplateCatalog.configs.first { it.displayName == "Cache > New Cache" }
        val def = ConfigGenerator.definition(template)
        val tmpDir = Files.createTempDirectory("cfggen-reject")
        try {
            // Write the first time
            ConfigGenerator.prepare(
                template, ConfigGenerator.GenerationType.XML,
                def.initialFormData(ConfigGenerator.GenerationType.XML), "egovframework.example.sample",
            ).generate(tmpDir)
            // Second attempt must fail
            assertThrows(IllegalArgumentException::class.java) {
                ConfigGenerator.prepare(
                    template, ConfigGenerator.GenerationType.XML,
                    def.initialFormData(ConfigGenerator.GenerationType.XML), "egovframework.example.sample",
                ).generate(tmpDir)
            }
        } finally {
            tmpDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `generate produces correct file for each generation type variant`() {
        data class Case(val displayName: String, val type: ConfigGenerator.GenerationType, val extension: String)

        val cases = listOf(
            Case("Cache > New Cache", ConfigGenerator.GenerationType.XML, "xml"),
            Case("Datasource > New Datasource", ConfigGenerator.GenerationType.JAVA, "java"),
            Case("Logging > New Console Appender", ConfigGenerator.GenerationType.YAML, "yaml"),
            Case("Logging > New Console Appender", ConfigGenerator.GenerationType.PROPERTIES, "properties"),
        )

        for (case in cases) {
            val template = TemplateCatalog.configs.first { it.displayName == case.displayName }
            val def = ConfigGenerator.definition(template)
            val formData = def.initialFormData(case.type)
            val tmpDir = Files.createTempDirectory("cfggen-variant-")
            try {
                val result = ConfigGenerator.prepare(
                    template, case.type, formData, "egovframework.example.sample",
                ).generate(tmpDir)
                assertTrue(
                    Files.exists(result.path),
                    "Generated file must exist for ${case.displayName} / ${case.type}",
                )
                assertTrue(
                    result.path.fileName.toString().endsWith(".${case.extension}"),
                    "File extension must be .${case.extension} for ${case.displayName} / ${case.type}, " +
                        "but was ${result.path.fileName}",
                )
                assertTrue(
                    result.content.isNotBlank(),
                    "Generated content must be non-blank for ${case.displayName} / ${case.type}",
                )
            } finally {
                tmpDir.toFile().deleteRecursively()
            }
        }
    }
}
