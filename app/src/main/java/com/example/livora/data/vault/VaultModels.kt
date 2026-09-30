package com.example.livora.data.vault

import org.json.JSONArray
import org.json.JSONObject

data class VaultEntry(
    val id: String,
    val title: String,
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val notes: String = "",
    val totpSecret: String = "",
    val favorite: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

object VaultJson {

    fun encode(entries: List<VaultEntry>): ByteArray {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("title", entry.title)
                    .put("username", entry.username)
                    .put("password", entry.password)
                    .put("url", entry.url)
                    .put("notes", entry.notes)
                    .put("totp", entry.totpSecret)
                    .put("favorite", entry.favorite)
                    .put("created", entry.createdAt)
                    .put("updated", entry.updatedAt)
            )
        }
        return JSONObject().put("v", 1).put("entries", array).toString().toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray): List<VaultEntry> {
        val root = JSONObject(String(bytes, Charsets.UTF_8))
        val array = root.getJSONArray("entries")
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            VaultEntry(
                id = item.getString("id"),
                title = item.optString("title"),
                username = item.optString("username"),
                password = item.optString("password"),
                url = item.optString("url"),
                notes = item.optString("notes"),
                totpSecret = item.optString("totp"),
                favorite = item.optBoolean("favorite"),
                createdAt = item.optLong("created"),
                updatedAt = item.optLong("updated")
            )
        }
    }
}
