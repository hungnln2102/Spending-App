# ARCHITECTURE - Local-first với 3 chế độ lấy dữ liệu

## 1. Quyết định kiến trúc chính

Ứng dụng được thiết kế theo hướng **local-first**:

- Database chính nằm trên thiết bị Android của người dùng.
- Dữ liệu tài chính cá nhân không bắt buộc đi qua server của chủ app.
- Người dùng có thể dùng app hoàn toàn thủ công nếu không kết nối SePay.
- SePay chỉ là nguồn dữ liệu bên ngoài để đồng bộ giao dịch ngân hàng.
- App phải hoạt động được khi offline với dữ liệu đã có trong máy.

Kiến trúc tổng quan:

```text
Android App
├─ Local encrypted database
├─ Manual transaction input
├─ Pull sync từ SePay API
└─ Optional webhook inbox nếu người dùng có public URL riêng
```

## 2. Ba chế độ lấy dữ liệu

Ứng dụng hỗ trợ tổng cộng 3 chế độ.

### Mode 1 - Local manual only

Đây là chế độ riêng tư nhất và đơn giản nhất.

```text
Người dùng nhập giao dịch thủ công
→ App lưu vào database local
→ Dashboard / Budget / Goal cập nhật từ local database
```

Phù hợp khi:

- Người dùng không có SePay.
- Người dùng không muốn kết nối ngân hàng.
- Người dùng muốn kiểm soát dữ liệu hoàn toàn thủ công.

Đặc điểm:

- Không cần server.
- Không cần API token SePay.
- Không cần webhook URL.
- Hoạt động offline hoàn toàn.

### Mode 2 - Pull sync từ SePay API

Đây là chế độ khuyến nghị cho MVP nếu muốn có dữ liệu ngân hàng nhưng vẫn không cần server riêng.

```text
SePay lưu lịch sử giao dịch
→ App mở lên hoặc user bấm Đồng bộ
→ App gọi SePay API bằng token của user
→ App lấy giao dịch còn thiếu
→ App lưu vào database local
```

Các thời điểm sync:

- Khi người dùng bấm `Đồng bộ`.
- Khi mở app nếu có mạng và đã quá thời gian sync tối thiểu.
- Chạy nền định kỳ bằng `WorkManager` nếu hệ điều hành cho phép.

Lưu ý:

- Không realtime tuyệt đối.
- Nếu thiết bị offline, app không lấy được dữ liệu mới ngay.
- Khi có mạng lại hoặc mở app, app kéo giao dịch thiếu từ SePay về.
- Background sync trên Android không đảm bảo chạy đúng giờ trên mọi thiết bị.

### Mode 3 - Webhook URL do người dùng tự cấu hình

Đây là chế độ nâng cao cho người dùng có URL public riêng.

```text
SePay
→ Public webhook URL do người dùng tự cung cấp
→ Endpoint đó nhận/lưu/forward dữ liệu theo cách của người dùng
→ App đồng bộ từ nguồn đó hoặc import dữ liệu vào local database
```

Quan trọng:

- Webhook cần một URL public có thể nhận HTTP request từ SePay.
- Điện thoại Android thông thường không nên và không đáng tin cậy để tự làm public webhook server.
- Nếu người dùng có server/NAS/tunnel/public endpoint riêng, họ có thể nhập URL đó vào app để lưu cấu hình và hướng dẫn kết nối với SePay.
- App không bắt buộc phải vận hành server thay người dùng.

Chế độ webhook trong app nên được hiểu là:

- App cho phép người dùng lưu cấu hình webhook URL.
- App hướng dẫn người dùng copy URL đó sang SePay.
- App có thể đồng bộ dữ liệu từ endpoint riêng của người dùng nếu họ cung cấp thêm API đọc dữ liệu.
- Nếu không có public URL, người dùng vẫn dùng Mode 1 hoặc Mode 2.

## 3. Luồng fallback

Nếu người dùng bật nhiều chế độ, thứ tự ưu tiên dữ liệu như sau:

1. Dữ liệu local đã lưu là nguồn hiển thị chính.
2. Pull sync từ SePay API để bổ sung giao dịch thiếu.
3. Webhook/external endpoint nếu người dùng cấu hình và có thể truy cập.
4. Nhập thủ công luôn khả dụng để sửa thiếu/sai.

Mọi nguồn dữ liệu ngoài đều phải đi qua cùng một import pipeline:

```text
External transaction
→ Normalize
→ Deduplicate
→ Validate
→ Save Transaction
→ Update Balance
→ Create BalanceLog
→ Emit Local Event
→ Update Dashboard
```

## 4. Chống trùng dữ liệu

Vì một giao dịch có thể đến từ nhiều nguồn, app phải chống trùng bằng nhiều khóa:

- `external_transaction_id`
- `reference_number`
- `bank_account_id`
- `amount`
- `transaction_date`
- `description`
- `source`

Không được cộng/trừ số dư hai lần cho cùng một giao dịch.

## 5. Lưu token và cấu hình nhạy cảm

Nếu dùng Pull sync SePay:

- Mỗi người dùng tự nhập API token SePay của họ.
- Không hardcode token của chủ app vào APK.
- Token phải lưu bằng Android Keystore hoặc storage mã hóa.
- Không log token ra console/logcat.

Nếu dùng webhook URL:

- URL public do người dùng tự nhập có thể được lưu local.
- Secret/signature nếu có cũng phải lưu mã hóa.
- App cần cảnh báo người dùng rằng webhook URL là hạ tầng riêng của họ.

## 6. Đồng bộ nền

App có thể dùng `WorkManager` cho sync nền, nhưng không được xem là realtime.

Điều kiện chạy nền đề xuất:

- Chỉ chạy khi có mạng.
- Có thể cho chọn chỉ sync khi Wi-Fi.
- Tần suất tối thiểu nên là vài giờ, không quá dày.
- Nếu sync nền thất bại, lưu lỗi vào `SyncState` và thử lại sau.

## 7. Trạng thái đồng bộ

Cần có bảng `SyncState` local:

- `id`
- `source`: manual, sepay_api, webhook_endpoint, import_file
- `account_id`
- `last_synced_at`
- `last_cursor`
- `last_transaction_id`
- `last_transaction_date`
- `status`
- `last_error`
- `created_at`
- `updated_at`

## 8. Kết luận triển khai MVP

MVP nên ưu tiên:

1. Mode 1 - nhập thủ công local.
2. Mode 2 - pull sync SePay khi mở app/bấm đồng bộ.
3. Mode 3 - chỉ làm phần cấu hình webhook URL và tài liệu hướng dẫn, xử lý nâng cao để phase sau.

Không cần server của chủ app trong MVP.
