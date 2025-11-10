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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.push_notification_demo.ui.theme.PushnotificationdemoTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainActivity : ComponentActivity() {

    private var prioritySeatService: PrioritySeatService? = null
    private var serviceBound = false
    private var currentMode by mutableStateOf(PrioritySeatService.UserMode.AVAILABLE)
    private var showAlert by mutableStateOf(false)
    private var alertMessage by mutableStateOf("")
    private var isMockMode by mutableStateOf(false)
    private var showPointsAnimationFlag by mutableStateOf(false)

    // TransferManager
    private lateinit var transferManager: TransferManager
    // SettingsManager
    private lateinit var settingsManager: SettingsManager

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
                // 通知設定を確認
                if (settingsManager.enableNotifications.value) {
                    showAlert = true
                    alertMessage = "近くに席を必要としている方がいます"
                }
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

        // TransferManagerを初期化
        transferManager = TransferManager(this)
        // SettingsManagerを初期化
        settingsManager = SettingsManager(this)

        // 設定からデフォルトモードを適用
        currentMode = settingsManager.defaultMode.value

        // ブロードキャストレシーバーを登録
        try {
            val needSeatFilter = IntentFilter(PrioritySeatService.ACTION_FOUND_NEED_SEAT)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(needSeatReceiver, needSeatFilter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(needSeatReceiver, needSeatFilter)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to register receiver", e)
        }

        setContent {
            PushnotificationdemoTheme {
                MainScreen(
                    currentMode = currentMode,
                    showAlert = showAlert,
                    alertMessage = alertMessage,
                    isMockMode = isMockMode,
                    transferManager = transferManager,
                    settingsManager = settingsManager,
                    showPointsAnimationFlag = showPointsAnimationFlag,
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
                    },
                    onPointsAnimationShown = {
                        showPointsAnimationFlag = false
                    }
                )
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
fun MainScreen(
    currentMode: PrioritySeatService.UserMode,
    showAlert: Boolean,
    alertMessage: String,
    isMockMode: Boolean,
    transferManager: TransferManager,
    settingsManager: SettingsManager,
    showPointsAnimationFlag: Boolean,
    onModeChange: (PrioritySeatService.UserMode) -> Unit,
    onAlertDismiss: () -> Unit,
    onTestNotification: () -> Unit,
    onPointsAnimationShown: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "ホーム") },
                    label = { Text("ホーム") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Info, contentDescription = "統計") },
                    label = { Text("統計") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "設定") },
                    label = { Text("設定") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
            }
        }
    ) { paddingValues ->
        when (selectedTab) {
            0 -> PrioritySeatScreen(
                modifier = Modifier.padding(paddingValues),
                currentMode = currentMode,
                showAlert = showAlert,
                alertMessage = alertMessage,
                isMockMode = isMockMode,
                transferManager = transferManager,
                showPointsAnimationFlag = showPointsAnimationFlag,
                onModeChange = onModeChange,
                onAlertDismiss = onAlertDismiss,
                onTestNotification = onTestNotification,
                onPointsAnimationShown = onPointsAnimationShown
            )
            1 -> StatisticsScreen(
                modifier = Modifier.padding(paddingValues),
                transferManager = transferManager
            )
            2 -> SettingsScreen(
                modifier = Modifier.padding(paddingValues),
                settingsManager = settingsManager
            )
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
    transferManager: TransferManager,
    showPointsAnimationFlag: Boolean,
    onModeChange: (PrioritySeatService.UserMode) -> Unit,
    onAlertDismiss: () -> Unit,
    onTestNotification: () -> Unit = {},
    onPointsAnimationShown: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var showPointsAnimation by remember { mutableStateOf(false) }

    // ポイントアニメーションフラグを監視
    LaunchedEffect(showPointsAnimationFlag) {
        if (showPointsAnimationFlag) {
            showPointsAnimation = true
            delay(2000)
            showPointsAnimation = false
            onPointsAnimationShown()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
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

        // アラート表示（BLE検出通知）
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
                    Button(
                        onClick = {
                            onAlertDismiss()
                            // 席を譲るボタンを押した時点でポイント付与
                            if (currentMode == PrioritySeatService.UserMode.AVAILABLE) {
                                scope.launch {
                                    // ポイント付与
                                    transferManager.addPoints(50)
                                    // ポイントアニメーション表示
                                    showPointsAnimation = true
                                    delay(2000)
                                    showPointsAnimation = false
                                }
                            }
                        }
                    ) {
                        Text("席を譲る")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onAlertDismiss) {
                        Text("閉じる")
                    }
                }
            )
        }

        // ポイント獲得アニメーション
        AnimatedVisibility(
            visible = showPointsAnimation,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFD700)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🎉", fontSize = 64.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "+50 pt",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
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

// 統計画面
@Composable
fun StatisticsScreen(
    modifier: Modifier = Modifier,
    transferManager: TransferManager
) {
    val points by transferManager.points.collectAsState()
    val totalTransfers by transferManager.totalTransfers.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "統計",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        // ポイント
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFFFD700).copy(alpha = 0.2f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "累計ポイント",
                    fontSize = 16.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⭐", fontSize = 48.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "$points pt",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF8C00)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 譲渡回数
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF4ECDC4).copy(alpha = 0.2f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "累計助けた人数",
                    fontSize = 16.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "$totalTransfers 人",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4ECDC4)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ポイント交換先
        Text(
            text = "ポイント交換",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        PointExchangeCard(
            title = "駅ナカカフェ 50円引き",
            points = 300,
            icon = "☕",
            enabled = points >= 300,
            transferManager = transferManager
        )

        Spacer(modifier = Modifier.height(12.dp))

        PointExchangeCard(
            title = "交通系IC 100円チャージ",
            points = 500,
            icon = "🚃",
            enabled = points >= 500,
            transferManager = transferManager
        )

        Spacer(modifier = Modifier.height(12.dp))

        PointExchangeCard(
            title = "慈善団体へ寄付",
            points = 1000,
            icon = "❤️",
            enabled = points >= 1000,
            transferManager = transferManager
        )
    }
}

