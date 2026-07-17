package kr.kyg.ijplugin.egovframe.crud

import com.google.gson.GsonBuilder
import kr.kyg.ijplugin.egovframe.assets.EgovAssets
import kr.kyg.ijplugin.egovframe.ddl.DdlAnalysis
import kr.kyg.ijplugin.egovframe.ddl.DdlAnalysisResult
import kr.kyg.ijplugin.egovframe.ddl.DdlAnalyzer
import kr.kyg.ijplugin.egovframe.ddl.DdlColumn
import kr.kyg.ijplugin.egovframe.render.EgovHandlebars
import java.time.Clock
import java.time.LocalDate
import java.nio.file.Path
import java.util.Collections

internal class CrudGeneration(
  private val clock: Clock = Clock.systemUTC(),
) {
  private data class PreparationKey(
    val ddl: String,
    val packageName: String,
    val date: LocalDate,
  )

  private var cached: Pair<PreparationKey, CrudPreparation>? = null

  fun prepare(ddl: String, packageName: String): CrudPreparation {
    val date = LocalDate.now(clock)
    val key = PreparationKey(ddl, packageName, date)
    cached?.takeIf { it.first == key }?.let { return it.second }

    val preparation = when (val result = DdlAnalyzer.analyze(ddl)) {
      is DdlAnalysisResult.Invalid -> CrudPreparation.Rejected(result.message)
      is DdlAnalysisResult.Success -> prepare(result.analysis, packageName, date)
    }
    cached = key to preparation
    return preparation
  }

  private fun prepare(analysis: DdlAnalysis, packageName: String, date: LocalDate): CrudPreparation {
    val erdText = formatErd(analysis)
    if (analysis.tables.size != 1) {
      return CrudPreparation.Rejected(
        message = "CRUD generation requires exactly one CREATE TABLE statement.",
        erdText = erdText,
      )
    }

    val table = analysis.tables.single()
    return CrudPreparation.Ready(
      PreparedCrud(
        summary = CrudTableSummary(
          tableName = table.className,
          dbTableName = table.dbName,
          columnCount = table.columns.size,
        ),
        erdText = erdText,
        context = buildContext(table.className, table.dbName, table.columns, packageName, date),
      )
    )
  }

  private fun buildContext(
    tableName: String,
    dbTableName: String,
    columns: List<DdlColumn>,
    packageName: String,
    date: LocalDate,
  ): Map<String, Any?> = Collections.unmodifiableMap(
    linkedMapOf(
      "tableName" to tableName,
      "dbTableName" to dbTableName,
      "attributes" to columnsToContext(columns),
      "pkAttributes" to columnsToContext(columns.filter(DdlColumn::isPrimaryKey)),
      "packageName" to packageName,
      "className" to tableName,
      "classNameFirstCharLower" to "${tableName[0].lowercaseChar()}${tableName.substring(1)}",
      "author" to "author",
      "date" to date.toString(),
      "version" to "1.0.0",
      "packagePath" to packageName.replace(".", "/"),
    )
  )

  private fun columnsToContext(columns: List<DdlColumn>): List<Map<String, Any?>> =
    Collections.unmodifiableList(columns.map(::columnToContext))

  private fun columnToContext(column: DdlColumn): Map<String, Any?> = Collections.unmodifiableMap(
    linkedMapOf(
      "ccName" to column.camelName,
      "columnName" to column.name,
      "isPrimaryKey" to column.isPrimaryKey,
      "pcName" to column.pascalName,
      "dataType" to column.dataType,
      "javaType" to column.javaType,
    )
  )

  private fun formatErd(analysis: DdlAnalysis): String = buildString {
    analysis.tables.forEach { table ->
      appendLine("[${table.dbName}]")
      table.columns.forEach { column ->
        val badges = buildList {
          if (column.isPrimaryKey) add("PK")
          if (column.isForeignKey) add("FK")
        }.joinToString(",")
        append("  ${column.name}: ${column.dataType}")
        if (badges.isNotEmpty()) append(" [$badges]")
        appendLine()
      }
      appendLine()
    }
    if (analysis.relationships.isNotEmpty()) {
      appendLine("Relationships")
      analysis.relationships.forEach { relationship ->
        appendLine(
          "  ${relationship.fromTable}.${relationship.fromColumn} -> " +
            "${relationship.toTable}.${relationship.toColumn}"
        )
      }
    }
  }
}

internal sealed interface CrudPreparation {
  data class Ready(val prepared: PreparedCrud) : CrudPreparation

  data class Rejected(
    val message: String,
    val erdText: String = "",
  ) : CrudPreparation
}

internal data class CrudTableSummary(
  val tableName: String,
  val dbTableName: String,
  val columnCount: Int,
)

internal data class RenderedCrudArtifact(
  val artifact: CrudArtifact,
  val relativePath: String,
  val fileName: String,
  val language: String,
  val content: String,
)

internal class PreparedCrud internal constructor(
  val summary: CrudTableSummary,
  val erdText: String,
  private val context: Map<String, Any?>,
) {
  val artifacts: List<RenderedCrudArtifact> by lazy(LazyThreadSafetyMode.NONE) {
    val tableName = context.getValue("tableName") as String
    val packagePath = context.getValue("packagePath") as String
    CrudArtifact.entries.map { artifact ->
      val relativePath = artifact.relativePath(tableName, packagePath)
      RenderedCrudArtifact(
        artifact = artifact,
        relativePath = relativePath,
        fileName = relativePath.substringAfterLast('/'),
        language = artifact.language,
        content = EgovHandlebars.render(
          EgovAssets.resourceText("${EgovAssets.CODE_DIR}/${artifact.templateFile}"),
          freshContext(),
        ),
      )
    }
  }

  fun renderCustom(templateText: String): String = EgovHandlebars.render(templateText, freshContext())

  fun contextJson(): String = GsonBuilder().setPrettyPrinting().create().toJson(context) + "\n"

  fun plan(outputRoot: Path): GenerationPlan = GenerationPlan.create(artifacts, outputRoot)

  private fun freshContext(): MutableMap<String, Any?> = LinkedHashMap(context)
}
