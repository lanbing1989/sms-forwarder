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
import java.net.MalformedURLException
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
        // 单例 OkHttpClient，配置超时，复用连接池
        val client: OkHttpClient = OkHttpClient.Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        private val executor = Executors.newSingleThreadExecutor()
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val prefs = context.getSharedPreferences("app_config", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("enabled", false)
        if (!isEnabled) return

        val webhookUrl = prefs.getString("webhook", "") ?: ""
        val keywordsStr = prefs.getString("keywords", "") ?: ""

        if (webhookUrl.isBlank()) {
            LogStore.append(context, "未配置 webhook，已跳过转发")
            return
        }

        // 简单校验 URL 格式，避免抛出异常
        if (!isValidUrl(webhookUrl)) {
            LogStore.append(context, " webhook 格式无效：$webhookUrl")
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val sb = StringBuilder()
        var sender = ""
        for (sms in messages) {
            sender = sms.displayOriginatingAddress ?: sender
            sb.append(sms.displayMessageBody)
        }
        val fullMessage = sb.toString()

        val keywords = keywordsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val isMatch = if (keywords.isEmpty()) true else keywords.any { fullMessage.contains(it, ignoreCase = true) }

        if (!isMatch) return

        val pendingResult = goAsync()
        executor.execute {
            try {
                // 重试策略：最多 2 次，指数退避（0ms, 1000ms）
                var attempt = 0
                var success = false
                val maxAttempts = 2
                var backoff = 0L
                while (attempt < maxAttempts && !success) {
                    if (backoff > 0) {
                        try { Thread.sleep(backoff) } catch (_: InterruptedException) { }
                    }
                    try {
                        success = sendToWecom(webhookUrl, sender, fullMessage)
                    } catch (e: Exception) {
                        Log.e(TAG, "send attempt ${attempt+1} failed", e)
                        // 当出现非致命网络异常时，继续重试
                    }
                    attempt++
                    if (!success) backoff = 1000L * attempt
                }

                if (success) {
                    LogStore.append(context, "转发成功 — 来自: $sender 内容: ${fullMessage.take(200)}")
                } else {
                    LogStore.append(context, "转发失败 — 来自: $sender (重试后失败)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send", e)
                LogStore.append(context, "转发异常: ${e.message}")
            } finally {
                // 通知前台服务更新展示（如果在运行）
                try {
                    context.sendBroadcast(Intent(SmsForegroundService.ACTION_UPDATE))
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
                pendingResult.finish()
            }
        }
    }

    private fun isValidUrl(url: String): Boolean {
        return try {
            val u = URL(url)
            (u.protocol == "http" || u.protocol == "https") && u.host.isNotEmpty()
        } catch (t: MalformedURLException) {
            false
        } catch (t: Throwable) {
            false
        }
    }

    private fun sendToWecom(url: String, sender: String, content: String): Boolean {
        try {
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
                val code = response.code
                return code in 200..299
            }
        } catch (t: Throwable) {
            Log.e(TAG, "sendToWecom error", t)
            throw t
        }
    }
}