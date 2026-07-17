package kr.kyg.ijplugin.egovframe.ddl

internal sealed interface DdlAnalysisResult {
  data class Success(val analysis: DdlAnalysis) : DdlAnalysisResult

  data class Invalid(val message: String) : DdlAnalysisResult
}

internal data class DdlAnalysis(
  val tables: List<DdlTable>,
  val relationships: List<DdlRelationship>,
)

internal data class DdlTable(
  val dbName: String,
  val className: String,
  val columns: List<DdlColumn>,
)

internal data class DdlColumn(
  val name: String,
  val camelName: String,
  val pascalName: String,
  val dataType: String,
  val javaType: String,
  val isPrimaryKey: Boolean,
  val isForeignKey: Boolean,
)

internal data class DdlRelationship(
  val fromTable: String,
  val fromColumn: String,
  val toTable: String,
  val toColumn: String,
)

/**
 * Dialect-neutral canonical DDL analysis.
 *
 * SQL dialect policy belongs to [DdlSyntaxDiagnostics]; this analyzer accepts the supported
 * `COMMENT ON` syntax so PostgreSQL input can reach the shared CRUD/ERD domain model after validation.
 */
internal object DdlAnalyzer {

  private const val INVALID_DDL = "Invalid DDL"
  private val leadingWordRegex = Regex("""^\w+""")
  private val whitespaceRegex = Regex("""\s+""")
  private val inlinePrimaryKeyRegex = Regex("""\bPRIMARY\s+KEY\b""", RegexOption.IGNORE_CASE)
  private val primaryKeyConstraintRegex =
    Regex("""\bPRIMARY\s+KEY\s*\((.*?)\)""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
  private val foreignKeyConstraintRegex = Regex(
    """\bFOREIGN\s+KEY\s*\((.*?)\)\s+REFERENCES\s+((?:`(?:``|\\.|[^`])*`|"(?:""|\\.|[^"])*"|\w+))\s*\((.*?)\)""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
  )
  private val inlineReferenceRegex = Regex(
    """\bREFERENCES\s+((?:`(?:``|\\.|[^`])*`|"(?:""|\\.|[^"])*"|\w+))\s*\((.*?)\)""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
  )
  private val ignoredDefinitionKinds = setOf("PRIMARY", "FOREIGN", "CONSTRAINT", "UNIQUE", "KEY", "INDEX", "COMMENT")

  fun analyze(ddl: String): DdlAnalysisResult {
    val text = ddl.trim()
    if (text.isEmpty()) return DdlAnalysisResult.Invalid(INVALID_DDL)

    val tables = mutableListOf<DdlTable>()
    val relationships = mutableListOf<DdlRelationship>()
    var cursor = 0

    while (cursor < text.length) {
      cursor = skipWhitespace(text, cursor)
      val statement = parseCreateTable(text, cursor) ?: return DdlAnalysisResult.Invalid(INVALID_DDL)
      val parsed = parseTable(statement, relationships)
      when (parsed) {
        is TableParseResult.Invalid -> return DdlAnalysisResult.Invalid(parsed.message)
        is TableParseResult.Success -> tables.add(parsed.table)
      }

      val suffixEnd = SqlScanner(text).findTopLevelSemicolon(statement.bodyCloseIndex + 1)
        ?: return DdlAnalysisResult.Invalid(INVALID_DDL)
      cursor = if (suffixEnd < text.length) suffixEnd + 1 else suffixEnd
      cursor = skipWhitespace(text, cursor)
      cursor = skipCommentOnStatements(text, cursor)
      if (cursor < text.length && !matchesKeyword(text, cursor, "CREATE")) {
        return DdlAnalysisResult.Invalid(INVALID_DDL)
      }
    }

    return if (tables.isEmpty()) {
      DdlAnalysisResult.Invalid(INVALID_DDL)
    } else {
      DdlAnalysisResult.Success(DdlAnalysis(tables.toList(), relationships.toList()))
    }
  }

