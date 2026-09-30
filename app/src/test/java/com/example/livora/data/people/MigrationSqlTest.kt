package com.example.livora.data.people

import com.example.livora.data.people.db.PeopleMigrationSql
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

class MigrationSqlTest {

    private lateinit var connection: Connection

    private class Column(val name: String, val type: String, val notNull: Boolean, val default: String?)

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
            File("schemas/com.example.livora.data.people.db.PeopleDatabase/$version.json"),
            File("app/schemas/com.example.livora.data.people.db.PeopleDatabase/$version.json")
        )
        val file = candidates.first { it.exists() }
        return JSONObject(file.readText()).getJSONObject("database")
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
                    out[rs.getString("name")] = Column(rs.getString("name"), rs.getString("type"), rs.getInt("notnull") == 1, rs.getString("dflt_value"))
                }
            }
        }
        return out
    }

    private fun tables(): Set<String> {
        val out = HashSet<String>()
        connection.createStatement().use { st ->
            st.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name != 'room_master_table'").use { rs ->
                while (rs.next()) out.add(rs.getString(1))
            }
        }
        return out
    }

    private fun assertMatchesSchema(version: Int) {
        val entities = schema(version).getJSONArray("entities")
        val expectedTables = HashSet<String>()
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            expectedTables.add(table)
            val fields = entity.getJSONArray("fields")
            val actual = columns(table)
            val expectedNames = (0 until fields.length()).map { fields.getJSONObject(it).getString("columnName") }.toSet()
            assertEquals("column names of $table", expectedNames, actual.keys)
            for (j in 0 until fields.length()) {
                val field = fields.getJSONObject(j)
                val name = field.getString("columnName")
                val column = actual.getValue(name)
                assertEquals("type of $name", field.getString("affinity"), column.type)
                assertEquals("notNull of $name", field.optBoolean("notNull", false), column.notNull)
                if (field.has("defaultValue")) {
                    assertEquals("default of $name", field.getString("defaultValue"), column.default)
                }
            }
        }
        assertEquals(expectedTables, tables())
    }

    private fun exec(sql: String) {
        connection.createStatement().use { it.execute(sql) }
    }

    private fun scalar(sql: String): String? {
        connection.createStatement().use { st ->
            st.executeQuery(sql).use { rs -> return if (rs.next()) rs.getString(1) else null }
        }
    }

    private fun seedVersionOne() {
        exec("INSERT INTO persons (id, name, hidden, kind, createdAt, threshold, linkedFolderPath, linkedFolderName, linkMode) VALUES (7, 'Ana', 0, 1, 100, 0.62, 'Pictures/Ana/', 'Ana', 1)")
        exec("INSERT INTO persons (id, name, hidden, kind, createdAt, threshold, linkMode) VALUES (8, NULL, 1, 0, 200, 0.62, 0)")
        exec("INSERT INTO photos (mediaId, dateModified, dateTaken, scannedAt, faceCount, status, orientation) VALUES (501, 1700, 1700000, 5, 2, 0, 90)")
        exec("INSERT INTO photos (mediaId, dateModified, dateTaken, scannedAt, faceCount, status, orientation) VALUES (502, 1701, 1701000, 6, 0, 1, 0)")
        exec("INSERT INTO faces (id, mediaId, boxLeft, boxTop, boxRight, boxBottom, score, quality, embedding, personId, locked) VALUES (1, 501, 0.1, 0.1, 0.4, 0.5, 0.9, 0.8, x'00010203', 7, 1)")
        exec("INSERT INTO person_references (id, personId, embedding, quality, createdAt) VALUES (3, 7, x'0405', 0.9, 10)")
    }

    @Test
    fun versionOneToThreeKeepsAllData() {
        createFrom(1)
        assertMatchesSchema(1)
        seedVersionOne()
        PeopleMigrationSql.V1_TO_V2.forEach { exec(it) }
        assertMatchesSchema(2)
        PeopleMigrationSql.V2_TO_V3.forEach { exec(it) }
        assertMatchesSchema(3)
        assertEquals("2", scalar("SELECT COUNT(*) FROM persons"))
        assertEquals("Ana", scalar("SELECT name FROM persons WHERE id = 7"))
        assertEquals("Pictures/Ana/", scalar("SELECT linkedFolderPath FROM persons WHERE id = 7"))
        assertEquals("0", scalar("SELECT pinned FROM persons WHERE id = 7"))
        assertEquals("2", scalar("SELECT COUNT(*) FROM photos"))
        assertEquals("90", scalar("SELECT orientation FROM photos WHERE mediaId = 501"))
        assertEquals("-1", scalar("SELECT size FROM photos WHERE mediaId = 501"))
        assertEquals("1", scalar("SELECT status FROM photos WHERE mediaId = 502"))
        assertEquals("0", scalar("SELECT retryCount FROM photos WHERE mediaId = 502"))
        assertEquals("1", scalar("SELECT pipelineVersion FROM photos WHERE mediaId = 501"))
        assertEquals("1", scalar("SELECT modelVersion FROM faces WHERE id = 1"))
        assertEquals("1", scalar("SELECT pipelineVersion FROM faces WHERE id = 1"))
        assertEquals("1", scalar("SELECT locked FROM faces WHERE id = 1"))
        assertEquals("1", scalar("SELECT COUNT(*) FROM person_references"))
        assertEquals("0", scalar("SELECT COUNT(*) FROM person_separations"))
    }

    @Test
    fun versionTwoToThreeKeepsAllData() {
        createFrom(2)
        assertMatchesSchema(2)
        exec("INSERT INTO persons (id, name, hidden, kind, createdAt, threshold, linkMode, pinned) VALUES (1, 'Kim', 0, 0, 1, 0.62, 0, 1)")
        exec("INSERT INTO person_separations (personA, personB) VALUES (1, 2)")
        exec("INSERT INTO photos (mediaId, dateModified, dateTaken, scannedAt, faceCount, status, orientation) VALUES (9, 1, 1, 1, 1, 0, 0)")
        PeopleMigrationSql.V2_TO_V3.forEach { exec(it) }
        assertMatchesSchema(3)
        assertEquals("1", scalar("SELECT pinned FROM persons WHERE id = 1"))
        assertEquals("1", scalar("SELECT COUNT(*) FROM person_separations"))
        assertEquals("-1", scalar("SELECT size FROM photos WHERE mediaId = 9"))
    }

    @Test
    fun versionThreeToFourAddsAiMoves() {
        createFrom(3)
        assertMatchesSchema(3)
        exec("INSERT INTO persons (id, name, hidden, kind, createdAt, threshold, linkMode, pinned) VALUES (1, 'Kim', 0, 0, 1, 0.62, 0, 0)")
        PeopleMigrationSql.V3_TO_V4.forEach { exec(it) }
        assertMatchesSchema(4)
        assertEquals("Kim", scalar("SELECT name FROM persons WHERE id = 1"))
        exec("INSERT INTO ai_moves (mediaId, personId, sourceMediaId, kind, fromPath, toPath, movedAt) VALUES (5, 1, 5, 0, 'DCIM/Camera/', 'Pictures/Kim/', 10)")
        assertEquals("DCIM/Camera/", scalar("SELECT fromPath FROM ai_moves WHERE mediaId = 5"))
    }

    @Test
    fun schemaFilesExistForEveryVersion() {
        for (v in 1..4) assertNotNull(schema(v))
        assertTrue(schema(4).getInt("version") == 4)
    }
}
