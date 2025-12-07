package com.lanbing.smsforwarder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ConfigScreen()
                }
            }
        }
    }
}

@Composable
fun ConfigScreen() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("app_config", Context.MODE_PRIVATE)

    // 读取保存的配置
    var webhookUrl by remember { mutableStateOf(prefs.getString("webhook", "") ?: "") }
    var keywords by remember { mutableStateOf(prefs.getString("keywords", "") ?: "") }
    var isEnabled by remember { mutableStateOf(prefs.getBoolean("enabled", false)) }

    // 权限请求回调
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) Toast.makeText(context, "权限已获取", Toast.LENGTH_SHORT).show()
        else Toast.makeText(context, "必须给短信权限才能用哦", Toast.LENGTH_LONG).show()
    }

    Column(
        modifier = Modifier.padding(20.dp).fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "短信转发助手", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(30.dp))

        OutlinedTextField(
            value = webhookUrl,
            onValueChange = { webhookUrl = it },
            label = { Text("企业微信 Webhook 地址") },
            placeholder = { Text("https://qyapi.weixin.qq.com/...") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = keywords,
            onValueChange = { keywords = it },
            label = { Text("关键词 (英文逗号分隔)") },
            placeholder = { Text("验证码,警告,快递") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Text(
            text = "留空则转发所有短信",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("启用转发服务", style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = isEnabled,
                onCheckedChange = { isEnabled = it }
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = {
                prefs.edit().apply {
                    putString("webhook", webhookUrl.trim())
                    putString("keywords", keywords)
                    putBoolean("enabled", isEnabled)
                    apply()
                }
                Toast.makeText(context, "配置已保存", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存配置")
        }

        Spacer(modifier = Modifier.height(16.dp))

        FilledTonalButton(
            onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) 
                    != PackageManager.PERMISSION_GRANTED) {
                    permissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                } else {
                    Toast.makeText(context, "权限状态正常", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("检查/请求短信权限")
        }
    }
}