package com.lanbing.smsforwarder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.app.PendingIntent

class SmsForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "sms_forwarder_channel"
        const val CHANNEL_NAME = "短信转发服务"
        const val NOTIF_ID = 1423
        const val ACTION_UPDATE = "com.lanbing.smsforwarder.ACTION_LOG_UPDATED"
        const val ACTION_STOP = "com.lanbing.smsforwarder.ACTION_STOP_SERVICE"
    }

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // 更新通知内容
            updateNotification()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        registerReceiver(updateReceiver, IntentFilter(ACTION_UPDATE))
        // 不在这里注册 ACTION_STOP 为避免误触发；如需增加停止按钮可在通知上添加 action
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
            channel.setShowBadge(false)
            nm.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 确保启动为前台服务
        startForeground(NOTIF_ID, buildNotification())
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val prefs = getSharedPreferences("app_config", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("enabled", false)
        val status = if (enabled) "已启用" else "已禁用"
        val latest = LogStore.latest(this)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("短信转发助手 - $status")
            .setContentText(latest)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // 保证系统图标可用
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)

        // 点击通知回到主界面
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP },
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
        )
        builder.setContentIntent(pendingIntent)

        return builder.build()
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification())
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(updateReceiver)
        } catch (e: Exception) {
            // ignore
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}