package com.yacunne.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Conversation(
    val id: String,
    val title: String,
    val messages: List<ChatMessage>
)

object ChatStore {
    private const val PREFS = "yacunne_chats"
    private const val KEY = "conversations"

    fun load(context: Context): List<Conversation> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val result = mutableListOf<Conversation>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val msgs = o.getJSONArray("messages")
                val list = mutableListOf<ChatMessage>()
                for (j in 0 until msgs.length()) {
                    val m = msgs.getJSONObject(j)
                    list.add(ChatMessage(m.getString("text"), m.getBoolean("user")))
                }
                result.add(Conversation(o.getString("id"), o.getString("title"), list))
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, conversations: List<Conversation>) {
        try {
            val arr = JSONArray()
            for (c in conversations) {
                val msgs = JSONArray()
                for (m in c.messages) {
                    msgs.put(JSONObject().put("text", m.text).put("user", m.fromUser))
                }
                arr.put(
                    JSONObject()
                        .put("id", c.id)
                        .put("title", c.title)
                        .put("messages", msgs)
                )
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, arr.toString())
                .apply()
        } catch (e: Exception) {
            // ntacyo dukora: kubika bivuyemo, app ikomeza gukora
        }
    }
}
