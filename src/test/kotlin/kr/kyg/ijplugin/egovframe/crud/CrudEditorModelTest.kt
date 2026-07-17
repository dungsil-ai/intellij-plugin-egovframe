package kr.kyg.ijplugin.egovframe.crud

import kr.kyg.ijplugin.egovframe.ddl.DdlSyntaxDiagnostics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CrudEditorModelTest {

  @Test
  fun `initial state uses MYSQL dialect with no sample and empty text`() {
    val model = CrudEditorModel()
    assertEquals(SqlDialect.MYSQL, model.dialect)
    assertNull(model.selectedSample)
    assertEquals("", model.sqlText)
    assertNull(model.diagnosticResult)
    assertFalse(model.isPendingInput)
  }

  @Test
  fun `switchDialect changes dialect and clears sample`() {
    val model = CrudEditorModel()
    val mysqlSample = CrudSampleCatalog.forDialect(SqlDialect.MYSQL).first()
    model.selectSample(mysqlSample)
    assertEquals(mysqlSample, model.selectedSample)

    model.switchDialect(SqlDialect.POSTGRESQL)
    assertEquals(SqlDialect.POSTGRESQL, model.dialect)
    assertNull(model.selectedSample)
    assertEquals("", model.sqlText)
    assertNull(model.diagnosticResult)
  }

  @Test
  fun `switchDialect to same dialect is a no-op`() {
    val model = CrudEditorModel()
    var changeCount = 0
    model.addChangeListener { changeCount++ }

    model.switchDialect(SqlDialect.MYSQL)
    assertEquals(0, changeCount)
  }

  @Test
  fun `selectSample sets sqlText and clears pendingInput`() {
    val model = CrudEditorModel()
    val sample = CrudSampleCatalog.forDialect(SqlDialect.MYSQL).first()

    model.selectSample(sample)
    assertEquals(sample, model.selectedSample)
    assertEquals(sample.ddl, model.sqlText)
    assertFalse(model.isPendingInput)
  }

  @Test
  fun `clearSample resets to empty state`() {
    val model = CrudEditorModel()
    val sample = CrudSampleCatalog.forDialect(SqlDialect.MYSQL).first()
    model.selectSample(sample)

    model.clearSample()
    assertNull(model.selectedSample)
    assertEquals("", model.sqlText)
    assertFalse(model.isPendingInput)
  }

  @Test
  fun `setSqlText marks pending and clears sample when text diverges`() {
    val model = CrudEditorModel()
    val sample = CrudSampleCatalog.forDialect(SqlDialect.MYSQL).first()
    model.selectSample(sample)

    model.setSqlText("modified SQL")
    assertTrue(model.isPendingInput)
    assertNull(model.selectedSample)
    assertEquals("modified SQL", model.sqlText)
  }

  @Test
  fun `setSqlText keeps sample when text matches sample ddl`() {
    val model = CrudEditorModel()
    val sample = CrudSampleCatalog.forDialect(SqlDialect.MYSQL).first()
    model.selectSample(sample)

    model.setSqlText(sample.ddl)
    assertEquals(sample, model.selectedSample)
  }

  @Test
  fun `markInputSettled updates diagnostics and clears pending`() {
    val model = CrudEditorModel()
    model.setSqlText("CREATE TABLE t (id INT PRIMARY KEY);")
    assertTrue(model.isPendingInput)

    model.markInputSettled()
    assertFalse(model.isPendingInput)
    assertNotNull(model.diagnosticResult)
    assertTrue(model.diagnosticResult is DdlSyntaxDiagnostics.DiagnosticResult.Ok)
  }

  @Test
  fun `markInputSettled with invalid SQL produces error diagnostics`() {
    val model = CrudEditorModel()
    model.setSqlText("INVALID SQL")
    model.markInputSettled()

    assertTrue(model.diagnosticResult is DdlSyntaxDiagnostics.DiagnosticResult.Error)
  }

  @Test
  fun `requestPreview updates diagnostics and clears pending`() {
    val model = CrudEditorModel()
    model.setSqlText("CREATE TABLE t (id INT PRIMARY KEY);")
    assertTrue(model.isPendingInput)

    model.requestPreview()
    assertFalse(model.isPendingInput)
    assertNotNull(model.diagnosticResult)
  }

  @Test
  fun `change listener is called on state transitions`() {
    val model = CrudEditorModel()
    var changeCount = 0
    model.addChangeListener { changeCount++ }

    model.setSqlText("test")
    assertEquals(1, changeCount)

    model.markInputSettled()
    assertEquals(2, changeCount)

    model.clearSample()
    assertEquals(3, changeCount)
  }

  @Test
  fun `availableSamples returns samples for current dialect`() {
    val model = CrudEditorModel()
    val mysqlSamples = model.availableSamples
    assertTrue(mysqlSamples.all { it.dialect == SqlDialect.MYSQL })
    assertTrue(mysqlSamples.isNotEmpty())

    model.switchDialect(SqlDialect.POSTGRESQL)
    val pgsqlSamples = model.availableSamples
    assertTrue(pgsqlSamples.all { it.dialect == SqlDialect.POSTGRESQL })
    assertTrue(pgsqlSamples.isNotEmpty())

    model.switchDialect(SqlDialect.GENERIC)
    assertTrue(model.availableSamples.isEmpty())
  }

  @Test
  fun `sample catalog has exactly 10 entries across MySQL and PostgreSQL`() {
    val all = CrudSampleCatalog.all()
    assertEquals(10, all.size)
    assertEquals(5, CrudSampleCatalog.forDialect(SqlDialect.MYSQL).size)
    assertEquals(5, CrudSampleCatalog.forDialect(SqlDialect.POSTGRESQL).size)
    assertEquals(0, CrudSampleCatalog.forDialect(SqlDialect.GENERIC).size)
  }

  @Test
  fun `sample catalog find returns correct sample by key`() {
    val board = CrudSampleCatalog.find("board-mysql")
    assertNotNull(board)
    assertEquals("Board Table", board!!.name)
    assertEquals(SqlDialect.MYSQL, board.dialect)
    assertTrue(board.ddl.contains("CREATE TABLE board"))
  }

  @Test
  fun `all samples have non-blank DDL content`() {
    CrudSampleCatalog.all().forEach { sample ->
      assertTrue(sample.ddl.isNotBlank(), "Sample ${sample.key} should have non-blank DDL")
      assertTrue(sample.ddl.contains("CREATE TABLE"), "Sample ${sample.key} should contain CREATE TABLE")
    }
  }

  @Test
  fun `all samples parse successfully with DdlAnalyzer`() {
    CrudSampleCatalog.all().forEach { sample ->
      val result = kr.kyg.ijplugin.egovframe.ddl.DdlAnalyzer.analyze(sample.ddl)
      assertTrue(
        result is kr.kyg.ijplugin.egovframe.ddl.DdlAnalysisResult.Success,
        "Sample ${sample.key} should parse successfully but got: $result",
      )
    }
  }

  @Test
  fun `diagnostics are null for blank input`() {
    val model = CrudEditorModel()
    model.setSqlText("   ")
    model.markInputSettled()
    assertNull(model.diagnosticResult)
  }

  @Test
  fun `dialect transition preserves direct input text`() {
    val model = CrudEditorModel()
    model.setSqlText("CREATE TABLE custom (id INT PRIMARY KEY);")
    val text = model.sqlText

    model.switchDialect(SqlDialect.POSTGRESQL)
    assertEquals(text, model.sqlText, "Direct input text should be preserved across dialect switch")
  }

  @Test
  fun `prepare gate refreshes diagnostics before debounce settles`() {
    val model = CrudEditorModel()
    model.setSqlText("CREATE TABLE (id INT PRIMARY KEY);")
    assertTrue(model.isPendingInput)

    val result = prepareCrudInput(model, CrudGeneration(), "egovframework.example")

    assertTrue(result is CrudPreparation.Rejected)
    assertTrue((result as CrudPreparation.Rejected).message.contains("line 1"))
    assertFalse(model.isPendingInput)
  }
}
