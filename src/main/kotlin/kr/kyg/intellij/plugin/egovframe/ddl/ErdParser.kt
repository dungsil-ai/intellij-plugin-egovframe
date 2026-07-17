package kr.kyg.intellij.plugin.egovframe.ddl

/** Plugin-local ERD adapter; the official upstream v5.0.6 tag has no ERD parser. */
data class ErdColumn(
  val name: String,
  val dataType: String,
  val isPrimaryKey: Boolean,
  val isForeignKey: Boolean,
)

data class ErdTable(
  val name: String,
  val columns: List<ErdColumn>,
)

data class ErdRelationship(
  val fromTable: String,
  val fromColumn: String,
  val toTable: String,
  val toColumn: String,
)

data class ErdModel(
  val tables: List<ErdTable>,
  val relationships: List<ErdRelationship>,
)

object ErdParser {

  private val columnSplitRegex = Regex(""",(?![^(]*\))""")
  private val edgeQuoteRegex = Regex("""^[`"']|[`"']$""")
  private val pkRegex = Regex("""PRIMARY\s+KEY\s*\(([^)]+)\)""", RegexOption.IGNORE_CASE)
  private val fkRegex =
    Regex("""FOREIGN\s+KEY\s*\(([^)]+)\)\s+REFERENCES\s+[`"]?(\w+)[`"]?\s*\(([^)]+)\)""", RegexOption.IGNORE_CASE)
  private val inlineRefRegex = Regex("""REFERENCES\s+[`"]?(\w+)[`"]?\s*\(([^)]+)\)""", RegexOption.IGNORE_CASE)
  private val inlinePkRegex = Regex("""\bPRIMARY\s+KEY\b""", RegexOption.IGNORE_CASE)
  private val whitespaceSplit = Regex("""\s+""")

  private fun cleanIdentifier(identifier: String): String =
    identifier.trim().replace(edgeQuoteRegex, "")

  private fun splitColumnDefinitions(body: String): List<String> =
    body.split(columnSplitRegex).map { it.trim() }.filter { it.isNotEmpty() }

  private fun parseColumnList(columnList: String): List<String> =
    columnList.split(",").map { cleanIdentifier(it) }.filter { it.isNotEmpty() }

  private fun getDefinitionKind(definition: String): String =
    definition.split(whitespaceSplit).firstOrNull()?.uppercase() ?: ""

  fun parseErdModel(ddl: String): ErdModel {
    val createTableStatements = DdlParser.extractCreateTableStatements(ddl)
    val tables = mutableListOf<ErdTable>()
    val relationships = mutableListOf<ErdRelationship>()

    for (createTableStatement in createTableStatements) {
      val tableName = cleanIdentifier(createTableStatement.tableName)
      val definitions = splitColumnDefinitions(createTableStatement.body)
      val primaryKeyColumns = mutableSetOf<String>()
      val foreignKeyColumns = mutableSetOf<String>()
      val columns = mutableListOf<ErdColumn>()

      for (definition in definitions) {
        val pkMatch = pkRegex.find(definition)
        if (pkMatch != null) {
          parseColumnList(pkMatch.groupValues[1]).forEach { primaryKeyColumns.add(it) }
          continue
        }

        val fkMatch = fkRegex.find(definition)
        if (fkMatch != null) {
          val fromColumns = parseColumnList(fkMatch.groupValues[1])
          val toTable = cleanIdentifier(fkMatch.groupValues[2])
          val toColumns = parseColumnList(fkMatch.groupValues[3])
          fromColumns.forEachIndexed { index, fromColumn ->
            foreignKeyColumns.add(fromColumn)
            relationships.add(
              ErdRelationship(
                fromTable = tableName,
                fromColumn = fromColumn,
                toTable = toTable,
                toColumn = toColumns.getOrNull(index) ?: toColumns.firstOrNull() ?: "id",
              )
            )
          }
          continue
        }
      }

      for (definition in definitions) {
        val definitionKind = getDefinitionKind(definition)
        if (definitionKind in listOf("PRIMARY", "FOREIGN", "CONSTRAINT", "UNIQUE", "KEY", "INDEX", "COMMENT")) {
          continue
        }

        val parts = definition.split(whitespaceSplit).filter { it.isNotEmpty() }
        val columnName = cleanIdentifier(parts.getOrNull(0) ?: "")
        val dataType = (parts.getOrNull(1) ?: "").uppercase()

        if (columnName.isEmpty() || dataType.isEmpty()) continue

        val inlineReferenceMatch = inlineRefRegex.find(definition)
        if (inlineReferenceMatch != null) {
          val toTable = cleanIdentifier(inlineReferenceMatch.groupValues[1])
          val toColumn = parseColumnList(inlineReferenceMatch.groupValues[2]).firstOrNull() ?: "id"
          foreignKeyColumns.add(columnName)
          relationships.add(
            ErdRelationship(
              fromTable = tableName,
              fromColumn = columnName,
              toTable = toTable,
              toColumn = toColumn,
            )
          )
        }

        columns.add(
          ErdColumn(
            name = columnName,
            dataType = dataType,
            isPrimaryKey = primaryKeyColumns.contains(columnName) || inlinePkRegex.containsMatchIn(definition),
            isForeignKey = foreignKeyColumns.contains(columnName),
          )
        )
      }

      if (columns.isNotEmpty()) {
        tables.add(ErdTable(name = tableName, columns = columns))
      }
    }

    return ErdModel(tables, relationships)
  }
}

/** Parses all CREATE TABLE statements into an ERD model. */
fun parseErdModel(ddl: String): ErdModel = ErdParser.parseErdModel(ddl)
