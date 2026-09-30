package com.example.livora.data.db

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AppMigrationSqlTest {

    private lateinit var connection: Connection

    private class Column(val type: String, val notNull: Boolean, val default: String?)

    @Before
    fun open() {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:")
    }

    @After
    fun close() {
        connection.close()
    }

    private fun schema(version: Int): JSONObject {
        val candidates = listOf(
            File("schemas/com.example.livora.data.db.AppDatabase/$version.json"),
            File("app/schemas/com.example.livora.data.db.AppDatabase/$version.json")
        )
        return JSONObject(candidates.first { it.exists() }.readText()).getJSONObject("database")
    }

    private fun createFrom(version: Int) {
        val entities = schema(version).getJSONArray("entities")
        connection.createStatement().use { st ->
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                st.execute(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                if (indices != null) {
                    for (j in 0 until indices.length()) {
                        st.execute(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                }
            }
        }
    }

    private fun columns(table: String): Map<String, Column> {
        val out = LinkedHashMap<String, Column>()
        connection.createStatement().use { st ->
            st.executeQuery("PRAGMA table_info(`$table`)").use { rs ->
                while (rs.next()) {
                    out[rs.getString("name")] = Column(rs.getString("type"), rs.getInt("notnull") == 1, rs.getString("dflt_value"))
                }
            }
        }
        return out
    }

    private fun assertMatchesSchema(version: Int) {
        val entities = schema(version).getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            val fields = entity.getJSONArray("fields")
            val actual = columns(table)
            val expected = (0 until fields.length()).map { fields.getJSONObject(it).getString("columnName") }.toSet()
            assertEquals("columns of $table", expected, actual.keys)
            for (j in 0 until fields.length()) {
                val field = fields.getJSONObject(j)
                val column = actual.getValue(field.getString("columnName"))
                assertEquals(field.getString("affinity"), column.type)
                assertEquals(field.optBoolean("notNull", false), column.notNull)
            }
        }
    }

    private fun exec(sql: String) {
        connection.createStatement().use { it.execute(sql) }
    }

    private fun scalar(sql: String): String? {
        connection.createStatement().use { st ->
            st.executeQuery(sql).use { rs -> return if (rs.next()) rs.getString(1) else null }
        }
    }

    @Test
    fun versionOneToTwoKeepsDataAndAddsIcons() {
        createFrom(1)
        assertMatchesSchema(1)
        exec("INSERT INTO expense_categories (id, name, income, slot, position) VALUES (1, 'Food', 0, 1, 0)")
        exec("INSERT INTO expense_categories (id, name, income, slot, position) VALUES (2, 'Other income', 1, 5, 1)")
        exec("INSERT INTO expense_categories (id, name, income, slot, position) VALUES (3, 'Coffee beans', 0, 3, 2)")
        exec("INSERT INTO expense_accounts (id, name, position) VALUES (1, 'Cash', 0)")
        exec("INSERT INTO expense_transactions (id, amount, categoryId, accountId, note, day, createdAt) VALUES (1, -38000, 1, 1, 'Lunch', 20000, 5)")

        AppMigrationSql.V1_TO_V2.forEach { exec(it) }

        assertMatchesSchema(2)
        assertEquals("food", scalar("SELECT iconKey FROM expense_categories WHERE id = 1"))
        assertEquals("income", scalar("SELECT iconKey FROM expense_categories WHERE id = 2"))
        assertEquals("other", scalar("SELECT iconKey FROM expense_categories WHERE id = 3"))
        assertEquals("-38000", scalar("SELECT amount FROM expense_transactions WHERE id = 1"))
        assertEquals("Cash", scalar("SELECT name FROM expense_accounts WHERE id = 1"))
    }

    @Test
    fun versionTwoToThreeAddsUsageTables() {
        createFrom(2)
        assertMatchesSchema(2)
        exec("INSERT INTO expense_accounts (id, name, position) VALUES (1, 'Cash', 0)")
        AppMigrationSql.V2_TO_V3.forEach { exec(it) }
        assertMatchesSchema(3)
        assertEquals("Cash", scalar("SELECT name FROM expense_accounts WHERE id = 1"))
        exec("INSERT INTO usage_days (day, packageName, millis) VALUES (20000, 'a.b', 1000)")
        exec("INSERT INTO usage_hours (hourStart, millis) VALUES (480000, 500)")
        assertEquals("1000", scalar("SELECT millis FROM usage_days WHERE packageName = 'a.b'"))
    }

    @Test
    fun versionThreeToFourAddsTheKeptTable() {
        createFrom(3)
        assertMatchesSchema(3)
        exec("INSERT INTO usage_days (day, packageName, millis) VALUES (20000, 'a.b', 1000)")
        AppMigrationSql.V3_TO_V4.forEach { exec(it) }
        assertMatchesSchema(4)
        assertEquals("1000", scalar("SELECT millis FROM usage_days WHERE packageName = 'a.b'"))
        exec("INSERT INTO cleaner_kept (fileKey, keptAt) VALUES ('i:5', 100)")
        assertEquals("i:5", scalar("SELECT fileKey FROM cleaner_kept"))
    }

    @Test
    fun versionFourToFiveAddsTheQrHistory() {
        createFrom(4)
        assertMatchesSchema(4)
        exec("INSERT INTO cleaner_kept (fileKey, keptAt) VALUES ('i:5', 100)")
        AppMigrationSql.V4_TO_V5.forEach { exec(it) }
        assertMatchesSchema(5)
        assertEquals("i:5", scalar("SELECT fileKey FROM cleaner_kept"))
        exec("INSERT INTO qr_history (value, kind, scannedAt, fromPhoto) VALUES ('https://a.b', 'Website', 5, 0)")
        assertEquals("1", scalar("SELECT id FROM qr_history"))
    }

    @Test
    fun seedRowsMatchTheCurrentSchema() {
        createFrom(5)
        AppSeed.SQL.forEach { exec(it) }
        assertEquals("12", scalar("SELECT COUNT(*) FROM expense_categories"))
        assertEquals("3", scalar("SELECT COUNT(*) FROM expense_accounts"))
        assertEquals("0", scalar("SELECT COUNT(*) FROM expense_categories WHERE iconKey = ''"))
    }
}
