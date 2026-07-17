package kr.kyg.ijplugin.egovframe.settings

import kr.kyg.ijplugin.egovframe.config.ConfigFormRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.Locale
import java.util.ResourceBundle

class SettingsModuleTest {

    /** The root bundle (no locale suffix) carries English translations. */
    private val enBundle: ResourceBundle = ResourceBundle.getBundle("messages.EgovBundle", Locale.ROOT)
    private val koBundle: ResourceBundle = EgovBundle.bundleFor("ko")

    // ── 1. Bundle parity ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("en and ko bundles have the same set of keys")
    fun bundleParityKeys() {
        val enKeys = enBundle.keySet()
        val koKeys = koBundle.keySet()

        assertEquals(enKeys, koKeys, "en and ko bundles must have identical key sets")
    }

    @Test
    @DisplayName("every typed config field has en and ko labels")
    fun everyConfigFieldHasLocalizedLabel() {
        ConfigFormRegistry.all()
            .flatMap { it.fields }
            .map { "config.field.${it.key}" }
            .distinct()
            .forEach { key ->
                assertTrue(enBundle.containsKey(key), "Missing English config field key: $key")
                assertTrue(koBundle.containsKey(key), "Missing Korean config field key: $key")
            }
    }

    @Test
    @DisplayName("new wizard and CRUD surfaces have localized keys")
    fun extendedUiKeysAreComplete() {
        val keys = listOf(
            "wizard.progress.title",
            "wizard.progress.resolveTemplate",
            "wizard.progress.extract",
            "wizard.progress.writePom",
            "wizard.progress.linkMaven",
            "wizard.progress.configureJdk",
            "wizard.progress.complete",
            "crud.label.dialect",
            "crud.label.sample",
            "crud.sample.directInput",
            "crud.notification.renderedMany",
            "config.validation.transactionSelection",
        )
        keys.forEach { key ->
            assertTrue(enBundle.containsKey(key), "Missing English UI key: $key")
            assertTrue(koBundle.containsKey(key), "Missing Korean UI key: $key")
        }
    }

    // ── 2. Bundle completeness ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("bundles contain all expected important keys")
    fun bundleCompletenessSpotCheck() {
        val expectedKeys = listOf(
            "plugin.name",
            "settings.display.name",
            "settings.label.language",
            "settings.label.groupId",
            "settings.label.artifactId",
            "settings.label.package",
            "settings.validation.groupId.blank",
            "settings.validation.groupId.invalid",
            "settings.validation.artifactId.blank",
            "settings.validation.artifactId.invalid",
            "settings.validation.package.blank",
            "settings.validation.package.invalid",
            "action.requires.project",
            "wizard.name",
            "wizard.project.created",
            "about.title",
            "about.version",
            "about.requires.project",
        )
        expectedKeys.forEach { key ->
            assertTrue(enBundle.containsKey(key), "Missing expected key: $key")
        }
    }

    // ── 3. SettingsValidator ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("valid defaults pass validation")
    fun validatorDefaultsPass() {
        val state = EgovSettings.SettingsState()
        val result = SettingsValidator.validate(state)

        assertTrue(result.isValid, "Default SettingsState should pass validation")
        assertTrue(result.errors.isEmpty())
    }

