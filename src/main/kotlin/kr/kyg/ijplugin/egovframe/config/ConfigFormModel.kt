package kr.kyg.ijplugin.egovframe.config

import kr.kyg.ijplugin.egovframe.assets.ConfigTemplate
import kr.kyg.ijplugin.egovframe.settings.EgovBundle

// ── Core types ──────────────────────────────────────────────────────────────

enum class ControlType { TEXT, SELECT, RADIO, CHECK, FILE }

data class SelectOption(val value: String, val label: String)

data class FieldDef(
    val key: String,
    val label: String,
    val control: ControlType = ControlType.TEXT,
    val options: List<SelectOption> = emptyList(),
    val required: Boolean = false,
    val numeric: Boolean = false,
    val noSpecialChars: Boolean = false,
    val packageField: Boolean = false,
    val classField: Boolean = false,
    val fileNameField: Boolean = false,
    val visibleWhen: ((FormState) -> Boolean)? = null,
    val requiredWhen: ((FormState) -> Boolean)? = null,
)

class FormState(initial: Map<String, Any?>) {
    private val data = LinkedHashMap<String, Any?>(initial)
    operator fun get(key: String): Any? = data[key]
    operator fun set(key: String, value: Any?) { data[key] = value }
    fun toMap(): Map<String, Any?> = LinkedHashMap(data)
    fun getString(key: String): String = data[key]?.toString().orEmpty()
    fun getBoolean(key: String): Boolean = data[key] as? Boolean ?: false
}

data class LinkedUpdate(
    val sourceField: String,
    val update: (FormState) -> Unit,
)

data class ConfigFormSpec(
    val displayName: String,
    val fields: List<FieldDef>,
    val activeTypes: List<ConfigGenerator.GenerationType>,
    val linkedUpdates: List<LinkedUpdate> = emptyList(),
    val validateExtra: ((FormState) -> ConfigGenerator.ValidationIssue?)? = null,
    val javaDefaultFileName: String? = null,
)

// ── Path normalisation ──────────────────────────────────────────────────────

fun normalizeConfigLocation(rawPath: String, resourceRoot: String = "src/main/resources"): String {
    val normalized = rawPath.replace('\\', '/')
    val marker = "$resourceRoot/"
    val idx = normalized.indexOf(marker)
    return if (idx >= 0) normalized.substring(idx + marker.length) else normalized
}

// ── Validation ──────────────────────────────────────────────────────────────

private val SPECIAL_CHARS = Regex("""[^A-Za-z0-9_-]""")
private val PACKAGE_REGEX = Regex("""^[a-z]([a-z0-9.]*[a-z0-9])?$""")
private val CLASS_NAME_REGEX = Regex("""^[A-Z][A-Za-z0-9]*$""")
private val FILE_NAME_REGEX = Regex("""^[A-Za-z0-9_-]+$""")

private fun stripOptionalExtension(value: String, generationType: ConfigGenerator.GenerationType?): String {
    val suffix = generationType?.let { ".${it.extension}" } ?: return value
    return if (value.endsWith(suffix, ignoreCase = true)) value.dropLast(suffix.length) else value
}

fun ConfigFormSpec.validate(state: FormState): ConfigGenerator.ValidationIssue? {
    val generationType = ConfigGenerator.GenerationType.entries.firstOrNull {
        it.id == state.getString("generationType")
    }
    val isJava = generationType == ConfigGenerator.GenerationType.JAVA
    for (field in fields) {
        if (field.visibleWhen != null && !field.visibleWhen.invoke(state)) continue

        val value = state.getString(field.key)
        val label = EgovBundle.messageOrDefault("config.field.${field.key}", field.label)
        val isRequired = if (field.requiredWhen != null) field.requiredWhen.invoke(state) else field.required

        if (isRequired && value.isBlank()) {
            return ConfigGenerator.ValidationIssue(EgovBundle.message("config.validation.required", label), field.key)
        }
        if (value.isBlank()) continue

        if (field.numeric && value.toDoubleOrNull() == null) {
            return ConfigGenerator.ValidationIssue(EgovBundle.message("config.validation.numeric", label), field.key)
        }
        if (field.noSpecialChars && SPECIAL_CHARS.containsMatchIn(value)) {
            return ConfigGenerator.ValidationIssue(EgovBundle.message("config.validation.special", label), field.key)
        }
        if (field.packageField && isJava && !PACKAGE_REGEX.matches(value)) {
            return ConfigGenerator.ValidationIssue(EgovBundle.message("config.validation.package", label), field.key)
        }

        val baseFileName = stripOptionalExtension(value, generationType)
        if (field.classField && isJava) {
            if (!CLASS_NAME_REGEX.matches(baseFileName)) {
                return ConfigGenerator.ValidationIssue(EgovBundle.message("config.validation.class", label), field.key)
            }
        } else if (field.fileNameField && !FILE_NAME_REGEX.matches(baseFileName)) {
            return ConfigGenerator.ValidationIssue(EgovBundle.message("config.validation.filename", label), field.key)
        }
    }
    return validateExtra?.invoke(state)
}

