package kr.kyg.intellij.plugin.egovframe.ddl

/** Upstream v5.0.6 DDL parsing plus plugin-local multi-table extraction for ERD input. */
data class Column(
  val ccName: String,
  val columnName: String,
  val isPrimaryKey: Boolean,
  val pcName: String,
  val dataType: String,
  val javaType: String,
)

data class ParsedDdl(
  val tableName: String,
  val dbTableName: String,
  val attributes: List<Column>,
  val pkAttributes: List<Column>,
)

data class CreateTableStatement(
  val tableName: String,
  val body: String,
  val statement: String,
)

object DdlParser {

  private val createTableRegex =
    Regex("""CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?[`"]?(\w+)[`"]?\s*\(""", RegexOption.IGNORE_CASE)
  private val columnSplitRegex = Regex(""",(?![^(]*\))""")
  private val quoteStripRegex = Regex("""[`"']""")
  private val leadingWordRegex = Regex("""^\w+""")
  private val whitespaceRegex = Regex("""\s+""")
  private val pkConstraintRegex = Regex("""PRIMARY KEY\s*\(([^)]+)\)""", RegexOption.IGNORE_CASE)
  private val validateStartRegex = Regex("""^\s*CREATE\s+TABLE\s+""", RegexOption.IGNORE_CASE)

  fun convertToCamelCase(str: String): String =
    str.lowercase().replace(Regex("""_([a-z0-9])""")) { m -> m.groupValues[1].uppercase() }

  fun convertCamelcaseToPascalcase(name: String): String =
    if (name.isEmpty()) name else name[0].uppercaseChar() + name.substring(1)


  fun extractCreateTableStatements(ddl: String): List<CreateTableStatement> {
    val statements = mutableListOf<CreateTableStatement>()
    var searchStart = 0
    while (true) {
      val match = createTableRegex.find(ddl, searchStart) ?: break
      val bodyStart = match.range.last + 1
      var depth = 1
      var currentIndex = bodyStart
      while (currentIndex < ddl.length && depth > 0) {
        when (ddl[currentIndex]) {
          '(' -> depth += 1
          ')' -> depth -= 1
        }
        currentIndex += 1
      }
      if (depth == 0) {
        val bodyEnd = currentIndex - 1
        statements.add(
          CreateTableStatement(
            tableName = match.groupValues[1],
            body = ddl.substring(bodyStart, bodyEnd),
            statement = ddl.substring(match.range.first, currentIndex),
          )
        )
        searchStart = currentIndex
      } else {
        searchStart = match.range.last + 1
      }
    }
    return statements
  }

  private fun isValidColumnDefinition(column: String, includePrimaryKey: Boolean = false): Boolean {
    val upper = column.uppercase()
    return column.isNotEmpty() &&
      !upper.startsWith("UNIQUE KEY") &&
      !upper.startsWith("KEY") &&
      !upper.startsWith("CONSTRAINT") &&
      !upper.startsWith("COMMENT ON") &&
      !upper.startsWith("FOREIGN KEY") &&
      (includePrimaryKey || !upper.startsWith("PRIMARY KEY"))
  }

