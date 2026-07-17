package kr.kyg.intellij.plugin.egovframe.config

import kr.kyg.intellij.plugin.egovframe.assets.TemplateCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class ConfigGeneratorTest {

    // --- FormDefinition tests ---

    @Test
    fun definitionExcludesHiddenAndGenerationTypeKeys() {
        val template = TemplateCatalog.configs.first { it.displayName == "Cache > New Cache" }
        val def = ConfigGenerator.definition(template)
        val visibleNames = def.visibleFields.map { it.first }
        assertTrue("generationType should be excluded", "generationType" !in visibleNames)
        assertTrue("underscore-prefixed keys should be excluded", visibleNames.none { it.startsWith("_") })
        val initial = def.initialFormData(ConfigGenerator.GenerationType.XML)
        assertTrue("initialFormData should contain generationType", "generationType" in initial)
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
        assertTrue("Hidden _javaFileName should be preserved", "_javaFileName" in data)
        assertTrue("Hidden _formType should be preserved", "_formType" in data)
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
    fun validateRejectsInvalidPackageName() {
        val template = TemplateCatalog.configs.first()
        val prepared = ConfigGenerator.prepare(
            template, ConfigGenerator.GenerationType.XML,
            mapOf(template.fileNameProperty to "file", "txtConfigPackage" to "123bad"), "pkg",
        )
        val issue = prepared.validate(Path.of("build"))
        assertNotNull(issue)
        assertEquals("Invalid Java package name", issue!!.message)
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
    fun sanitizationStripsTraversalSegmentsViaValidation() {
        val template = TemplateCatalog.configs.first()
        val tmpDir = Files.createTempDirectory("cfggen-sanitize")
        try {
            val prepared = ConfigGenerator.prepare(
                template, ConfigGenerator.GenerationType.XML,
                mapOf(template.fileNameProperty to "../../etc/passwd"), "pkg",
            )
            // The sanitized file name should resolve safely inside tmpDir
            assertNull(prepared.validate(tmpDir))
        } finally {
            tmpDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun sanitizationStripsDirectoriesFromFileName() {
        val template = TemplateCatalog.configs.first()
        val tmpDir = Files.createTempDirectory("cfggen-sanitize-dir")
        try {
            val prepared = ConfigGenerator.prepare(
                template, ConfigGenerator.GenerationType.XML,
                mapOf(template.fileNameProperty to "../nested/context-cache"), "pkg",
            )
            // Should validate without error (path stays inside output folder)
            assertNull(prepared.validate(tmpDir))

            val result = prepared.generate(tmpDir)
            // Generated file should be directly inside tmpDir with traversal stripped
            assertEquals(tmpDir.toAbsolutePath().normalize(), result.path.parent)
            assertTrue(result.path.fileName.toString().endsWith(".xml"))
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
        assertTrue("Rendered content should be non-empty", content.isNotBlank())
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
            assertTrue("Generated file should exist", Files.exists(result.path))
            assertEquals(result.content, Files.readString(result.path))
            assertTrue("Path should be inside output folder", result.path.startsWith(tmpDir.toAbsolutePath().normalize()))
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
}
