# Spending App

Ứng dụng Android quản lý chi tiêu cá nhân theo kiến trúc local-first.

## Kiến trúc hiện tại

- Android native Kotlin.
- Jetpack Compose + Material 3.
- Room làm local database chính.
- DataStore/Android Keystore dự kiến cho settings và token.
- Không yêu cầu backend/server trong MVP.
- SePay sẽ được tích hợp bằng pull sync: user nhập token riêng, app kéo giao dịch khi mở app hoặc bấm Đồng bộ.
- Webhook URL là chế độ optional cho user nâng cao có public endpoint riêng. Xem `docs/WEBHOOK_USER_OWNED.md`.

## Cấu trúc

```text
android/
├─ app/
│  ├─ src/main/java/com/spendingapp/
│  │  ├─ core/
│  │  │  ├─ database/     # Room database, DAO, entity, converters
│  │  │  ├─ data/         # AppContainer, DatabaseSeeder
│  │  │  ├─ domain/       # BalanceService
│  │  │  ├─ model/        # Domain enum
│  │  │  ├─ money/        # Money utilities
│  │  │  └─ sync/         # Import pipeline
│  │  ├─ feature/         # Feature packages
│  │  ├─ MainActivity.kt
│  │  └─ SpendingApplication.kt
│  └─ build.gradle.kts
├─ build.gradle.kts
└─ settings.gradle.kts
```

## Môi trường đã cài

- JDK 17: `C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot`.
- Android command-line SDK local: `tools/android-sdk`.
- Gradle binary local: `tools/gradle-8.10.2`.
- Android SDK packages: `platforms;android-35`, `build-tools;35.0.0`, `platform-tools`.

## Build

Build bằng Gradle local trong repo:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:ANDROID_HOME = (Resolve-Path 'tools\android-sdk').Path
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
cd android
..\tools\gradle-8.10.2\bin\gradle.bat :app:assembleDebug --console=plain
```

APK debug sau khi build:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## Trạng thái triển khai

Đã scaffold:

- Gradle Android project.
- Manifest và Compose `MainActivity`.
- `SpendingApplication` + `AppContainer`.
- Room entities: `Account`, `Category`, `Transaction`, `BalanceLog`, `Budget`, `Goal`, `SyncState`.
- Room DAO cơ bản và enum converters.
- Seed dữ liệu mặc định: category phổ biến và account `Tiền mặt`.
- `BalanceService` cập nhật số dư và tạo `BalanceLog` trong Room transaction.
- TransactionImportPipeline chống trùng theo external id và cập nhật balance qua service.
- Màn Giao dịch: thêm thu/chi thủ công và xem lịch sử local.
- Màn Cài đặt: thêm/list nguồn tiền bank/cash, số dư ban đầu có BalanceLog.

## Kiểm tra gần nhất

Đã chạy thành công:

```powershell
..\tools\gradle-8.10.2\bin\gradle.bat :app:assembleDebug --console=plain --no-daemon
```

Kết quả: `BUILD SUCCESSFUL`.

## Việc tiếp theo

Theo `TASKS.md`, tiếp tục từ:

1. Kiểm thử SePay token thật và điều chỉnh mapping payload nếu cần.
2. Làm hạn mức chi tiêu local.
3. Làm dashboard báo cáo chi tiết hơn.
4. Thêm WorkManager auto sync nền.