  private fun parseCreateTable(text: String, start: Int): CreateTable? {
    var cursor = consumeKeyword(text, start, "CREATE") ?: return null
    cursor = skipWhitespace(text, cursor)
    cursor = consumeKeyword(text, cursor, "TABLE") ?: return null
    cursor = skipWhitespace(text, cursor)

    val ifCursor = consumeKeyword(text, cursor, "IF")
    if (ifCursor != null) {
      cursor = skipWhitespace(text, ifCursor)
      cursor = consumeKeyword(text, cursor, "NOT") ?: return null
      cursor = skipWhitespace(text, cursor)
      cursor = consumeKeyword(text, cursor, "EXISTS") ?: return null
      cursor = skipWhitespace(text, cursor)
    }

    val identifier = parseIdentifier(text, cursor) ?: return null
    cursor = skipWhitespace(text, identifier.endIndex)
    if (cursor >= text.length || text[cursor] != '(') return null
    val bodyCloseIndex = SqlScanner(text).findMatchingParenthesis(cursor) ?: return null
    return CreateTable(
      dbName = identifier.value,
      body = text.substring(cursor + 1, bodyCloseIndex),
      bodyCloseIndex = bodyCloseIndex,
    )
  }

  private fun parseTable(
    statement: CreateTable,
    relationships: MutableList<DdlRelationship>,
  ): TableParseResult {
    val definitions = SqlScanner(statement.body).splitTopLevelCommas()
      ?: return TableParseResult.Invalid(INVALID_DDL)
    val nonEmptyDefinitions = definitions.map(String::trim).filter(String::isNotEmpty)
    val primaryKeyColumns = linkedSetOf<String>()
    val foreignKeyColumns = linkedSetOf<String>()

    for (definition in nonEmptyDefinitions) {
      primaryKeyConstraintRegex.find(definition)?.let { match ->
        parseIdentifierList(match.groupValues[1]).forEach(primaryKeyColumns::add)
      }

      foreignKeyConstraintRegex.find(definition)?.let { match ->
        val fromColumns = parseIdentifierList(match.groupValues[1])
        val toTable = cleanIdentifier(match.groupValues[2])
        val toColumns = parseIdentifierList(match.groupValues[3])
        fromColumns.forEachIndexed { index, fromColumn ->
          foreignKeyColumns.add(fromColumn)
          relationships.add(
            DdlRelationship(
              fromTable = statement.dbName,
              fromColumn = fromColumn,
              toTable = toTable,
              toColumn = toColumns.getOrNull(index) ?: toColumns.firstOrNull() ?: "id",
            )
          )
        }
      }
    }

    val columns = mutableListOf<DdlColumn>()
    for (definition in nonEmptyDefinitions) {
      if (definitionKind(definition) in ignoredDefinitionKinds) continue

      val columnIdentifier = parseIdentifier(definition, 0)
      if (columnIdentifier == null || columnIdentifier.value.isBlank()) {
        return TableParseResult.Invalid(
          "Invalid column definition: missing column name in \"${normalizeWhitespace(definition)}\""
        )
      }
      val columnName = columnIdentifier.value
      val typeStart = skipWhitespace(definition, columnIdentifier.endIndex)
      if (typeStart >= definition.length) {
        return TableParseResult.Invalid(
          "Invalid column definition: missing data type for column \"$columnName\""
        )
      }
      val rawDataType = definition.substring(typeStart).takeWhile { !it.isWhitespace() }.uppercase()
      if (rawDataType.isBlank()) {
        return TableParseResult.Invalid(
          "Invalid column definition: missing data type for column \"$columnName\""
        )
      }
      val dataType = leadingWordRegex.find(rawDataType)?.value ?: rawDataType

      inlineReferenceRegex.find(definition)?.let { match ->
        val toTable = cleanIdentifier(match.groupValues[1])
        val toColumn = parseIdentifierList(match.groupValues[2]).firstOrNull() ?: "id"
        foreignKeyColumns.add(columnName)
        relationships.add(
          DdlRelationship(
            fromTable = statement.dbName,
            fromColumn = columnName,
            toTable = toTable,
            toColumn = toColumn,
          )
        )
      }

      val camelName = convertToCamelCase(columnName)
      columns.add(
        DdlColumn(
          name = columnName,
          camelName = camelName,
          pascalName = convertCamelCaseToPascalCase(camelName),
          dataType = dataType,
          javaType = DataTypes.getJavaClassName(dataType),
          isPrimaryKey = columnName in primaryKeyColumns || inlinePrimaryKeyRegex.containsMatchIn(definition),
          isForeignKey = columnName in foreignKeyColumns,
        )
      )
    }

    if (columns.isEmpty()) return TableParseResult.Invalid("No valid columns found in DDL")
    return TableParseResult.Success(
      DdlTable(
        dbName = statement.dbName,
        className = convertCamelCaseToPascalCase(convertToCamelCase(statement.dbName)),
        columns = columns.toList(),
      )
    )
  }

