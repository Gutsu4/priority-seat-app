package com.example.push_notification_demo

import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class PrioritySeatService : Service() {

    private val binder = LocalBinder()
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var isScanning = false
    private var userMode = UserMode.AVAILABLE // 譲れる人モード

    // モック機能(エミュレータ用)
    private var mockBleManager: MockBleManager? = null
    private var useMockBle = false

    // 通知スロットリング用（同じデバイスから連続通知を防ぐ）
    private val lastNotificationTime = mutableMapOf<String, Long>()
    private val NOTIFICATION_COOLDOWN_MS = 30000L // 30秒

    enum class UserMode {
        NEED_SEAT,      // 譲ってほしい人モード
        AVAILABLE       // 譲れる人モード(健常者)
    }

    companion object {
        private const val TAG = "PrioritySeatService"
        private const val CHANNEL_ID = "PrioritySeatChannel"
        private const val ALERT_CHANNEL_ID = "PrioritySeatAlertChannel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_FOUND_NEED_SEAT = "com.example.push_notification_demo.FOUND_NEED_SEAT"
        const val ACTION_TRANSFER_REQUEST = "com.example.push_notification_demo.TRANSFER_REQUEST"
        const val ACTION_TRANSFER_CONFIRM = "com.example.push_notification_demo.TRANSFER_CONFIRM"

        const val EXTRA_TRANSFER_ID = "transfer_id"
        const val EXTRA_SENDER_DEVICE = "sender_device"
        const val EXTRA_RECEIVER_TYPE = "receiver_type"
        const val EXTRA_CONFIRMED = "confirmed"

        // BLE用の独自UUID
        private const val PRIORITY_SEAT_UUID = "0000FFF0-0000-1000-8000-00805F9B34FB"
        private const val TRANSFER_REQUEST_UUID = "0000FFF1-0000-1000-8000-00805F9B34FB"
        private const val TRANSFER_CONFIRM_UUID = "0000FFF2-0000-1000-8000-00805F9B34FB"

        // BLE ManufacturerデータのID
        private const val MANUFACTURER_ID = 0xFFFF
    }

    inner class LocalBinder : Binder() {
        fun getService(): PrioritySeatService = this@PrioritySeatService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        try {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bluetoothLeScanner = bluetoothManager?.adapter?.bluetoothLeScanner

            // BLEが使えない場合はモックモードを使用
            useMockBle = bluetoothLeScanner == null
            if (useMockBle) {
                mockBleManager = MockBleManager(this)
                Log.d(TAG, "モックBLEモードで動作します(エミュレータ用)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bluetooth initialization failed, using mock mode", e)
            useMockBle = true
            mockBleManager = MockBleManager(this)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            // 通知チャンネルが確実に作成されてから通知を表示
            createNotificationChannel()
            val notification = createForegroundNotification()

            // Android 14以降はForeground Service Typeが必須
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // API 34以降
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // API 29以降
                startForeground(NOTIFICATION_ID, notification)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Foreground Service用の通常チャンネル
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "優先席アシスト（バックグラウンド）",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "バックグラウンドで動作中"
                enableVibration(false)
                setShowBadge(false)
            }

            // アラート用の高優先度チャンネル
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "優先席アラート",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "席を必要としている方の検出通知"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
                setSound(
                    android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }

            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(alertChannel)

            Log.d(TAG, "通知チャンネル作成完了 - ホーム画面でも通知表示可能")
        }
    }

    private fun createForegroundNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val message = when(userMode) {
            UserMode.NEED_SEAT -> "譲ってほしいモードで探索中..."
            UserMode.AVAILABLE -> "譲れる人モードで待機中..."
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("優先席アシスト")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun setUserMode(mode: UserMode) {
        userMode = mode
        updateForegroundNotification()

        if (mode == UserMode.NEED_SEAT) {
            startAdvertising()
            // NEED_SEATモードでもスキャンを継続（譲渡リクエストを受信するため）
            startScanning()
        } else {
            startScanning()
            stopAdvertising()
        }
    }

    fun getUserMode(): UserMode = userMode

    private fun updateForegroundNotification() {
        val notification = createForegroundNotification()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let {
                val scanRecord = it.scanRecord
                scanRecord?.serviceUuids?.forEach { uuid ->
                    when {
                        // 譲ってほしい人のアドバタイズを検出
                        uuid.toString().equals(PRIORITY_SEAT_UUID, ignoreCase = true) -> {
                            Log.d(TAG, "席を譲ってほしい人を検出しました!")
                            onNeedSeatDetected(it.device.address)
                        }
                        // 譲渡リクエストを検出
                        uuid.toString().equals(TRANSFER_REQUEST_UUID, ignoreCase = true) -> {
                            scanRecord.manufacturerSpecificData?.get(MANUFACTURER_ID)?.let { data ->
                                decodeTransferData(data)?.let { (transferId, receiverType) ->
                                    Log.d(TAG, "TransferRequestを受信: $transferId")
                                    onTransferRequestReceived(transferId, it.device.address, receiverType)
                                }
                            }
                        }
                        // 譲渡確認を検出
                        uuid.toString().equals(TRANSFER_CONFIRM_UUID, ignoreCase = true) -> {
                            scanRecord.manufacturerSpecificData?.get(MANUFACTURER_ID)?.let { data ->
                                decodeTransferData(data)?.let { (transferId, status) ->
                                    Log.d(TAG, "TransferConfirmationを受信: $transferId ($status)")
                                    onTransferConfirmationReceived(transferId, status == "confirmed")
                                }
                            }
                        }
                    }
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE Scan failed: $errorCode")
        }
    }

    private fun startScanning() {
        if (useMockBle) {
            // モックBLEでスキャン
            mockBleManager?.startScanning { deviceId ->
                Log.d(TAG, "モック: 席を譲ってほしい人を検出しました!")
                onNeedSeatDetected(deviceId)
            }
            return
        }

        try {
            if (!isScanning) {
                val settings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build()

                bluetoothLeScanner?.startScan(null, settings, scanCallback)
                isScanning = true
                Log.d(TAG, "BLE Scanning started")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for BLE scan", e)
        }
    }

    private fun stopScanning() {
        if (useMockBle) {
            mockBleManager?.stopScanning()
            return
        }

        try {
            if (isScanning) {
                bluetoothLeScanner?.stopScan(scanCallback)
                isScanning = false
                Log.d(TAG, "BLE Scanning stopped")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for stopping BLE scan", e)
        }
    }

    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null

    private fun startAdvertising() {
        if (useMockBle) {
            mockBleManager?.startAdvertising()
            return
        }

        try {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            advertiser = bluetoothManager.adapter?.bluetoothLeAdvertiser

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(false)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build()

            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(android.os.ParcelUuid.fromString(PRIORITY_SEAT_UUID))
                .build()

            advertiseCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    Log.d(TAG, "BLE Advertising started")
                }

                override fun onStartFailure(errorCode: Int) {
                    Log.e(TAG, "BLE Advertising failed: $errorCode")
                }
            }

            advertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for BLE advertise", e)
        }
    }

    private fun stopAdvertising() {
        if (useMockBle) {
            mockBleManager?.stopAdvertising()
            return
        }

        try {
            advertiseCallback?.let {
                advertiser?.stopAdvertising(it)
                Log.d(TAG, "BLE Advertising stopped")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for stopping BLE advertise", e)
        }
    }

    private fun onNeedSeatDetected(deviceAddress: String) {
        // 通知スロットリング: 同じデバイスから30秒以内の通知は無視
        val currentTime = System.currentTimeMillis()
        val lastTime = lastNotificationTime[deviceAddress] ?: 0L

        if (currentTime - lastTime < NOTIFICATION_COOLDOWN_MS) {
            Log.d(TAG, "通知スキップ（クールダウン中）: $deviceAddress")
            return
        }

        // 最終通知時刻を更新
        lastNotificationTime[deviceAddress] = currentTime

        Log.d(TAG, "新しい通知を送信: $deviceAddress")

        // アプリ外通知を送信
        sendNotification(
            "優先席を必要としている方がいます",
            "周りを確認して、席を譲ることを検討してください"
        )

        // アプリ内通知用のブロードキャスト
        val intent = Intent(ACTION_FOUND_NEED_SEAT)
        sendBroadcast(intent)
    }

    /**
     * 譲渡リクエストを受信した時の処理
     */
    private fun onTransferRequestReceived(transferId: String, senderDevice: String, receiverType: String) {
        // アプリ内ブロードキャストで通知
        val intent = Intent(ACTION_TRANSFER_REQUEST).apply {
            putExtra(EXTRA_TRANSFER_ID, transferId)
            putExtra(EXTRA_SENDER_DEVICE, senderDevice)
            putExtra(EXTRA_RECEIVER_TYPE, receiverType)
        }
        sendBroadcast(intent)
    }

    /**
     * 譲渡確認を受信した時の処理
     */
    private fun onTransferConfirmationReceived(transferId: String, confirmed: Boolean) {
        // アプリ内ブロードキャストで通知
        val intent = Intent(ACTION_TRANSFER_CONFIRM).apply {
            putExtra(EXTRA_TRANSFER_ID, transferId)
            putExtra(EXTRA_CONFIRMED, confirmed)
        }
        sendBroadcast(intent)
    }

    private fun sendNotification(title: String, message: String) {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // フルスクリーンインテント（ホーム画面でポップアップ表示）
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 1, fullScreenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)  // ホーム画面でもポップアップ
            .setPriority(NotificationCompat.PRIORITY_MAX)  // 最高優先度
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)  // メッセージカテゴリでヘッドアップ表示
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 250, 500, 250, 500))  // より長いバイブレーション
            .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)  // サウンド追加
            .setLights(0xFFFF0000.toInt(), 1000, 1000)  // 赤色のLED点滅
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(message)
                .setBigContentTitle(title))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)  // ロック画面でも表示
            .setOngoing(false)  // スワイプで消せるように
            .setTimeoutAfter(30000)  // 30秒後に自動削除
            .setDefaults(NotificationCompat.DEFAULT_ALL)  // デフォルト設定全部
            .setOnlyAlertOnce(false)  // 毎回アラート
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notification)

        Log.d(TAG, "ホーム画面対応の通知を送信しました: $title (ID: $notificationId)")
    }

    override fun onDestroy() {
        stopScanning()
        stopAdvertising()
        mockBleManager?.cleanup()
        super.onDestroy()
    }

    fun isMockMode(): Boolean = useMockBle

    /**
     * 譲渡リクエストをBLEで送信
     */
    fun sendTransferRequest(receiverDeviceId: String, receiverType: String, transferId: String) {
        Log.d(TAG, "★★★ sendTransferRequest呼び出し: transferId=$transferId, useMockBle=$useMockBle")
        if (useMockBle) {
            // モックモード: ブロードキャストで送信（テスト用）
            Log.d(TAG, "★★★ モックモードでTransferRequest送信開始")

            // テスト用デバイスIDの場合、テストメソッドを呼び出し
            if (receiverDeviceId == "test_device") {
                mockBleManager?.sendTestTransferRequest(transferId, receiverType)
            } else {
                mockBleManager?.sendTransferRequest(transferId, receiverType)
            }
            Log.d(TAG, "★★★ モックモードでTransferRequest送信完了")
            return
        }

        // BLE Advertisingでtransfer情報を送信
        startTransferAdvertising(transferId, receiverType, isConfirm = false)
        Log.d(TAG, "TransferRequestをBLEで送信: $transferId -> $receiverDeviceId")
    }

    /**
     * 譲渡確認結果をBLEで送信
     */
    fun sendTransferConfirmation(transferId: String, confirmed: Boolean) {
        if (useMockBle) {
            // モックモード: ブロードキャストで送信（テスト用）
            mockBleManager?.sendTransferConfirmation(transferId, confirmed)
            return
        }

        // BLE Advertisingで確認結果を送信
        startTransferAdvertising(transferId, if (confirmed) "confirmed" else "rejected", isConfirm = true)
        Log.d(TAG, "TransferConfirmationをBLEで送信: $transferId (confirmed=$confirmed)")
    }

    /**
     * Transfer情報を含むBLE Advertisingを開始
     */
    private fun startTransferAdvertising(transferId: String, data: String, isConfirm: Boolean) {
        if (useMockBle) return

        try {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val tempAdvertiser = bluetoothManager.adapter?.bluetoothLeAdvertiser

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(false)
                .setTimeout(3000) // 3秒間送信
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build()

            // Transfer情報をManufacturerDataとして送信
            val transferData = encodeTransferData(transferId, data)
            val uuid = if (isConfirm) TRANSFER_CONFIRM_UUID else TRANSFER_REQUEST_UUID

            val advertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(android.os.ParcelUuid.fromString(uuid))
                .addManufacturerData(MANUFACTURER_ID, transferData)
                .build()

            val tempCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    Log.d(TAG, "Transfer Advertising started: $transferId")
                }

                override fun onStartFailure(errorCode: Int) {
                    Log.e(TAG, "Transfer Advertising failed: $errorCode")
                }
            }

            tempAdvertiser?.startAdvertising(settings, advertiseData, tempCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for transfer advertising", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start transfer advertising", e)
        }
    }

    /**
     * Transfer情報をバイト配列にエンコード
     */
    private fun encodeTransferData(transferId: String, data: String): ByteArray {
        // フォーマット: [transferId(16bytes)][dataLength(1byte)][data(variable)]
        val transferIdBytes = transferId.take(16).padEnd(16, ' ').toByteArray()
        val dataBytes = data.toByteArray()
        val dataLength = dataBytes.size.toByte()

        return transferIdBytes + dataLength + dataBytes
    }

    /**
     * バイト配列からTransfer情報をデコード
     */
    private fun decodeTransferData(data: ByteArray): Pair<String, String>? {
        if (data.size < 17) return null

        try {
            val transferId = String(data.sliceArray(0..15)).trim()
            val dataLength = data[16].toInt()
            if (data.size < 17 + dataLength) return null

            val dataStr = String(data.sliceArray(17 until 17 + dataLength))
            return Pair(transferId, dataStr)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode transfer data", e)
            return null
        }
    }
}
