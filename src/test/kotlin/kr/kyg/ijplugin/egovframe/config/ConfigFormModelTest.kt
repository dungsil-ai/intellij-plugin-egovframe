package kr.kyg.ijplugin.egovframe.config

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ConfigFormModelTest {

    // ── Registry coverage ───────────────────────────────────────────────

    @Test
    fun registryCovers21Specs() {
        assertEquals(21, ConfigFormRegistry.all().size)
    }

    @Test
    fun activeVariantsMatchUpstreamMatrix() {
        assertEquals(
            mapOf(
                "Cache > New Cache" to listOf("xml"),
                "Cache > New Ehcache Configuration" to listOf("xml", "javaConfig"),
                "Datasource > New Datasource" to listOf("xml", "javaConfig"),
                "Datasource > New JNDI Datasource" to listOf("xml", "javaConfig"),
                "ID Generation > New Sequence ID Generation" to listOf("xml", "javaConfig"),
                "ID Generation > New Table ID Generation" to listOf("xml", "javaConfig"),
                "ID Generation > New UUID Generation" to listOf("xml", "javaConfig"),
                "Logging > New Console Appender" to listOf("xml", "yaml", "properties"),
                "Logging > New File Appender" to listOf("xml", "yaml", "properties"),
                "Logging > New Rolling File Appender" to listOf("xml", "yaml", "properties"),
                "Logging > New Time-Based Rolling File Appender" to listOf("xml", "yaml", "properties"),
                "Logging > New JDBC Appender" to listOf("xml", "yaml"),
                "Property > New Property" to listOf("xml", "javaConfig"),
                "Scheduling > New Detail Bean Job" to listOf("xml", "javaConfig"),
                "Scheduling > New Method Invoking Job" to listOf("xml", "javaConfig"),
                "Scheduling > New Simple Trigger" to listOf("xml", "javaConfig"),
                "Scheduling > New Cron Trigger" to listOf("xml", "javaConfig"),
                "Scheduling > New Scheduler" to listOf("xml", "javaConfig"),
                "Transaction > New Datasource Transaction" to listOf("xml", "javaConfig"),
                "Transaction > New JPA Transaction" to listOf("xml", "javaConfig"),
                "Transaction > New JTA Transaction" to listOf("xml", "javaConfig"),
            ),
            ConfigFormRegistry.all().associate { spec -> spec.displayName to spec.activeTypes.map { it.id } },
        )
    }

    // ── Control types and field order ───────────────────────────────────

    @Test
    fun allFiveControlTypesAreUsedAcrossRegistry() {
        val usedTypes = ConfigFormRegistry.all()
            .flatMap { it.fields }
            .map { it.control }
            .toSet()
        assertEquals(ControlType.entries.toSet(), usedTypes)
    }

    @Test
    fun datasourceFieldOrderMatchesDeclaration() {
        val spec = ConfigFormRegistry.forTemplate("Datasource > New Datasource")!!
        val keys = spec.fields.map { it.key }
        assertEquals(
            listOf(
                "txtConfigPackage", "txtFileName", "txtDatasourceName", "rdoType",
                "txtDriver", "txtUrl", "txtUser", "txtPasswd",
            ),
            keys,
        )
    }

    // ── ID Generation: strategy and address visibility ──────────────────

    @Test
    fun idGenTableStrategyVisibility() {
        val spec = ConfigFormRegistry.forTemplate("ID Generation > New Table ID Generation")!!
        val strategyFields = spec.fields.filter {
            it.key in setOf("txtStrategyName", "txtPrefix", "txtCipers", "txtFillChar")
        }
        assertTrue(strategyFields.all { it.visibleWhen != null })

        val stateOff = FormState(mapOf("chkStrategy" to false))
        assertTrue(strategyFields.all { !it.visibleWhen!!.invoke(stateOff) })

        val stateOn = FormState(mapOf("chkStrategy" to true))
        assertTrue(strategyFields.all { it.visibleWhen!!.invoke(stateOn) })
    }

    @Test
    fun idGenUuidAddressVisibility() {
        val spec = ConfigFormRegistry.forTemplate("ID Generation > New UUID Generation")!!
        val addressField = spec.fields.first { it.key == "txtAddress" }
        assertNotNull(addressField.visibleWhen)

        assertFalse(addressField.visibleWhen!!.invoke(FormState(mapOf("rdoIdType" to "Base"))))
        assertTrue(addressField.visibleWhen!!.invoke(FormState(mapOf("rdoIdType" to "Address"))))
    }

    // ── Logging: JDBC connection type ───────────────────────────────────

    @Test
    fun loggingJdbcConnectionTypeVisibility() {
        val spec = ConfigFormRegistry.forTemplate("Logging > New JDBC Appender")!!
        val driverFields = spec.fields.filter {
            it.key in setOf("txtDriver", "txtUrl", "txtUser", "txtPasswrd")
        }
        val factoryFields = spec.fields.filter {
            it.key in setOf("txtConnectionFactoryClass", "txtConnectionFactoryMethod")
        }

        val stateDriver = FormState(mapOf("rdoConnectionType" to "DriverManager"))
        assertTrue(driverFields.all { it.visibleWhen!!.invoke(stateDriver) })
        assertTrue(factoryFields.all { !it.visibleWhen!!.invoke(stateDriver) })

        val stateFactory = FormState(mapOf("rdoConnectionType" to "ConnectionFactory"))
        assertTrue(driverFields.all { !it.visibleWhen!!.invoke(stateFactory) })
        assertTrue(factoryFields.all { it.visibleWhen!!.invoke(stateFactory) })
    }

    // ── Property: internal/external ─────────────────────────────────────

    @Test
    fun propertyInternalExternalVisibility() {
        val spec = ConfigFormRegistry.forTemplate("Property > New Property")!!
        val internalFields = spec.fields.filter { it.key in setOf("txtKey", "txtValue") }
        val externalFields = spec.fields.filter { it.key in setOf("cboEncoding", "txtPropertyFile") }

        val stateInternal = FormState(mapOf("rdoType" to "Internal Properties"))
        assertTrue(internalFields.all { it.visibleWhen!!.invoke(stateInternal) })
        assertTrue(externalFields.all { !it.visibleWhen!!.invoke(stateInternal) })

        val stateExternal = FormState(mapOf("rdoType" to "External File"))
        assertTrue(internalFields.all { !it.visibleWhen!!.invoke(stateExternal) })
        assertTrue(externalFields.all { it.visibleWhen!!.invoke(stateExternal) })
    }

    @Test
    fun propertyInternalValueAcceptsArbitraryStrings() {
        val spec = ConfigFormRegistry.forTemplate("Property > New Property")!!
        val valueField = spec.fields.single { it.key == "txtValue" }
        val state = FormState(
            mapOf(
                "generationType" to "xml",
                "txtFileName" to "context-properties",
                "txtPropertyServiceName" to "propertiesService",
                "rdoType" to "Internal Properties",
                "txtKey" to "greeting",
                "txtValue" to "hello world",
            ),
        )

        assertFalse(valueField.numeric)
        assertNull(spec.validate(state))
    }

    // ── Scheduling: linked job/trigger updates ──────────────────────────

    @Test
    fun schedulingLinkedJobUpdate() {
        val spec = ConfigFormRegistry.forTemplate("Scheduling > New Simple Trigger")!!
        val link = spec.linkedUpdates.first { it.sourceField == "cboJobDetailType" }

        val state = FormState(mapOf("cboJobDetailType" to "JobDetailFactoryBean", "txtJobName" to "old"))
        link.update(state)
        assertEquals("jobDetail", state.getString("txtJobName"))

        state["cboJobDetailType"] = "MethodInvokingJobDetailFactoryBean"
        link.update(state)
        assertEquals("methodInvokingJobDetail", state.getString("txtJobName"))
    }

    @Test
    fun schedulingLinkedTriggerUpdate() {
        val spec = ConfigFormRegistry.forTemplate("Scheduling > New Scheduler")!!
        val link = spec.linkedUpdates.first { it.sourceField == "cboTriggerType" }

        val state = FormState(mapOf("cboTriggerType" to "SimpleTriggerFactoryBean", "txtTriggerName" to "old"))
        link.update(state)
        assertEquals("simpleTrigger", state.getString("txtTriggerName"))

        state["cboTriggerType"] = "CronTriggerFactoryBean"
        link.update(state)
        assertEquals("cronTrigger", state.getString("txtTriggerName"))
    }

    // ── Transaction: AOP / minimum selection ────────────────────────────

    @Test
    fun transactionMinSelectionValidation() {
        val spec = ConfigFormRegistry.forTemplate("Transaction > New Datasource Transaction")!!
        assertNotNull(spec.validateExtra)

        val stateNone = FormState(mapOf(
            "chkAopConfigTransaction" to false,
            "chkAnnotationTransaction" to false,
        ))
        val issue = spec.validateExtra!!.invoke(stateNone)
        assertNotNull(issue)
        assertTrue(issue!!.message.isNotBlank())
        assertEquals("chkAopConfigTransaction", issue.field)

        val stateAop = FormState(mapOf(
            "chkAopConfigTransaction" to true,
            "chkAnnotationTransaction" to false,
        ))
        assertNull(spec.validateExtra!!.invoke(stateAop))
    }

    // ── Cache: eternal hides TTL / TTI ──────────────────────────────────

    @Test
    fun cacheEternalHidesTTL() {
        val spec = ConfigFormRegistry.forTemplate("Cache > New Cache")!!
        val ttlField = spec.fields.first { it.key == "txtDftLiveTime" }
        assertNotNull(ttlField.visibleWhen)

        assertFalse(ttlField.visibleWhen!!.invoke(FormState(mapOf("txtDftEternal" to "true"))))
        assertTrue(ttlField.visibleWhen!!.invoke(FormState(mapOf("txtDftEternal" to "false"))))
    }

    @Test
    fun cacheEternalHidesTTI() {
        val spec = ConfigFormRegistry.forTemplate("Cache > New Cache")!!
        val ttiField = spec.fields.first { it.key == "txtIdleTime" }
        assertNotNull(ttiField.visibleWhen)

        assertFalse(ttiField.visibleWhen!!.invoke(FormState(mapOf("txtEternal" to "true"))))
        assertTrue(ttiField.visibleWhen!!.invoke(FormState(mapOf("txtEternal" to "false"))))
    }

    // ── Validation: required / numeric / special / package / class / file

    @Test
    fun validationRequiredField() {
        val spec = ConfigFormRegistry.forTemplate("Cache > New Cache")!!
        val state = FormState(mapOf(
            "txtFileName" to "",
            "txtDiskStore" to "store",
            "txtDftEternal" to "false",
            "txtEternal" to "false",
            "txtCacheName" to "c",
        ))
        val issue = spec.validate(state)
        assertNotNull(issue)
        assertEquals("txtFileName", issue!!.field)
        assertTrue(issue.message.isNotBlank())
    }

    @Test
    fun validationNumericField() {
        val spec = ConfigFormRegistry.forTemplate("Cache > New Cache")!!
        val state = FormState(mapOf(
            "txtFileName" to "test",
            "txtDiskStore" to "store",
            "txtDftEternal" to "false",
            "txtDftLiveTime" to "abc",
            "txtEternal" to "false",
            "txtCacheName" to "c",
        ))
        val issue = spec.validate(state)
        assertNotNull(issue)
        assertEquals("txtDftLiveTime", issue!!.field)
        assertTrue(issue.message.isNotBlank())
    }

    @Test
    fun validationSpecialChars() {
        val spec = ConfigFormRegistry.forTemplate("Datasource > New Datasource")!!
        val state = FormState(mapOf(
            "generationType" to "xml",
            "txtFileName" to "test",
            "txtDatasourceName" to "ds<script>",
            "rdoType" to "DBCP",
            "txtDriver" to "driver",
            "txtUrl" to "url",
            "txtUser" to "user",
        ))
        val issue = spec.validate(state)
        assertNotNull(issue)
        assertEquals("txtDatasourceName", issue!!.field)
        assertTrue(issue.message.isNotBlank())
    }

    @Test
    fun validationPackageName() {
        val spec = ConfigFormRegistry.forTemplate("Datasource > New Datasource")!!
        val state = FormState(mapOf(
            "generationType" to "javaConfig",
            "txtConfigPackage" to "123invalid",
            "txtFileName" to "EgovTest",
            "txtDatasourceName" to "ds",
            "rdoType" to "DBCP",
            "txtDriver" to "driver",
            "txtUrl" to "url",
            "txtUser" to "user",
        ))
        val issue = spec.validate(state)
        assertNotNull(issue)
        assertEquals("txtConfigPackage", issue!!.field)
        assertTrue(issue.message.isNotBlank())
    }

    @Test
    fun validationClassName() {
        val spec = ConfigFormRegistry.forTemplate("Datasource > New Datasource")!!
        val state = FormState(mapOf(
            "generationType" to "javaConfig",
            "txtConfigPackage" to "pkg",
            "txtFileName" to "lower-case",
            "txtDatasourceName" to "ds",
            "rdoType" to "DBCP",
            "txtDriver" to "driver",
            "txtUrl" to "url",
            "txtUser" to "user",
        ))
        val issue = spec.validate(state)
        assertNotNull(issue)
        assertEquals("txtFileName", issue!!.field)
        assertTrue(issue.message.isNotBlank())
    }

    @Test
    fun validationFileName() {
        val spec = ConfigFormRegistry.forTemplate("Datasource > New Datasource")!!
        val state = FormState(mapOf(
            "generationType" to "xml",
            "txtFileName" to "file.name",
            "txtDatasourceName" to "ds",
            "rdoType" to "DBCP",
            "txtDriver" to "driver",
            "txtUrl" to "url",
            "txtUser" to "user",
        ))
        val issue = spec.validate(state)
        assertNotNull(issue)
        assertEquals("txtFileName", issue!!.field)
        assertTrue(issue.message.isNotBlank())
    }

    @Test
    fun validationMatchesUpstreamCodeUtilsRegexes() {
        fun issueFor(field: FieldDef, value: String, generationType: String): ConfigGenerator.ValidationIssue? =
            ConfigFormSpec("test", listOf(field), ConfigGenerator.GenerationType.entries).validate(
                FormState(mapOf("generationType" to generationType, field.key to value)),
            )

        val packageField = FieldDef("package", "Package", packageField = true)
        assertNull(issueFor(packageField, "A.b", "xml"))
        assertNotNull(issueFor(packageField, "A.b", "javaConfig"))
        assertNotNull(issueFor(packageField, "a.", "javaConfig"))

        val classField = FieldDef("file", "File", classField = true)
        assertNull(issueFor(classField, "EgovConfig2", "javaConfig"))
        assertNull(issueFor(classField, "EgovConfig.java", "javaConfig"))
        assertNotNull(issueFor(classField, "Egov_Config.java", "javaConfig"))

        val fileField = FieldDef("file", "File", fileNameField = true)
        assertNull(issueFor(fileField, "config-file_2", "xml"))
        assertNull(issueFor(fileField, "config-file.xml", "xml"))
        assertNotNull(issueFor(fileField, "config-file.yaml", "xml"))
        assertNotNull(issueFor(fileField, "config file.xml", "xml"))

        val specialField = FieldDef("name", "Name", noSpecialChars = true)
        assertNull(issueFor(specialField, "name-2_value", "xml"))
        assertNotNull(issueFor(specialField, "name.value", "xml"))
        assertNotNull(issueFor(specialField, "name value", "xml"))
    }

    @Test
    fun cacheAndTransactionsRetainSingleScrollFieldsAndConditions() {
        val cache = ConfigFormRegistry.forTemplate("Cache > New Cache")!!
        assertEquals(
            setOf("txtDftLiveTime", "txtIdleTime"),
            cache.fields.filter { it.visibleWhen != null }.map { it.key }.toSet(),
        )

        assertEquals(
            setOf("txtFileName", "txtDiskStore", "txtDftEternal", "txtCacheName", "txtEternal"),
            cache.fields.filter { it.required || it.requiredWhen != null }.map { it.key }.toSet(),
        )

        for (name in listOf(
            "Transaction > New Datasource Transaction",
            "Transaction > New JPA Transaction",
            "Transaction > New JTA Transaction",
        )) {
            val spec = ConfigFormRegistry.forTemplate(name)!!
            assertTrue(spec.fields.any { it.key == "chkAopConfigTransaction" })
            assertTrue(spec.fields.any { it.key == "chkAnnotationTransaction" })
            assertEquals(
                setOf(
                    "txtConfigPackage",
                    "txtPointCutName", "txtPointCutExpression", "txtAdviceName", "txtMethodName",
                    "chkReadOnly", "txtRollbackFor", "txtNoRollbackFor", "txtTimeout",
                    "cmbPropagation", "cmbIsolation",
                ),
                spec.fields.filter { it.visibleWhen != null }.map { it.key }.toSet(),
            )
            val requiredBase = when (name) {
                "Transaction > New Datasource Transaction" -> setOf(
                    "txtFileName", "txtConfigPackage", "txtTransactionName", "txtDataSourceName",
                )
                "Transaction > New JPA Transaction" -> setOf(
                    "txtFileName", "txtConfigPackage", "txtTransactionName", "txtEntityManagerFactory",
                    "txtDataSourceName", "txtPackagesToScan", "cmbDialectName", "txtSpringDataJpaRepositoriesPackage",
                )
                else -> setOf(
                    "txtFileName", "txtConfigPackage", "txtTransactionName", "txtGlobalTimeout",
                )
            }
            assertEquals(
                requiredBase + setOf(
                    "txtPointCutName", "txtPointCutExpression", "txtAdviceName", "txtMethodName",
                    "cmbPropagation", "cmbIsolation",
                ),
                spec.fields.filter { it.required || it.requiredWhen != null }.map { it.key }.toSet(),
            )
        }
    }

    // ── Ehcache resource path normalization ─────────────────────────────

    @Test
    fun ehcacheResourcePathNormalization() {
        assertEquals(
            "egovframework/spring/cache/ehcache.xml",
            normalizeConfigLocation("C:\\project\\src\\main\\resources\\egovframework\\spring\\cache\\ehcache.xml"),
        )
        assertEquals(
            "egovframework/spring/cache/ehcache.xml",
            normalizeConfigLocation("src/main/resources/egovframework/spring/cache/ehcache.xml"),
        )
        assertEquals("plain/path.xml", normalizeConfigLocation("plain/path.xml"))
    }

    // ── Four Java defaults ──────────────────────────────────────────────

    @Test
    fun fourJavaDefaults() {
        assertEquals(
            "EgovEhcacheSpringConfig",
            ConfigFormRegistry.forTemplate("Cache > New Ehcache Configuration")!!.javaDefaultFileName,
        )
        assertEquals(
            "EgovDataSourceConfig",
            ConfigFormRegistry.forTemplate("Datasource > New Datasource")!!.javaDefaultFileName,
        )
        assertEquals(
            "EgovPropertiesConfig",
            ConfigFormRegistry.forTemplate("Property > New Property")!!.javaDefaultFileName,
        )
        assertEquals(
            "EgovTransactionConfig",
            ConfigFormRegistry.forTemplate("Transaction > New Datasource Transaction")!!.javaDefaultFileName,
        )
    }
}
