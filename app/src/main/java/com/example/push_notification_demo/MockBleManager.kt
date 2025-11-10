package com.example.push_notification_demo

import android.content.Context
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
    }

    private val deviceId = "MOCK_DEVICE_${++instanceCount}"

    fun startScanning(onDeviceFound: (String) -> Unit) {
        if (isScanning) {
            Log.d(TAG, "$deviceId: スキャンは既に開始されています")
            return
        }

        isScanning = true
        onDeviceFoundCallback = onDeviceFound

        Log.d(TAG, "$deviceId: スキャン開始")

        // 定期的にアドバタイズ中のデバイスをチェック
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
}
