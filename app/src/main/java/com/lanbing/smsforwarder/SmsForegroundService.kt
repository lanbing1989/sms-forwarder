package com.lanbing.smsforwarder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.app.PendingIntent
import android.provider.Settings
import android.util.Log

class SmsForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "sms_forwarder_channel"
        const val CHANNEL_NAME = "短信转发服务"
        const val NOTIF_ID = 1423
        const val ACTION_UPDATE = "com.lanbing.smsforwarder.ACTION_LOG_UPDATED"
        const val ACTION_STOP = "com.lanbing.smsforwarder.ACTION_STOP_SERVICE"
        private const val TAG = "SmsForegroundService"
    }

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                val action = intent?.action
                if (action == ACTION_STOP) {
                    stopSelf()
                    LogStore.append(applicationContext, "收到通知停止服务请求，服务已停止")
                    return
                }
                updateNotification()
            } catch (t: Throwable) {
                Log.w(TAG, "updateNotification failed", t)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        try {
            registerReceiver(updateReceiver, IntentFilter(ACTION_UPDATE))
            registerReceiver(updateReceiver, IntentFilter(ACTION_STOP))
        } catch (t: Throwable) {
            Log.w(TAG, "registerReceiver failed", t)
        }
    }

    private fun createChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = getSystemService(NotificationManager::class.java)
                if (nm != null) {
                    val importance = NotificationManager.IMPORTANCE_HIGH
                    val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance)
                    channel.setShowBadge(false)
                    channel.lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                    nm.createNotificationChannel(channel)
                } else {
                    Log.w(TAG, "NotificationManager is null when creating channel")
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "createChannel failed", t)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 先构建通知（失败时有回退），然后再 startForeground（包裹异常）
        val notification: Notification = try {
            buildNotification()
        } catch (t: Throwable) {
            Log.w(TAG, "buildNotification failed, use fallback", t)
            // 最小回退通知，确保 startForeground 有一个合法通知对象
            val fallbackIcon = resolveSmallIcon()
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("短信转发助手")
                .setContentText("服务正在运行")
                .setSmallIcon(fallbackIcon)
                .setOngoing(true)
                .build()
        }

        try {
            startForeground(NOTIF_ID, notification)
        } catch (t: Throwable) {
            // 如果 startForeground 抛异常，在某些定制系统上会直接导致应用崩溃，尽量记录并优雅停止服务
            Log.w(TAG, "startForeground failed, stopping service", t)
            LogStore.append(applicationContext, "ERROR: startForeground failed: ${t.javaClass.simpleName} ${t.message}")
            stopSelf()
            return START_NOT_STICKY
        }

        // 记录诊断到日志：channel 与权限状态（便于无 adb 情况下调试）
        try {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) nm?.getNotificationChannel(CHANNEL_ID) else null
            val chInfo = if (channel != null) "channel(${channel.id}): importance=${channel.importance} name=${channel.name}" else "channel:null"
            val notifAllowed = NotificationManagerCompat.from(this).areNotificationsEnabled()
            LogStore.append(applicationContext, "DEBUG: notifAllowed=$notifAllowed ; $chInfo")
        } catch (t: Throwable) {
            LogStore.append(applicationContext, "DEBUG: 检查 channel 失败: ${t.message}")
        }

        // 也再次 notify 一遍以兼容某些 ROM（但不要让它成为崩溃点）
        try {
            val nm2 = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm2?.notify(NOTIF_ID, notification)
        } catch (t: Throwable) {
            Log.w(TAG, "extra notify failed", t)
        }
        return START_STICKY
    }

    private fun resolveSmallIcon(): Int {
        // 优先使用 mipmap 中的 ic_notification（如果你把图片放在 mipmap 下）
        val mipmapId = resources.getIdentifier("ic_notification", "mipmap", packageName)
        if (mipmapId != 0) return mipmapId
        // 如果没放到 mipmap，再尝试 drawable
        val drawableId = resources.getIdentifier("ic_notification", "drawable", packageName)
        if (drawableId != 0) return drawableId
        // 回退到应用自带 icon 或 launcher icon
        val appIcon = applicationInfo.icon
        if (appIcon != 0) return appIcon
        return R.mipmap.ic_launcher
    }

    private fun buildNotification(): Notification {
        val prefs = getSharedPreferences("app_config", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("enabled", false)
        val status = if (enabled) "已启用" else "已禁用"
        val latest = LogStore.latest(this)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("短信转发助手 - $status")
            .setContentText(latest)
            .setSmallIcon(resolveSmallIcon())
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)

        // 兼容不同 API 的 PendingIntent flags
        val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        // 点击通知回到主界面（显式化包名）
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            `package` = packageName
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, mainIntent, piFlags)
        builder.setContentIntent(pendingIntent)

        // 停止服务 action（显式化包名）
        val stopIntent = Intent(ACTION_STOP).apply { `package` = packageName }
        val stopPending = PendingIntent.getBroadcast(this, 1, stopIntent, piFlags)
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止服务", stopPending)

        // 添加“通知设置” action（跳转到 channel 设置页，Android O+），显式包名，避免某些 ROM 拦截或拒绝隐式 intent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                `package` = packageName
            }
            val pi = PendingIntent.getActivity(this, 2, intent, piFlags)
            builder.addAction(android.R.drawable.ic_menu_manage, "通知设置", pi)
        } else {
            // 低版本跳转到应用设置页
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                `package` = packageName
            }
            val pi = PendingIntent.getActivity(this, 3, intent, piFlags)
            builder.addAction(android.R.drawable.ic_menu_manage, "应用设置", pi)
        }

        return builder.build()
    }

    private fun updateNotification() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID, buildNotification())
        } catch (t: Throwable) {
            Log.w(TAG, "updateNotification failed", t)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(updateReceiver) } catch (e: Exception) { /* ignore */ }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}