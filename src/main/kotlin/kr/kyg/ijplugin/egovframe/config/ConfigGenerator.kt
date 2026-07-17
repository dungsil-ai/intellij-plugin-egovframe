package kr.kyg.ijplugin.egovframe.config

import kr.kyg.ijplugin.egovframe.assets.ConfigTemplate
import kr.kyg.ijplugin.egovframe.assets.EgovAssets
import kr.kyg.ijplugin.egovframe.assets.TemplateCatalog
import kr.kyg.ijplugin.egovframe.render.EgovHandlebars
import java.nio.file.Files
import java.nio.file.Path

object ConfigGenerator {

    enum class GenerationType(val id: String, val extension: String, val label: String) {
        XML("xml", "xml", "XML"),
        JAVA("javaConfig", "java", "JavaConfig"),
        YAML("yaml", "yaml", "YAML"),
        PROPERTIES("properties", "properties", "Properties"),
    }

    data class GeneratedConfig(val path: Path, val content: String)

    /** Identifies a validation problem, optionally tied to a specific form [field]. */
    data class ValidationIssue(val message: String, val field: String? = null)

    /**
     * Describes the form surface for a given [ConfigTemplate]: the visible fields a UI
     * should render, generation types, and a factory for variant-aware initial data.
     */
    data class FormDefinition(
        /** Visible fields in source order (excludes `generationType` and underscore-prefixed keys). */
        val visibleFields: List<Pair<String, Any?>>,
        /** Generation types supported by the template. */
        val generationTypes: List<GenerationType>,
        /** The form-data key that holds the output file name. */
        val fileNameProperty: String,
        private val allDefaults: Map<String, Any?>,
        /** Model-based form specification, if available. */
        val formSpec: ConfigFormSpec? = null,
    ) {
        /**
         * Returns variant-aware initial form data.
         * Preserves hidden underscore-prefixed defaults, sets `generationType`,
         * and switches `_javaFileName` into [fileNameProperty] only for [GenerationType.JAVA].
         */
        fun initialFormData(generationType: GenerationType): Map<String, Any?> {
            val data = LinkedHashMap(allDefaults)
            data["generationType"] = generationType.id
            if (generationType == GenerationType.JAVA) {
                val javaName = allDefaults["_javaFileName"]
                if (javaName != null) {
                    data[fileNameProperty] = javaName
                }
            }
            return data
        }
    }

    /** Builds a [FormDefinition] for the given template, deriving defaults from [TemplateCatalog.configDefaults]. */
    fun definition(template: ConfigTemplate): FormDefinition {
        val allDefaults = LinkedHashMap(TemplateCatalog.configDefaults[template.displayName].orEmpty())
        val spec = ConfigFormRegistry.forTemplate(template)
        val visible = allDefaults.entries
            .filter { (key, _) -> key != "generationType" && !key.startsWith("_") }
            .map { (key, value) -> key to value }
        return FormDefinition(
            visibleFields = visible,
            generationTypes = spec?.activeTypes ?: availableTypes(template),
            fileNameProperty = template.fileNameProperty,
            allDefaults = allDefaults,
            formSpec = spec,
        )
    }

