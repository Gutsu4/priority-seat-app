package com.example.push_notification_demo

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * 譲渡の相互確認を管理するクラス
 */
class TransferManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("transfer_prefs", Context.MODE_PRIVATE)

    // 譲渡リクエストの状態
    private val _pendingTransfers = MutableStateFlow<List<TransferRequest>>(emptyList())
    val pendingTransfers: StateFlow<List<TransferRequest>> = _pendingTransfers

    // ポイント
    private val _points = MutableStateFlow(prefs.getInt("total_points", 0))
    val points: StateFlow<Int> = _points

    // 統計
    private val _totalTransfers = MutableStateFlow(prefs.getInt("total_transfers", 0))
    val totalTransfers: StateFlow<Int> = _totalTransfers

    // 感謝メッセージ
    private val _thankYouMessages = MutableStateFlow<List<ThankYouMessage>>(emptyList())
    val thankYouMessages: StateFlow<List<ThankYouMessage>> = _thankYouMessages

    /**
     * 譲渡を開始する（譲る側）
     */
    fun initiateTransfer(receiverDeviceId: String, receiverType: String): String {
        val transferId = UUID.randomUUID().toString()
        val request = TransferRequest(
            id = transferId,
            senderDeviceId = "my_device",
            receiverDeviceId = receiverDeviceId,
            receiverType = receiverType,
            status = TransferStatus.PENDING,
            timestamp = System.currentTimeMillis()
        )

        _pendingTransfers.value = _pendingTransfers.value + request
        return transferId
    }

    /**
     * 譲渡確認を受信する（譲られた側）
     */
    fun receiveTransferRequest(request: TransferRequest) {
        android.util.Log.d("TransferManager", "★★★ receiveTransferRequest: ${request.id}, 現在のpendingTransfers数=${_pendingTransfers.value.size}")
        _pendingTransfers.value = _pendingTransfers.value + request
        android.util.Log.d("TransferManager", "★★★ 追加後のpendingTransfers数=${_pendingTransfers.value.size}")
    }

    /**
     * 譲渡を承認する（譲られた側）
     */
    fun confirmTransfer(transferId: String, confirmed: Boolean) {
        val transfers = _pendingTransfers.value.toMutableList()
        val index = transfers.indexOfFirst { it.id == transferId }

        if (index != -1) {
            val transfer = transfers[index]
            transfers[index] = transfer.copy(
                status = if (confirmed) TransferStatus.CONFIRMED else TransferStatus.REJECTED,
                confirmedAt = System.currentTimeMillis()
            )
            _pendingTransfers.value = transfers

            if (confirmed) {
                // 感謝メッセージを送信
                sendThankYouMessage(transfer.senderDeviceId)
            }
        }
    }

    /**
     * 譲渡完了（ポイント付与）
     */
    fun completeTransfer(transferId: String) {
        val transfer = _pendingTransfers.value.find { it.id == transferId }

        if (transfer != null && transfer.status == TransferStatus.CONFIRMED) {
            // ポイント付与
            val pointsToAdd = 50
            val newPoints = _points.value + pointsToAdd
            _points.value = newPoints
            prefs.edit().putInt("total_points", newPoints).apply()

            // 統計更新
            val newTotal = _totalTransfers.value + 1
            _totalTransfers.value = newTotal
            prefs.edit().putInt("total_transfers", newTotal).apply()

            // 保留リストから削除
            _pendingTransfers.value = _pendingTransfers.value.filter { it.id != transferId }
        }
    }

    /**
     * 感謝メッセージを送信
     */
    private fun sendThankYouMessage(receiverDeviceId: String) {
        val message = ThankYouMessage(
            id = UUID.randomUUID().toString(),
            message = "ありがとうございます！",
            timestamp = System.currentTimeMillis()
        )
        _thankYouMessages.value = _thankYouMessages.value + message
    }

    /**
     * 感謝メッセージをクリア
     */
    fun clearThankYouMessages() {
        _thankYouMessages.value = emptyList()
    }

    /**
     * タイムアウトした譲渡を自動承認（5秒経過）
     */
    fun checkTimeouts() {
        val currentTime = System.currentTimeMillis()
        val transfers = _pendingTransfers.value.toMutableList()
        var updated = false

        transfers.forEachIndexed { index, transfer ->
            if (transfer.status == TransferStatus.PENDING &&
                currentTime - transfer.timestamp > 5000) {
                // 5秒経過したら自動承認
                transfers[index] = transfer.copy(
                    status = TransferStatus.CONFIRMED,
                    confirmedAt = currentTime,
                    autoConfirmed = true
                )
                sendThankYouMessage(transfer.senderDeviceId)
                updated = true
            }
        }

        if (updated) {
            _pendingTransfers.value = transfers
        }
    }
}

/**
 * 譲渡リクエスト
 */
data class TransferRequest(
    val id: String,
    val senderDeviceId: String,
    val receiverDeviceId: String,
    val receiverType: String, // "妊婦", "高齢者", etc.
    val status: TransferStatus,
    val timestamp: Long,
    val confirmedAt: Long? = null,
    val autoConfirmed: Boolean = false
)

enum class TransferStatus {
    PENDING,    // 確認待ち
    CONFIRMED,  // 確認済み
    REJECTED    // 拒否
}

/**
 * 感謝メッセージ
 */
data class ThankYouMessage(
    val id: String,
    val message: String,
    val timestamp: Long
)
