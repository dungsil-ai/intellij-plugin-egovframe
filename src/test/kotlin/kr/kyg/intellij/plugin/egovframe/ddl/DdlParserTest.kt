package kr.kyg.intellij.plugin.egovframe.ddl

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** 1:1 port of upstream `webview-ui/src/utils/__tests__/ddlParser.spec.ts`. */
class DdlParserTest {

  @Test
  fun `should parse basic MySQL columns and inline primary key`() {
    val result = DdlParser.parseDDL(
      """
            CREATE TABLE users (
                id INT PRIMARY KEY,
                user_name VARCHAR(100),
                created_at TIMESTAMP
            );
        """
    )

    assertEquals("Users", result.tableName)
    assertEquals(3, result.attributes.size)
    assertEquals(1, result.pkAttributes.size)

    result.attributes[0].let {
      assertEquals("id", it.columnName)
      assertEquals("id", it.ccName)
      assertEquals("Id", it.pcName)
      assertEquals("INT", it.dataType)
      assertEquals("java.lang.Integer", it.javaType)
      assertTrue(it.isPrimaryKey)
    }
    result.attributes[1].let {
      assertEquals("user_name", it.columnName)
      assertEquals("userName", it.ccName)
      assertEquals("UserName", it.pcName)
      assertEquals("VARCHAR", it.dataType)
      assertEquals("java.lang.String", it.javaType)
      assertFalse(it.isPrimaryKey)
    }
    result.attributes[2].let {
      assertEquals("created_at", it.columnName)
      assertEquals("createdAt", it.ccName)
      assertEquals("CreatedAt", it.pcName)
      assertEquals("TIMESTAMP", it.dataType)
      assertEquals("java.sql.Timestamp", it.javaType)
      assertFalse(it.isPrimaryKey)
    }
  }

  @Test
  fun `should parse table-level primary key and decimal type with precision`() {
    val result = DdlParser.parseDDL(
      """
            CREATE TABLE orders (
                order_id BIGINT,
                user_id INT,
                amount DECIMAL(10,2),
                PRIMARY KEY (order_id)
            );
        """
    )

    assertEquals("Orders", result.tableName)
    assertEquals(3, result.attributes.size)
    assertEquals(1, result.pkAttributes.size)
    result.pkAttributes[0].let {
      assertEquals("order_id", it.columnName)
      assertEquals("orderId", it.ccName)
      assertEquals("BIGINT", it.dataType)
      assertEquals("java.lang.Long", it.javaType)
      assertTrue(it.isPrimaryKey)
    }
    result.attributes[2].let {
      assertEquals("amount", it.columnName)
      assertEquals("DECIMAL", it.dataType)
      assertEquals("java.math.BigDecimal", it.javaType)
    }
  }

  @Test
  fun `should ignore table constraints and indexes`() {
    val result = DdlParser.parseDDL(
      """
            CREATE TABLE members (
                id INT,
                email VARCHAR(255),
                CONSTRAINT uq_email UNIQUE (email),
                FOREIGN KEY (id) REFERENCES other_table(id),
                KEY idx_email (email),
                PRIMARY KEY (id)
            );
        """
    )

    assertEquals(2, result.attributes.size)
    assertEquals(listOf("id", "email"), result.attributes.map { it.columnName })
    assertEquals(1, result.pkAttributes.size)
    assertEquals("id", result.pkAttributes[0].columnName)
  }

  @Test
  fun `should strip quotes from column names and convert snake case names`() {
    val result = DdlParser.parseDDL(
      """
            CREATE TABLE user_profiles (
                `profile_id` INT PRIMARY KEY,
                "display_name" VARCHAR(255)
            );
        """
    )

    assertEquals("UserProfiles", result.tableName)
    assertEquals("user_profiles", result.dbTableName)
    result.attributes[0].let {
      assertEquals("profile_id", it.columnName)
      assertEquals("profileId", it.ccName)
      assertEquals("ProfileId", it.pcName)
      assertTrue(it.isPrimaryKey)
    }
    result.attributes[1].let {
      assertEquals("display_name", it.columnName)
      assertEquals("displayName", it.ccName)
      assertEquals("DisplayName", it.pcName)
    }
  }

  @Test
  fun `should use Object as fallback Java type for unknown SQL types`() {
    val result = DdlParser.parseDDL(
      """
            CREATE TABLE files (
                id INT,
                metadata JSONB
            );
        """
    )

    result.attributes[1].let {
      assertEquals("metadata", it.columnName)
      assertEquals("JSONB", it.dataType)
      assertEquals("java.lang.Object", it.javaType)
    }
  }

  @Test
  fun `should convert underscore followed by a digit into a clean camelCase name`() {
    val result = DdlParser.parseDDL(
      """
            CREATE TABLE addresses (
                id INT PRIMARY KEY,
                addr_1 VARCHAR(200),
                zip_no_1 VARCHAR(10)
            );
        """
    )

    val addr = result.attributes.find { it.columnName == "addr_1" }
    assertEquals("addr1", addr?.ccName)
    assertEquals("Addr1", addr?.pcName)

    val zip = result.attributes.find { it.columnName == "zip_no_1" }
    assertEquals("zipNo1", zip?.ccName)
    assertEquals("ZipNo1", zip?.pcName)

    val id = result.attributes.find { it.columnName == "id" }
    assertEquals("id", id?.ccName)
  }

  // --- validateDDL ---

  @Test
  fun `should accept valid CREATE TABLE statements`() {
    assertTrue(
      DdlParser.validateDDL(
        """
            CREATE TABLE sample (
                id INT,
                name VARCHAR(20)
            );
        """
      )
    )
  }

  @Test
  fun `should reject invalid DDL`() {
    assertFalse(DdlParser.validateDDL("SELECT * FROM sample"))
    assertFalse(DdlParser.validateDDL("CREATE TABLE sample ()"))
    assertFalse(
      DdlParser.validateDDL(
        """
            CREATE TABLE sample (
                id
            );
        """
      )
    )
  }

  @Test
  fun `should reject empty input`() {
    assertFalse(DdlParser.validateDDL(""))
  }

  @Test
  fun `should reject input that does not start with CREATE TABLE`() {
    assertFalse(DdlParser.validateDDL("   \n\t "))
    assertFalse(DdlParser.validateDDL("SELECT * FROM users"))
  }

  @Test
  fun `should reject CREATE TABLE with no closing parenthesis`() {
    assertFalse(DdlParser.validateDDL("CREATE TABLE sample (id INT"))
  }

  // --- error messages ---

  @Test
  fun `should throw Unable to parse table name from DDL`() {
    assertThrows(IllegalArgumentException::class.java) {
      DdlParser.parseDDL("SELECT 1")
    }
  }

  @Test
  fun `should throw for missing column name`() {
    try {
      DdlParser.parseDDL("CREATE TABLE t ( INT )")
      fail("expected exception")
    } catch (e: IllegalArgumentException) {
      assertTrue(e.message!!.startsWith("Invalid column definition: missing data type for column"))
    }
  }

  @Test
  fun `should throw No valid columns found in DDL`() {
    try {
      DdlParser.parseDDL("CREATE TABLE t ( PRIMARY KEY (id) )")
      fail("expected exception")
    } catch (e: IllegalArgumentException) {
      assertEquals("No valid columns found in DDL", e.message)
    }
  }
}
