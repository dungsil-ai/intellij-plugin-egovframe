package kr.kyg.ijplugin.egovframe.ddl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DdlAnalysisTest {

  @Test
  fun `parses upstream CRUD naming types and inline primary key`() {
    val analysis = success(
      """
        CREATE TABLE users (
          id INT PRIMARY KEY,
          user_name VARCHAR(100),
          created_at TIMESTAMP,
          metadata JSONB
        );
      """.trimIndent()
    )

    val table = analysis.tables.single()
    assertEquals("users", table.dbName)
    assertEquals("Users", table.className)
    assertEquals(4, table.columns.size)
    assertEquals(
      DdlColumn("id", "id", "Id", "INT", "java.lang.Integer", isPrimaryKey = true, isForeignKey = false),
      table.columns[0],
    )
    assertEquals(
      DdlColumn(
        "user_name",
        "userName",
        "UserName",
        "VARCHAR",
        "java.lang.String",
        isPrimaryKey = false,
        isForeignKey = false,
      ),
      table.columns[1],
    )
    assertEquals("java.sql.Timestamp", table.columns[2].javaType)
    assertEquals("java.lang.Object", table.columns[3].javaType)
  }

  @Test
  fun `parses table primary key decimal precision and ignores indexes`() {
    val analysis = success(
      """
        CREATE TABLE orders (
          order_id BIGINT,
          user_id INT,
          amount DECIMAL(10,2),
          CONSTRAINT uq_amount UNIQUE (amount),
          KEY idx_user (user_id),
          INDEX idx_amount (amount),
          PRIMARY KEY (order_id)
        );
      """.trimIndent()
    )

    val columns = analysis.tables.single().columns
    assertEquals(listOf("order_id", "user_id", "amount"), columns.map(DdlColumn::name))
    assertTrue(columns[0].isPrimaryKey)
    assertEquals("DECIMAL", columns[2].dataType)
    assertEquals("java.math.BigDecimal", columns[2].javaType)
  }

  @Test
  fun `supports IF NOT EXISTS quoted identifiers and underscore digits`() {
    val analysis = success(
      """
        CREATE TABLE IF NOT EXISTS "user_profiles" (
          `profile_id` INT PRIMARY KEY,
          "display_name" VARCHAR(255),
          addr_1 VARCHAR(200),
          zip_no_1 VARCHAR(10)
        );
      """.trimIndent()
    )

    val table = analysis.tables.single()
    assertEquals("user_profiles", table.dbName)
    assertEquals("UserProfiles", table.className)
    assertEquals(listOf("profileId", "displayName", "addr1", "zipNo1"), table.columns.map(DdlColumn::camelName))
    assertEquals(listOf("ProfileId", "DisplayName", "Addr1", "ZipNo1"), table.columns.map(DdlColumn::pascalName))
  }

  @Test
  fun `quoted literals do not split columns or close the CREATE body`() {
    val quote = '"'
    val analysis = success(
      """
        CREATE TABLE quoted_values (
          id INT PRIMARY KEY COMMENT 'value ) , and doubled ''quote''',
          text_value VARCHAR(100) DEFAULT ${quote}a,b)${quote} COMMENT ${quote}doubled ${quote}${quote}quote${quote}${quote}${quote},
          tick_value VARCHAR(100) DEFAULT `a,b)``tick`
        ) COMMENT='table ) , option';
      """.trimIndent()
    )

    assertEquals(listOf("id", "text_value", "tick_value"), analysis.tables.single().columns.map(DdlColumn::name))
  }

  @Test
  fun `parses the special chars fixture with a trailing table comment`() {
    val ddl = requireNotNull(javaClass.getResource("/golden/crud/special_chars/ddl.sql")).readText()

    val table = success(ddl).tables.single()

    assertEquals("event_info", table.dbName)
    assertEquals(listOf("event_id", "event_name", "description"), table.columns.map(DdlColumn::name))
  }

  @Test
  fun `parses table and inline foreign key relationships`() {
    val analysis = success(
      """
        CREATE TABLE users (
          id INT PRIMARY KEY
        );
        CREATE TABLE orders (
          id INT PRIMARY KEY,
          user_id INT NOT NULL,
          CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id)
        );
        CREATE TABLE comments (
          id INT PRIMARY KEY,
          user_id INT REFERENCES users(id)
        );
      """.trimIndent()
    )

    assertEquals(3, analysis.tables.size)
    assertTrue(analysis.tables[1].columns.single { it.name == "user_id" }.isForeignKey)
    assertTrue(analysis.tables[2].columns.single { it.name == "user_id" }.isForeignKey)
    assertEquals(
      listOf(
        DdlRelationship("orders", "user_id", "users", "id"),
        DdlRelationship("comments", "user_id", "users", "id"),
      ),
      analysis.relationships,
    )
  }

  @Test
  fun `pairs composite foreign keys and falls back to the first target column`() {
    val analysis = success(
      """
        CREATE TABLE parents (
          tenant_id INT,
          parent_id INT,
          PRIMARY KEY (tenant_id, parent_id)
        );
        CREATE TABLE children (
          tenant_id INT,
          parent_id INT,
          legacy_id INT,
          CONSTRAINT fk_parent FOREIGN KEY (tenant_id, parent_id, legacy_id)
            REFERENCES parents(tenant_id, parent_id)
        );
      """.trimIndent()
    )

    assertEquals(
      listOf(
        DdlRelationship("children", "tenant_id", "parents", "tenant_id"),
        DdlRelationship("children", "parent_id", "parents", "parent_id"),
        DdlRelationship("children", "legacy_id", "parents", "tenant_id"),
      ),
      analysis.relationships,
    )
    assertTrue(analysis.tables[1].columns.all(DdlColumn::isForeignKey))
  }

  @Test
  fun `returns Invalid for empty non-DDL and unbalanced input`() {
    assertInvalid("", "Invalid DDL")
    assertInvalid("SELECT * FROM sample", "Invalid DDL")
    assertInvalid("CREATE TABLE sample (id INT", "Invalid DDL")
    assertInvalid("CREATE TABLE sample (id VARCHAR(10);", "Invalid DDL")
  }

  @Test
  fun `returns exact malformed column messages without throwing`() {
    assertInvalid(
      "CREATE TABLE sample (\"\" INT)",
      "Invalid column definition: missing column name in \"\"\" INT\"",
    )
    assertInvalid(
      "CREATE TABLE sample (id)",
      "Invalid column definition: missing data type for column \"id\"",
    )
    assertInvalid(
      "CREATE TABLE sample (PRIMARY KEY (id))",
      "No valid columns found in DDL",
    )
  }

  @Test
  fun `marks non-key columns without false positives`() {
    val column = success("CREATE TABLE sample (id INT, name VARCHAR(20));").tables.single().columns[1]

    assertFalse(column.isPrimaryKey)
    assertFalse(column.isForeignKey)
  }

  private fun success(ddl: String): DdlAnalysis = when (val result = DdlAnalyzer.analyze(ddl)) {
    is DdlAnalysisResult.Success -> result.analysis
    is DdlAnalysisResult.Invalid -> error("Expected successful analysis but got: ${result.message}")
  }

  private fun assertInvalid(ddl: String, message: String) {
    assertEquals(DdlAnalysisResult.Invalid(message), DdlAnalyzer.analyze(ddl))
  }
}