  fun parseDDL(ddl: String): ParsedDdl {
    val normalizedDdl = ddl.replace(whitespaceRegex, " ").trim()
    val tableNameMatch = Regex(
      """^\s*CREATE\s+TABLE\s+[`]?(\w+)[`]?""",
      RegexOption.IGNORE_CASE,
    ).find(normalizedDdl) ?: throw IllegalArgumentException("Unable to parse table name from DDL")

    val dbTableName = tableNameMatch.groupValues[1]
    val tableName = convertCamelcaseToPascalcase(convertToCamelCase(dbTableName))
    val columnDefinitions = Regex("""\((.*)\)""", RegexOption.DOT_MATCHES_ALL)
      .find(normalizedDdl)
      ?.groupValues?.get(1)
      ?: throw IllegalArgumentException("Unable to parse column definitions from DDL")
    val columnsArray = columnDefinitions
      .split(columnSplitRegex)
      .map { it.trim() }
      .filter { column ->
        val upper = column.uppercase()
        column.isNotEmpty() &&
          !upper.startsWith("UNIQUE KEY") &&
          !upper.startsWith("KEY") &&
          !upper.startsWith("CONSTRAINT") &&
          !upper.startsWith("FOREIGN KEY")
      }

    val attributes = mutableListOf<Column>()
    val pkAttributes = mutableListOf<Column>()
    val primaryKeyColumns = pkConstraintRegex.find(normalizedDdl)
      ?.groupValues?.get(1)
      ?.split(",")
      ?.map { it.trim().replace(quoteStripRegex, "") }
      ?: emptyList()

    columnsArray.forEach { columnDef ->
      val upper = columnDef.trim().uppercase()
      if (upper.startsWith("PRIMARY KEY") || upper.startsWith("COMMENT ON")) return@forEach

      val parts = columnDef.split(" ").filter { it.trim().isNotEmpty() }
      val columnName = parts.getOrNull(0)?.replace(quoteStripRegex, "")
      val rawDataType = parts.getOrNull(1)?.uppercase().orEmpty()
      if (columnName.isNullOrBlank()) {
        throw IllegalArgumentException("Invalid column definition: missing column name in \"$columnDef\"")
      }
      if (rawDataType.isBlank()) {
        throw IllegalArgumentException("Invalid column definition: missing data type for column \"$columnName\"")
      }

      val dataType = leadingWordRegex.find(rawDataType)?.value ?: rawDataType
      val isPrimaryKey = primaryKeyColumns.contains(columnName) || columnDef.uppercase().contains("PRIMARY KEY")
      val ccName = convertToCamelCase(columnName)
      val column = Column(
        ccName = ccName,
        columnName = columnName,
        isPrimaryKey = isPrimaryKey,
        pcName = convertCamelcaseToPascalcase(ccName),
        dataType = dataType,
        javaType = DataTypes.getJavaClassName(dataType),
      )
      attributes.add(column)
      if (isPrimaryKey) pkAttributes.add(column)
    }

    if (attributes.isEmpty()) throw IllegalArgumentException("No valid columns found in DDL")
    return ParsedDdl(tableName, dbTableName, attributes, pkAttributes)
  }

  fun validateDDL(ddl: String?): Boolean {
    if (ddl.isNullOrEmpty()) return false

    val trimmedDDL = ddl.trim()
    if (!validateStartRegex.containsMatchIn(trimmedDDL)) return false
    if (!Regex("""CREATE\s+TABLE\s+[^\s(]+\s*\(""", RegexOption.IGNORE_CASE).containsMatchIn(ddl)) return false
    if (ddl.count { it == '(' } != ddl.count { it == ')' }) return false

    val columnDefinitions = Regex("""\((.*)\)""", RegexOption.DOT_MATCHES_ALL)
      .find(ddl)
      ?.groupValues?.get(1)
      ?.takeIf { it.trim().isNotEmpty() }
      ?: return false
    val columnsArray = columnDefinitions
      .split(columnSplitRegex)
      .map { it.trim() }
      .filter { isValidColumnDefinition(it) }

    for (columnDef in columnsArray) {
      val parts = columnDef.split(" ").filter { it.trim().isNotEmpty() }
      val columnName = parts.getOrNull(0)?.replace(quoteStripRegex, "")
      val dataType = parts.getOrNull(1)
      if (columnName.isNullOrBlank() || dataType.isNullOrBlank()) return false
    }
    return true
  }
}

/** Parses one CREATE TABLE statement using the upstream-compatible DDL parser. */
fun parseDdl(ddl: String): ParsedDdl = DdlParser.parseDDL(ddl)

/** Returns whether [ddl] satisfies the upstream-compatible minimum CREATE TABLE validation. */
fun validateDdl(ddl: String): Boolean = DdlParser.validateDDL(ddl)
