package kr.kyg.ijplugin.egovframe.ddl

/** 1:1 port of upstream `src/shared/dataTypes.ts` from the official v5.0.6 tag. */
object DataTypes {

  private val predefinedDataTypes: Map<String, String> = mapOf(
    "VARCHAR" to "java.lang.String",
    "VARCHAR2" to "java.lang.String",
    "CHAR" to "java.lang.String",
    "TEXT" to "java.lang.String",
    "MEDIUMTEXT" to "java.lang.String",
    "INT" to "java.lang.Integer",
    "INTEGER" to "java.lang.Integer",
    "NUMBER" to "java.lang.Integer",
    "BIGINT" to "java.lang.Long",
    "SMALLINT" to "java.lang.Short",
    "TINYINT" to "java.lang.Byte",
    "DECIMAL" to "java.math.BigDecimal",
    "NUMERIC" to "java.math.BigDecimal",
    "FLOAT" to "java.lang.Float",
    "REAL" to "java.lang.Double",
    "DOUBLE" to "java.lang.Double",
    "SMALLSERIAL" to "java.lang.Short",
    "SERIAL" to "java.lang.Integer",
    "BIGSERIAL" to "java.lang.Long",
    "DATE" to "java.sql.Date",
    "TIME" to "java.sql.Time",
    "DATETIME" to "java.util.Date",
    "TIMESTAMP" to "java.sql.Timestamp",
    "BOOLEAN" to "java.lang.Boolean",
    "BIT" to "java.lang.Boolean",
    "ENUM" to "java.lang.String",
    "SET" to "java.lang.String",
  )

  fun getJavaClassName(dataType: String): String =
    predefinedDataTypes[dataType.uppercase()] ?: "java.lang.Object"
}

/** Returns the Java class name corresponding to an SQL data type. */
fun getJavaClassName(dataType: String): String = DataTypes.getJavaClassName(dataType)
