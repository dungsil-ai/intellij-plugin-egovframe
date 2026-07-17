package kr.kyg.intellij.plugin.egovframe.ddl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 1:1 port of upstream `webview-ui/src/utils/__tests__/erdParser.spec.ts`. */
class ErdParserTest {

  @Test
  fun `parses tables and table-level foreign key relationships`() {
    val model = ErdParser.parseErdModel(
      """
            CREATE TABLE users (
                id INT PRIMARY KEY AUTO_INCREMENT,
                name VARCHAR(100) NOT NULL
            );

            CREATE TABLE orders (
                id INT PRIMARY KEY AUTO_INCREMENT,
                user_id INT NOT NULL,
                CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id)
            );
        """
    )

    assertEquals(2, model.tables.size)
    assertTrue(model.tables[0].columns.find { it.name == "id" }!!.isPrimaryKey)
    assertTrue(model.tables[1].columns.find { it.name == "user_id" }!!.isForeignKey)
    assertEquals(1, model.relationships.size)
    model.relationships[0].let {
      assertEquals("orders", it.fromTable)
      assertEquals("user_id", it.fromColumn)
      assertEquals("users", it.toTable)
      assertEquals("id", it.toColumn)
    }
  }

  @Test
  fun `parses inline foreign key references`() {
    val model = ErdParser.parseErdModel(
      """
            CREATE TABLE users (
                id INT PRIMARY KEY
            );

            CREATE TABLE comments (
                id INT PRIMARY KEY,
                user_id INT REFERENCES users(id)
            );
        """
    )

    assertEquals(1, model.relationships.size)
    model.relationships[0].let {
      assertEquals("comments", it.fromTable)
      assertEquals("user_id", it.fromColumn)
      assertEquals("users", it.toTable)
      assertEquals("id", it.toColumn)
    }
  }
}