@Composable
fun PointExchangeCard(
    title: String,
    points: Int,
    icon: String,
    enabled: Boolean,
    transferManager: TransferManager
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled)
                MaterialTheme.colorScheme.surfaceVariant
            else
                Color.LightGray.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, fontSize = 32.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) Color.Black else Color.Gray
                )
                Text(
                    text = "$points pt",
                    fontSize = 14.sp,
                    color = if (enabled) Color.DarkGray else Color.Gray
                )
            }
            if (enabled) {
                Button(
                    onClick = { showConfirmDialog = true },
                    modifier = Modifier.height(40.dp)
                ) {
                    Text("交換")
                }
            } else {
                Text(
                    text = "不足",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("ポイント交換") },
            text = { Text("$points ポイントを使って「$title」と交換しますか？") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            if (transferManager.usePoints(points)) {
                                android.widget.Toast.makeText(
                                    context,
                                    "交換しました！",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        showConfirmDialog = false
                    }
                ) {
                    Text("交換する")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }
}

data class VirtualUser(
    val id: String,
    val type: String,
    val icon: String,
    val color: Color
)

// 設定画面
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    settingsManager: SettingsManager
) {
    val defaultMode by settingsManager.defaultMode.collectAsState()
    val enableNotifications by settingsManager.enableNotifications.collectAsState()
    val userType by settingsManager.userType.collectAsState()

    var showUserTypeDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "設定",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        // デフォルトモード設定
        Text(
            text = "デフォルト設定",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "起動時のモード",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = defaultMode == PrioritySeatService.UserMode.AVAILABLE,
                        onClick = { settingsManager.setDefaultMode(PrioritySeatService.UserMode.AVAILABLE) }
                    )
                    Text(
                        text = "席を譲れる",
                        modifier = Modifier.clickable {
                            settingsManager.setDefaultMode(PrioritySeatService.UserMode.AVAILABLE)
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = defaultMode == PrioritySeatService.UserMode.NEED_SEAT,
                        onClick = { settingsManager.setDefaultMode(PrioritySeatService.UserMode.NEED_SEAT) }
                    )
                    Text(
                        text = "席を譲ってほしい",
                        modifier = Modifier.clickable {
                            settingsManager.setDefaultMode(PrioritySeatService.UserMode.NEED_SEAT)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ユーザータイプ設定
        Text(
            text = "プロフィール",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showUserTypeDialog = true },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "譲ってほしい理由",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = userType,
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "選択",
                    tint = Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 通知設定
        Text(
            text = "通知設定",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "通知を有効化",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "近くに席を必要とする方がいる時に通知",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Switch(
                    checked = enableNotifications,
                    onCheckedChange = { settingsManager.setEnableNotifications(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // アプリ情報
        Text(
            text = "アプリ情報",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "バージョン",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Text(
                    text = "1.0.0",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // ユーザータイプ選択ダイアログ
    if (showUserTypeDialog) {
        AlertDialog(
            onDismissRequest = { showUserTypeDialog = false },
            title = {
                Text(
                    text = "譲ってほしい理由",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    val userTypes = listOf(
                        "妊婦",
                        "高齢者",
                        "障がい者",
                        "体調不良",
                        "乳幼児連れ",
                        "怪我をしている"
                    )
                    userTypes.forEach { type ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    settingsManager.setUserType(type)
                                    showUserTypeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = userType == type,
                                onClick = {
                                    settingsManager.setUserType(type)
                                    showUserTypeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = type, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showUserTypeDialog = false }) {
                    Text("閉じる")
                }
            }
        )
    }
}

// 設定管理クラス
class SettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _defaultMode = MutableStateFlow(
        PrioritySeatService.UserMode.valueOf(
            prefs.getString("default_mode", PrioritySeatService.UserMode.AVAILABLE.name)
                ?: PrioritySeatService.UserMode.AVAILABLE.name
        )
    )
    val defaultMode: StateFlow<PrioritySeatService.UserMode> = _defaultMode

    private val _enableNotifications = MutableStateFlow(prefs.getBoolean("enable_notifications", true))
    val enableNotifications: StateFlow<Boolean> = _enableNotifications

    private val _userType = MutableStateFlow(prefs.getString("user_type", "妊婦") ?: "妊婦")
    val userType: StateFlow<String> = _userType

    fun setDefaultMode(mode: PrioritySeatService.UserMode) {
        _defaultMode.value = mode
        prefs.edit().putString("default_mode", mode.name).apply()
    }

    fun setEnableNotifications(enabled: Boolean) {
        _enableNotifications.value = enabled
        prefs.edit().putBoolean("enable_notifications", enabled).apply()
    }

    fun setUserType(type: String) {
        _userType.value = type
        prefs.edit().putString("user_type", type).apply()
    }
}