  private fun parseIdentifierList(text: String): List<String> =
    SqlScanner(text).splitTopLevelCommas()
      ?.map(String::trim)
      ?.map(::cleanIdentifier)
      ?.filter(String::isNotEmpty)
      .orEmpty()

  private fun definitionKind(definition: String): String {
    val identifier = parseIdentifier(definition, 0) ?: return ""
    return identifier.value.uppercase()
  }

  private fun cleanIdentifier(identifier: String): String {
    val trimmed = identifier.trim()
    if (trimmed.length < 2) return trimmed
    val quote = trimmed.first()
    if ((quote != '`' && quote != '"') || trimmed.last() != quote) return trimmed
    return buildString {
      var index = 1
      while (index < trimmed.lastIndex) {
        val current = trimmed[index]
        if (current == '\\' && index + 1 < trimmed.lastIndex) {
          append(trimmed[index + 1])
          index += 2
        } else if (current == quote && index + 1 < trimmed.lastIndex && trimmed[index + 1] == quote) {
          append(quote)
          index += 2
        } else {
          append(current)
          index += 1
        }
      }
    }
  }

  private fun parseIdentifier(text: String, start: Int): ParsedIdentifier? {
    if (start >= text.length) return null
    val quote = text[start]
    if (quote == '`' || quote == '"') {
      var index = start + 1
      while (index < text.length) {
        val current = text[index]
        if (current == '\\' && index + 1 < text.length) {
          index += 2
          continue
        }
        if (current == quote) {
          if (index + 1 < text.length && text[index + 1] == quote) {
            index += 2
            continue
          }
          return ParsedIdentifier(cleanIdentifier(text.substring(start, index + 1)), index + 1)
        }
        index += 1
      }
      return null
    }

    var index = start
    while (index < text.length && (text[index].isLetterOrDigit() || text[index] == '_')) index += 1
    if (index == start) return null
    return ParsedIdentifier(text.substring(start, index), index)
  }

  private fun convertToCamelCase(value: String): String =
    value.lowercase().replace(Regex("""_([a-z0-9])""")) { match -> match.groupValues[1].uppercase() }

  private fun convertCamelCaseToPascalCase(value: String): String =
    if (value.isEmpty()) value else value[0].uppercaseChar() + value.substring(1)

  private fun normalizeWhitespace(value: String): String = value.replace(whitespaceRegex, " ").trim()

  private fun skipWhitespace(text: String, start: Int): Int {
    var index = start
    while (index < text.length && text[index].isWhitespace()) index += 1
    return index
  }