    /**
     * Centralises template resolution, validation, rendering, and file generation for a single
     * config-generation request. Create via [prepare].
     */
    class PreparedConfig internal constructor(
        private val template: ConfigTemplate,
        private val generationType: GenerationType,
        private val formData: Map<String, Any?>,
        private val defaultPackageName: String,
    ) {
        /** Validates file name, package name, Java-class name, target path, and existing-file. */
        fun validate(outputFolder: Path): ValidationIssue? {
            val rawFileName = formData[template.fileNameProperty]?.toString().orEmpty()
            if (rawFileName.isBlank()) {
                return ValidationIssue("File name is required", template.fileNameProperty)
            }

            val packageName = formData["txtConfigPackage"]?.toString()
            if (
                generationType == GenerationType.JAVA &&
                !packageName.isNullOrBlank() &&
                !PACKAGE_NAME_REGEX.matches(packageName)
            ) {
                return ValidationIssue("Invalid Java package name", "txtConfigPackage")
            }

            val baseFileName = stripOptionalExtension(rawFileName, generationType)
            if (generationType == GenerationType.JAVA) {
                if (!JAVA_CLASS_NAME_REGEX.matches(baseFileName)) {
                    return ValidationIssue(
                        "JavaConfig file name must be a PascalCase class name",
                        template.fileNameProperty,
                    )
                }
            } else if (!FILE_NAME_REGEX.matches(baseFileName)) {
                return ValidationIssue("Invalid file name", template.fileNameProperty)
            }

            val target = runCatching { targetPath(outputFolder) }.getOrElse {
                return ValidationIssue(it.message ?: "Invalid output path", template.fileNameProperty)
            }
            if (Files.exists(target)) {
                return ValidationIssue("File already exists: $target", template.fileNameProperty)
            }

            return null
        }

        /** Resolves the template, expands includes, and renders against the form data. */
        fun render(): String {
            val templateFileName = resolveTemplateFile()
            val resourcePath = "egovframe/config/${template.templateFolder}/$templateFileName"
            return EgovHandlebars.renderConfig(
                templateText = EgovAssets.resourceText(resourcePath),
                formData = formData,
                defaultPackageName = defaultPackageName,
                includeResolver = { relativePath ->
                    runCatching {
                        EgovAssets.resourceText("egovframe/config/${template.templateFolder}/$relativePath")
                    }.getOrNull()
                },
            )
        }

        /** Validates, renders, and writes the file. Returns the generated config. */
        fun generate(outputFolder: Path): GeneratedConfig {
            val target = targetPath(outputFolder)
            require(!Files.exists(target)) { "File already exists: $target" }
            val content = render()
            Files.createDirectories(target.parent)
            Files.writeString(target, content, Charsets.UTF_8)
            return GeneratedConfig(target, content)
        }

        private fun targetPath(outputFolder: Path): Path {
            val rawFileName = formData[template.fileNameProperty]?.toString().orEmpty().ifBlank { "default_filename" }
            return outputPath(outputFolder, rawFileName, generationType)
        }

        private fun resolveTemplateFile(): String = when (generationType) {
            GenerationType.XML -> template.templateFile
            GenerationType.JAVA -> template.javaConfigTemplate.ifBlank {
                throw IllegalArgumentException("JavaConfig template is not available for ${template.displayName}")
            }
            GenerationType.YAML -> template.yamlTemplate.ifBlank {
                throw IllegalArgumentException("YAML template is not available for ${template.displayName}")
            }
            GenerationType.PROPERTIES -> template.propertiesTemplate.ifBlank {
                throw IllegalArgumentException("Properties template is not available for ${template.displayName}")
            }
        }
    }

    /** Creates a [PreparedConfig] ready for validation, rendering, or generation. */
    fun prepare(
        template: ConfigTemplate,
        generationType: GenerationType,
        formData: Map<String, Any?>,
        defaultPackageName: String,
    ): PreparedConfig = PreparedConfig(template, generationType, formData, defaultPackageName)

    private fun availableTypes(template: ConfigTemplate): List<GenerationType> = buildList {
        add(GenerationType.XML)
        if (template.javaConfigTemplate.isNotBlank()) add(GenerationType.JAVA)
        if (template.yamlTemplate.isNotBlank()) add(GenerationType.YAML)
        if (template.propertiesTemplate.isNotBlank()) add(GenerationType.PROPERTIES)
    }

    private fun outputPath(
        outputFolder: Path,
        rawFileName: String,
        generationType: GenerationType,
    ): Path {
        val fileName = sanitizeFileName(rawFileName)
        val suffixedName = if (fileName.endsWith(".${generationType.extension}", ignoreCase = true)) {
            fileName
        } else {
            "$fileName.${generationType.extension}"
        }
        val base = outputFolder.toAbsolutePath().normalize()
        val target = base.resolve(suffixedName).normalize()
        require(target != base && target.startsWith(base)) { "Resolved path escapes the target directory" }
        return target
    }

    private fun sanitizeFileName(rawFileName: String): String {
        val trimmed = rawFileName.trim()
        require(trimmed.isNotEmpty()) { "Invalid file name" }
        val normalized = trimmed.replace('\\', '/')
        val baseName = normalized.substringAfterLast('/')
        require(baseName.isNotEmpty() && baseName != "." && baseName != "..") { "Invalid file name" }
        require(baseName.none { it in INVALID_FILE_NAME_CHARACTERS }) { "Invalid file name: $baseName" }
        return baseName
    }

    private fun stripOptionalExtension(fileName: String, generationType: GenerationType): String {
        val suffix = ".${generationType.extension}"
        return if (fileName.endsWith(suffix, ignoreCase = true)) fileName.dropLast(suffix.length) else fileName
    }

    private val PACKAGE_NAME_REGEX = Regex("^[a-z]([a-z0-9.]*[a-z0-9])?$")
    private val JAVA_CLASS_NAME_REGEX = Regex("^[A-Z][A-Za-z0-9]*$")
    private val FILE_NAME_REGEX = Regex("^[A-Za-z0-9_-]+$")
    private const val INVALID_FILE_NAME_CHARACTERS = "<>:\"/\\|?*"
}