// ── Registry ────────────────────────────────────────────────────────────────

object ConfigFormRegistry {

    private val specs: Map<String, ConfigFormSpec> by lazy { buildSpecs() }

    fun forTemplate(displayName: String): ConfigFormSpec? = specs[displayName]

    fun forTemplate(template: ConfigTemplate): ConfigFormSpec? = specs[template.displayName]

    fun all(): Collection<ConfigFormSpec> = specs.values

    // ── Shared helpers ──────────────────────────────────────────────────

    private fun isJava(s: FormState) = s.getString("generationType") == "javaConfig"
    private val whenJava: (FormState) -> Boolean = ::isJava
    private val whenNotJava: (FormState) -> Boolean = { !isJava(it) }

    private val booleanOptions = listOf(SelectOption("true", "true"), SelectOption("false", "false"))

    private fun configPackageField() = FieldDef(
        key = "txtConfigPackage", label = "Config Package",
        packageField = true, visibleWhen = whenJava, requiredWhen = whenJava,
    )

    private fun fileNameField(classField: Boolean = false) = FieldDef(
        key = "txtFileName", label = "File Name",
        required = true, fileNameField = true, classField = classField,
    )

    // ── Build all specs ─────────────────────────────────────────────────

    private fun buildSpecs(): Map<String, ConfigFormSpec> {
        val list = listOf(
            cacheNewCache(),
            cacheEhcacheConfig(),
            datasource(),
            jndiDatasource(),
            idGenSequence(),
            idGenTable(),
            idGenUuid(),
            loggingConsole(),
            loggingFile(),
            loggingRollingFile(),
            loggingTimeBasedRollingFile(),
            loggingJdbc(),
            property(),
            schedulingBeanJob(),
            schedulingMethodJob(),
            schedulingSimpleTrigger(),
            schedulingCronTrigger(),
            schedulingScheduler(),
            transactionDatasource(),
            transactionJpa(),
            transactionJta(),
        )
        return list.associateBy { it.displayName }
    }

    // ── Cache ───────────────────────────────────────────────────────────

    private fun cacheNewCache(): ConfigFormSpec {
        val eternalFalse: (FormState) -> Boolean = { it.getString("txtDftEternal") == "false" }
        val cacheEternalFalse: (FormState) -> Boolean = { it.getString("txtEternal") == "false" }
        return ConfigFormSpec(
            displayName = "Cache > New Cache",
            activeTypes = listOf(ConfigGenerator.GenerationType.XML),
            fields = listOf(
                FieldDef(key = "txtFileName", label = "File Name", required = true, fileNameField = true),
                FieldDef(key = "txtDiskStore", label = "Disk Store", required = true),
                FieldDef(
                    key = "txtDftEternal", label = "Default Eternal",
                    control = ControlType.SELECT, options = booleanOptions,
                    required = true, noSpecialChars = true,
                ),
                FieldDef(
                    key = "txtDftLiveTime", label = "Default Time to Live",
                    numeric = true, visibleWhen = eternalFalse,
                ),
                FieldDef(key = "txtDftHeapEntries", label = "Default Heap Entries", numeric = true),
                FieldDef(key = "txtDftOffheapSize", label = "Default Off-Heap Size", numeric = true),
                FieldDef(
                    key = "txtDftDiskPersistence", label = "Default Disk Persistence",
                    control = ControlType.SELECT, options = booleanOptions, noSpecialChars = true,
                ),
                FieldDef(key = "txtCacheName", label = "Cache Name", required = true, noSpecialChars = true),
                FieldDef(
                    key = "txtEternal", label = "Eternal",
                    control = ControlType.SELECT, options = booleanOptions,
                    required = true, noSpecialChars = true,
                ),
                FieldDef(
                    key = "txtIdleTime", label = "Idle Time",
                    numeric = true, visibleWhen = cacheEternalFalse,
                ),
                FieldDef(key = "txtHeapEntries", label = "Heap Entries", numeric = true),
            ),
        )
    }

    private fun cacheEhcacheConfig() = ConfigFormSpec(
        displayName = "Cache > New Ehcache Configuration",
        activeTypes = listOf(ConfigGenerator.GenerationType.XML, ConfigGenerator.GenerationType.JAVA),
        javaDefaultFileName = "EgovEhcacheSpringConfig",
        linkedUpdates = listOf(
            LinkedUpdate("txtConfigLocation") { state ->
                val raw = state.getString("txtConfigLocation")
                if (raw.isNotBlank()) state["txtConfigLocation"] = normalizeConfigLocation(raw)
            },
        ),
        fields = listOf(
            configPackageField(),
            FieldDef(key = "txtComponentScanBasePackage", label = "Component Scan Base Package", required = true),
            fileNameField(classField = true),
            FieldDef(key = "txtConfigLocation", label = "Config Location", control = ControlType.FILE, required = true),
        ),
    )

