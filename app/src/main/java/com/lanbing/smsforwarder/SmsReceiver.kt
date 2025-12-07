package com.lanbing.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.Executors

class SmsReceiver : BroadcastReceiver() {

    private val client = OkHttpClient()
    private val executor = Executors.newSingleThreadExecutor()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val prefs = context.getSharedPreferences("app_config", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("enabled", false)
        if (!isEnabled) return

        val webhookUrl = prefs.getString("webhook", "") ?: ""
        val keywordsStr = prefs.getString("keywords", "") ?: ""
        
        if (webhookUrl.isEmpty()) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val sb = StringBuilder()
        var sender = ""
        for (sms in messages) {
            sender = sms.displayOriginatingAddress
            sb.append(sms.displayMessageBody)
        }
        val fullMessage = sb.toString()
        
        val keywords = keywordsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val isMatch = if (keywords.isEmpty()) true else keywords.any { fullMessage.contains(it, ignoreCase = true) }

        if (isMatch) {
            val pendingResult = goAsync()
            executor.execute {
                try {
                    sendToWecom(webhookUrl, sender, fullMessage)
                    // 写日志
                    LogStore.append(context, "转发成功 — 来自: $sender 内容: ${fullMessage.take(200)}")
                    // 通知前台服务更新展示
                    context.sendBroadcast(Intent(SmsForegroundService.ACTION_UPDATE))
                } catch (e: Exception) {
                    Log.e("SmsForwarder", "Failed to send", e)
                    LogStore.append(context, "转发失败: ${e.message}")
                    context.sendBroadcast(Intent(SmsForegroundService.ACTION_UPDATE))
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun sendToWecom(url: String, sender: String, content: String) {
        val json = JSONObject()
        json.put("msgtype", "text")
        
        val textObj = JSONObject()
        textObj.put("content", "【短信转发】\n来自: $sender\n内容: $content")
        json.put("text", textObj)

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
        }
    }
}