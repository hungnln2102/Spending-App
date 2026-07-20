# Hướng dẫn cài APK internal

## File APK

File mới nhất:

```text
android/app/build/outputs/apk/release/SpendingApp-0.2.0-internal-release.apk
```

## Cài trên thiết bị Android

1. Copy file APK vào điện thoại.
2. Mở file APK trên điện thoại.
3. Cho phép cài đặt từ nguồn không xác định nếu Android hỏi.
4. Nếu Google Play Protect cảnh báo, chọn tiếp tục cài đặt chỉ khi bạn tin nguồn APK này.
5. Mở app `Spending App` và cấp quyền thông báo nếu muốn nhận cảnh báo local.

## Lưu ý khi nâng cấp

- Bản `0.2.0-internal` đổi package từ `com.spendingapp` sang `com.mavrykpremium.spendingapp`.
- Nếu máy đang có bản debug cũ, Android có thể xem đây là app khác. Có thể gỡ bản cũ trước khi cài bản mới để tránh nhầm lẫn.
- APK được ký bằng keystore nội bộ local, không phải chữ ký Google Play.
- Không chia sẻ APK đã nhập token/API key cá nhân cho người khác.

## Kiểm tra thông tin APK

```powershell
& tools/android-sdk/build-tools/35.0.0/aapt.exe dump badging android/app/build/outputs/apk/release/SpendingApp-0.2.0-internal-release.apk
& tools/android-sdk/build-tools/35.0.0/apksigner.bat verify --print-certs android/app/build/outputs/apk/release/SpendingApp-0.2.0-internal-release.apk
```
