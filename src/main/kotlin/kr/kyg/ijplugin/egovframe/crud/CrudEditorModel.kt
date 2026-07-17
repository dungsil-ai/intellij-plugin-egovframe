package kr.kyg.ijplugin.egovframe.crud

import kr.kyg.ijplugin.egovframe.ddl.DdlSyntaxDiagnostics

/**
 * Observable model for the CRUD SQL editor.
 * Manages dialect switching, sample filtering/selection, direct input, and
 * debounce/manual preview/auto-preview state transitions.
 * Pure Kotlin — no IntelliJ platform dependency.
 */
internal class CrudEditorModel(
  initialDialect: SqlDialect = SqlDialect.MYSQL,
) {
  var dialect: SqlDialect = initialDialect
    private set

  var selectedSample: CrudSampleCatalog.Sample? = null
    private set

  var sqlText: String = ""
    private set

  var diagnosticResult: DdlSyntaxDiagnostics.DiagnosticResult? = null
    private set

  var autoPreview: Boolean = false

  private var pendingInput = false

  private val listeners = mutableListOf<() -> Unit>()

  fun addChangeListener(listener: () -> Unit) {
    listeners.add(listener)
  }

  fun switchDialect(newDialect: SqlDialect) {
    if (dialect == newDialect) return
    val hadSelectedSample = selectedSample != null
    dialect = newDialect
    selectedSample = null
    if (hadSelectedSample) {
      sqlText = ""
      pendingInput = false
    }
    updateDiagnostics()
    fireChanged()
  }

  fun selectSample(sample: CrudSampleCatalog.Sample?) {
    if (sample != null) {
      require(sample.dialect == dialect) { "Sample dialect ${sample.dialect} does not match current dialect $dialect" }
    }
    selectedSample = sample
    if (sample != null) {
      sqlText = sample.ddl
    }
    pendingInput = false
    updateDiagnostics()
    fireChanged()
  }

  fun clearSample() {
    selectedSample = null
    sqlText = ""
    pendingInput = false
    updateDiagnostics()
    fireChanged()
  }

  fun setSqlText(text: String) {
    sqlText = text
    if (selectedSample != null && text != selectedSample!!.ddl) {
      selectedSample = null
    }
    pendingInput = true
    fireChanged()
  }

  fun markInputSettled() {
    if (pendingInput) {
      pendingInput = false
      updateDiagnostics()
      fireChanged()
    }
  }

  fun requestPreview() {
    updateDiagnostics()
    pendingInput = false
    fireChanged()
  }

  val isPendingInput: Boolean get() = pendingInput

  val availableSamples: List<CrudSampleCatalog.Sample> get() = CrudSampleCatalog.forDialect(dialect)

  private fun updateDiagnostics() {
    diagnosticResult = if (sqlText.isBlank()) null else DdlSyntaxDiagnostics.diagnose(sqlText, dialect)
  }

  private fun fireChanged() {
    listeners.forEach { it() }
  }
}