    // ── Datasource ──────────────────────────────────────────────────────

    private fun datasource() = ConfigFormSpec(
        displayName = "Datasource > New Datasource",
        activeTypes = listOf(ConfigGenerator.GenerationType.XML, ConfigGenerator.GenerationType.JAVA),
        javaDefaultFileName = "EgovDataSourceConfig",
        fields = listOf(
            configPackageField(),
            fileNameField(classField = true),
            FieldDef(key = "txtDatasourceName", label = "Datasource Name", required = true, noSpecialChars = true),
            FieldDef(
                key = "rdoType", label = "Type",
                control = ControlType.SELECT,
                options = listOf(
                    SelectOption("DBCP", "DBCP"),
                    SelectOption("C3P0", "C3P0"),
                    SelectOption("JDBC", "JDBC"),
                ),
                required = true, noSpecialChars = true,
            ),
            FieldDef(key = "txtDriver", label = "Driver", required = true),
            FieldDef(key = "txtUrl", label = "URL", required = true),
            FieldDef(key = "txtUser", label = "User", required = true),
            FieldDef(key = "txtPasswd", label = "Password"),
        ),
    )

    private fun jndiDatasource() = ConfigFormSpec(
        displayName = "Datasource > New JNDI Datasource",
        activeTypes = listOf(ConfigGenerator.GenerationType.XML, ConfigGenerator.GenerationType.JAVA),
        javaDefaultFileName = "EgovJndiDatasourceConfig",
        fields = listOf(
            configPackageField(),
            fileNameField(),
            FieldDef(key = "txtDatasourceName", label = "Datasource Name", required = true, noSpecialChars = true),
            FieldDef(key = "txtJndiName", label = "JNDI Name", required = true),
        ),
    )

    // ── ID Generation ───────────────────────────────────────────────────

    private fun idGenSequence() = ConfigFormSpec(
        displayName = "ID Generation > New Sequence ID Generation",
        activeTypes = listOf(ConfigGenerator.GenerationType.XML, ConfigGenerator.GenerationType.JAVA),
        javaDefaultFileName = "EgovIdgnSequenceConfig",
        fields = listOf(
            configPackageField(),
            fileNameField(),
            FieldDef(key = "txtIdServiceName", label = "ID Service Name", required = true, noSpecialChars = true),
            FieldDef(key = "txtDatasourceName", label = "Datasource Name", required = true, noSpecialChars = true),
            FieldDef(key = "txtQuery", label = "Query", required = true),
            FieldDef(
                key = "rdoIdType", label = "ID Type",
                control = ControlType.RADIO,
                options = listOf(SelectOption("Base", "Base"), SelectOption("BigDecimal", "BigDecimal")),
                noSpecialChars = true,
            ),
        ),
    )

    private fun idGenTable(): ConfigFormSpec {
        val whenStrategy: (FormState) -> Boolean = { it.getBoolean("chkStrategy") }
        return ConfigFormSpec(
            displayName = "ID Generation > New Table ID Generation",
            activeTypes = listOf(ConfigGenerator.GenerationType.XML, ConfigGenerator.GenerationType.JAVA),
            javaDefaultFileName = "EgovIdgnTableConfig",
            fields = listOf(
                configPackageField(),
                fileNameField(),
                FieldDef(key = "txtIdServiceName", label = "ID Service Name", required = true, noSpecialChars = true),
                FieldDef(key = "txtDatasourceName", label = "Datasource Name", required = true, noSpecialChars = true),
                FieldDef(key = "txtTable", label = "Table", required = true, noSpecialChars = true),
                FieldDef(key = "txtTableNameFieldValue", label = "Table Name Field Value", required = true, noSpecialChars = true),
                FieldDef(key = "txtBlockSize", label = "Block Size", required = true, numeric = true),
                FieldDef(key = "chkStrategy", label = "Use Strategy", control = ControlType.CHECK),
                FieldDef(
                    key = "txtStrategyName", label = "Strategy Name",
                    noSpecialChars = true, visibleWhen = whenStrategy, requiredWhen = whenStrategy,
                ),
                FieldDef(
                    key = "txtPrefix", label = "Prefix",
                    visibleWhen = whenStrategy, requiredWhen = whenStrategy,
                ),
                FieldDef(
                    key = "txtCipers", label = "Cipers",
                    numeric = true, visibleWhen = whenStrategy, requiredWhen = whenStrategy,
                ),
                FieldDef(
                    key = "txtFillChar", label = "Fill Character",
                    visibleWhen = whenStrategy, requiredWhen = whenStrategy,
                ),
            ),
        )
    }

