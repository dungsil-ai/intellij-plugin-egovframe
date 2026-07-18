package kr.kyg.ijplugin.egovframe.config

import kr.kyg.ijplugin.egovframe.assets.ConfigTemplate
import kr.kyg.ijplugin.egovframe.assets.TemplateCatalog
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path

class ConfigGeneratorTest {

    /** Returns full initial form data for a template + type, with optional overrides. */
    private fun formData(
        template: ConfigTemplate,
        type: ConfigGenerator.GenerationType,
        overrides: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?> {
        val def = ConfigGenerator.definition(template)
        val data = LinkedHashMap(def.initialFormData(type))
        data.putAll(overrides)
        return data
    }

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
        val tmpDir = Files.createTempDirectory("cfggen-blank")
        try {
            val prepared = ConfigGenerator.prepare(
                template,
                ConfigGenerator.GenerationType.XML,
                formData(template, ConfigGenerator.GenerationType.XML, mapOf(template.fileNameProperty to "")),
                "pkg",
            )
            val issue = prepared.validate(tmpDir)
            assertNotNull(issue)

            val exception = assertThrows(IllegalArgumentException::class.java) { prepared.generate(tmpDir) }
            assertEquals(issue!!.message, exception.message)
            assertTrue(Files.list(tmpDir).use { it.toList() }.isEmpty())
        } finally {
            tmpDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun validateRejectsBlankJavaPackageName() {
        val template = TemplateCatalog.configs.first { it.displayName == "Datasource > New Datasource" }
        val tmpDir = Files.createTempDirectory("cfggen-blank-pkg")
        try {
            val prepared = ConfigGenerator.prepare(
                template,
                ConfigGenerator.GenerationType.JAVA,
                formData(template, ConfigGenerator.GenerationType.JAVA, mapOf("txtConfigPackage" to "")),
                "pkg",
            )
            val issue = prepared.validate(tmpDir)
            assertNotNull(issue)

            val exception = assertThrows(IllegalArgumentException::class.java) { prepared.generate(tmpDir) }
            assertEquals(issue!!.message, exception.message)
            assertTrue(Files.list(tmpDir).use { it.toList() }.isEmpty())
        } finally {
            tmpDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun validateRejectsInvalidJavaPackageName() {
        val template = TemplateCatalog.configs.first { it.displayName == "Datasource > New Datasource" }
        val tmpDir = Files.createTempDirectory("cfggen-pkg")
        try {
            val data = formData(template, ConfigGenerator.GenerationType.JAVA,
                mapOf("txtConfigPackage" to "123bad"))
            val issue = ConfigGenerator.prepare(template, ConfigGenerator.GenerationType.JAVA, data, "pkg")
                .validate(tmpDir)
            assertNotNull(issue)
            assertEquals("txtConfigPackage", issue!!.field)
        } finally {
            tmpDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun validateIgnoresStaleJavaPackageForNonJavaVariants() {
        val template = TemplateCatalog.configs.first()
        val tmpDir = Files.createTempDirectory("cfggen-stale")
        try {
            val data = formData(template, ConfigGenerator.GenerationType.XML,
                mapOf("txtConfigPackage" to "123bad"))
            assertNull(
                ConfigGenerator.prepare(template, ConfigGenerator.GenerationType.XML, data, "pkg")
                    .validate(tmpDir),
            )
        } finally {
            tmpDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun validateRejectsNonPascalCaseJavaFileName() {
        val template = TemplateCatalog.configs.first { it.displayName == "Datasource > New Datasource" }
        val tmpDir = Files.createTempDirectory("cfggen-pascal")
        try {
            val prepared = ConfigGenerator.prepare(
                template,
                ConfigGenerator.GenerationType.JAVA,
                formData(template, ConfigGenerator.GenerationType.JAVA, mapOf(template.fileNameProperty to "lower-case")),
                "pkg",
            )
            val issue = prepared.validate(tmpDir)
            assertNotNull(issue)
            assertEquals(template.fileNameProperty, issue!!.field)

            val exception = assertThrows(IllegalArgumentException::class.java) { prepared.generate(tmpDir) }
            assertEquals(issue.message, exception.message)
            assertTrue(Files.list(tmpDir).use { it.toList() }.isEmpty())
        } finally {
            tmpDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun validationMatchesUpstreamFileAndPackageRules() {
        val xmlTemplate = TemplateCatalog.configs.first()
        val javaTemplate = TemplateCatalog.configs.first { it.displayName == "Datasource > New Datasource" }
        val tmpDir = Files.createTempDirectory("cfggen-upstream")
        try {
            fun validate(
                template: ConfigTemplate,
                type: ConfigGenerator.GenerationType,
                name: String,
                packageName: String,
            ) = ConfigGenerator.prepare(
                template, type,
                formData(template, type, mapOf(template.fileNameProperty to name, "txtConfigPackage" to packageName)),
                "pkg",
            ).validate(tmpDir)

            assertNull(validate(xmlTemplate, ConfigGenerator.GenerationType.XML, "file-name_2", "a..b"))
            assertNotNull(validate(xmlTemplate, ConfigGenerator.GenerationType.XML, "file.name", "pkg"))
            assertNotNull(validate(xmlTemplate, ConfigGenerator.GenerationType.XML, "file name", "pkg"))
            assertNotNull(validate(javaTemplate, ConfigGenerator.GenerationType.JAVA, "Egov_Config", "pkg"))
            assertNotNull(validate(javaTemplate, ConfigGenerator.GenerationType.JAVA, "EgovConfig", "Pkg"))
        } finally {
            tmpDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun validationAcceptsAnOptionalMatchingExtension() {
        val xmlTemplate = TemplateCatalog.configs.first()
        val javaTemplate = TemplateCatalog.configs.first { it.displayName == "Datasource > New Datasource" }
        val tmpDir = Files.createTempDirectory("cfggen-ext")
        try {
            assertNull(
                ConfigGenerator.prepare(
                    xmlTemplate, ConfigGenerator.GenerationType.XML,
                    formData(xmlTemplate, ConfigGenerator.GenerationType.XML,
                        mapOf(xmlTemplate.fileNameProperty to "context-cache.xml")),
                    "pkg",
                ).validate(tmpDir),
            )
            assertNull(
                ConfigGenerator.prepare(
                    javaTemplate, ConfigGenerator.GenerationType.JAVA,
                    formData(javaTemplate, ConfigGenerator.GenerationType.JAVA,
                        mapOf(javaTemplate.fileNameProperty to "EgovConfig.java")),
                    "pkg",
                ).validate(tmpDir),
            )
            assertNotNull(
                ConfigGenerator.prepare(
                    xmlTemplate, ConfigGenerator.GenerationType.XML,
                    formData(xmlTemplate, ConfigGenerator.GenerationType.XML,
                        mapOf(xmlTemplate.fileNameProperty to "context-cache.yaml")),
                    "pkg",
                ).validate(tmpDir),
            )
        } finally {
            tmpDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun validatePassesForValidInput() {
        val template = TemplateCatalog.configs.first()
        val tmpDir = Files.createTempDirectory("cfggen-validate")
        try {
            val data = formData(template, ConfigGenerator.GenerationType.XML)
            assertNull(
                ConfigGenerator.prepare(template, ConfigGenerator.GenerationType.XML, data, "pkg")
                    .validate(tmpDir),
            )
        } finally {
            tmpDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun validateRejectsExistingFile() {
        val template = TemplateCatalog.configs.first()
        val tmpDir = Files.createTempDirectory("cfggen-exists")
        try {
            val data = formData(template, ConfigGenerator.GenerationType.XML)
            val target = tmpDir.resolve("${data[template.fileNameProperty]}.xml")
            val existingBytes = "existing".toByteArray()
            Files.write(target, existingBytes)
            val prepared = ConfigGenerator.prepare(template, ConfigGenerator.GenerationType.XML, data, "pkg")
            val issue = prepared.validate(tmpDir)
            assertNotNull(issue)

            val exception = assertThrows(IllegalArgumentException::class.java) { prepared.generate(tmpDir) }
            assertEquals(issue!!.message, exception.message)
            assertArrayEquals(existingBytes, Files.readAllBytes(target))
            assertEquals(listOf(target), Files.list(tmpDir).use { it.toList() })
        } finally {
            tmpDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun validateReportsMissingFormSpecification() {
        val template = ConfigTemplate(
            displayName = "Unknown Config",
            templateFolder = "unused",
            templateFile = "unused",
            webView = "unused",
            fileNameProperty = "txtFileName",
            javaConfigTemplate = "",
            yamlTemplate = "",
            propertiesTemplate = "",
            description = "unused",
        )
        val tmpDir = Files.createTempDirectory("cfggen-unknown")
        try {
            val prepared = ConfigGenerator.prepare(
                template,
                ConfigGenerator.GenerationType.XML,
                mapOf("txtFileName" to "test"),
                "pkg",
            )
            val issue = prepared.validate(tmpDir)
            assertNotNull(issue)
            assertEquals("Missing form specification: Unknown Config", issue!!.message)
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
                val data = formData(template, ConfigGenerator.GenerationType.XML,
                    mapOf(template.fileNameProperty to name))
                assertNotNull(
                    ConfigGenerator.prepare(template, ConfigGenerator.GenerationType.XML, data, "pkg")
                        .validate(tmpDir),
                    name,
                )
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
            val data = formData(template, ConfigGenerator.GenerationType.XML,
                mapOf(template.fileNameProperty to ".."))
            assertThrows(IllegalArgumentException::class.java) {
                ConfigGenerator.prepare(template, ConfigGenerator.GenerationType.XML, data, "pkg")
                    .generate(tmpDir)
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
    fun generateCreatesMissingOutputDirectoryAndTarget() {
        val template = TemplateCatalog.configs.first { it.displayName == "Cache > New Cache" }
        val root = Files.createTempDirectory("cfggen-missing-output-root")
        val outputFolder = root.resolve("generated")
        try {
            val prepared = ConfigGenerator.prepare(
                template,
                ConfigGenerator.GenerationType.XML,
                formData(template, ConfigGenerator.GenerationType.XML),
                "egovframework.example.sample",
            )
            assertNull(prepared.validate(outputFolder))

            val result = prepared.generate(outputFolder)
            assertTrue(Files.isDirectory(outputFolder))
            assertTrue(Files.exists(result.path))
            assertEquals(result.content, Files.readString(result.path))
        } finally {
            root.toFile().deleteRecursively()
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

    // --- Java-capable template _javaFileName contract ---

    @Test
    fun `every Java-capable template has nonblank _javaFileName default matching initialFormData`() {
        val javaCapable = TemplateCatalog.configs.filter { it.javaConfigTemplate.isNotBlank() }
        assertTrue(javaCapable.isNotEmpty(), "Should have Java-capable templates")

        for (template in javaCapable) {
            val defaults = TemplateCatalog.configDefaults[template.displayName]
            assertNotNull(defaults, "Defaults missing for ${template.displayName}")

            val javaFileName = defaults!!["_javaFileName"]?.toString()
            assertNotNull(javaFileName, "_javaFileName missing for ${template.displayName}")
            assertTrue(javaFileName!!.isNotBlank(), "_javaFileName blank for ${template.displayName}")

            val def = ConfigGenerator.definition(template)
            val javaData = def.initialFormData(ConfigGenerator.GenerationType.JAVA)
            assertEquals(
                javaFileName,
                javaData[def.fileNameProperty],
                "initialFormData filename for ${template.displayName}",
            )
        }
    }

    // --- Temp file / no-overwrite publish ---

    @Test
    fun `generate success leaves no temp file`() {
        val template = TemplateCatalog.configs.first { it.displayName == "Cache > New Cache" }
        val def = ConfigGenerator.definition(template)
        val tmpDir = Files.createTempDirectory("cfggen-notemp")
        try {
            ConfigGenerator.prepare(
                template, ConfigGenerator.GenerationType.XML,
                def.initialFormData(ConfigGenerator.GenerationType.XML), "egovframework.example.sample",
            ).generate(tmpDir)

            val temps = Files.list(tmpDir).use { s -> s.filter { it.fileName.toString().endsWith(".tmp") }.toList() }
            assertTrue(temps.isEmpty(), "No temp files should remain after successful generate")
        } finally {
            tmpDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `concurrent target appearance before move preserves existing bytes and cleans temp`() {
        val template = TemplateCatalog.configs.first { it.displayName == "Cache > New Cache" }
        val def = ConfigGenerator.definition(template)
        val tmpDir = Files.createTempDirectory("cfggen-concurrent")
        try {
            val existingContent = "pre-existing content"
            val ops = object : ConfigFileOps by NioConfigFileOps {
                override fun move(source: Path, target: Path) {
                    // Simulate another process creating the target just before our move
                    Files.writeString(target, existingContent, Charsets.UTF_8)
                    NioConfigFileOps.move(source, target) // will throw FileAlreadyExistsException
                }
            }
            val error = assertThrows(FileAlreadyExistsException::class.java) {
                ConfigGenerator.prepare(
                    template, ConfigGenerator.GenerationType.XML,
                    def.initialFormData(ConfigGenerator.GenerationType.XML), "egovframework.example.sample",
                    ops,
                ).generate(tmpDir)
            }
            assertNotNull(error)
            // Existing bytes preserved
            val targetName = def.initialFormData(ConfigGenerator.GenerationType.XML)[def.fileNameProperty].toString() + ".xml"
            assertEquals(existingContent, Files.readString(tmpDir.resolve(targetName)))
            // Temp file cleaned
            val temps = Files.list(tmpDir).use { s -> s.filter { it.fileName.toString().endsWith(".tmp") }.toList() }
            assertTrue(temps.isEmpty(), "Temp file should be cleaned after move failure")
        } finally {
            tmpDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `real NIO move does not replace existing file`() {
        val tmpDir = Files.createTempDirectory("cfggen-noreplace")
        try {
            val target = tmpDir.resolve("existing.xml")
            Files.writeString(target, "original")
            val tempFile = Files.createTempFile(tmpDir, ".cfg-", ".tmp")
            Files.writeString(tempFile, "new content")
            assertThrows(FileAlreadyExistsException::class.java) {
                NioConfigFileOps.move(tempFile, target) // no REPLACE_EXISTING
            }
            assertEquals("original", Files.readString(target))
        } finally {
            tmpDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `write failure creates neither target nor temp`() {
        val template = TemplateCatalog.configs.first { it.displayName == "Cache > New Cache" }
        val def = ConfigGenerator.definition(template)
        val tmpDir = Files.createTempDirectory("cfggen-writefail")
        try {
            val ops = object : ConfigFileOps by NioConfigFileOps {
                override fun writeString(path: Path, content: String) {
                    throw IOException("Injected write failure")
                }
            }
            assertThrows(IOException::class.java) {
                ConfigGenerator.prepare(
                    template, ConfigGenerator.GenerationType.XML,
                    def.initialFormData(ConfigGenerator.GenerationType.XML), "egovframework.example.sample",
                    ops,
                ).generate(tmpDir)
            }
            val allFiles = Files.list(tmpDir).use { it.toList() }
            assertTrue(allFiles.isEmpty(), "No files should remain after write failure")
        } finally {
            tmpDir.toFile().deleteRecursively()
        }
    }
}
