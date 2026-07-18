package kr.kyg.ijplugin.egovframe.ddl

import kr.kyg.ijplugin.egovframe.crud.SqlDialect

/**
 * Lightweight syntax diagnostics for DDL input, independent of IntelliJ platform SQL support.
 * Reports errors with 1-based line/column and original offset.
 */
internal object DdlSyntaxDiagnostics {

  data class Diagnostic(
    val message: String,
    val line: Int,
    val column: Int,
    val offset: Int,
  )

  sealed interface DiagnosticResult {
    data class Ok(val summary: String) : DiagnosticResult

    data class Error(val diagnostics: List<Diagnostic>) : DiagnosticResult
  }

  fun diagnose(sql: String, dialect: SqlDialect): DiagnosticResult {
    val start = skipWhitespace(sql, 0)
    if (start >= sql.length) {
      return DiagnosticResult.Error(listOf(Diagnostic("Empty input", 1, 1, 0)))
    }

    val errors = mutableListOf<Diagnostic>()

    checkBalance(sql, errors)
    if (errors.isNotEmpty()) return DiagnosticResult.Error(errors)

    validateStatements(sql, dialect, errors)
    if (errors.isNotEmpty()) return DiagnosticResult.Error(errors)

    return when (val analysis = DdlAnalyzer.analyze(sql)) {
      is DdlAnalysisResult.Success -> DiagnosticResult.Ok("Valid DDL")
      is DdlAnalysisResult.Invalid -> DiagnosticResult.Error(listOf(Diagnostic(analysis.message, 1, 1, 0)))
    }
  }

  private fun checkBalance(text: String, errors: MutableList<Diagnostic>) {
    var depth = 0
    var quote: Char? = null
    var index = 0
    while (index < text.length) {
      val ch = text[index]
      if (quote != null) {
        if (ch == '\\' && index + 1 < text.length) {
          index += 2
          continue
        }
        if (ch == quote) {
          if (index + 1 < text.length && text[index + 1] == quote) {
            index += 2
            continue
          }
          quote = null
        }
        index += 1
        continue
      }
      when (ch) {
        '\'', '"', '`' -> quote = ch
        '(' -> depth += 1
        ')' -> {
          depth -= 1
          if (depth < 0) {
            val pos = offsetToLineColumn(text, index)
            errors.add(Diagnostic("Unmatched closing parenthesis", pos.first, pos.second, index))
            return
          }
        }
      }
      index += 1
    }
    if (quote != null) {
      val pos = offsetToLineColumn(text, text.length - 1)
      errors.add(Diagnostic("Unterminated string literal", pos.first, pos.second, text.length - 1))
    }
    if (depth > 0) {
      val pos = offsetToLineColumn(text, text.length - 1)
      errors.add(Diagnostic("Unclosed parenthesis", pos.first, pos.second, text.length - 1))
    }
  }

  private fun validateStatements(text: String, dialect: SqlDialect, errors: MutableList<Diagnostic>) {
    var cursor = 0
    var hasCreateTable = false

    while (cursor < text.length) {
      cursor = skipWhitespace(text, cursor)
      if (cursor >= text.length) break

      if (matchesKeyword(text, cursor, "CREATE")) {
        val createOffset = cursor
        var c = cursor + 6
        c = skipWhitespace(text, c)
        if (matchesKeyword(text, c, "TABLE")) {
          hasCreateTable = true
          // Find the closing paren and optional suffix/semicolon
          c = skipWhitespace(text, c + 5)

          // Skip IF NOT EXISTS
          if (matchesKeyword(text, c, "IF")) {
            c = skipWhitespace(text, c + 2)
            if (matchesKeyword(text, c, "NOT")) {
              c = skipWhitespace(text, c + 3)
              if (matchesKeyword(text, c, "EXISTS")) {
                c = skipWhitespace(text, c + 6)
              }
            }
          }

          // Skip table name
          val tableNameStart = c
          c = skipIdentifier(text, c)
          if (c == tableNameStart) {
            val pos = offsetToLineColumn(text, c)
            errors.add(
              Diagnostic(
                "Expected table name after CREATE TABLE",
                pos.first,
                pos.second,
                c,
              ),
            )
            return
          }
          c = skipWhitespace(text, c)

          if (c >= text.length || text[c] != '(') {
            val pos = offsetToLineColumn(text, c)
            errors.add(Diagnostic("Expected '(' after table name", pos.first, pos.second, c))
            return
          }

          val closeIdx = findMatchingParen(text, c)
          if (closeIdx == null) {
            val pos = offsetToLineColumn(text, c)
            errors.add(Diagnostic("Unclosed parenthesis in CREATE TABLE", pos.first, pos.second, c))
            return
          }

          // After close paren, find suffix and semicolon
          cursor = findStatementEnd(text, closeIdx + 1)
          continue
        } else {
          val pos = offsetToLineColumn(text, createOffset)
          errors.add(Diagnostic("Expected TABLE after CREATE", pos.first, pos.second, createOffset))
          return
        }
      } else if (dialect == SqlDialect.POSTGRESQL && matchesCommentOn(text, cursor)) {
        // PostgreSQL COMMENT ON TABLE/COLUMN is allowed
        val semiIdx = findNextSemicolon(text, cursor)
        cursor = if (semiIdx != null) semiIdx + 1 else text.length
        continue
      } else {
        // Not a recognized DDL statement
        val pos = offsetToLineColumn(text, cursor)
        errors.add(Diagnostic("Unexpected statement; expected CREATE TABLE", pos.first, pos.second, cursor))
        return
      }
    }

    if (!hasCreateTable) {
      errors.add(Diagnostic("No CREATE TABLE statement found", 1, 1, 0))
    }
  }

