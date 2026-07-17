package kr.kyg.ijplugin.egovframe.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
        assertTrue(PluginMetadata.VERSION.isNotBlank())
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
}
