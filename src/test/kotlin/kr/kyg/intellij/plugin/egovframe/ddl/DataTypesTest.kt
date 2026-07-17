package kr.kyg.intellij.plugin.egovframe.ddl

import org.junit.Assert.assertEquals
import org.junit.Test

/** 1:1 port of upstream `webview-ui/src/utils/__tests__/dataTypes.spec.ts`. */
class DataTypesTest {

  @Test
  fun `string types map to java lang String`() {
    assertEquals("java.lang.String", DataTypes.getJavaClassName("VARCHAR"))
    assertEquals("java.lang.String", DataTypes.getJavaClassName("VARCHAR2"))
    assertEquals("java.lang.String", DataTypes.getJavaClassName("CHAR"))
    assertEquals("java.lang.String", DataTypes.getJavaClassName("TEXT"))
    assertEquals("java.lang.String", DataTypes.getJavaClassName("MEDIUMTEXT"))
  }

  @Test
  fun `integer types map to appropriate Java types`() {
    assertEquals("java.lang.Integer", DataTypes.getJavaClassName("INT"))
    assertEquals("java.lang.Integer", DataTypes.getJavaClassName("INTEGER"))
    assertEquals("java.lang.Long", DataTypes.getJavaClassName("BIGINT"))
    assertEquals("java.lang.Short", DataTypes.getJavaClassName("SMALLINT"))
    assertEquals("java.lang.Byte", DataTypes.getJavaClassName("TINYINT"))
  }

  @Test
  fun `Oracle NUMBER follows the v5_0_6 exact type lookup`() {
    assertEquals("java.lang.Integer", DataTypes.getJavaClassName("NUMBER"))
    assertEquals("java.lang.Object", DataTypes.getJavaClassName("NUMBER(10,2)"))
  }

  @Test
  fun `decimal types map to appropriate Java types`() {
    assertEquals("java.math.BigDecimal", DataTypes.getJavaClassName("DECIMAL"))
    assertEquals("java.math.BigDecimal", DataTypes.getJavaClassName("NUMERIC"))
    assertEquals("java.lang.Float", DataTypes.getJavaClassName("FLOAT"))
    assertEquals("java.lang.Double", DataTypes.getJavaClassName("REAL"))
    assertEquals("java.lang.Double", DataTypes.getJavaClassName("DOUBLE"))
  }

  @Test
  fun `PostgreSQL serial types map to appropriate Java types`() {
    assertEquals("java.lang.Short", DataTypes.getJavaClassName("SMALLSERIAL"))
    assertEquals("java.lang.Integer", DataTypes.getJavaClassName("SERIAL"))
    assertEquals("java.lang.Long", DataTypes.getJavaClassName("BIGSERIAL"))
  }

  @Test
  fun `date and time types map to appropriate Java types`() {
    assertEquals("java.sql.Date", DataTypes.getJavaClassName("DATE"))
    assertEquals("java.sql.Time", DataTypes.getJavaClassName("TIME"))
    assertEquals("java.util.Date", DataTypes.getJavaClassName("DATETIME"))
    assertEquals("java.sql.Timestamp", DataTypes.getJavaClassName("TIMESTAMP"))
  }

  @Test
  fun `boolean types map to java lang Boolean`() {
    assertEquals("java.lang.Boolean", DataTypes.getJavaClassName("BOOLEAN"))
    assertEquals("java.lang.Boolean", DataTypes.getJavaClassName("BIT"))
  }

  @Test
  fun `MySQL enum types map to java lang String`() {
    assertEquals("java.lang.String", DataTypes.getJavaClassName("ENUM"))
    assertEquals("java.lang.String", DataTypes.getJavaClassName("SET"))
  }

  @Test
  fun `lowercase type names are handled case-insensitively`() {
    assertEquals("java.lang.String", DataTypes.getJavaClassName("varchar"))
    assertEquals("java.lang.Integer", DataTypes.getJavaClassName("int"))
    assertEquals("java.sql.Timestamp", DataTypes.getJavaClassName("timestamp"))
  }

  @Test
  fun `unknown types return java lang Object`() {
    assertEquals("java.lang.Object", DataTypes.getJavaClassName("JSONB"))
    assertEquals("java.lang.Object", DataTypes.getJavaClassName("UNKNOWN_TYPE"))
    assertEquals("java.lang.Object", DataTypes.getJavaClassName("XML"))
  }
}
