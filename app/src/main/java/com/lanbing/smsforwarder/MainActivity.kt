package com.lanbing.smsforwarder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmsForwarderApp()
        }
    }
}

@Composable
fun SmsForwarderApp() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("app_config", Context.MODE_PRIVATE)

    var webhookUrl by remember { mutableStateOf(prefs.getString("webhook", "") ?: "") }
    var keywords by remember { mutableStateOf(prefs.getString("keywords", "") ?: "") }
    var isEnabled by remember { mutableStateOf(prefs.getBoolean("enabled", false)) }
    var logs by remember { mutableStateOf(LogStore.readAll(context)) }

    // 明确声明回调参数类型为 Boolean，避免编译器无法推断
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        if (granted) Toast.makeText(context, "短信权限已授权", Toast.LENGTH_SHORT).show()
        else Toast.makeText(context, "请授予短信权限以接收短信", Toast.LENGTH_LONG).show()
    }

    // 当启用切换时启动/停止前台服务
    LaunchedEffect(isEnabled) {
        prefs.edit().putBoolean("enabled", isEnabled).apply()
        val svcIntent = Intent(context, SmsForegroundService::class.java)
        if (isEnabled) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
            }
            ContextCompat.startForegroundService(context, svcIntent)
            Toast.makeText(context, "服务已启动（请在系统中允许自启动与省电策略）", Toast.LENGTH_SHORT).show()
            LogStore.append(context, "服务已启动")
        } else {
            context.stopService(svcIntent)
            Toast.makeText(context, "服务已停止", Toast.LENGTH_SHORT).show()
            LogStore.append(context, "服务已停止")
        }
        logs = LogStore.readAll(context)
        context.sendBroadcast(Intent(SmsForegroundService.ACTION_UPDATE))
    }

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

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("启用转发服务")
                Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
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
                }) {
                    Text("保存配置")
                }

                Button(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                    } else {
                        Toast.makeText(context, "权限已存在", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("检查/请求短信权限")
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

            // 日志列表
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(logs) { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp))
                    Divider()
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = {
                logs = LogStore.readAll(context)
            }, modifier = Modifier.fillMaxWidth()) {
                Text("刷新日志")
            }
        }
    }
}