  private fun matchesCommentOn(text: String, start: Int): Boolean {
    if (!matchesKeyword(text, start, "COMMENT")) return false
    var c = skipWhitespace(text, start + 7)
    if (!matchesKeyword(text, c, "ON")) return false
    c = skipWhitespace(text, c + 2)
    return matchesKeyword(text, c, "TABLE") || matchesKeyword(text, c, "COLUMN")
  }

  private fun findStatementEnd(text: String, start: Int): Int {
    // After the closing paren of CREATE TABLE, consume optional suffix (COMMENT=..., ENGINE=...) up to semicolon
    var c = start
    var quote: Char? = null
    while (c < text.length) {
      val ch = text[c]
      if (quote != null) {
        if (ch == '\\' && c + 1 < text.length) { c += 2; continue }
        if (ch == quote) {
          if (c + 1 < text.length && text[c + 1] == quote) { c += 2; continue }
          quote = null
        }
        c += 1
        continue
      }
      when (ch) {
        '\'', '"', '`' -> { quote = ch; c += 1 }
        ';' -> return c + 1
        else -> c += 1
      }
    }
    return c
  }

  private fun findMatchingParen(text: String, openIndex: Int): Int? {
    var depth = 1
    var quote: Char? = null
    var i = openIndex + 1
    while (i < text.length) {
      val ch = text[i]
      if (quote != null) {
        if (ch == '\\' && i + 1 < text.length) { i += 2; continue }
        if (ch == quote) {
          if (i + 1 < text.length && text[i + 1] == quote) { i += 2; continue }
          quote = null
        }
        i += 1
        continue
      }
      when (ch) {
        '\'', '"', '`' -> { quote = ch; i += 1 }
        '(' -> { depth += 1; i += 1 }
        ')' -> {
          depth -= 1
          if (depth == 0) return i
          i += 1
        }
        else -> i += 1
      }
    }
    return null
  }

  private fun findNextSemicolon(text: String, start: Int): Int? {
    var quote: Char? = null
    var i = start
    while (i < text.length) {
      val ch = text[i]
      if (quote != null) {
        if (ch == '\\' && i + 1 < text.length) { i += 2; continue }
        if (ch == quote) {
          if (i + 1 < text.length && text[i + 1] == quote) { i += 2; continue }
          quote = null
        }
        i += 1
        continue
      }
      when (ch) {
        '\'', '"', '`' -> { quote = ch; i += 1 }
        ';' -> return i
        else -> i += 1
      }
    }
    return null
  }

  private fun skipIdentifier(text: String, start: Int): Int {
    if (start >= text.length) return start
    val first = text[start]
    if (first == '`' || first == '"') {
      var i = start + 1
      while (i < text.length) {
        val ch = text[i]
        if (ch == '\\' && i + 1 < text.length) { i += 2; continue }
        if (ch == first) {
          if (i + 1 < text.length && text[i + 1] == first) { i += 2; continue }
          return i + 1
        }
        i += 1
      }
      return i
    }
    var i = start
    while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_' || text[i] == '.')) i += 1
    return i
  }

  private fun skipWhitespace(text: String, start: Int): Int {
    var i = start
    while (i < text.length && text[i].isWhitespace()) i += 1
    return i
  }

  private fun matchesKeyword(text: String, start: Int, keyword: String): Boolean {
    if (start < 0 || start + keyword.length > text.length) return false
    if (!text.regionMatches(start, keyword, 0, keyword.length, ignoreCase = true)) return false
    val after = text.getOrNull(start + keyword.length)
    return after == null || !after.isLetterOrDigit() && after != '_'
  }

  internal fun offsetToLineColumn(text: String, offset: Int): Pair<Int, Int> {
    var line = 1
    var lineStart = 0
    for (i in 0 until offset.coerceAtMost(text.length)) {
      if (text[i] == '\n') {
        line += 1
        lineStart = i + 1
      }
    }
    return Pair(line, offset - lineStart + 1)
  }
}
