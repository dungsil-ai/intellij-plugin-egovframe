package kr.kyg.ijplugin.egovframe.config

import com.intellij.ui.components.JBTextField
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.EventQueue
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JList

class ConfigFormDialogInteractionTest {

    @Test
    fun selectControlChangeUpdatesCacheVisibility() {
        val control = JComboBox(arrayOf("false", "true"))
        val ttl = ConfigFormRegistry.forTemplate("Cache > New Cache")!!
            .fields.first { it.key == "txtDftLiveTime" }
        var ttlVisible = true

        ConfigFormControlListener.install(control) {
            ttlVisible = ttl.visibleWhen!!.invoke(FormState(mapOf("txtDftEternal" to control.selectedItem)))
        }

        control.selectedItem = "true"

        assertFalse(ttlVisible)
    }

    @Test
    fun selectControlDisplaysOptionLabelWhileKeepingStoredValue() {
        val field = FieldDef(
            key = "cmbDialectName",
            label = "Dialect Name",
            control = ControlType.SELECT,
            options = listOf(SelectOption("org.hibernate.dialect.H2Dialect", "H2")),
        )

        val control = createSelectControl(field, null) { }
        val rendered = control.renderer.getListCellRendererComponent(
            JList(),
            control.getItemAt(0),
            0,
            false,
            false,
        ) as JLabel

        assertEquals("org.hibernate.dialect.H2Dialect", control.selectedItem)
        assertEquals("H2", rendered.text)
    }

    @Test
    fun selectControlChangeAppliesSchedulingLinkedUpdate() {
        val control = JComboBox(arrayOf("JobDetailFactoryBean", "MethodInvokingJobDetailFactoryBean"))
        val jobName = JBTextField()
        val link = ConfigFormRegistry.forTemplate("Scheduling > New Simple Trigger")!!
            .linkedUpdates.single()

        ConfigFormControlListener.install(control) {
            val state = FormState(mapOf("cboJobDetailType" to control.selectedItem, "txtJobName" to jobName.text))
            link.update(state)
            jobName.text = state.getString("txtJobName")
        }

        control.selectedItem = "MethodInvokingJobDetailFactoryBean"

        assertEquals("methodInvokingJobDetail", jobName.text)
    }

    @Test
    fun textAndFileTextControlsNotifyOnTypedChanges() {
        val text = JBTextField()
        val fileText = JBTextField()
        var textChanges = 0
        var fileChanges = 0
        ConfigFormControlListener.install(text) { textChanges++ }
        ConfigFormControlListener.install(fileText) { fileChanges++ }

        text.text = "changed"
        fileText.text = "src/main/resources/ehcache.xml"
        EventQueue.invokeAndWait { }

        assertTrue(textChanges > 0)
        assertTrue(fileChanges > 0)
    }

    @Test
    fun invalidOutputPathIsValidationInfoInsteadOfException() {
        val field = JBTextField()

        val validation = validateOutputFolderPath("\u0000", field)

        assertNotNull(validation)
        assertEquals(field, validation!!.component)
    }
}
