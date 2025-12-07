package com.lanbing.smsforwarder

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var requestSmsPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var requestNotifPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Activity 层注册短信权限回调
        requestSmsPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                Toast.makeText(this, "短信权限已授权", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "请授予短信权限以接收短信", Toast.LENGTH_LONG).show()
            }
        }

        // Activity 层注册通知权限回调（仅用于 API >= 33）
        requestNotifPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                Toast.makeText(this, "通知权限已授权", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "请允许通知权限以显示常驻通知", Toast.LENGTH_LONG).show()
            }
        }

        setContent {
            // 把 Activity 的启动/停止服务和权限请求通过回调传给 Composable
            SmsForwarderApp(
                onRequestSmsPermission = { requestSmsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS) },
                onRequestNotificationPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestNotifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onStartService = { startForegroundService(Intent(this, SmsForegroundService::class.java)) },
                onStopService = { stopService(Intent(this, SmsForegroundService::class.java)) }
            )
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

            // 开关只做状态保存并通过回调明确启动或停止服务（不会在重组时被触发）
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
                            if (!hasNotif) {
                                onRequestNotificationPermission()
                            }
                        }
                        val hasSms = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
                        if (!hasSms) {
                            onRequestSmsPermission()
                        }
                        // 启动前台服务（确保通知权限请求已发起/存在）
                        onStartService()
                        LogStore.append(context, "服务已启动（由用户开启）")
                        Toast.makeText(context, "服务已启动", Toast.LENGTH_SHORT).show()
                    } else {
                        onStopService()
                        LogStore.append(context, "服务已停止（由用户关闭）")
                        Toast.makeText(context, "服务已停止", Toast.LENGTH_SHORT).show()
                    }
                    // 更新通知（如果服务在运行，会收到广播并刷新）
                    context.sendBroadcast(Intent(SmsForegroundService.ACTION_UPDATE))
                })
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = {
                    prefs.edit().apply {
                        putString("webhook", webhookUrl.trim())
                        putString("keywords", keywords)
                        putBoolean("enabled", isEnabled)
                        apply()
                    }
                    Toast.makeText(context, "配置已保存", Toast.LENGTH_SHORT).show()
                    LogStore.append(context, "配置已保存")
                    context.sendBroadcast(Intent(SmsForegroundService.ACTION_UPDATE))
                }) {
                    Text("保存配置")
                }

                Button(onClick = {
                    // 手动触发权限请求（Activity 层会执行实际 launcher）
                    onRequestSmsPermission()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        onRequestNotificationPermission()
                    }
                }) {
                    Text("检查/请求权限")
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