    private fun idGenUuid(): ConfigFormSpec {
        val whenAddress: (FormState) -> Boolean = { it.getString("rdoIdType") == "Address" }
        return ConfigFormSpec(
            displayName = "ID Generation > New UUID Generation",
            activeTypes = listOf(ConfigGenerator.GenerationType.XML, ConfigGenerator.GenerationType.JAVA),
            javaDefaultFileName = "EgovIdgnUuidConfig",
            fields = listOf(
                configPackageField(),
                fileNameField(),
                FieldDef(key = "txtIdServiceName", label = "ID Service Name", required = true, noSpecialChars = true),
                FieldDef(
                    key = "rdoIdType", label = "ID Type",
                    control = ControlType.RADIO,
                    options = listOf(SelectOption("Base", "Base"), SelectOption("Address", "Address")),
                    noSpecialChars = true,
                ),
                FieldDef(
                    key = "txtAddress", label = "Address",
                    visibleWhen = whenAddress, requiredWhen = whenAddress,
                ),
            ),
        )
    }

    // ── Logging ─────────────────────────────────────────────────────────

    private fun loggingConsole() = ConfigFormSpec(
        displayName = "Logging > New Console Appender",
        activeTypes = listOf(
            ConfigGenerator.GenerationType.XML,
            ConfigGenerator.GenerationType.YAML,
            ConfigGenerator.GenerationType.PROPERTIES,
        ),
        fields = listOf(
            configPackageField(),
            fileNameField(),
            FieldDef(key = "txtAppenderName", label = "Appender Name", required = true, noSpecialChars = true),
            FieldDef(key = "txtConversionPattern", label = "Conversion Pattern", required = true),
        ),
    )

    private fun loggingFile() = ConfigFormSpec(
        displayName = "Logging > New File Appender",
        activeTypes = listOf(
            ConfigGenerator.GenerationType.XML,
            ConfigGenerator.GenerationType.YAML,
            ConfigGenerator.GenerationType.PROPERTIES,
        ),
        fields = listOf(
            configPackageField(),
            fileNameField(),
            FieldDef(key = "txtAppenderName", label = "Appender Name", required = true, noSpecialChars = true),
            FieldDef(key = "txtConversionPattern", label = "Conversion Pattern", required = true),
            FieldDef(key = "txtLogFileName", label = "Log File Name", required = true),
            FieldDef(key = "cboAppend", label = "Append", control = ControlType.CHECK),
        ),
    )

    private fun loggingRollingFile() = ConfigFormSpec(
        displayName = "Logging > New Rolling File Appender",
        activeTypes = listOf(
            ConfigGenerator.GenerationType.XML,
            ConfigGenerator.GenerationType.YAML,
            ConfigGenerator.GenerationType.PROPERTIES,
        ),
        fields = listOf(
            configPackageField(),
            fileNameField(),
            FieldDef(key = "txtAppenderName", label = "Appender Name", required = true, noSpecialChars = true),
            FieldDef(key = "txtConversionPattern", label = "Conversion Pattern", required = true),
            FieldDef(key = "txtLogFileName", label = "Log File Name", required = true),
            FieldDef(key = "txtLogFileNamePattern", label = "Log File Name Pattern", required = true),
            FieldDef(key = "txtMaxIndex", label = "Max Index", required = true, numeric = true),
            FieldDef(key = "txtMaxFileSize", label = "Max File Size", required = true, numeric = true),
        ),
    )

    private fun loggingTimeBasedRollingFile() = ConfigFormSpec(
        displayName = "Logging > New Time-Based Rolling File Appender",
        activeTypes = listOf(
            ConfigGenerator.GenerationType.XML,
            ConfigGenerator.GenerationType.YAML,
            ConfigGenerator.GenerationType.PROPERTIES,
        ),
        fields = listOf(
            configPackageField(),
            fileNameField(),
            FieldDef(key = "txtAppenderName", label = "Appender Name", required = true, noSpecialChars = true),
            FieldDef(key = "txtConversionPattern", label = "Conversion Pattern", required = true),
            FieldDef(key = "txtLogFileName", label = "Log File Name", required = true),
            FieldDef(key = "txtLogFileNamePattern", label = "Log File Name Pattern", required = true),
            FieldDef(key = "txtInterval", label = "Interval", required = true, numeric = true),
            FieldDef(key = "cboModulate", label = "Modulate", control = ControlType.CHECK),
        ),
    )

