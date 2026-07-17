package kr.kyg.ijplugin.egovframe.crud

internal enum class SqlDialect(val id: String, val displayName: String) {
  MYSQL("mysql", "MySQL"),
  POSTGRESQL("pgsql", "PostgreSQL"),
  GENERIC("generic", "Generic");

  companion object {
    fun fromId(id: String): SqlDialect = entries.first { it.id == id }
  }
}
