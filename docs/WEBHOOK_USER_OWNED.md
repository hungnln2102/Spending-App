# Webhook user-owned cho SePay

Tài liệu này dành cho người dùng nâng cao muốn dùng webhook riêng để nhận biến động từ SePay. Đây là chế độ optional; app vẫn hoạt động bình thường bằng nhập thủ công, sync khi mở app hoặc bấm Đồng bộ.

## Khi nào cần webhook

Webhook phù hợp khi bạn đã có một URL public riêng như server cá nhân, NAS có reverse proxy, Cloudflare Tunnel, ngrok, VPS hoặc một service serverless do bạn quản lý. SePay sẽ gửi HTTP request tới URL đó khi có biến động.

Nếu bạn không có URL public, hãy dùng pull sync trong app:

```text
Mở app hoặc bấm Đồng bộ
→ App gọi SePay API bằng token trên thiết bị
→ App lấy giao dịch còn thiếu
→ App lưu vào Room local database
```

## Điện thoại không nên làm public server

Không khuyến nghị biến điện thoại thành webhook server public vì:

- Điện thoại thường nằm sau NAT/mobile network nên SePay không gọi trực tiếp được.
- Pin, chế độ ngủ và giới hạn chạy nền có thể làm server không ổn định.
- Mở cổng public vào điện thoại tăng rủi ro bảo mật.
- Khi điện thoại mất mạng, webhook không thể gửi tới thiết bị.

Thiết kế local-first của app không cần server trung tâm. Webhook chỉ là kênh nâng cao nếu bạn tự có hạ tầng public.

## Luồng đề xuất

```text
SePay
→ Public endpoint của bạn
→ Endpoint xác thực secret/signature
→ Endpoint lưu raw payload tối thiểu hoặc chuẩn hóa giao dịch
→ App pull dữ liệu từ endpoint khi có mạng hoặc khi user bấm Đồng bộ
→ TransactionImportPipeline chống trùng và ghi vào database local
```

Endpoint riêng không nên ghi thẳng vào database của app trên điện thoại. Cách an toàn hơn là endpoint giữ một inbox nhỏ, app chủ động kéo dữ liệu còn thiếu khi có mạng.

## Ví dụ hạ tầng có thể dùng

- VPS nhỏ chạy HTTPS endpoint.
- NAS tại nhà qua reverse proxy HTTPS.
- Cloudflare Tunnel trỏ về máy cá nhân/NAS.
- ngrok dùng cho kiểm thử tạm thời.
- Serverless function nếu bạn chấp nhận dữ liệu đi qua nhà cung cấp đó.

Dù dùng cách nào, endpoint cần dùng HTTPS và có cơ chế xác thực request.

## Bảo mật bắt buộc

Endpoint webhook nên có các lớp bảo vệ sau:

- Secret/API key riêng, không hardcode trong app hoặc public repo.
- Signature verification nếu SePay hỗ trợ chữ ký webhook.
- Kiểm tra timestamp/nonce để giảm replay attack.
- Idempotency key để cùng một giao dịch gửi lại nhiều lần không tạo trùng.
- Rate limit cơ bản để tránh spam endpoint.
- Log tối thiểu, không ghi token, API key hoặc dữ liệu dư thừa.

Trong app, dữ liệu từ endpoint user-owned phải đi qua `TransactionImportPipeline` để dùng chung logic chống trùng, cập nhật balance và event domain.

## Fallback khi webhook không chạy

Webhook không thay thế pull sync. Nếu endpoint hoặc thiết bị mất mạng:

- SePay không thể gọi tới URL không reachable.
- App vẫn có thể sync khi mở lên hoặc khi user bấm Đồng bộ.
- Background sync bằng WorkManager chỉ là best-effort, không realtime tuyệt đối.
- Dữ liệu đã có vẫn xem được offline vì database nằm local trên thiết bị.

## Cấu hình trong app

Trong màn Cài đặt, user có thể nhập `Public webhook URL` và API key optional nếu tự có endpoint. Nếu không nhập, app vẫn dùng được bằng thủ công/pull sync.

Không chia sẻ URL webhook, API key hoặc APK đã cài token cho người khác.