  private fun skipCommentOnStatements(text: String, start: Int): Int {
    var cursor = start
    while (cursor < text.length) {
      cursor = skipWhitespace(text, cursor)
      if (cursor >= text.length) break
      if (!matchesKeyword(text, cursor, "COMMENT")) break
      var c = skipWhitespace(text, cursor + 7)
      if (!matchesKeyword(text, c, "ON")) break
      c = skipWhitespace(text, c + 2)
      if (!matchesKeyword(text, c, "TABLE") && !matchesKeyword(text, c, "COLUMN")) break
      // This is a COMMENT ON TABLE/COLUMN statement; skip to semicolon
      val semiIdx = SqlScanner(text).findTopLevelSemicolon(cursor)
      if (semiIdx == null) break
      cursor = if (semiIdx < text.length) semiIdx + 1 else semiIdx
    }
    return cursor
  }

  private fun consumeKeyword(text: String, start: Int, keyword: String): Int? =
    if (matchesKeyword(text, start, keyword)) start + keyword.length else null

  private fun matchesKeyword(text: String, start: Int, keyword: String): Boolean {
    if (start < 0 || start + keyword.length > text.length) return false
    if (!text.regionMatches(start, keyword, 0, keyword.length, ignoreCase = true)) return false
    val before = text.getOrNull(start - 1)
    val after = text.getOrNull(start + keyword.length)
    return (before == null || !before.isLetterOrDigit() && before != '_') &&
      (after == null || !after.isLetterOrDigit() && after != '_')
  }

  private data class CreateTable(
    val dbName: String,
    val body: String,
    val bodyCloseIndex: Int,
  )

  private data class ParsedIdentifier(
    val value: String,
    val endIndex: Int,
  )

  private sealed interface TableParseResult {
    data class Success(val table: DdlTable) : TableParseResult

    data class Invalid(val message: String) : TableParseResult
  }

  private class SqlScanner(private val text: String) {

    fun findMatchingParenthesis(openIndex: Int): Int? {
      if (openIndex !in text.indices || text[openIndex] != '(') return null
      return scan(openIndex + 1, 1) { index, character, depth -> character == ')' && depth == 1 }
        .takeIf { it.valid }
        ?.stopIndex
    }

    fun findTopLevelSemicolon(start: Int): Int? {
      val result = scan(start, 0) { _, character, depth -> character == ';' && depth == 0 }
      return if (result.valid) result.stopIndex ?: text.length else null
    }

    fun splitTopLevelCommas(): List<String>? {
      val commas = mutableListOf<Int>()
      val result = scan(0, 0) { index, character, depth ->
        if (character == ',' && depth == 0) commas.add(index)
        false
      }
      if (!result.valid) return null

      val parts = ArrayList<String>(commas.size + 1)
      var start = 0
      for (comma in commas) {
        parts.add(text.substring(start, comma))
        start = comma + 1
      }
      parts.add(text.substring(start))
      return parts
    }

    private fun scan(
      start: Int,
      initialDepth: Int,
      shouldStop: (index: Int, character: Char, depth: Int) -> Boolean,
    ): ScanResult {
      var depth = initialDepth
      var quote: Char? = null
      var index = start
      while (index < text.length) {
        val character = text[index]
        if (quote != null) {
          if (character == '\\' && index + 1 < text.length) {
            index += 2
            continue
          }
          if (character == quote) {
            if (index + 1 < text.length && text[index + 1] == quote) {
              index += 2
              continue
            }
            quote = null
          }
          index += 1
          continue
        }

        if (character == '\'' || character == '"' || character == '`') {
          quote = character
          index += 1
          continue
        }
        if (shouldStop(index, character, depth)) return ScanResult(index, valid = true)
        when (character) {
          '(' -> depth += 1
          ')' -> {
            depth -= 1
            if (depth < 0) return ScanResult(null, valid = false)
          }
        }
        index += 1
      }
      return ScanResult(null, valid = quote == null && depth == 0)
    }

    private data class ScanResult(
      val stopIndex: Int?,
      val valid: Boolean,
    )
  }
}