    private fun loggingJdbc(): ConfigFormSpec {
        val whenDriverManager: (FormState) -> Boolean = { it.getString("rdoConnectionType") == "DriverManager" }
        val whenConnectionFactory: (FormState) -> Boolean = { it.getString("rdoConnectionType") == "ConnectionFactory" }
        return ConfigFormSpec(
            displayName = "Logging > New JDBC Appender",
            activeTypes = listOf(
                ConfigGenerator.GenerationType.XML,
                ConfigGenerator.GenerationType.YAML,
            ),
            fields = listOf(
                configPackageField(),
                fileNameField(),
                FieldDef(key = "txtAppenderName", label = "Appender Name", required = true, noSpecialChars = true),
                FieldDef(key = "txtTableName", label = "Table Name", required = true, noSpecialChars = true),
                FieldDef(
                    key = "rdoConnectionType", label = "Connection Type",
                    control = ControlType.RADIO,
                    options = listOf(
                        SelectOption("DriverManager", "DriverManager"),
                        SelectOption("ConnectionFactory", "ConnectionFactory"),
                    ),
                    required = true, noSpecialChars = true,
                ),
                FieldDef(
                    key = "txtDriver", label = "Driver",
                    visibleWhen = whenDriverManager, requiredWhen = whenDriverManager,
                ),
                FieldDef(
                    key = "txtUrl", label = "URL",
                    visibleWhen = whenDriverManager, requiredWhen = whenDriverManager,
                ),
                FieldDef(
                    key = "txtUser", label = "User",
                    visibleWhen = whenDriverManager, requiredWhen = whenDriverManager,
                ),
                FieldDef(
                    key = "txtPasswrd", label = "Password",
                    visibleWhen = whenDriverManager, requiredWhen = whenDriverManager,
                ),
                FieldDef(
                    key = "txtConnectionFactoryClass", label = "Connection Factory Class",
                    visibleWhen = whenConnectionFactory, requiredWhen = whenConnectionFactory,
                ),
                FieldDef(
                    key = "txtConnectionFactoryMethod", label = "Connection Factory Method",
                    visibleWhen = whenConnectionFactory, requiredWhen = whenConnectionFactory,
                ),
            ),
        )
    }

    // ── Property ────────────────────────────────────────────────────────

    private fun property(): ConfigFormSpec {
        val whenInternal: (FormState) -> Boolean = { it.getString("rdoType") == "Internal Properties" }
        val whenExternal: (FormState) -> Boolean = { it.getString("rdoType") == "External File" }
        return ConfigFormSpec(
            displayName = "Property > New Property",
            activeTypes = listOf(ConfigGenerator.GenerationType.XML, ConfigGenerator.GenerationType.JAVA),
            javaDefaultFileName = "EgovPropertiesConfig",
            fields = listOf(
                configPackageField(),
                fileNameField(),
                FieldDef(key = "txtPropertyServiceName", label = "Property Service Name", required = true, noSpecialChars = true),
                FieldDef(
                    key = "rdoType", label = "Type",
                    control = ControlType.RADIO,
                    options = listOf(
                        SelectOption("Internal Properties", "Internal Properties"),
                        SelectOption("External File", "External File"),
                    ),
                    required = true,
                ),
                FieldDef(
                    key = "txtKey", label = "Key",
                    noSpecialChars = true, visibleWhen = whenInternal,
                ),
                FieldDef(
                    key = "txtValue", label = "Value",
                    visibleWhen = whenInternal,
                ),
                FieldDef(
                    key = "cboEncoding", label = "Encoding",
                    control = ControlType.SELECT,
                    options = listOf(
                        SelectOption("UTF-8", "UTF-8"),
                        SelectOption("UTF-16", "UTF-16"),
                        SelectOption("ASCII", "ASCII"),
                        SelectOption("US-ASCII", "US-ASCII"),
                        SelectOption("MS949", "MS949"),
                        SelectOption("ISO-8859-1", "ISO-8859-1"),
                    ),
                    visibleWhen = whenExternal,
                ),
                FieldDef(
                    key = "txtPropertyFile", label = "Property File",
                    visibleWhen = whenExternal, requiredWhen = whenExternal,
                ),
            ),
        )
    }

    // ── Scheduling ──────────────────────────────────────────────────────

    private fun schedulingBeanJob(): ConfigFormSpec {
        val whenProperty: (FormState) -> Boolean = { it.getBoolean("chkProperty") }
        return ConfigFormSpec(
            displayName = "Scheduling > New Detail Bean Job",
            activeTypes = listOf(ConfigGenerator.GenerationType.XML, ConfigGenerator.GenerationType.JAVA),
            javaDefaultFileName = "EgovSchedulingJobDetailConfig",
            fields = listOf(
                configPackageField(),
                fileNameField(),
                FieldDef(key = "txtJobName", label = "Job Name", required = true, noSpecialChars = true),
                FieldDef(key = "txtServiceClass", label = "Service Class", required = true),
                FieldDef(key = "chkProperty", label = "Use Property", control = ControlType.CHECK),
                FieldDef(
                    key = "txtPropertyName", label = "Property Name",
                    noSpecialChars = true, visibleWhen = whenProperty,
                ),
                FieldDef(
                    key = "txtPropertyValue", label = "Property Value",
                    noSpecialChars = true, visibleWhen = whenProperty,
                ),
            ),
        )
    }

