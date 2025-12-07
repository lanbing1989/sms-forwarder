package com.lanbing.smsforwarder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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

    // 用于在权限回调后继续启动服务
    private var pendingStartAfterNotifGrant: Boolean = false

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
                // 如果之前需要在授权后启动服务，继续启动
                if (pendingStartAfterNotifGrant) {
                    pendingStartAfterNotifGrant = false
                    startServiceWithNotificationCheck()
                }
            } else {
                Toast.makeText(this, "请允许通知权限以显示常驻通知", Toast.LENGTH_LONG).show()
                // 引导到应用通知设置（更可靠）
                openAppNotificationSettings()
            }
        }

        setContent {
            SmsForwarderApp(
                onRequestSmsPermission = { requestSmsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS) },
                onRequestNotificationPermission = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) requestNotifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                onStartService = { ensureNotifPermissionThenStart() },
                onStopService = { stopService(Intent(this, SmsForegroundService::class.java)) },
                onOpenChannelSettings = { openChannelSettings() },
                onOpenAppSettings = { openAppSettings() }
            )
        }
    }

    private fun ensureNotifPermissionThenStart() {
        // 如果 API >= 33，先检查 POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                // 标记：授权后继续启动服务
                pendingStartAfterNotifGrant = true
                requestNotifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        // 如果已授权或 API < 33，走正常启动检查
        startServiceWithNotificationCheck()
    }

    private fun startServiceWithNotificationCheck() {
        val pkg = packageName
        val notifEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
        if (!notifEnabled) {
            // 引导到应用通知设置
            Toast.makeText(this, "请允许应用通知（将打开通知设置）", Toast.LENGTH_LONG).show()
            openAppNotificationSettings()
            return
        }

        // 检查短信权限
        val smsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        if (!smsGranted) {
            requestSmsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
            return
        }

        val svc = Intent(this, SmsForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc) else startService(svc)
    }

    private fun openAppNotificationSettings() {
        val intent = Intent().apply {
            action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        startActivity(intent)
    }

    private fun openChannelSettings() {
        // 打开本应用的指定 channel 设置页（Android O+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent().apply {
                action = Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, SmsForegroundService.CHANNEL_ID)
            }
            startActivity(intent)
        } else {
            openAppSettings()
        }
    }

    private fun openAppSettings() {
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        i.data = Uri.parse("package:$packageName")
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(i)
    }
}

@Composable
fun SmsForwarderApp(
    onRequestSmsPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onOpenChannelSettings: () -> Unit,
    onOpenAppSettings: () -> Unit
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

            OutlinedTextField(value = webhookUrl, onValueChange = { webhookUrl = it }, label = { Text("企业微信 Webhook 地址") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = keywords, onValueChange = { keywords = it }, label = { Text("关键词 (逗号分隔，留空转发全部)") }, modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("启用转发服务")
                Switch(checked = isEnabled, onCheckedChange = { checked ->
                    isEnabled = checked
                    prefs.edit().putBoolean("enabled", isEnabled).apply()
                    if (checked) {
                        // 启动前确保通知权限（Activity 层会请求并在授权后继续）
                        onStartService()
                        LogStore.append(context, "用户请求启动服务")
                    } else {
                        onStopService()
                        LogStore.append(context, "用户请求停止服务")
                    }
                    context.sendBroadcast(Intent(SmsForegroundService.ACTION_UPDATE))
                })
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("开机启动")
                Switch(checked = startOnBoot, onCheckedChange = {
                    startOnBoot = it
                    prefs.edit().putBoolean("start_on_boot", startOnBoot).apply()
                    LogStore.append(context, if (startOnBoot) "启用开机启动" else "禁用开机启动")
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
                }) { Text("保存配置") }

                Row {
                    Button(onClick = {
                        // 手动检查并请求权限
                        onRequestNotificationPermission()
                        onRequestSmsPermission()
                    }) { Text("检查/请求权限") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onOpenChannelSettings() }) { Text("打开频道设置") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onOpenAppSettings() }) { Text("打开应用设置") }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("运行日志（最新在上）", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { LogStore.clear(context); logs = LogStore.readAll(context); context.sendBroadcast(Intent(SmsForegroundService.ACTION_UPDATE)) }) { Text("清除日志") }
            }

            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.weight(1f)) { items(logs) { line -> Text(line, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp)); Divider() } }

            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { logs = LogStore.readAll(context) }, modifier = Modifier.fillMaxWidth()) { Text("刷新日志") }
        }
    }
}