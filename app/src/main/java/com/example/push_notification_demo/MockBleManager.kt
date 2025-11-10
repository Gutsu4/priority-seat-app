package com.example.push_notification_demo

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.*

/**
 * エミュレータやテスト用のモックBLEマネージャー
 * 実機のBluetooth LEの代わりに、シミュレーションで動作します
 */
class MockBleManager(private val context: Context) {

    private var isScanning = false
    private var isAdvertising = false
    private var onDeviceFoundCallback: ((String) -> Unit)? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    companion object {
        private const val TAG = "MockBleManager"
        private val advertisingDevices = mutableSetOf<String>()
        private var instanceCount = 0

        // Transfer情報を保持するマップ
        data class TransferData(
            val transferId: String,
            val senderDevice: String,
            val data: String,
            val isConfirm: Boolean,
            val timestamp: Long
        )

        private val transferMessages = mutableListOf<TransferData>()

        // 全インスタンス間で共有されるアドバタイズ情報
        @Synchronized
        fun addAdvertisingDevice(deviceId: String) {
            advertisingDevices.add(deviceId)
            Log.d(TAG, "デバイス追加: $deviceId, 現在の数: ${advertisingDevices.size}")
        }

        @Synchronized
        fun removeAdvertisingDevice(deviceId: String) {
            advertisingDevices.remove(deviceId)
            Log.d(TAG, "デバイス削除: $deviceId, 現在の数: ${advertisingDevices.size}")
        }

        @Synchronized
        fun getAdvertisingDevices(): Set<String> {
            return advertisingDevices.toSet()
        }

        @Synchronized
        fun addTransferMessage(data: TransferData) {
            transferMessages.add(data)
            Log.d(TAG, "【重要】Transfer情報追加: ID=${data.transferId}, sender=${data.senderDevice}, timestamp=${data.timestamp}, 全メッセージ数=${transferMessages.size}")
        }

        @Synchronized
        fun getTransferMessages(since: Long): List<TransferData> {
            // >= にすることで、送信した瞬間のメッセージも取得できる
            val messages = transferMessages.filter { it.timestamp >= since }
            Log.d(TAG, "getTransferMessages: since=$since, 全メッセージ数=${transferMessages.size}, フィルタ後=${messages.size}")
            return messages
        }

        @Synchronized
        fun clearOldMessages(olderThan: Long) {
            transferMessages.removeAll { it.timestamp < olderThan }
        }
    }

    private val deviceId = "MOCK_DEVICE_${++instanceCount}"
    private var lastCheckedTime = 0L

    fun startScanning(onDeviceFound: (String) -> Unit) {
        if (isScanning) {
            Log.d(TAG, "$deviceId: スキャンは既に開始されています")
            return
        }

        isScanning = true
        onDeviceFoundCallback = onDeviceFound
        lastCheckedTime = System.currentTimeMillis()

        Log.d(TAG, "$deviceId: スキャン開始 (lastCheckedTime=$lastCheckedTime)")

        // 定期的にアドバタイズ中のデバイスとTransfer情報をチェック
        scope.launch {
            while (isScanning) {
                delay(2000) // 2秒ごとにチェック

                // デバイス検出
                val devices = getAdvertisingDevices()
                if (devices.isNotEmpty()) {
                    Log.d(TAG, "$deviceId: ${devices.size}台のデバイスを検出")
                    devices.forEach { device ->
                        if (device != deviceId) { // 自分自身は除外
                            withContext(Dispatchers.Main) {
                                onDeviceFoundCallback?.invoke(device)
                            }
                        }
                    }
                }

                // Transfer情報を受信（lastCheckedTimeより新しいメッセージを取得）
                val newMessages = getTransferMessages(lastCheckedTime)

                Log.d(TAG, "$deviceId: Transfer情報チェック - 新しいメッセージ数: ${newMessages.size}, lastCheckedTime=$lastCheckedTime")

                newMessages.forEach { transfer ->
                    Log.d(TAG, "$deviceId: Transfer検出 - ID:${transfer.transferId}, sender:${transfer.senderDevice}, isConfirm:${transfer.isConfirm}, timestamp:${transfer.timestamp}")

                    if (transfer.senderDevice != deviceId) { // 自分が送信したものは除外
                        withContext(Dispatchers.Main) {
                            if (transfer.isConfirm) {
                                Log.d(TAG, "$deviceId: TransferConfirmブロードキャスト送信: ${transfer.transferId}")
                                sendTransferConfirmBroadcast(transfer.transferId, transfer.data == "confirmed")
                            } else {
                                Log.d(TAG, "$deviceId: TransferRequestブロードキャスト送信: ${transfer.transferId}")
                                sendTransferRequestBroadcast(transfer.transferId, transfer.senderDevice, transfer.data)
                            }
                        }
                    } else {
                        Log.d(TAG, "$deviceId: 自分が送信したTransferなのでスキップ: ${transfer.transferId}")
                    }
                }

                // 最後にチェックした時刻を更新（次回は今回より新しいメッセージのみ取得）
                val currentTime = System.currentTimeMillis()
                lastCheckedTime = currentTime

                // 古いメッセージを削除（10秒以上前）
                clearOldMessages(currentTime - 10000)
            }
        }
    }

