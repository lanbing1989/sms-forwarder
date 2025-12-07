package com.lanbing.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

            val prefs = context.getSharedPreferences("app_config", Context.MODE_PRIVATE)
            val startOnBoot = prefs.getBoolean("start_on_boot", false)
            val enabled = prefs.getBoolean("enabled", false)
            if (startOnBoot && enabled) {
                val svcIntent = Intent(context, SmsForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, svcIntent)
                } else {
                    context.startService(svcIntent)
                }
                LogStore.append(context, "设备开机：根据设置已启动前台服务")
            } else {
                Log.d(TAG, "开机未启动服务: startOnBoot=$startOnBoot enabled=$enabled")
            }
        } catch (t: Throwable) {
            Log.w("BootReceiver", "onReceive failed", t)
        }
    }
}