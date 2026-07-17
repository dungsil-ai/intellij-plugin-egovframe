package kr.kyg.ijplugin.egovframe.ddl

import kr.kyg.ijplugin.egovframe.crud.SqlDialect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DdlSyntaxDiagnosticsTest {

  @Test
  fun `valid single CREATE TABLE returns Ok`() {
    val result = DdlSyntaxDiagnostics.diagnose(
      "CREATE TABLE users (id INT PRIMARY KEY);",
      SqlDialect.MYSQL,
    )
    assertTrue(result is DdlSyntaxDiagnostics.DiagnosticResult.Ok)
  }

  @Test
  fun `empty input returns error at line 1 column 1`() {
    val result = DdlSyntaxDiagnostics.diagnose("", SqlDialect.MYSQL)
    val error = result as DdlSyntaxDiagnostics.DiagnosticResult.Error
    assertEquals(1, error.diagnostics.size)
    assertEquals("Empty input", error.diagnostics[0].message)
    assertEquals(1, error.diagnostics[0].line)
    assertEquals(1, error.diagnostics[0].column)
  }

  @Test
  fun `unbalanced parenthesis reports correct line and column`() {
    val sql = "CREATE TABLE t (\n  id INT\n"
    val result = DdlSyntaxDiagnostics.diagnose(sql, SqlDialect.MYSQL)
    val error = result as DdlSyntaxDiagnostics.DiagnosticResult.Error
    val diag = error.diagnostics.first()
    assertEquals("Unclosed parenthesis", diag.message)
  }

  @Test
  fun `unmatched closing paren reports correct position`() {
    val sql = "CREATE TABLE t (id INT))"
    val result = DdlSyntaxDiagnostics.diagnose(sql, SqlDialect.MYSQL)
    val error = result as DdlSyntaxDiagnostics.DiagnosticResult.Error
    val diag = error.diagnostics.first()
    assertEquals("Unmatched closing parenthesis", diag.message)
    assertEquals(1, diag.line)
    assertEquals(24, diag.column)
  }

  @Test
  fun `unterminated string literal is detected`() {
    val sql = "CREATE TABLE t (id INT DEFAULT 'hello);"
    val result = DdlSyntaxDiagnostics.diagnose(sql, SqlDialect.MYSQL)
    val error = result as DdlSyntaxDiagnostics.DiagnosticResult.Error
    assertEquals("Unterminated string literal", error.diagnostics.first().message)
  }

  @Test
  fun `non-CREATE statement is rejected`() {
    val sql = "SELECT * FROM users;"
    val result = DdlSyntaxDiagnostics.diagnose(sql, SqlDialect.MYSQL)
    val error = result as DdlSyntaxDiagnostics.DiagnosticResult.Error
    assertEquals("Unexpected statement; expected CREATE TABLE", error.diagnostics.first().message)
    assertEquals(1, error.diagnostics.first().line)
    assertEquals(1, error.diagnostics.first().column)
  }

  @Test
  fun `CREATE without TABLE is rejected`() {
    val sql = "CREATE INDEX idx ON users(id);"
    val result = DdlSyntaxDiagnostics.diagnose(sql, SqlDialect.MYSQL)
    val error = result as DdlSyntaxDiagnostics.DiagnosticResult.Error
    assertEquals("Expected TABLE after CREATE", error.diagnostics.first().message)
  }

  @Test
  fun `PostgreSQL COMMENT ON TABLE is allowed after CREATE TABLE`() {
    val sql = """
      CREATE TABLE board (
        id VARCHAR(36) PRIMARY KEY,
        title VARCHAR(200) NOT NULL
      );
      COMMENT ON TABLE board IS 'Board Table';
      COMMENT ON COLUMN board.id IS 'Board ID';
    """.trimIndent()
    val result = DdlSyntaxDiagnostics.diagnose(sql, SqlDialect.POSTGRESQL)
    assertTrue(result is DdlSyntaxDiagnostics.DiagnosticResult.Ok, "PostgreSQL COMMENT ON should be valid")
  }

  @Test
  fun `COMMENT ON is rejected for MySQL dialect`() {
    val sql = """
      CREATE TABLE board (id INT PRIMARY KEY);
      COMMENT ON TABLE board IS 'Board Table';
    """.trimIndent()
    val result = DdlSyntaxDiagnostics.diagnose(sql, SqlDialect.MYSQL)
    val error = result as DdlSyntaxDiagnostics.DiagnosticResult.Error
    assertEquals("Unexpected statement; expected CREATE TABLE", error.diagnostics.first().message)
  }

  @Test
  fun `COMMENT ON is rejected for Generic dialect`() {
    val sql = """
      CREATE TABLE board (id INT PRIMARY KEY);
      COMMENT ON TABLE board IS 'Board Table';
    """.trimIndent()
    val result = DdlSyntaxDiagnostics.diagnose(sql, SqlDialect.GENERIC)
    val error = result as DdlSyntaxDiagnostics.DiagnosticResult.Error
    assertEquals("Unexpected statement; expected CREATE TABLE", error.diagnostics.first().message)
  }

  @Test
  fun `arbitrary trailing SQL after CREATE TABLE is rejected`() {
    val sql = """
      CREATE TABLE board (id INT PRIMARY KEY);
      DROP TABLE board;
    """.trimIndent()
    val result = DdlSyntaxDiagnostics.diagnose(sql, SqlDialect.POSTGRESQL)
    val error = result as DdlSyntaxDiagnostics.DiagnosticResult.Error
    assertEquals("Unexpected statement; expected CREATE TABLE", error.diagnostics.first().message)
  }

  @Test
  fun `multiple CREATE TABLEs are valid`() {
    val sql = """
      CREATE TABLE users (id INT PRIMARY KEY);
      CREATE TABLE orders (id INT PRIMARY KEY, user_id INT);
    """.trimIndent()
    val result = DdlSyntaxDiagnostics.diagnose(sql, SqlDialect.MYSQL)
    assertTrue(result is DdlSyntaxDiagnostics.DiagnosticResult.Ok)
  }

  @Test
  fun `multiline DDL reports correct line and column`() {
    val sql = "CREATE TABLE t (\n  id INT PRIMARY KEY\n);\nINVALID SQL;"
    val result = DdlSyntaxDiagnostics.diagnose(sql, SqlDialect.MYSQL)
    val error = result as DdlSyntaxDiagnostics.DiagnosticResult.Error
    val diag = error.diagnostics.first()
    assertEquals(4, diag.line)
    assertEquals(1, diag.column)
  }

  @Test
  fun `offsetToLineColumn computes 1-based line and column`() {
    val text = "abc\ndef\nghi"
    assertEquals(Pair(1, 1), DdlSyntaxDiagnostics.offsetToLineColumn(text, 0))
    assertEquals(Pair(1, 4), DdlSyntaxDiagnostics.offsetToLineColumn(text, 3))
    assertEquals(Pair(2, 1), DdlSyntaxDiagnostics.offsetToLineColumn(text, 4))
    assertEquals(Pair(2, 3), DdlSyntaxDiagnostics.offsetToLineColumn(text, 6))
    assertEquals(Pair(3, 1), DdlSyntaxDiagnostics.offsetToLineColumn(text, 8))
    assertEquals(Pair(3, 3), DdlSyntaxDiagnostics.offsetToLineColumn(text, 10))
  }

  @Test
  fun `IF NOT EXISTS is supported in diagnostics`() {
    val sql = "CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY);"
    val result = DdlSyntaxDiagnostics.diagnose(sql, SqlDialect.MYSQL)
    assertTrue(result is DdlSyntaxDiagnostics.DiagnosticResult.Ok)
  }

  @Test
  fun `missing opening paren after table name is reported`() {
    val sql = "CREATE TABLE users id INT;"
    val result = DdlSyntaxDiagnostics.diagnose(sql, SqlDialect.MYSQL)
    val error = result as DdlSyntaxDiagnostics.DiagnosticResult.Error
    assertEquals("Expected '(' after table name", error.diagnostics.first().message)
  }

  @Test
  fun `missing table name is rejected before the opening parenthesis`() {
    val sql = "CREATE TABLE (id INT PRIMARY KEY);"
    val result = DdlSyntaxDiagnostics.diagnose(sql, SqlDialect.MYSQL)
    val error = result as DdlSyntaxDiagnostics.DiagnosticResult.Error

    assertEquals("Expected table name after CREATE TABLE", error.diagnostics.first().message)
    assertEquals(sql.indexOf('('), error.diagnostics.first().offset)
  }

  @Test
  fun `leading whitespace preserves original offsets`() {
    val sql = "   INVALID"
    val result = DdlSyntaxDiagnostics.diagnose(sql, SqlDialect.MYSQL)
    val error = result as DdlSyntaxDiagnostics.DiagnosticResult.Error
    val diag = error.diagnostics.first()
    assertEquals(3, diag.offset, "offset should point to 'I' in original text")
    assertEquals(1, diag.line)
    assertEquals(4, diag.column)
  }

  @Test
  fun `leading newlines report correct line`() {
    val sql = "\n\n  INVALID"
    val result = DdlSyntaxDiagnostics.diagnose(sql, SqlDialect.MYSQL)
    val error = result as DdlSyntaxDiagnostics.DiagnosticResult.Error
    val diag = error.diagnostics.first()
    assertEquals(3, diag.line)
    assertEquals(3, diag.column)
    assertEquals(4, diag.offset)
  }

  @Test
  fun `valid DDL with leading whitespace returns Ok`() {
    val sql = "  \n  CREATE TABLE test (id INT PRIMARY KEY);"
    val result = DdlSyntaxDiagnostics.diagnose(sql, SqlDialect.MYSQL)
    assertTrue(result is DdlSyntaxDiagnostics.DiagnosticResult.Ok)
  }

  @Test
  fun `blank input returns Empty input error`() {
    val result = DdlSyntaxDiagnostics.diagnose("   ", SqlDialect.MYSQL)
    val error = result as DdlSyntaxDiagnostics.DiagnosticResult.Error
    assertEquals("Empty input", error.diagnostics.first().message)
  }

  @Test
  fun `unmatched paren with leading whitespace reports correct offset`() {
    val sql = "    )CREATE TABLE test (id INT PRIMARY KEY);"
    val result = DdlSyntaxDiagnostics.diagnose(sql, SqlDialect.MYSQL)
    val error = result as DdlSyntaxDiagnostics.DiagnosticResult.Error
    val diag = error.diagnostics.first()
    assertEquals(4, diag.offset)
    assertEquals(1, diag.line)
    assertEquals(5, diag.column)
  }

  @Test
  fun `all sample DDLs pass diagnostics`() {
    val samples = kr.kyg.ijplugin.egovframe.crud.CrudSampleCatalog.all()
    for (sample in samples) {
      val result = DdlSyntaxDiagnostics.diagnose(sample.ddl, sample.dialect)
      assertTrue(
        result is DdlSyntaxDiagnostics.DiagnosticResult.Ok,
        "Sample ${sample.key} should pass diagnostics but got: $result",
      )
    }
  }
}
