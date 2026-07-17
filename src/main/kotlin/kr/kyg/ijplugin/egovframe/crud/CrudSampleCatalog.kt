package kr.kyg.ijplugin.egovframe.crud

/**
 * Vendored sample DDLs from `src/utils/SampleDDLs.ts` (v5.0.6).
 * Each entry's content is loaded from runtime resources under `egovframe/samples/`.
 * Generic dialect has no samples.
 */
internal object CrudSampleCatalog {

  data class Sample(
    val key: String,
    val name: String,
    val dialect: SqlDialect,
    val ddl: String,
  )

  private val entries: List<Sample> = listOf(
    entry("board-mysql", "Board Table", SqlDialect.MYSQL),
    entry("board-pgsql", "Board Table", SqlDialect.POSTGRESQL),
    entry("user-mysql", "User Table", SqlDialect.MYSQL),
    entry("user-pgsql", "User Table", SqlDialect.POSTGRESQL),
    entry("product-mysql", "Product Table", SqlDialect.MYSQL),
    entry("product-pgsql", "Product Table", SqlDialect.POSTGRESQL),
    entry("order-mysql", "Order Table", SqlDialect.MYSQL),
    entry("order-pgsql", "Order Table", SqlDialect.POSTGRESQL),
    entry("comment-mysql", "Comment Table", SqlDialect.MYSQL),
    entry("comment-pgsql", "Comment Table", SqlDialect.POSTGRESQL),
  )

  fun all(): List<Sample> = entries

  fun forDialect(dialect: SqlDialect): List<Sample> = entries.filter { it.dialect == dialect }

  fun find(key: String): Sample? = entries.find { it.key == key }

  private fun entry(key: String, name: String, dialect: SqlDialect): Sample {
    val resource = CrudSampleCatalog::class.java.classLoader
      .getResourceAsStream("egovframe/samples/$key.sql")
      ?: throw IllegalStateException("Missing sample resource: egovframe/samples/$key.sql")
    val ddl = resource.bufferedReader(Charsets.UTF_8).use { it.readText().trimEnd() }
    return Sample(key, name, dialect, ddl)
  }
}