    private fun schedulingMethodJob(): ConfigFormSpec {
        val whenProperty: (FormState) -> Boolean = { it.getBoolean("chkProperty") }
        return ConfigFormSpec(
            displayName = "Scheduling > New Method Invoking Job",
            activeTypes = listOf(ConfigGenerator.GenerationType.XML, ConfigGenerator.GenerationType.JAVA),
            javaDefaultFileName = "EgovSchedulingMethodInvokingJobDetailConfig",
            fields = listOf(
                configPackageField(),
                fileNameField(),
                FieldDef(key = "txtJobName", label = "Job Name", required = true, noSpecialChars = true),
                FieldDef(key = "txtServiceClass", label = "Service Class", required = true),
                FieldDef(key = "txtServiceName", label = "Service Name", required = true, noSpecialChars = true),
                FieldDef(key = "txtServiceMethod", label = "Service Method", required = true, noSpecialChars = true),
                FieldDef(
                    key = "cboConcurrent", label = "Concurrent",
                    control = ControlType.SELECT,
                    options = listOf(SelectOption("false", "false"), SelectOption("true", "true")),
                    required = true, noSpecialChars = true,
                ),
                FieldDef(key = "chkProperty", label = "Use Property", control = ControlType.CHECK),
                FieldDef(
                    key = "txtPropertyName", label = "Property Name",
                    noSpecialChars = true, visibleWhen = whenProperty,
                ),
                FieldDef(
                    key = "txtPropertyValue", label = "Property Value",
                    noSpecialChars = true, visibleWhen = whenProperty,
                ),
            ),
        )
    }

    private val jobDetailTypeOptions = listOf(
        SelectOption("JobDetailFactoryBean", "JobDetailFactoryBean"),
        SelectOption("MethodInvokingJobDetailFactoryBean", "MethodInvokingJobDetailFactoryBean"),
    )

    private val jobDetailLinkedUpdate = LinkedUpdate("cboJobDetailType") { state ->
        state["txtJobName"] = when (state.getString("cboJobDetailType")) {
            "JobDetailFactoryBean" -> "jobDetail"
            "MethodInvokingJobDetailFactoryBean" -> "methodInvokingJobDetail"
            else -> state.getString("txtJobName")
        }
    }

    private fun schedulingSimpleTrigger() = ConfigFormSpec(
        displayName = "Scheduling > New Simple Trigger",
        activeTypes = listOf(ConfigGenerator.GenerationType.XML, ConfigGenerator.GenerationType.JAVA),
        javaDefaultFileName = "EgovSchedulingSimpleTriggerConfig",
        linkedUpdates = listOf(jobDetailLinkedUpdate),
        fields = listOf(
            configPackageField(),
            fileNameField(),
            FieldDef(key = "txtTriggerName", label = "Trigger Name", required = true, noSpecialChars = true),
            FieldDef(
                key = "cboJobDetailType", label = "Job Detail Type",
                control = ControlType.SELECT, options = jobDetailTypeOptions,
                required = true, noSpecialChars = true,
            ),
            FieldDef(key = "txtJobName", label = "Job Name", required = true, noSpecialChars = true),
            FieldDef(key = "txtStartDelay", label = "Start Delay", required = true, numeric = true),
            FieldDef(key = "txtRepeatInterval", label = "Repeat Interval", required = true, numeric = true),
        ),
    )

    private fun schedulingCronTrigger() = ConfigFormSpec(
        displayName = "Scheduling > New Cron Trigger",
        activeTypes = listOf(ConfigGenerator.GenerationType.XML, ConfigGenerator.GenerationType.JAVA),
        javaDefaultFileName = "EgovSchedulingCronTriggerConfig",
        linkedUpdates = listOf(jobDetailLinkedUpdate),
        fields = listOf(
            configPackageField(),
            fileNameField(),
            FieldDef(key = "txtTriggerName", label = "Trigger Name", required = true, noSpecialChars = true),
            FieldDef(
                key = "cboJobDetailType", label = "Job Detail Type",
                control = ControlType.SELECT, options = jobDetailTypeOptions,
                required = true,
            ),
            FieldDef(key = "txtJobName", label = "Job Name", required = true, noSpecialChars = true),
            FieldDef(key = "txtCronExpression", label = "Cron Expression", required = true),
        ),
    )

    private fun schedulingScheduler() = ConfigFormSpec(
        displayName = "Scheduling > New Scheduler",
        activeTypes = listOf(ConfigGenerator.GenerationType.XML, ConfigGenerator.GenerationType.JAVA),
        javaDefaultFileName = "EgovSchedulingSchedulerConfig",
        linkedUpdates = listOf(
            LinkedUpdate("cboTriggerType") { state ->
                state["txtTriggerName"] = when (state.getString("cboTriggerType")) {
                    "SimpleTriggerFactoryBean" -> "simpleTrigger"
                    "CronTriggerFactoryBean" -> "cronTrigger"
                    else -> state.getString("txtTriggerName")
                }
            },
        ),
        fields = listOf(
            configPackageField(),
            fileNameField(),
            FieldDef(key = "txtSchedulerName", label = "Scheduler Name", required = true, noSpecialChars = true),
            FieldDef(
                key = "cboTriggerType", label = "Trigger Type",
                control = ControlType.SELECT,
                options = listOf(
                    SelectOption("SimpleTriggerFactoryBean", "SimpleTriggerFactoryBean"),
                    SelectOption("CronTriggerFactoryBean", "CronTriggerFactoryBean"),
                ),
                required = true, noSpecialChars = true,
            ),
            FieldDef(key = "txtTriggerName", label = "Trigger Name", required = true, noSpecialChars = true),
        ),
    )

