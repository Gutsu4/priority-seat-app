package com.example.push_notification_demo

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.push_notification_demo.ui.theme.PushnotificationdemoTheme

class MainActivity : ComponentActivity() {

    private var prioritySeatService: PrioritySeatService? = null
    private var serviceBound = false
    private var currentMode by mutableStateOf(PrioritySeatService.UserMode.AVAILABLE)
    private var showAlert by mutableStateOf(false)
    private var alertMessage by mutableStateOf("")
    private var isMockMode by mutableStateOf(false)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as PrioritySeatService.LocalBinder
            prioritySeatService = binder.getService()
            serviceBound = true
            currentMode = prioritySeatService?.getUserMode() ?: PrioritySeatService.UserMode.AVAILABLE
            isMockMode = prioritySeatService?.isMockMode() ?: false
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            prioritySeatService = null
        }
    }

    private val needSeatReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == PrioritySeatService.ACTION_FOUND_NEED_SEAT) {
                showAlert = true
                alertMessage = "近くに席を必要としている方がいます"
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // 権限に関わらずサービスを開始（モックモードで動作可能）
        startService()

        val deniedPermissions = permissions.filterValues { !it }.keys
        if (deniedPermissions.isNotEmpty()) {
            Toast.makeText(this, "一部の権限が許可されていませんが、テストモードで動作します", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ブロードキャストレシーバーを登録
        try {
            val filter = IntentFilter(PrioritySeatService.ACTION_FOUND_NEED_SEAT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(needSeatReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(needSeatReceiver, filter)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to register receiver", e)
        }

        setContent {
            PushnotificationdemoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PrioritySeatScreen(
                        modifier = Modifier.padding(innerPadding),
                        currentMode = currentMode,
                        showAlert = showAlert,
                        alertMessage = alertMessage,
                        isMockMode = isMockMode,
                        onModeChange = { mode ->
                            currentMode = mode
                            prioritySeatService?.setUserMode(mode)
                        },
                        onAlertDismiss = { showAlert = false },
                        onTestNotification = {
                            // テスト通知を送信
                            prioritySeatService?.let {
                                val testIntent = Intent(PrioritySeatService.ACTION_FOUND_NEED_SEAT)
                                sendBroadcast(testIntent)
                            }
                        }
                    )
                }
            }
        }

        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions(): Boolean {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // フルスクリーン通知の権限確認（Android 14以降）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions.add(Manifest.permission.USE_FULL_SCREEN_INTENT)
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        return if (notGranted.isNotEmpty()) {
            Log.d("MainActivity", "権限要求: ${notGranted.joinToString()}")
            requestPermissionLauncher.launch(notGranted.toTypedArray())
            true  // サービスは権限要求後に開始される
        } else {
            Log.d("MainActivity", "全ての権限が許可されています")
            startService()
            true
        }
    }

    private fun startService() {
        try {
            val intent = Intent(this, PrioritySeatService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start service", e)
            Toast.makeText(this, "サービスの起動に失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (serviceBound) {
                unbindService(serviceConnection)
                serviceBound = false
            }
            unregisterReceiver(needSeatReceiver)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error during cleanup", e)
        }
    }
}

@Composable
fun PrioritySeatScreen(
    modifier: Modifier = Modifier,
    currentMode: PrioritySeatService.UserMode,
    showAlert: Boolean,
    alertMessage: String,
    isMockMode: Boolean,
    onModeChange: (PrioritySeatService.UserMode) -> Unit,
    onAlertDismiss: () -> Unit,
    onTestNotification: () -> Unit = {}
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // モックモード表示
            if (isMockMode) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFA500).copy(alpha = 0.2f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🧪",
                            fontSize = 24.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "テストモード",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF8C00)
                            )
                            Text(
                                text = "エミュレータで動作中",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(
                text = "優先席アシスト",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "あなたの状況を選択してください",
                fontSize = 16.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // 譲ってほしいモード
            ModeButton(
                text = "席を譲ってほしい",
                description = "優先席が必要な方",
                isSelected = currentMode == PrioritySeatService.UserMode.NEED_SEAT,
                backgroundColor = Color(0xFFFF6B6B),
                onClick = { onModeChange(PrioritySeatService.UserMode.NEED_SEAT) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 譲れるモード
            ModeButton(
                text = "席を譲れる",
                description = "健常者の方",
                isSelected = currentMode == PrioritySeatService.UserMode.AVAILABLE,
                backgroundColor = Color(0xFF4ECDC4),
                onClick = { onModeChange(PrioritySeatService.UserMode.AVAILABLE) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // テストボタン
            if (isMockMode) {
                Button(
                    onClick = onTestNotification,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFA500)
                    )
                ) {
                    Text("🔔 テスト通知を送信", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 現在のステータス表示
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "現在の状態",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (currentMode) {
                            PrioritySeatService.UserMode.NEED_SEAT -> "近くの方に通知しています"
                            PrioritySeatService.UserMode.AVAILABLE -> "周囲を検知中"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // アラート表示
        if (showAlert) {
            AlertDialog(
                onDismissRequest = onAlertDismiss,
                title = {
                    Text(
                        text = "⚠️ お知らせ",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(text = alertMessage)
                },
                confirmButton = {
                    TextButton(onClick = onAlertDismiss) {
                        Text("確認")
                    }
                }
            )
        }
    }
}

@Composable
fun ModeButton(
    text: String,
    description: String,
    isSelected: Boolean,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) backgroundColor else Color.LightGray.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = text,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else Color.Gray
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                fontSize = 14.sp,
                color = if (isSelected) Color.White.copy(alpha = 0.9f) else Color.Gray
            )
        }
    }
}
