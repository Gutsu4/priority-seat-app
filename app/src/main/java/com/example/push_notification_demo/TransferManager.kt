package com.example.push_notification_demo

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * ポイントと統計を管理するクラス
 */
class TransferManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("transfer_prefs", Context.MODE_PRIVATE)

    // ポイント
    private val _points = MutableStateFlow(prefs.getInt("total_points", 0))
    val points: StateFlow<Int> = _points

    // 統計
    private val _totalTransfers = MutableStateFlow(prefs.getInt("total_transfers", 0))
    val totalTransfers: StateFlow<Int> = _totalTransfers

    /**
     * ポイントを直接追加（席を譲った時）
     */
    fun addPoints(amount: Int = 50) {
        val newPoints = _points.value + amount
        _points.value = newPoints
        prefs.edit().putInt("total_points", newPoints).apply()

        // 統計更新
        val newTotal = _totalTransfers.value + 1
        _totalTransfers.value = newTotal
        prefs.edit().putInt("total_transfers", newTotal).apply()
    }

    /**
     * ポイントを使用（交換時）
     */
    fun usePoints(amount: Int): Boolean {
        if (_points.value >= amount) {
            val newPoints = _points.value - amount
            _points.value = newPoints
            prefs.edit().putInt("total_points", newPoints).apply()
            return true
        }
        return false
    }
}