    // ── Transaction ─────────────────────────────────────────────────────

    private val propagationOptions = listOf(
        SelectOption("REQUIRED", "REQUIRED"),
        SelectOption("REQUIRES_NEW", "REQUIRES_NEW"),
        SelectOption("SUPPORTS", "SUPPORTS"),
        SelectOption("NOT_SUPPORTED", "NOT_SUPPORTED"),
        SelectOption("MANDATORY", "MANDATORY"),
        SelectOption("NEVER", "NEVER"),
        SelectOption("NESTED", "NESTED"),
    )

    private val isolationOptions = listOf(
        SelectOption("DEFAULT", "DEFAULT"),
        SelectOption("READ_UNCOMMITTED", "READ_UNCOMMITTED"),
        SelectOption("READ_COMMITTED", "READ_COMMITTED"),
        SelectOption("REPEATABLE_READ", "REPEATABLE_READ"),
        SelectOption("SERIALIZABLE", "SERIALIZABLE"),
    )

    private val dialectOptions = listOf(
        SelectOption("org.hibernate.dialect.H2Dialect", "H2"),
        SelectOption("org.hibernate.dialect.HSQLDialect", "HSQL"),
        SelectOption("org.hibernate.dialect.MySQLDialect", "MySQL"),
        SelectOption("org.hibernate.dialect.MySQL5Dialect", "MySQL 5"),
        SelectOption("org.hibernate.dialect.MySQL5InnoDBDialect", "MySQL 5 InnoDB"),
        SelectOption("org.hibernate.dialect.MySQL8Dialect", "MySQL 8"),
        SelectOption("org.hibernate.dialect.MariaDBDialect", "MariaDB"),
        SelectOption("org.hibernate.dialect.Oracle8iDialect", "Oracle 8i"),
        SelectOption("org.hibernate.dialect.Oracle9iDialect", "Oracle 9i"),
        SelectOption("org.hibernate.dialect.Oracle10gDialect", "Oracle 10g"),
        SelectOption("org.hibernate.dialect.Oracle12cDialect", "Oracle 12c"),
        SelectOption("org.hibernate.dialect.PostgreSQLDialect", "PostgreSQL"),
        SelectOption("org.hibernate.dialect.PostgreSQL82Dialect", "PostgreSQL 8.2"),
        SelectOption("org.hibernate.dialect.PostgreSQL95Dialect", "PostgreSQL 9.5"),
        SelectOption("org.hibernate.dialect.SQLServerDialect", "SQL Server"),
        SelectOption("org.hibernate.dialect.SQLServer2012Dialect", "SQL Server 2012"),
        SelectOption("org.hibernate.dialect.DB2Dialect", "DB2"),
        SelectOption("org.hibernate.dialect.DerbyTenSevenDialect", "Derby"),
        SelectOption("org.hibernate.dialect.InformixDialect", "Informix"),
        SelectOption("org.hibernate.dialect.SybaseDialect", "Sybase"),
        SelectOption("org.hibernate.dialect.CUBRIDDialect", "CUBRID"),
    )

    private val transactionExtraValidation: (FormState) -> ConfigGenerator.ValidationIssue? = { state ->
        if (!state.getBoolean("chkAopConfigTransaction") && !state.getBoolean("chkAnnotationTransaction")) {
            ConfigGenerator.ValidationIssue(
                EgovBundle.message("config.validation.transactionSelection"),
                "chkAopConfigTransaction",
            )
        } else null
    }

