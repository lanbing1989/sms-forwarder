package com.lanbing.smsforwarder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object LogStore {
    private const val PREFS = "sms_forwarder_logs"
    private const val KEY_LOGS = "logs_json"
    private const val MAX_ENTRIES = 200
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun append(context: Context, text: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_LOGS, "[]"))
        val obj = JSONObject()
        obj.put("t", sdf.format(Date()))
        obj.put("m", text)
        // 新放到头部
        val newArr = JSONArray()
        newArr.put(obj)
        for (i in 0 until arr.length()) {
            if (newArr.length() >= MAX_ENTRIES) break
            newArr.put(arr.get(i))
        }
        prefs.edit().putString(KEY_LOGS, newArr.toString()).apply()
    }

    fun readAll(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_LOGS, "[]"))
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val t = o.optString("t", "")
            val m = o.optString("m", "")
            out.add("[$t] $m")
        }
        return out
    }

    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LOGS, "[]").apply()
    }

    fun latest(context: Context): String {
        val list = readAll(context)
        return if (list.isEmpty()) "暂无日志" else list.first()
    }
}