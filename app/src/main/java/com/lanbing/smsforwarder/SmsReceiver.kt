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
import java.io.IOException
import java.util.concurrent.Executors

class SmsReceiver : BroadcastReceiver() {

    private val client = OkHttpClient()
    // 使用单线程池处理网络请求
    private val executor = Executors.newSingleThreadExecutor()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val prefs = context.getSharedPreferences("app_config", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("enabled", false)
        if (!isEnabled) return

        val webhookUrl = prefs.getString("webhook", "") ?: ""
        if (webhookUrl.isEmpty() || !webhookUrl.startsWith("http")) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val sb = StringBuilder()
        var sender = ""
        
        for (sms in messages) {
            sender = sms.displayOriginatingAddress
            sb.append(sms.displayMessageBody)
        }
        val fullMessage = sb.toString()
        
        // 关键词检查
        val keywordsStr = prefs.getString("keywords", "") ?: ""
        val keywords = keywordsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        
        // 逻辑：如果关键词列表为空 -> 匹配成功(全部转发)
        // 如果关键词列表不为空 -> 必须包含其中一个
        val isMatch = if (keywords.isEmpty()) {
            true 
        } else {
            keywords.any { fullMessage.contains(it, ignoreCase = true) }
        }

        if (isMatch) {
            // Android 广播接收器只有10秒寿命，必须使用 goAsync 延长寿命进行网络请求
            val pendingResult = goAsync()
            executor.execute {
                try {
                    sendToWecom(webhookUrl, sender, fullMessage)
                } catch (e: Exception) {
                    Log.e("SmsForwarder", "Error sending msg", e)
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
        textObj.put("content", "【收到短信】\n发送人: $sender\n内容: $content")
        json.put("text", textObj)

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            // 这里仅仅是触发请求，不处理返回值
            Log.d("SmsForwarder", "Response: ${response.code}")
        }
    }
}