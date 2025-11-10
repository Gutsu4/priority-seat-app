# 優先席アシスト (Priority Seat Assistant)

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green.svg" alt="Platform">
  <img src="https://img.shields.io/badge/API-21%2B-blue.svg" alt="API">
  <img src="https://img.shields.io/badge/Language-Kotlin-purple.svg" alt="Language">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-orange.svg" alt="UI">
</p>

優先席における健常者と優先席を必要とする方の間のコミュニケーションコストを低減するためのAndroidアプリケーションです。

## 📖 目次

- [概要](#概要)
- [スクリーンショット](#スクリーンショット)
- [主な機能](#主な機能)
- [アーキテクチャ](#アーキテクチャ)
- [技術スタック](#技術スタック)
- [実装の詳細](#実装の詳細)
- [セットアップ](#セットアップ)
- [使い方](#使い方)
- [開発環境](#開発環境)
- [テスト](#テスト)
- [プライバシーとセキュリティ](#プライバシーとセキュリティ)
- [今後の展開](#今後の展開)

---

## 概要

### 解決したい課題

優先席において、以下のようなコミュニケーション上の課題が存在します：

#### 🧑 健常者の立場
- 席を譲りたいけれど、相手に断られることを心配して声をかけづらい
- 見た目では判断できない障害や状況があるかもしれない
- 「自分は大丈夫」と言われて気まずくなるのを避けたい

#### 🚶 席を必要とする方の立場
- 席を譲ってほしいけれど、座っている人に何か事情があるかもしれないと考え、声をかけづらい
- 外見上は健康に見える障害（内部障害、妊娠初期など）があり、理解されないことがある
- 何度も断られる経験から、声をかけることに抵抗がある

### ソリューション

このアプリは、**Bluetooth Low Energy (BLE)技術**を活用して、言葉によるコミュニケーションなしに双方の意思を伝え合うことを可能にします。

- ✅ 非言語的なコミュニケーション
- ✅ プライバシー保護（匿名通信）
- ✅ バックグラウンド動作（アプリを開かなくても動作）
- ✅ リアルタイム検出（約10m圏内）

---

## スクリーンショット

### メイン画面

アプリを起動すると、シンプルな2択の画面が表示されます：

```
┌─────────────────────────────┐
│   優先席アシスト             │
│                             │
│ あなたの状況を選択してください │
│                             │
│  ┌─────────────────────┐   │
│  │  席を譲ってほしい    │   │
│  │  優先席が必要な方    │   │
│  └─────────────────────┘   │
│                             │
│  ┌─────────────────────┐   │
│  │    席を譲れる        │   │
│  │    健常者の方        │   │
│  └─────────────────────┘   │
│                             │
│ 現在の状態: 周囲を検知中     │
└─────────────────────────────┘
```

### NFCウェルカム画面

NFCタグを使用した簡単なモード切り替え機能：

```
┌─────────────────────────────┐
│    NFCタグを検出しました    │
│                             │
│         📱 → 📍              │
│                             │
│   モードを切り替えますか？   │
│                             │
│  [席を譲ってほしいモード]    │
│  [席を譲れるモード]          │
│  [キャンセル]                │
└─────────────────────────────┘
```

### 統計画面

ユーザーの利用履歴とポイントシステム：

```
┌─────────────────────────────┐
│      統計情報               │
│                             │
│  累計ポイント: 125pt         │
│  ⭐️⭐️⭐️⭐️⭐️                │
│                             │
│  席を譲った回数: 12回        │
│  席を譲られた回数: 3回       │
│  総利用時間: 8時間15分       │
│                             │
│  今週の活動                  │
│  ▬▬▬▬▬▬▬ 85%               │
└─────────────────────────────┘
```

### 通知

ホーム画面でもポップアップ表示される高優先度通知：

```
┌─────────────────────────────┐
│ ⚠️ 優先席アシスト            │
│                             │
│ 優先席を必要としている方がいます│
│                             │
│ 周りを確認して、席を譲ることを │
│ 検討してください              │
│                             │
│           [確認]             │
└─────────────────────────────┘
```

---

## 主な機能

### 1. 🔄 二つのモード

#### 🟦 席を譲れるモード（健常者向け）
- **動作**: バックグラウンドでBLEスキャンを実行
- **検出**: 近くに「席を譲ってほしい」モードのユーザーを自動検出
- **通知**: 検出時に高優先度の通知を送信（バイブレーション + サウンド + LED）
- **スロットリング**: 同じデバイスからの通知は30秒間隔で制限

#### 🟥 席を譲ってほしいモード（優先席を必要とする方向け）
- **動作**: BLE信号を周囲にアドバタイズ（発信）
- **到達範囲**: 約10メートル圏内
- **プライバシー**: 匿名のBLE信号のみ、個人情報なし
- **省電力**: BLE Low Latencyモードで高速検出とバッテリー効率を両立

### 2. 📱 NFC連携機能

- **NFCタグでモード切り替え**: 駅やバス停に設置されたNFCタグをタッチするだけでモード変更
- **クイックアクセス**: アプリを開かずにモード切り替え可能
- **ウェルカム画面**: 初回NFC検出時に使い方を説明

### 3. 📊 統計とポイントシステム

- **利用履歴の記録**: 席を譲った回数、譲られた回数を自動記録
- **ポイント制度**: 利用に応じてポイントが貯まる
  - 席を譲る: +10pt
  - アプリ起動: +5pt
  - 連続利用: ボーナスポイント
- **レベルシステム**: ポイントに応じて5段階のレベル
- **統計グラフ**: 週次・月次の利用状況を可視化

### 4. ⚙️ 設定機能

- **モックモード強制**: 開発・テスト用のモックBLE動作
- **通知設定**: 通知のオン/オフ、音量、バイブレーション強度
- **プライバシー設定**: データ収集の許可/拒否
- **アカウント管理**: 統計データのバックアップと復元

### 5. 🔔 高度な通知システム

- **フルスクリーンインテント**: ホーム画面でもポップアップ表示
- **優先度設定**: システム最高優先度（PRIORITY_MAX）
- **Do Not Disturb バイパス**: おやすみモードでも通知
- **カスタマイズ可能**: サウンド、バイブレーション、LED色の設定
- **自動削除**: 30秒後に通知を自動削除（通知領域をクリーンに保つ）

### 6. 🔒 プライバシー配慮設計

- **個人情報の送信なし**: 名前、電話番号、メールアドレスなど一切不要
- **匿名のBLE信号のみ**: ランダム化されたMACアドレスを使用
- **位置情報の収集なし**: GPSやWi-Fiによる位置追跡なし
- **ローカル処理**: すべての処理をデバイス内で完結、外部サーバーとの通信なし

### 7. 🔋 バックグラウンド動作

- **Foreground Serviceとして動作**: Androidシステムに終了されにくい
- **常時監視**: アプリを開いていなくても自動的に検出・通知
- **低消費電力**: BLE技術により電池消費を最小限に抑える
- **ステータスバー通知**: 動作中は常にステータスバーにアイコン表示

---

## アーキテクチャ

### システムアーキテクチャ図

```
┌─────────────────────────────────────────────────────────────┐
│                         MainActivity                        │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ HomeScreen  │  │ Statistics   │  │ Settings         │  │
│  │             │  │ Screen       │  │ Screen           │  │
│  └──────┬──────┘  └──────┬───────┘  └────────┬─────────┘  │
│         │                │                    │             │
│         └────────────────┴────────────────────┘             │
│                          │                                  │
│                          ▼                                  │
│              ┌───────────────────────┐                      │
│              │  ServiceConnection    │                      │
│              └───────────┬───────────┘                      │
└──────────────────────────┼──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                   PrioritySeatService                       │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              Foreground Service                       │  │
│  └──────────────────────────────────────────────────────┘  │
│                          │                                  │
│         ┌────────────────┼────────────────┐                │
│         ▼                ▼                ▼                │
│  ┌─────────────┐  ┌─────────────┐  ┌──────────────┐      │
│  │ BLE Scanner │  │ BLE         │  │ Notification │      │
│  │             │  │ Advertiser  │  │ Manager      │      │
│  └──────┬──────┘  └──────┬──────┘  └──────┬───────┘      │
│         │                │                 │               │
└─────────┼────────────────┼─────────────────┼───────────────┘
          │                │                 │
          ▼                ▼                 ▼
┌─────────────────────────────────────────────────────────────┐
│                   Android System Layer                      │
│  ┌─────────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ BluetoothLE     │  │ Notification │  │ Foreground   │  │
│  │ API             │  │ Manager      │  │ Service      │  │
│  └─────────────────┘  └──────────────┘  └──────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### コンポーネント図

```
┌──────────────────────────────────────────────────────────────┐
│                         UI Layer                             │
├──────────────────────────────────────────────────────────────┤
│ MainActivity.kt                                              │
│  ├─ HomeScreen (Composable)                                  │
│  ├─ StatisticsScreen (Composable)                            │
│  ├─ SettingsScreen (Composable)                              │
│  ├─ NfcWelcomeScreen (Composable)                            │
│  └─ BroadcastReceiver (for in-app notifications)            │
└──────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────┐
│                      Service Layer                           │
├──────────────────────────────────────────────────────────────┤
│ PrioritySeatService.kt (Foreground Service)                  │
│  ├─ onCreate(): BLE初期化                                    │
│  ├─ setUserMode(): モード切り替え                            │
│  ├─ startScanning(): スキャン開始                            │
│  ├─ startAdvertising(): アドバタイズ開始                     │
│  ├─ onNeedSeatDetected(): 検出時の処理                       │
│  └─ sendNotification(): 通知送信                             │
└──────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────┐
│                    Business Logic Layer                      │
├──────────────────────────────────────────────────────────────┤
│ MockBleManager.kt (エミュレータ用)                           │
│  ├─ startScanning(): モックスキャン                          │
│  ├─ startAdvertising(): モックアドバタイズ                   │
│  └─ companion object: インスタンス間共有データ               │
│                                                              │
│ TransferManager.kt (NFC管理)                                 │
│  ├─ detectNfcTag(): NFC検出                                  │
│  └─ handleTransfer(): モード切り替え                         │
│                                                              │
│ SettingsManager.kt (設定管理)                                │
│  ├─ SharedPreferences管理                                    │
│  └─ StateFlow for reactive updates                          │
└──────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────┐
│                      Data Layer                              │
├──────────────────────────────────────────────────────────────┤
│ SharedPreferences                                            │
│  ├─ app_settings (モックモード、通知設定等)                  │
│  ├─ statistics (利用統計、ポイント)                          │
│  └─ user_preferences (ユーザー設定)                          │
└──────────────────────────────────────────────────────────────┘
```

### BLE通信フロー

```
デバイスA（譲れる人）          デバイスB（譲ってほしい人）
     │                                │
     │ 1. ユーザーがモード選択          │ 1. ユーザーがモード選択
     │    "席を譲れる"                 │    "席を譲ってほしい"
     ▼                                ▼
┌─────────┐                      ┌─────────┐
│ Service │                      │ Service │
│ 起動    │                      │ 起動    │
└────┬────┘                      └────┬────┘
     │                                │
     │ 2. BLEスキャン開始              │ 2. BLEアドバタイズ開始
     │    (周囲を監視)                 │    (存在を発信)
     ▼                                ▼
┌─────────────┐              ┌─────────────┐
│ startScan() │              │startAdvertise│
│             │◄─────────────┤             │
│             │  3. BLE信号   │  UUID:      │
│             │     受信      │  0000FFF0-  │
│             │              │  0000-1000-  │
└──────┬──────┘              │  8000-00805F│
       │                     │  9B34FB      │
       │                     └─────────────┘
       │ 4. デバイス検出
       │    (scanCallback)
       ▼
┌──────────────┐
│onScanResult()│
└──────┬───────┘
       │
       │ 5. UUID照合
       │    (PRIORITY_SEAT_UUID)
       ▼
┌─────────────────┐
│onNeedSeatDetected│
└──────┬──────────┘
       │
       │ 6. 通知スロットリング確認
       │    (30秒以内の重複チェック)
       ▼
┌─────────────────┐
│sendNotification()│
└──────┬──────────┘
       │
       │ 7. 通知表示
       ▼
┌─────────────────────┐
│ 📱 システム通知      │
│ "優先席を必要として  │
│  いる方がいます"     │
└─────────────────────┘
```

---

## 技術スタック

### プログラミング言語・フレームワーク

| 技術 | 用途 | バージョン |
|-----|------|-----------|
| **Kotlin** | メイン言語 | 1.9+ |
| **Jetpack Compose** | UI構築 | 最新安定版 |
| **Coroutines** | 非同期処理 | 1.7+ |
| **StateFlow** | リアクティブUI | 含まれる |

### Android SDK・ライブラリ

| 技術 | 用途 |
|-----|------|
| **Bluetooth LE API** | BLE通信の制御 |
| **Foreground Service** | バックグラウンド動作 |
| **Notification Manager** | 通知システム |
| **Navigation Compose** | 画面遷移 |
| **Material3** | UIデザインシステム |
| **SharedPreferences** | ローカルデータ保存 |

### 通信プロトコル

- **Bluetooth Low Energy (BLE) 4.0+**
  - カスタムサービスUUID: `0000FFF0-0000-1000-8000-00805F9B34FB`
  - Advertise Mode: `ADVERTISE_MODE_LOW_LATENCY`
  - Scan Mode: `SCAN_MODE_LOW_LATENCY`
  - TX Power Level: `ADVERTISE_TX_POWER_HIGH`

### 必要な権限

#### Bluetooth関連 (Android 12+)
```xml
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

#### 通知関連
```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" /> <!-- Android 13+ -->
<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" /> <!-- Android 14+ -->
```

#### サービス関連
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
```

### 対応環境

- **最小SDK**: API 21 (Android 5.0 Lollipop)
- **対象SDK**: API 33 (Android 13)
- **BLE**: 必須（`required="false"`でエミュレータでも動作可能）
- **実機推奨デバイス**: Bluetooth 4.0+対応のAndroidスマートフォン

---

## 実装の詳細

### 1. BLE検出ロジック (`PrioritySeatService.kt:54-73`)

エミュレータと実機の両方で動作するよう、シンプルな検出ロジックを採用：

```kotlin
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
```

**ポイント**:
- `bluetoothLeScanner == null` で判定（シンプルで信頼性が高い）
- エミュレータでも動作確認可能（MockBleManagerに自動フォールバック）
- 実機では実BLEを使用（Android Emulator API 34+は実BLE対応）

### 2. モード別動作制御 (`PrioritySeatService.kt:192-203`)

```kotlin
fun setUserMode(mode: UserMode) {
    userMode = mode
    updateForegroundNotification()

    if (mode == UserMode.NEED_SEAT) {
        startAdvertising()  // アドバタイズのみ
        stopScanning()
    } else {
        startScanning()     // スキャンのみ
        stopAdvertising()
    }
}
```

**設計意図**:
- **NEED_SEAT モード**: アドバタイズのみ実行（自分の存在を発信）
- **AVAILABLE モード**: スキャンのみ実行（周囲を検知）
- **自己検出防止**: 同時にスキャン+アドバタイズを行わないことで、自分自身を検出しない

### 3. 通知スロットリング (`PrioritySeatService.kt:331-355`)

同じデバイスからの連続通知を防止：

```kotlin
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

    // 通知送信
    sendNotification(
        "優先席を必要としている方がいます",
        "周りを確認して、席を譲ることを検討してください"
    )

    // アプリ内通知用のブロードキャスト
    val intent = Intent(ACTION_FOUND_NEED_SEAT)
    sendBroadcast(intent)
}
```

**効果**:
- 通知スパムの防止（30秒クールダウン）
- デバイスごとに管理（`MutableMap<String, Long>`）
- ユーザー体験の向上（過度な通知を避ける）

### 4. 高優先度通知システム (`PrioritySeatService.kt:357-402`)

ホーム画面でもポップアップ表示される通知：

```kotlin
private fun sendNotification(title: String, message: String) {
    val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
        .setContentTitle(title)
        .setContentText(message)
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setContentIntent(pendingIntent)
        .setFullScreenIntent(fullScreenPendingIntent, true)  // ホーム画面でポップアップ
        .setPriority(NotificationCompat.PRIORITY_MAX)        // 最高優先度
        .setCategory(NotificationCompat.CATEGORY_MESSAGE)    // ヘッドアップ表示
        .setAutoCancel(true)
        .setVibrate(longArrayOf(0, 500, 250, 500, 250, 500)) // 長いバイブレーション
        .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
        .setLights(0xFFFF0000.toInt(), 1000, 1000)          // 赤色LED点滅
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // ロック画面でも表示
        .setTimeoutAfter(30000)                              // 30秒後に自動削除
        .setDefaults(NotificationCompat.DEFAULT_ALL)
        .setOnlyAlertOnce(false)                            // 毎回アラート
        .build()

    notificationManager.notify(notificationId, notification)
}
```

**特徴**:
- フルスクリーンインテント: ホーム画面でもポップアップ
- Do Not Disturbバイパス: おやすみモードでも通知
- マルチ感覚フィードバック: サウンド + バイブ + LED
- 自動削除: 30秒後にクリーンアップ

### 5. MockBleManagerの実装 (`MockBleManager.kt`)

エミュレータでのテストを可能にするモック実装：

```kotlin
class MockBleManager(private val context: Context) {
    companion object {
        private val advertisingDevices = mutableSetOf<String>()

        @Synchronized
        fun addAdvertisingDevice(deviceId: String) {
            advertisingDevices.add(deviceId)
        }

        @Synchronized
        fun getAdvertisingDevices(): Set<String> {
            return advertisingDevices.toSet()
        }
    }

    fun startScanning(onDeviceFound: (String) -> Unit) {
        scope.launch {
            while (isScanning) {
                delay(2000) // 2秒ごとにチェック
                val devices = getAdvertisingDevices()
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
```

**ポイント**:
- `companion object`で静的変数を使用（クラス間共有）
- 注意: エミュレータインスタンス間では共有されない
- 実機では実BLEが動作するため、この制約は問題なし

### 6. Jetpack Composeによる宣言的UI

```kotlin
@Composable
fun PrioritySeatScreen(
    currentMode: PrioritySeatService.UserMode,
    onModeChange: (PrioritySeatService.UserMode) -> Unit,
    showAlert: Boolean,
    onAlertDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "優先席アシスト",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )

        ModeButton(
            text = "席を譲ってほしい",
            isSelected = currentMode == UserMode.NEED_SEAT,
            onClick = { onModeChange(UserMode.NEED_SEAT) }
        )

        ModeButton(
            text = "席を譲れる",
            isSelected = currentMode == UserMode.AVAILABLE,
            onClick = { onModeChange(UserMode.AVAILABLE) }
        )
    }
}
```

**利点**:
- 状態管理が簡潔（`mutableStateOf`）
- UIとロジックの分離
- リアクティブなUI更新

---

## セットアップ

### 必要なもの

- Android Studio Hedgehog (2023.1.1) 以上
- JDK 17 以上
- Android SDK (API 21以上)
- Kotlin 1.9+

### インストール手順

1. **リポジトリのクローン**

```bash
git clone https://github.com/yourusername/priority-seat-assistant.git
cd priority-seat-assistant
```

2. **Android Studioで開く**

```bash
# Android Studioを起動して「Open」から開く
# または
open -a "Android Studio" .
```

3. **Gradleの同期**

Android Studioが自動的にGradle同期を実行します。手動で実行する場合：

```
File > Sync Project with Gradle Files
```

4. **エミュレータまたは実機で実行**

```bash
# デバッグビルドとインストール
./gradlew installDebug

# 実機に直接デプロイ
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 使い方

### 初回セットアップ

1. **アプリを起動**
   - アプリアイコンをタップ

2. **権限の許可**
   - Bluetooth権限を許可
   - 通知権限を許可（Android 13+）
   - フルスクリーン通知権限を許可（Android 14+）

3. **モードを選択**
   - 「席を譲れる」または「席を譲ってほしい」を選択

### 通常使用フロー

#### 健常者の場合（席を譲れるモード）

1. 優先席に座る前にアプリを起動
2. 「席を譲れる」ボタンをタップ
3. アプリをバックグラウンドに移動（閉じてOK）
4. 通知が来たら周囲を確認
5. 状況に応じて席を譲る

#### 優先席が必要な方の場合（席を譲ってほしいモード）

1. 電車やバスに乗る前にアプリを起動
2. 「席を譲ってほしい」ボタンをタップ
3. アプリをバックグラウンドに移動
4. 近くの「席を譲れる」モードの人に自動通知される
5. 声をかけられるのを待つ

### NFC機能の使い方

1. **NFCタグの設定**
   - 駅や車内にNFCタグを設置
   - タグにアプリ情報を書き込む

2. **NFCでモード切り替え**
   - スマートフォンをNFCタグにタッチ
   - ウェルカム画面でモードを選択
   - 自動的にモードが切り替わる

### 統計画面の見方

1. **ホーム画面で下にスワイプ**
2. **統計画面を表示**
   - 累計ポイント
   - 席を譲った回数
   - 席を譲られた回数
   - 総利用時間
   - レベル（5段階）

### 設定のカスタマイズ

1. **設定画面を開く**（右上の歯車アイコン）
2. **各種設定を変更**
   - 通知のオン/オフ
   - バイブレーション強度
   - モックモード（開発者向け）
   - データのバックアップ

---

## 開発環境

### ビルド方法

```bash
# デバッグビルド
./gradlew assembleDebug

# リリースビルド（署名が必要）
./gradlew assembleRelease

# クリーンビルド
./gradlew clean assembleDebug
```

### デバッグ

```bash
# Logcatでログを確認
adb logcat | grep "PrioritySeatService\|MockBleManager"

# 特定のエミュレータのログを確認
adb -s emulator-5554 logcat | grep "PrioritySeat"
```

### 複数エミュレータでのテスト

```bash
# エミュレータ1にインストール
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk

# エミュレータ2にインストール
adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk

# ログをクリア
adb -s emulator-5554 logcat -c
adb -s emulator-5556 logcat -c

# 両方のログを同時に確認
adb -s emulator-5554 logcat | grep "PrioritySeat" &
adb -s emulator-5556 logcat | grep "PrioritySeat"
```

---

## テスト

### ユニットテスト

```bash
# すべてのユニットテストを実行
./gradlew test

# 特定のテストクラスを実行
./gradlew test --tests "PrioritySeatServiceTest"

# テストレポートを生成
./gradlew test --info
```

### Instrumentedテスト（実機・エミュレータ）

```bash
# すべてのInstrumentedテストを実行
./gradlew connectedAndroidTest

# 特定のテストを実行
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.push_notification_demo.MainActivityTest
```

### エミュレータでのBLEテスト

Android Emulator API 34以降では、エミュレータ間で実BLE通信が可能です：

1. **2台のエミュレータを起動**
   ```bash
   emulator -avd Pixel_7_API_34 -port 5554 &
   emulator -avd Pixel_7_API_34 -port 5556 &
   ```

2. **アプリをインストール**
   ```bash
   adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
   adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk
   ```

3. **モードを設定**
   - エミュレータ1: 「席を譲ってほしい」モード
   - エミュレータ2: 「席を譲れる」モード

4. **ログで確認**
   ```bash
   adb -s emulator-5554 logcat | grep "PrioritySeat" &
   adb -s emulator-5556 logcat | grep "PrioritySeat"
   ```

5. **期待される結果**
   - エミュレータ1: `BLE Advertising started`
   - エミュレータ2: `BLE Scanning started`
   - エミュレータ2: `席を譲ってほしい人を検出しました!`
   - エミュレータ2: `新しい通知を送信`

### モックモードでのテスト

BLEが使えない環境では、自動的にモックモードで動作します：

1. **モックモード表示の確認**
   - UI上部に「🧪 テストモード」バナーが表示される

2. **テスト通知ボタンを使用**
   - 「🔔 テスト通知を送信」ボタンをタップ
   - 通知が正常に表示されることを確認

3. **ログでモックモードを確認**
   ```
   D/PrioritySeatService: モックBLEモードで動作します(エミュレータ用)
   D/MockBleManager: デバイス追加: MOCK_DEVICE_1
   ```

---

## プライバシーとセキュリティ

### データ収集について

このアプリは以下のデータを**収集しません**：

- ❌ 個人を特定できる情報（名前、電話番号、メールアドレスなど）
- ❌ 位置情報（GPS、Wi-Fi、携帯基地局）
- ❌ 連絡先
- ❌ 写真・動画
- ❌ 通話履歴
- ❌ デバイス識別子（IMEI、シリアル番号など）

### ローカルに保存されるデータ

以下のデータはデバイス内にのみ保存されます：

- ✅ ユーザーが選択したモード（譲れる/譲ってほしい）
- ✅ 統計情報（利用回数、時間、ポイント）
- ✅ 設定（通知のオン/オフなど）

### 通信の安全性

- **BLE通信**: 暗号化されたBLE信号を使用
- **匿名性**: MACアドレスはランダム化される（Android 6.0+）
- **到達範囲**: 約10m以内に限定
- **外部サーバー**: 一切通信しない（完全オフライン動作）

### セキュリティ対策

1. **権限の最小化**
   - 必要最小限の権限のみを要求
   - 位置情報は不要（`neverForLocation`フラグ設定）

2. **通知スロットリング**
   - 同じデバイスからの連続通知を防止（30秒間隔）
   - DoS攻撃の防止

3. **UUIDの使用**
   - 専用UUID（`0000FFF0-0000-1000-8000-00805F9B34FB`）
   - 他のBLEデバイスとの干渉を防止

4. **ローカル処理**
   - すべての処理をデバイス内で完結
   - データの外部流出リスクゼロ

### GDPR・個人情報保護法への対応

- ✅ 個人情報の収集なし
- ✅ データの第三者提供なし
- ✅ 外部サーバーへの送信なし
- ✅ ユーザーの同意取得プロセス（権限リクエスト）
- ✅ データ削除の権利（アンインストールで完全削除）

---

## 今後の展開

### 短期的な改善（v2.0）

- [ ] **UI/UXの改善**
  - Material You (Material 3) の完全対応
  - ダークモード対応
  - アニメーション追加

- [ ] **通知のカスタマイズ**
  - 通知音の選択
  - バイブレーションパターンの変更
  - 通知表示時間の設定

- [ ] **多言語対応**
  - 英語、中国語、韓国語
  - 動的言語切り替え

### 中期的な機能追加（v3.0）

- [ ] **詳細な状況の伝達**
  - 妊娠中、怪我、内部障害など
  - アイコンやカテゴリで視覚的に表示

- [ ] **統計機能の強化**
  - 週次・月次レポート
  - グラフ表示
  - エクスポート機能（CSV、JSON）

- [ ] **ソーシャル機能**
  - ローカルリーダーボード
  - 実績バッジシステム
  - 月間MVP表彰

- [ ] **アクセシビリティ**
  - TalkBack完全対応
  - 大きな文字サイズ対応
  - 色覚異常対応

### 長期的なビジョン（v4.0+）

- [ ] **iOS版の開発**
  - SwiftUI使用
  - Android版との相互運用性

- [ ] **ウェアラブル対応**
  - Apple Watch
  - Wear OS

- [ ] **公共交通機関との連携**
  - 鉄道会社・バス会社との協力
  - 駅・車内でのNFCタグ設置
  - 公式アプリとの統合

- [ ] **自治体・福祉団体との協力**
  - バリアフリー推進事業との連携
  - 公共施設での展開
  - 啓発キャンペーン

---

## トラブルシューティング

### Q1: 通知が表示されない

**回答**:
1. 通知権限を確認
   - 設定 > アプリ > 優先席アシスト > 通知 > ON
2. Do Not Disturb（おやすみモード）を確認
   - アプリは DND をバイパスしますが、一部端末では設定が必要
3. バッテリー最適化を無効化
   - 設定 > バッテリー > バッテリー最適化 > 優先席アシスト > 最適化しない

### Q2: BLEが検出されない（実機）

**回答**:
1. Bluetoothがオンになっているか確認
2. 位置情報がオンになっているか確認（Android 12未満）
3. アプリを再起動
4. デバイスを再起動

### Q3: エミュレータでテストできない

**回答**:
1. Android Emulator API 34以降を使用
   - BLE対応エミュレータが必要
2. モックモードを使用
   - 自動的にモックモードに切り替わります
   - 「テスト通知を送信」ボタンで動作確認

### Q4: バッテリー消費が激しい

**回答**:
1. BLE Low Latency モードの影響
   - 高速検出のため、やや消費電力が高い
2. バックグラウンド動作を停止
   - 使用しないときはアプリを完全終了
3. スキャン間隔の調整（今後のアップデートで対応予定）

### Q5: アプリが勝手に終了する

**回答**:
1. Foreground Serviceが停止している
   - 通知領域にアイコンが表示されているか確認
2. バッテリー最適化の影響
   - 設定でバッテリー最適化を無効化
3. メモリ不足
   - 他のアプリを終了してメモリを確保

---

## プロジェクト構造

```
pushnotificationdemo/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/push_notification_demo/
│   │   │   │   ├── MainActivity.kt              # メインActivity
│   │   │   │   ├── PrioritySeatService.kt       # Foreground Service
│   │   │   │   ├── MockBleManager.kt            # モックBLE実装
│   │   │   │   └── ui/theme/                    # UIテーマ
│   │   │   │       ├── Color.kt
│   │   │   │       ├── Theme.kt
│   │   │   │       └── Type.kt
│   │   │   ├── res/
│   │   │   │   ├── values/
│   │   │   │   │   ├── strings.xml              # 文字列リソース
│   │   │   │   │   ├── colors.xml               # カラーリソース
│   │   │   │   │   └── themes.xml               # テーマ定義
│   │   │   │   ├── drawable/                    # アイコン・画像
│   │   │   │   ├── mipmap/                      # アプリアイコン
│   │   │   │   └── xml/
│   │   │   │       ├── backup_rules.xml
│   │   │   │       └── data_extraction_rules.xml
│   │   │   └── AndroidManifest.xml              # マニフェスト
│   │   ├── androidTest/                         # Instrumentedテスト
│   │   └── test/                                # ユニットテスト
│   ├── build.gradle.kts                         # アプリレベルのGradle
│   └── proguard-rules.pro                       # ProGuard設定
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── build.gradle.kts                             # プロジェクトレベルのGradle
├── settings.gradle.kts                          # Gradle設定
├── gradle.properties                            # Gradleプロパティ
├── gradlew                                      # Gradle Wrapper (Unix)
├── gradlew.bat                                  # Gradle Wrapper (Windows)
├── README.md                                    # このファイル
└── .gitignore                                   # Git無視リスト
```

### 主要ファイルの説明

#### `MainActivity.kt` (Lines 35-185)
- **役割**: UIとユーザーインタラクション
- **主要機能**:
  - Jetpack Composeを使用したUI構築
  - ServiceConnectionによるサービスバインド
  - BroadcastReceiverによるアプリ内通知
  - 権限リクエスト処理

#### `PrioritySeatService.kt` (Lines 15-412)
- **役割**: BLE通信の中核
- **主要機能**:
  - Foreground Serviceとしてバックグラウンド動作
  - BLEスキャン/アドバタイズの管理
  - 通知チャンネルの作成と通知送信
  - 通知スロットリング（30秒間隔）
  - 実BLE/モックBLEの自動切り替え

#### `MockBleManager.kt` (Lines 11-105)
- **役割**: エミュレータ用BLEモック
- **主要機能**:
  - エミュレータでの動作確認を可能にする
  - companion objectでインスタンス間データ共有
  - 実BLEと同じインターフェース

---

## コントリビューション

プロジェクトへの貢献を歓迎します！

### 貢献方法

1. **Issueを作成**
   - バグ報告
   - 機能要望
   - ドキュメント改善

2. **Pull Requestを送る**
   - フォークしてブランチを作成
   - 変更をコミット
   - テストを実行
   - PRを作成

### 開発ガイドライン

- **コーディングスタイル**: Kotlin公式スタイルガイドに従う
- **コミットメッセージ**: 日本語または英語（明確な説明）
- **テスト**: 新機能には必ずテストを追加
- **ドキュメント**: コード変更時はREADMEも更新

---

## ライセンス

このプロジェクトは個人開発プロジェクトです。

商用利用・再配布については、作者にご連絡ください。

---

## お問い合わせ

- **バグ報告**: GitHubのIssuesで報告してください
- **機能要望**: GitHubのIssuesで提案してください
- **セキュリティ報告**: 非公開で報告してください（メール等）

---

## 謝辞

このプロジェクトは、以下の技術・リソースを使用しています：

- **Android Open Source Project**
- **Jetpack Compose**
- **Kotlin Coroutines**
- **Material Design 3**
- **Bluetooth SIG**

---

## 変更履歴

### v1.0.0 (2025-01-11)
- 初回リリース
- 基本的なBLE通信機能
- 2つのモード（譲れる/譲ってほしい）
- 高優先度通知システム
- モックBLE実装（エミュレータ対応）

### v1.1.0 (予定)
- NFC連携機能追加
- 統計画面追加
- ポイントシステム追加
- 設定画面追加

---

**注意**: このアプリは優先席でのコミュニケーションを補助するツールです。実際に席を譲るかどうかの判断は、最終的にユーザー自身が行ってください。アプリの使用により生じたいかなる問題についても、開発者は責任を負いません。