    fun stopScanning() {
        if (!isScanning) return

        isScanning = false
        onDeviceFoundCallback = null
        Log.d(TAG, "$deviceId: スキャン停止")
    }

    fun startAdvertising() {
        if (isAdvertising) return

        isAdvertising = true
        addAdvertisingDevice(deviceId)
        Log.d(TAG, "$deviceId: アドバタイズ開始")
    }

    fun stopAdvertising() {
        if (!isAdvertising) return

        isAdvertising = false
        removeAdvertisingDevice(deviceId)
        Log.d(TAG, "$deviceId: アドバタイズ停止")
    }

    fun cleanup() {
        stopScanning()
        stopAdvertising()
        scope.cancel()
    }

    /**
     * Transfer Requestを送信
     */
    fun sendTransferRequest(transferId: String, receiverType: String) {
        val transferData = TransferData(
            transferId = transferId,
            senderDevice = deviceId,  // 実際のdeviceIdを送信
            data = receiverType,
            isConfirm = false,
            timestamp = System.currentTimeMillis()
        )
        addTransferMessage(transferData)
        Log.d(TAG, "$deviceId: TransferRequest送信: $transferId (senderDevice=$deviceId)")
    }

    /**
     * テスト用：別のデバイスからの譲渡リクエストを送信
     */
    fun sendTestTransferRequest(transferId: String, receiverType: String) {
        val testDeviceId = "TEST_DEVICE_OTHER"
        val transferData = TransferData(
            transferId = transferId,
            senderDevice = testDeviceId,
            data = receiverType,
            isConfirm = false,
            timestamp = System.currentTimeMillis()
        )
        addTransferMessage(transferData)
        Log.d(TAG, "★★★ テスト用TransferRequest送信: $transferId (senderDevice=$testDeviceId)")

        // すぐにブロードキャストを送信
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            sendTransferRequestBroadcast(transferData.transferId, transferData.senderDevice, transferData.data)
            Log.d(TAG, "★★★ テスト用ブロードキャスト送信完了: $transferId")
        }, 100)  // 100ms後に送信
    }

    /**
     * Transfer Confirmationを送信
     */
    fun sendTransferConfirmation(transferId: String, confirmed: Boolean) {
        val transferData = TransferData(
            transferId = transferId,
            senderDevice = deviceId,
            data = if (confirmed) "confirmed" else "rejected",
            isConfirm = true,
            timestamp = System.currentTimeMillis()
        )
        addTransferMessage(transferData)
        Log.d(TAG, "$deviceId: TransferConfirmation送信: $transferId (confirmed=$confirmed)")
    }

    /**
     * TransferRequestをブロードキャストで送信
     */
    private fun sendTransferRequestBroadcast(transferId: String, senderDevice: String, receiverType: String) {
        val intent = Intent(PrioritySeatService.ACTION_TRANSFER_REQUEST).apply {
            putExtra(PrioritySeatService.EXTRA_TRANSFER_ID, transferId)
            putExtra(PrioritySeatService.EXTRA_SENDER_DEVICE, senderDevice)
            putExtra(PrioritySeatService.EXTRA_RECEIVER_TYPE, receiverType)
        }
        context.sendBroadcast(intent)
        Log.d(TAG, "$deviceId: TransferRequestブロードキャスト送信: $transferId")
    }

    /**
     * TransferConfirmationをブロードキャストで送信
     */
    private fun sendTransferConfirmBroadcast(transferId: String, confirmed: Boolean) {
        val intent = Intent(PrioritySeatService.ACTION_TRANSFER_CONFIRM).apply {
            putExtra(PrioritySeatService.EXTRA_TRANSFER_ID, transferId)
            putExtra(PrioritySeatService.EXTRA_CONFIRMED, confirmed)
        }
        context.sendBroadcast(intent)
        Log.d(TAG, "$deviceId: TransferConfirmationブロードキャスト送信: $transferId (confirmed=$confirmed)")
    }
}
