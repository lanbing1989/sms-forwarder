package com.lanbing.smsforwarder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.ServiceInfo
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

/**
 * 前台服务：负责维持持续通知并在收到广播（如 SmsReceiver/BootReceiver）时更新通知。
 *
 * 关键点：
 * - 在 API>=34（Android 14）上，需要在运行时调用 startForeground 的重载并传入类型位掩码（remoteMessaging）。
 * - AndroidManifest.xml 中也必须声明 android:foregroundServiceType="remoteMessaging"。
 * - 在异常情况下尽量记录并优雅停止服务，避免抛异常导致进程崩溃。
 */
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
        // 先创建通知通道（如果需要）
        createChannel()
        try {
            // 注册用来刷新通知或停止服务的广播
            val filter = IntentFilter().apply {
                addAction(ACTION_UPDATE)
                addAction(ACTION_STOP)
            }
            registerReceiver(updateReceiver, filter)
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
                    // 可根据需要自定义 sound / vibration / light 等
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
        // 构建通知，若失败使用最小回退通知保证 startForeground 能拿到 Notification 实例
        val notification: Notification = try {
            buildNotification()
        } catch (t: Throwable) {
            Log.w(TAG, "buildNotification failed, use fallback", t)
            val fallbackIcon = resolveSmallIcon()
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("短信转发助手")
                .setContentText("服务正在运行")
                .setSmallIcon(fallbackIcon)
                .setOngoing(true)
                .build()
        }

        try {
            // Android 14+ 要在运行时传入对应类型
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (t: Throwable) {
            // 在一些定制 ROM（比如更严格的权限/策略）上 startForeground 可能抛出异常
            Log.w(TAG, "startForeground failed, stopping service", t)
            LogStore.append(applicationContext, "ERROR: startForeground failed: ${t.javaClass.simpleName} ${t.message}")
            // 优雅退出，避免无限重试或崩溃循环
            stopSelf()
            return START_NOT_STICKY
        }

        // 记录诊断信息供无 adb 环境下调试
        try {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) nm?.getNotificationChannel(CHANNEL_ID) else null
            val chInfo = if (channel != null) "channel(${channel.id}): importance=${channel.importance} name=${channel.name}" else "channel:null"
            val notifAllowed = NotificationManagerCompat.from(this).areNotificationsEnabled()
            LogStore.append(applicationContext, "DEBUG: notifAllowed=$notifAllowed ; $chInfo")
        } catch (t: Throwable) {
            LogStore.append(applicationContext, "DEBUG: 检查 channel 失败: ${t.message}")
        }

        // 额外再 notify 一次以兼容某些 ROM 的通知行为
        try {
            val nm2 = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm2?.notify(NOTIF_ID, notification)
        } catch (t: Throwable) {
            Log.w(TAG, "extra notify failed", t)
        }

        return START_STICKY
    }

    private fun resolveSmallIcon(): Int {
        // 优先查找自定义通知图标
        val mipmapId = resources.getIdentifier("ic_notification", "mipmap", packageName)
        if (mipmapId != 0) return mipmapId
        val drawableId = resources.getIdentifier("ic_notification", "drawable", packageName)
        if (drawableId != 0) return drawableId
        val appIcon = applicationInfo.icon
        if (appIcon != 0) return appIcon
        return android.R.drawable.ic_dialog_info
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

        // PendingIntent flags 兼容处理
        val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        // 主界面 Intent（显式包名以避免被 ROM 拦截）
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            `package` = packageName
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, mainIntent, piFlags)
        builder.setContentIntent(pendingIntent)

        // 停止服务 action（广播）
        val stopIntent = Intent(ACTION_STOP).apply { `package` = packageName }
        val stopPending = PendingIntent.getBroadcast(this, 1, stopIntent, piFlags)
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止服务", stopPending)

        // 通知设置（跳转到 channel 设置页或应用设置页）
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