    private fun aopFields(): List<FieldDef> {
        val whenAop: (FormState) -> Boolean = { it.getBoolean("chkAopConfigTransaction") }
        val whenAopNotJava: (FormState) -> Boolean = { it.getBoolean("chkAopConfigTransaction") && !isJava(it) }
        return listOf(
            FieldDef(
                key = "txtPointCutName", label = "Pointcut Name",
                noSpecialChars = true,
                visibleWhen = whenAopNotJava,
                required = true,
            ),
            FieldDef(
                key = "txtPointCutExpression", label = "Pointcut Expression",
                required = true, visibleWhen = whenAop,
            ),
            FieldDef(
                key = "txtAdviceName", label = "Advice Name",
                required = true, noSpecialChars = true, visibleWhen = whenAop,
            ),
            FieldDef(
                key = "txtMethodName", label = "Method Name",
                required = true, visibleWhen = whenAop,
            ),
            FieldDef(
                key = "chkReadOnly", label = "Read Only",
                control = ControlType.CHECK, visibleWhen = whenAop,
            ),
            FieldDef(
                key = "txtRollbackFor", label = "Rollback For",
                noSpecialChars = true, visibleWhen = whenAop,
            ),
            FieldDef(
                key = "txtNoRollbackFor", label = "No Rollback For",
                noSpecialChars = true, visibleWhen = whenAop,
            ),
            FieldDef(
                key = "txtTimeout", label = "Timeout",
                numeric = true, visibleWhen = whenAop,
            ),
            FieldDef(
                key = "cmbPropagation", label = "Propagation",
                control = ControlType.SELECT, options = propagationOptions,
                required = true, noSpecialChars = true, visibleWhen = whenAop,
            ),
            FieldDef(
                key = "cmbIsolation", label = "Isolation",
                control = ControlType.SELECT, options = isolationOptions,
                required = true, noSpecialChars = true, visibleWhen = whenAop,
            ),
        )
    }


    private fun transactionDatasource(): ConfigFormSpec {
        val page1 = listOf(
            configPackageField(),
            fileNameField(),
            FieldDef(key = "txtTransactionTemplateName", label = "Transaction Template Name", noSpecialChars = true),
            FieldDef(key = "txtTransactionName", label = "Transaction Name", required = true, noSpecialChars = true),
            FieldDef(key = "txtDataSourceName", label = "DataSource Name", required = true, noSpecialChars = true),
            FieldDef(key = "chkAopConfigTransaction", label = "AOP Config Transaction", control = ControlType.CHECK),
            FieldDef(key = "chkAnnotationTransaction", label = "Annotation Transaction", control = ControlType.CHECK),
        )
        return ConfigFormSpec(
            displayName = "Transaction > New Datasource Transaction",
            activeTypes = listOf(ConfigGenerator.GenerationType.XML, ConfigGenerator.GenerationType.JAVA),
            javaDefaultFileName = "EgovTransactionConfig",
            validateExtra = transactionExtraValidation,
            fields = page1 + aopFields(),
        )
    }

    private fun transactionJpa(): ConfigFormSpec {
        val page1 = listOf(
            configPackageField(),
            fileNameField(),
            FieldDef(key = "txtTransactionName", label = "Transaction Name", required = true, noSpecialChars = true),
            FieldDef(key = "txtEntityManagerFactory", label = "Entity Manager Factory", required = true, noSpecialChars = true),
            FieldDef(key = "txtDataSourceName", label = "DataSource Name", required = true, noSpecialChars = true),
            FieldDef(key = "txtPackagesToScan", label = "Packages to Scan", required = true),
            FieldDef(
                key = "cmbDialectName", label = "Dialect Name",
                control = ControlType.SELECT, options = dialectOptions, required = true,
            ),
            FieldDef(key = "txtSpringDataJpaRepositoriesPackage", label = "Spring Data JPA Repositories Package", required = true),
            FieldDef(key = "chkAopConfigTransaction", label = "AOP Config Transaction", control = ControlType.CHECK),
            FieldDef(key = "chkAnnotationTransaction", label = "Annotation Transaction", control = ControlType.CHECK),
        )
        return ConfigFormSpec(
            displayName = "Transaction > New JPA Transaction",
            activeTypes = listOf(ConfigGenerator.GenerationType.XML, ConfigGenerator.GenerationType.JAVA),
            javaDefaultFileName = "EgovTransactionJpaConfig",
            validateExtra = transactionExtraValidation,
            fields = page1 + aopFields(),
        )
    }

    private fun transactionJta(): ConfigFormSpec {
        val page1 = listOf(
            configPackageField(),
            fileNameField(),
            FieldDef(key = "txtTransactionName", label = "Transaction Name", required = true, noSpecialChars = true),
            FieldDef(key = "txtDataSourceName", label = "DataSource Name", noSpecialChars = true),
            FieldDef(
                key = "txtJtaImplementation", label = "JTA Implementation",
                control = ControlType.SELECT,
                options = listOf(SelectOption("Atomikos", "Atomikos")),
                noSpecialChars = true,
            ),
            FieldDef(key = "txtGlobalTimeout", label = "Global Timeout", required = true, numeric = true),
            FieldDef(key = "chkAopConfigTransaction", label = "AOP Config Transaction", control = ControlType.CHECK),
            FieldDef(key = "chkAnnotationTransaction", label = "Annotation Transaction", control = ControlType.CHECK),
        )
        return ConfigFormSpec(
            displayName = "Transaction > New JTA Transaction",
            activeTypes = listOf(ConfigGenerator.GenerationType.XML, ConfigGenerator.GenerationType.JAVA),
            javaDefaultFileName = "EgovTransactionJtaConfig",
            validateExtra = transactionExtraValidation,
            fields = page1 + aopFields(),
        )
    }
}