    @Test
    @DisplayName("blank groupId fails validation")
    fun validatorBlankGroupId() {
        val state = EgovSettings.SettingsState(defaultGroupId = "  ")
        val result = SettingsValidator.validate(state)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.field == "defaultGroupId" && it.messageKey.contains("blank") })
    }

    @Test
    @DisplayName("blank artifactId fails validation")
    fun validatorBlankArtifactId() {
        val state = EgovSettings.SettingsState(defaultArtifactId = "")
        val result = SettingsValidator.validate(state)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.field == "defaultArtifactId" && it.messageKey.contains("blank") })
    }

    @Test
    @DisplayName("blank package fails validation")
    fun validatorBlankPackage() {
        val state = EgovSettings.SettingsState(defaultPackageName = "")
        val result = SettingsValidator.validate(state)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.field == "defaultPackageName" && it.messageKey.contains("blank") })
    }

    @Test
    @DisplayName("invalid groupId format fails validation")
    fun validatorInvalidGroupIdFormat() {
        val state = EgovSettings.SettingsState(defaultGroupId = "COM.Example")
        val result = SettingsValidator.validate(state)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.field == "defaultGroupId" && it.messageKey.contains("invalid") })
    }

    @Test
    @DisplayName("invalid artifactId format fails validation")
    fun validatorInvalidArtifactIdFormat() {
        val state = EgovSettings.SettingsState(defaultArtifactId = "MyApp_1")
        val result = SettingsValidator.validate(state)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.field == "defaultArtifactId" && it.messageKey.contains("invalid") })
    }

    @Test
    @DisplayName("invalid package format fails validation")
    fun validatorInvalidPackageFormat() {
        val state = EgovSettings.SettingsState(defaultPackageName = "Com.Example.APP")
        val result = SettingsValidator.validate(state)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.field == "defaultPackageName" && it.messageKey.contains("invalid") })
    }

    @Test
    @DisplayName("multiple validation errors accumulate")
    fun validatorMultipleErrors() {
        val state = EgovSettings.SettingsState(
            defaultGroupId = "",
            defaultArtifactId = "",
            defaultPackageName = "",
        )
        val result = SettingsValidator.validate(state)

        assertFalse(result.isValid)
        assertEquals(3, result.errors.size, "All three blank fields should produce errors")
        val fields = result.errors.map { it.field }.toSet()
        assertTrue(fields.contains("defaultGroupId"))
        assertTrue(fields.contains("defaultArtifactId"))
        assertTrue(fields.contains("defaultPackageName"))
    }

    // ── 4. PluginMetadata ───────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PluginMetadata constants are non-blank")
    fun metadataConstantsNonBlank() {
        assertTrue(PluginMetadata.ID.isNotBlank())
        assertTrue(PluginMetadata.NAME.isNotBlank())
        assertTrue(PluginMetadata.version().isNotBlank())
        assertTrue(PluginMetadata.DESCRIPTION_EN.isNotBlank())
        assertTrue(PluginMetadata.DESCRIPTION_KO.isNotBlank())
        assertTrue(PluginMetadata.REPOSITORY.isNotBlank())
        assertTrue(PluginMetadata.HOMEPAGE.isNotBlank())
        assertTrue(PluginMetadata.GUIDE.isNotBlank())
        assertTrue(PluginMetadata.AUTHOR.isNotBlank())
        assertTrue(PluginMetadata.LICENSE.isNotBlank())
        assertTrue(PluginMetadata.LICENSE_URL.isNotBlank())
    }

    @Test
    @DisplayName("description(en) returns DESCRIPTION_EN")
    fun metadataDescriptionEn() {
        assertEquals(PluginMetadata.DESCRIPTION_EN, PluginMetadata.description("en"))
    }

    @Test
    @DisplayName("description(ko) returns DESCRIPTION_KO")
    fun metadataDescriptionKo() {
        assertEquals(PluginMetadata.DESCRIPTION_KO, PluginMetadata.description("ko"))
    }

    @Test
    @DisplayName("metadata includes projectless policy text")
    fun metadataProjectlessPolicy() {
        assertTrue(
            PluginMetadata.DESCRIPTION_EN.contains("Requires an open project"),
            "EN description should mention project requirement",
        )
        assertTrue(
            PluginMetadata.DESCRIPTION_KO.contains("열린 프로젝트가 필요합니다"),
            "KO description should mention project requirement",
        )
    }

    // ── 5. ActionAvailabilityPolicy ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("isConfigCrudAvailable(null) returns false")
    fun policyNullProjectUnavailable() {
        assertFalse(ActionAvailabilityPolicy.isConfigCrudAvailable(null))
    }

    @Test
    @DisplayName("disabledReason() is non-blank")
    fun policyDisabledReasonNonBlank() {
        val reason = ActionAvailabilityPolicy.disabledReason()
        assertNotNull(reason)
        assertTrue(reason.isNotBlank(), "disabledReason() should return a non-blank message")
    }

    @Test
    @DisplayName("available action restores its default description")
    fun policyRestoresDefaultDescription() {
        val defaultDescription = "Open the eGovFrame tool window"

        assertEquals(defaultDescription, ActionAvailabilityPolicy.descriptionFor(true, defaultDescription))
        assertNull(ActionAvailabilityPolicy.descriptionFor(true, null))
        assertEquals(ActionAvailabilityPolicy.disabledReason(), ActionAvailabilityPolicy.descriptionFor(false, defaultDescription))
    }

    @Test
    @DisplayName("English bundle spells Ciphers correctly")
    fun englishCipherLabelIsCorrect() {
        assertEquals("Ciphers", enBundle.getString("config.field.txtCipers"))
    }

    // ── 6. EgovSettings defaults ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DEFAULT_* constants match expected values")
    fun settingsDefaultConstants() {
        assertEquals("egovframework.com", EgovSettings.DEFAULT_GROUP_ID)
        assertEquals("egovframe-project", EgovSettings.DEFAULT_ARTIFACT_ID)
        assertEquals("egovframework.example.sample", EgovSettings.DEFAULT_PACKAGE_NAME)
        assertEquals("en", EgovSettings.DEFAULT_LANGUAGE)
    }

    @Test
    @DisplayName("SettingsState default construction has correct values")
    fun settingsStateDefaultConstruction() {
        val state = EgovSettings.SettingsState()

        assertEquals(EgovSettings.DEFAULT_GROUP_ID, state.defaultGroupId)
        assertEquals(EgovSettings.DEFAULT_ARTIFACT_ID, state.defaultArtifactId)
        assertEquals(EgovSettings.DEFAULT_PACKAGE_NAME, state.defaultPackageName)
        assertEquals(EgovSettings.DEFAULT_LANGUAGE, state.language)
    }

    // ── 7. Bundle resolver ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("bundleFor(en) and bundleFor(ko) return different values for translated keys")
    fun bundleResolverDifferentLocales() {
        val translatedKeys = listOf(
            "settings.label.language",
            "action.requires.project",
            "wizard.description",
            "wizard.progress.title",
            "crud.label.sample",
            "crud.sample.directInput",
            "config.field.txtFileName",
            "about.title",
        )
        translatedKeys.forEach { key ->
            assertNotEquals(
                enBundle.getString(key),
                koBundle.getString(key),
                "Key '$key' should differ between en and ko bundles",
            )
        }
    }

    @Test
    @DisplayName("bundleFor(en) returns English values")
    fun bundleResolverEnglishValues() {
        assertEquals("Language", enBundle.getString("settings.label.language"))
        assertEquals("eGovFrame Initializr", enBundle.getString("plugin.name"))
    }

    @Test
    @DisplayName("message() with args substitutes correctly")
    fun bundleMessageWithArgsSubstitution() {
        val pattern = enBundle.getString("wizard.project.created")
        assertTrue(pattern.contains("{0}"), "Pattern should contain {0} placeholder")

        val result = pattern.replace("{0}", "my-project")
        assertEquals("eGovFrame project created: my-project", result)
    }

    @Test
    @DisplayName("message() with multiple args substitutes all placeholders")
    fun bundleMessageWithMultipleArgs() {
        val pattern = enBundle.getString("crud.status.valid")
        assertTrue(pattern.contains("{0}") && pattern.contains("{1}"), "Pattern should contain {0} and {1}")

        val result = listOf("USERS", "5").foldIndexed(pattern) { i, acc, arg -> acc.replace("{$i}", arg) }
        assertEquals("Valid: USERS (5 columns)", result)
    }

    // ── 8. About action content ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("buildAboutText contains version, author, license, repository, homepage, guide, and project boundary")
    fun aboutTextContainsAllMetadata() {
        val text = AboutEgovAction.buildAboutText()
        assertNotEquals("dev", PluginMetadata.version(), "Packaged version resource must be resolved")
        assertTrue(text.contains(PluginMetadata.version()), "About text should contain version")
        assertTrue(text.contains(PluginMetadata.AUTHOR), "About text should contain author")
        assertTrue(text.contains(PluginMetadata.LICENSE), "About text should contain license")
        assertTrue(text.contains(PluginMetadata.REPOSITORY), "About text should contain repository")
        assertTrue(text.contains(PluginMetadata.HOMEPAGE), "About text should contain homepage")
        assertTrue(text.contains(PluginMetadata.GUIDE), "About text should contain guide")
        // projectless boundary
        assertTrue(
            text.contains("open project") || text.contains("프로젝트"),
            "About text should mention project-required boundary",
        )
    }

    @Test
    @DisplayName("buildAboutText contains localized description")
    fun aboutTextContainsDescription() {
        val text = AboutEgovAction.buildAboutText()
        // The description line comes from the bundle (en or ko depending on settings fallback)
        val enDesc = enBundle.getString("about.description")
        val koDesc = koBundle.getString("about.description")
        assertTrue(
            text.contains(enDesc) || text.contains(koDesc),
            "About text should contain the localized description",
        )
    }

    // ── 9. plugin.xml metadata ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("plugin.xml description contains repository, license, guide, and project boundary in both languages")
    fun pluginXmlDescriptionMetadata() {
        val xml = SettingsModuleTest::class.java.getResource("/META-INF/plugin.xml")!!.readText()
        // Repository
        assertTrue(xml.contains(PluginMetadata.REPOSITORY), "plugin.xml should contain repository URL")
        // License
        assertTrue(xml.contains("Apache-2.0"), "plugin.xml should mention Apache-2.0 license")
        assertTrue(xml.contains(PluginMetadata.LICENSE_URL), "plugin.xml should contain license URL")
        // Guide
        assertTrue(xml.contains(PluginMetadata.GUIDE), "plugin.xml should contain guide URL")
        // EN project boundary
        assertTrue(
            xml.contains("Config/CRUD generation requires an open project") ||
                xml.contains("Requires an open project"),
            "plugin.xml should mention project requirement in English",
        )
        // KO project boundary
        assertTrue(
            xml.contains("열린 프로젝트가 필요합니다"),
            "plugin.xml should mention project requirement in Korean",
        )
    }

    @Test
    @DisplayName("plugin.xml registers About action in HelpMenu")
    fun pluginXmlAboutActionRegistered() {
        val xml = SettingsModuleTest::class.java.getResource("/META-INF/plugin.xml")!!.readText()
        assertTrue(xml.contains("kr.kyg.ijplugin.egovframe.about"), "plugin.xml should register About action id")
        assertTrue(xml.contains("AboutEgovAction"), "plugin.xml should reference AboutEgovAction class")
        assertTrue(xml.contains("HelpMenu"), "About action should be in HelpMenu")
    }
}
