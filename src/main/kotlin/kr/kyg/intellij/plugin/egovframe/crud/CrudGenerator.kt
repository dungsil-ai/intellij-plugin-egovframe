package kr.kyg.intellij.plugin.egovframe.crud

import kr.kyg.intellij.plugin.egovframe.assets.EgovAssets
import kr.kyg.intellij.plugin.egovframe.ddl.DdlParser
import kr.kyg.intellij.plugin.egovframe.ddl.ParsedDdl
import kr.kyg.intellij.plugin.egovframe.ddl.Column
import kr.kyg.intellij.plugin.egovframe.render.EgovHandlebars
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Collections

object CrudGenerator {

    data class RenderedFile(val info: TemplateFileInfo, val content: String)
    data class GenerationResult(val written: List<Path>, val overwritten: List<Path>)
    class PreparedCrud internal constructor(
        val parsed: ParsedDdl,
        val context: Map<String, Any?>,
    ) {
        fun renderFiles(): List<RenderedFile> {
            val packagePath = context.getValue("packagePath") as String
            return CrudTemplates.templateFilesConfig(parsed.tableName, packagePath).map { info ->
                RenderedFile(
                    info = info,
                    content = EgovHandlebars.render(
                        EgovAssets.resourceText("egovframe/code/${info.templateFile}"),
                        freshContext(),
                    ),
                )
            }
        }

        fun renderTemplate(templateText: String): String =
            EgovHandlebars.render(templateText, freshContext())

        private fun freshContext(): MutableMap<String, Any?> = LinkedHashMap(context)
    }


    fun prepare(
        ddl: String,
        packageName: String,
        today: LocalDate = LocalDate.now(ZoneOffset.UTC),
    ): PreparedCrud {
        val parsed = DdlParser.parseDDL(ddl)
        return PreparedCrud(parsed, buildContext(parsed, packageName, today))
    }


    private fun buildContext(parsed: ParsedDdl, packageName: String, today: LocalDate): Map<String, Any?> {
        val tableName = parsed.tableName
        return Collections.unmodifiableMap(linkedMapOf(
            "tableName" to tableName,
            "dbTableName" to parsed.dbTableName,
            "attributes" to columnsToContext(parsed.attributes),
            "pkAttributes" to columnsToContext(parsed.pkAttributes),
            "packageName" to packageName,
            "className" to tableName,
            "classNameFirstCharLower" to "${tableName[0].lowercaseChar()}${tableName.substring(1)}",
            "author" to "author",
            "date" to today.toString(),
            "version" to "1.0.0",
            "packagePath" to packageName.replace(".", "/"),
        ))
    }

    private fun columnsToContext(columns: List<Column>): List<Map<String, Any?>> =
        Collections.unmodifiableList(columns.map(::columnToContext))

    private fun columnToContext(column: Column): Map<String, Any?> = Collections.unmodifiableMap(linkedMapOf(
        "ccName" to column.ccName,
        "columnName" to column.columnName,
        "isPrimaryKey" to column.isPrimaryKey,
        "pcName" to column.pcName,
        "dataType" to column.dataType,
        "javaType" to column.javaType,
    ))


    fun generate(projectRoot: Path, files: List<RenderedFile>): GenerationResult {
        val normalizedRoot = projectRoot.toAbsolutePath().normalize()
        val written = ArrayList<Path>(files.size)
        val overwritten = ArrayList<Path>()
        files.forEach { rendered ->
            val target = normalizedRoot.resolve(rendered.info.outputPath).normalize()
            require(target.startsWith(normalizedRoot)) { "CRUD output escapes the project root: $target" }
            if (Files.exists(target)) overwritten.add(target)
            Files.createDirectories(target.parent)
            Files.writeString(target, rendered.content, Charsets.UTF_8)
            written.add(target)
        }
        return GenerationResult(written, overwritten)
    }
}
