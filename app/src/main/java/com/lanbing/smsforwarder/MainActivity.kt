package com.lanbing.smsforwarder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var requestSmsPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var requestNotifPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestSmsPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                Toast.makeText(this, "短信权限已授权", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "请授予短信权限以接收短信", Toast.LENGTH_LONG).show()
            }
        }

        requestNotifPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                Toast.makeText(this, "通知权限已授权", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "请允许通知权限以显示常驻通知", Toast.LENGTH_LONG).show()
            }
        }

        setContent {
            SmsForwarderApp(
                onRequestSmsPermission = { requestSmsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS) },
                onRequestNotificationPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestNotifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onStartService = { startServiceWithNotificationCheck() },
                onStopService = { stopService(Intent(this, SmsForegroundService::class.java)) }
            )
        }
    }

    private fun startServiceWithNotificationCheck() {
        val pkg = packageName
        val notifEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
        if (!notifEnabled) {
            // 引导用户到应用的通知设置页（更醒目）
            Toast.makeText(this, "请允许应用通知（将打开通知设置）", Toast.LENGTH_LONG).show()
            val i = Intent().apply {
                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
            }
            startActivity(i)
            return
        }

        // 检查短信权限
        val smsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        if (!smsGranted) {
            requestSmsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
            return
        }

        // 启动前台服务
        val svc = Intent(this, SmsForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc)
        } else {
            startService(svc)
        }
    }
}

@Composable
fun SmsForwarderApp(
    onRequestSmsPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("app_config", Context.MODE_PRIVATE)

    var webhookUrl by remember { mutableStateOf(prefs.getString("webhook", "") ?: "") }
    var keywords by remember { mutableStateOf(prefs.getString("keywords", "") ?: "") }
    var isEnabled by remember { mutableStateOf(prefs.getBoolean("enabled", false)) }
    var startOnBoot by remember { mutableStateOf(prefs.getBoolean("start_on_boot", false)) }
    var logs by remember { mutableStateOf(LogStore.readAll(context)) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("短信转发助手", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = webhookUrl,
                onValueChange = { webhookUrl = it },
                label = { Text("企业微信 Webhook 地址") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = keywords,
                onValueChange = { keywords = it },
                label = { Text("关键词 (逗号分隔，留空转发全部)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("启用转发服务")
                Switch(checked = isEnabled, onCheckedChange = { checked ->
                    isEnabled = checked
                    prefs.edit().putBoolean("enabled", isEnabled).apply()
                    if (checked) {
                        // 请求通知权限（如果需要）和短信权限（如果需要）
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val hasNotif = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                            if (!hasNotif) onRequestNotificationPermission()
                        }
                        val hasSms = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
                        if (!hasSms) onRequestSmsPermission()
                        // 启动前台服务（Activity 层会做二次检查）
                        onStartService()
                        LogStore.append(context, "服务已启动（由用户开启）")
                    } else {
                        onStopService()
                        LogStore.append(context, "服务已停止（由用户关闭）")
                    }
                    // 更新通知
                    context.sendBroadcast(Intent(SmsForegroundService.ACTION_UPDATE))
                })
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("开机启动")
                Switch(checked = startOnBoot, onCheckedChange = {
                    startOnBoot = it
                    prefs.edit().putBoolean("start_on_boot", startOnBoot).apply()
                    if (startOnBoot) {
                        LogStore.append(context, "已开启开机启动（注意：请在系统设置中允许自启动/省电豁免）")
                    } else {
                        LogStore.append(context, "已关闭开机启动")
                    }
                })
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = {
                    prefs.edit().apply {
                        putString("webhook", webhookUrl.trim())
                        putString("keywords", keywords)
                        putBoolean("enabled", isEnabled)
                        putBoolean("start_on_boot", startOnBoot)
                        apply()
                    }
                    Toast.makeText(context, "配置已保存", Toast.LENGTH_SHORT).show()
                    LogStore.append(context, "配置已保存")
                    context.sendBroadcast(Intent(SmsForegroundService.ACTION_UPDATE))
                }) {
                    Text("保存配置")
                }

                Row {
                    Button(onClick = {
                        // 打开通知设置页
                        onRequestNotificationPermission()
                    }) {
                        Text("打开通知权限")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        // 打开应用详情设置（用户可在此设置自启动/省电等）
                        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        i.data = android.net.Uri.parse("package:" + context.packageName)
                        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(i)
                    }) {
                        Text("打开应用设置")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("运行日志（最新在上）", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    LogStore.clear(context)
                    logs = LogStore.readAll(context)
                    context.sendBroadcast(Intent(SmsForegroundService.ACTION_UPDATE))
                }) {
                    Text("清除日志")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(logs) { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp))
                    Divider()
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = { logs = LogStore.readAll(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("刷新日志")
            }
        }
    }
}