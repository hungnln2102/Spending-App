# Logic ứng dụng quản lý chi tiêu cá nhân


## 0. Quyết định kiến trúc

Ứng dụng đi theo hướng **local-first**: database chính nằm trên thiết bị Android của người dùng. App không bắt buộc có server trung tâm trong MVP.

Ứng dụng hỗ trợ 3 chế độ lấy dữ liệu:

1. `Local manual only`: người dùng nhập giao dịch thủ công, dữ liệu lưu local.
2. `Pull sync SePay API`: app gọi SePay API khi người dùng bấm đồng bộ, khi mở app, hoặc khi WorkManager chạy nền nếu Android cho phép.
3. `Webhook URL do người dùng tự cấu hình`: người dùng có thể nhập public webhook URL riêng nếu họ có server/NAS/tunnel/endpoint public để kết nối với SePay.

Lưu ý quan trọng:

- Webhook cần URL public, nhưng URL đó không mặc định là điện thoại Android.
- Nếu người dùng không có public URL, app vẫn hoạt động bằng nhập thủ công và pull sync.
- SePay webhook không phải yêu cầu bắt buộc cho MVP.
- Dữ liệu hiển thị trong app luôn lấy từ local database sau khi đã import/sync.

Tài liệu kiến trúc chi tiết nằm tại ARCHITECTURE.md.

## 1. Mục tiêu sản phẩm

Ứng dụng Android dùng để quản lý tài chính cá nhân theo hướng tự động hóa tối đa nhưng vẫn cho phép người dùng nhập và điều chỉnh thủ công khi cần.

Ứng dụng cần giúp người dùng:

- Theo dõi tổng tài sản hiện có gồm tiền ngân hàng và tiền mặt.
- Ghi nhận toàn bộ giao dịch thu/chi.
- Phân loại chi tiêu theo hạng mục.
- Đặt mục tiêu tài chính cá nhân.
- Đặt hạn mức chi tiêu theo tháng cho từng hạng mục.
- Nhận cảnh báo khi sắp vượt hoặc đã vượt hạn mức.
- So sánh tình hình chi tiêu giữa các tháng bằng số liệu và biểu đồ.

## 2. Phạm vi chức năng chính

Ứng dụng gồm các module chính:

1. Tổng tiền hiện có.
2. Log chi tiêu và log thu nhập.
3. Log cập nhật tổng tiền.
4. Mục tiêu tài chính.
5. Hạn mức chi tiêu theo hạng mục.
6. Trang tổng quan và báo cáo.
7. Hệ thống thông báo.
8. Hệ thống đồng bộ dữ liệu theo Event-Driven.

## 3. Tổng tiền hiện có

### 3.1. Thành phần tổng tiền

Tổng tiền hiện có được tính từ:

- Tiền ngân hàng.
- Tiền mặt.
- Các nguồn tiền khác nếu sau này cần mở rộng, ví dụ ví điện tử, tài khoản tiết kiệm, đầu tư.

Công thức tổng quát:

```text
Tổng tiền hiện có = Tổng số dư ngân hàng + Tổng tiền mặt + Tổng nguồn tiền khác
```

### 3.2. Tiền ngân hàng

Tiền ngân hàng được cập nhật tự động thông qua webhook từ SePay.

Webhook SePay dùng để nhận biến động số dư gồm:

- Giao dịch tiền vào.
- Giao dịch tiền ra.
- Số tiền giao dịch.
- Thời gian giao dịch.
- Nội dung chuyển khoản.
- Mã giao dịch hoặc mã tham chiếu.
- Tài khoản ngân hàng liên quan.

#### Luồng tiền vào

Khi nhận giao dịch tiền vào:

1. Hệ thống nhận webhook từ SePay.
2. Kiểm tra tính hợp lệ của webhook.
3. Kiểm tra giao dịch đã tồn tại chưa để tránh ghi trùng.
4. Tạo log giao dịch loại `income`.
5. Cộng số tiền vào số dư ngân hàng tương ứng.
6. Ghi log cập nhật tổng tiền.
7. Phát event `MoneyReceived`.
8. Cập nhật dashboard và thông báo nếu cần.

#### Luồng tiền ra

Khi nhận giao dịch tiền ra:

1. Hệ thống nhận webhook từ SePay.
2. Kiểm tra tính hợp lệ của webhook.
3. Kiểm tra giao dịch đã tồn tại chưa để tránh ghi trùng.
4. Tạo log giao dịch loại `expense` với trạng thái `pending_category`.
5. Trừ số tiền khỏi số dư ngân hàng tương ứng.
6. Đưa giao dịch vào danh sách chờ phân loại.
7. Phát event `ExpenseDetected`.
8. Người dùng mở form phân loại để gắn hạng mục chi tiêu.
9. Sau khi gắn hạng mục, hệ thống phát event `ExpenseCategorized`.
10. Hệ thống kiểm tra hạn mức tháng của hạng mục đó.

Giao dịch tiền ra không nên tự động tính vào báo cáo hạng mục nếu chưa được phân loại. Có thể hiển thị trong nhóm `Chưa phân loại` để người dùng dễ xử lý.

### 3.3. Tiền mặt

Tiền mặt được nhập và cập nhật thủ công bởi người dùng.

Các thao tác chính:

- Thiết lập số dư tiền mặt ban đầu.
- Thêm giao dịch chi tiền mặt.
- Thêm giao dịch nhận tiền mặt.
- Điều chỉnh số dư tiền mặt định kỳ.
- Ghi chú lý do điều chỉnh.

Khi người dùng cập nhật tiền mặt:

1. Người dùng nhập số tiền mới hoặc giao dịch tiền mặt.
2. Hệ thống tạo log cập nhật tiền mặt.
3. Hệ thống tính chênh lệch so với số dư cũ nếu là thao tác điều chỉnh số dư.
4. Phát event `CashBalanceUpdated`.
5. Cập nhật tổng tiền hiện có.

## 4. Log giao dịch

### 4.1. Mục đích

Log giao dịch là nơi hiển thị toàn bộ lịch sử thu/chi của người dùng.

Mỗi log cần có tối thiểu:

- Loại giao dịch: thu nhập, chi tiêu, điều chỉnh số dư, chuyển khoản nội bộ.
- Số tiền.
- Thời gian phát sinh.
- Nguồn tiền: ngân hàng, tiền mặt, ví điện tử hoặc nguồn khác.
- Hạng mục.
- Trạng thái phân loại.
- Nội dung hoặc ghi chú.
- Mã giao dịch ngoài nếu đến từ SePay/ngân hàng.
- Ngày tạo trong hệ thống.
- Ngày cập nhật gần nhất.

### 4.2. Trạng thái giao dịch

Một giao dịch có thể có các trạng thái:

- `pending_category`: đã ghi nhận nhưng chưa phân loại.
- `categorized`: đã phân loại.
- `ignored`: người dùng đánh dấu bỏ qua, không tính vào báo cáo.
- `duplicated`: giao dịch bị phát hiện trùng.
- `adjusted`: giao dịch đã được điều chỉnh thông tin.

### 4.3. Thêm log thủ công

Người dùng có thể bấm nút `Thêm log` để tự nhập giao dịch trong các trường hợp:

- Chi tiêu bằng tiền mặt.
- Ngân hàng không hỗ trợ webhook tiền ra.
- SePay chưa nhận được biến động số dư.
- Người dùng muốn ghi nhận một khoản thu/chi ngoài hệ thống ngân hàng.

Form thêm log cần có:

- Loại giao dịch.
- Nguồn tiền.
- Số tiền.
- Hạng mục.
- Thời gian.
- Ghi chú.
- Ảnh hóa đơn nếu sau này cần hỗ trợ.

Sau khi thêm log thủ công:

1. Hệ thống tạo giao dịch mới.
2. Cập nhật số dư nguồn tiền tương ứng.
3. Phát event phù hợp: `ManualExpenseCreated`, `ManualIncomeCreated` hoặc `BalanceAdjusted`.
4. Kiểm tra lại hạn mức nếu là chi tiêu.
5. Cập nhật dashboard.

### 4.4. Nguồn dữ liệu bổ sung

Ngoài webhook SePay, có thể cân nhắc các nguồn dữ liệu bổ sung:

- Đọc thông báo ngân hàng trên điện thoại.
- Đọc email thông báo biến động số dư.
- Import file sao kê CSV/Excel.

Các nguồn này nên được thiết kế như adapter riêng, không viết cứng vào logic giao dịch chính.

Lưu ý bảo mật: đọc thông báo và email là chức năng nhạy cảm, chỉ triển khai khi thật sự cần, phải xin quyền rõ ràng, giải thích mục đích sử dụng dữ liệu và cho phép người dùng tắt bất kỳ lúc nào.

## 5. Hạng mục chi tiêu

### 5.1. Hạng mục mặc định

Ứng dụng nên có sẵn một số hạng mục phổ biến:

- Ăn uống.
- Di chuyển.
- Nhà ở.
- Hóa đơn.
- Mua sắm.
- Sức khỏe.
- Giải trí.
- Giáo dục.
- Gia đình.
- Công việc.
- Tiết kiệm.
- Khác.

### 5.2. Quản lý hạng mục

Người dùng có thể:

- Thêm hạng mục mới.
- Đổi tên hạng mục.
- Chọn icon/màu cho hạng mục.
- Ẩn hạng mục không dùng.
- Gộp hạng mục nếu bị trùng.

Không nên xóa cứng hạng mục đã có giao dịch. Nếu người dùng muốn xóa, hệ thống nên chuyển sang trạng thái `archived` để giữ đúng lịch sử báo cáo.

## 6. Hạn mức chi tiêu

### 6.1. Mục đích

Hạn mức chi tiêu giúp người dùng kiểm soát ngân sách theo từng tháng và từng hạng mục.

Ví dụ:

- Ăn uống: 3.000.000đ/tháng.
- Di chuyển: 1.000.000đ/tháng.
- Mua sắm: 2.000.000đ/tháng.

### 6.2. Cấu hình hạn mức

Mỗi hạn mức gồm:

- Tháng áp dụng.
- Hạng mục.
- Số tiền giới hạn.
- Ngưỡng cảnh báo trước, ví dụ 70%, 80%, 90%.
- Trạng thái bật/tắt thông báo.

### 6.3. Luồng kiểm tra hạn mức

Khi có giao dịch chi tiêu đã được phân loại:

1. Hệ thống xác định tháng của giao dịch.
2. Hệ thống xác định hạng mục của giao dịch.
3. Tính tổng chi tiêu trong tháng của hạng mục đó.
4. So sánh với hạn mức đã đặt.
5. Nếu đạt ngưỡng cảnh báo, phát event `BudgetWarningTriggered`.
6. Nếu vượt hạn mức, phát event `BudgetExceeded`.
7. Gửi thông báo đến thiết bị nếu người dùng bật thông báo.

### 6.4. Quy tắc cảnh báo

- Chỉ gửi cảnh báo gần vượt hạn mức một lần cho mỗi ngưỡng trong một tháng.
- Khi vượt hạn mức, gửi cảnh báo rõ số tiền đã vượt.
- Nếu người dùng chỉnh hạn mức, hệ thống cần tính lại trạng thái cảnh báo.
- Nếu giao dịch bị sửa hoặc đổi hạng mục, hệ thống cần tính lại hạn mức của hạng mục cũ và hạng mục mới.

## 7. Mục tiêu tài chính

### 7.1. Mục đích

Người dùng có thể đặt mục tiêu tài chính để theo dõi tiến độ tích lũy.

Ví dụ:

- Quỹ khẩn cấp.
- Mua điện thoại.
- Du lịch.
- Trả nợ.
- Tiết kiệm học phí.

### 7.2. Thông tin mục tiêu

Mỗi mục tiêu gồm:

- Tên mục tiêu.
- Số tiền cần đạt.
- Số tiền hiện đã tích lũy.
- Ngày bắt đầu.
- Hạn hoàn thành dự kiến.
- Mức độ ưu tiên.
- Ghi chú.
- Trạng thái.

Trạng thái mục tiêu:

- `active`: đang thực hiện.
- `completed`: đã hoàn thành.
- `paused`: tạm dừng.
- `cancelled`: đã hủy.

### 7.3. Ưu tiên mục tiêu

Người dùng có thể đặt mức độ ưu tiên:

- Cao.
- Trung bình.
- Thấp.

Mục tiêu ưu tiên cao nên được hiển thị nổi bật hơn trên dashboard.

### 7.4. Cập nhật tiến độ

Tiến độ mục tiêu có thể được cập nhật bằng:

- Nhập thủ công số tiền đã tích lũy.
- Gắn một giao dịch tiền vào với mục tiêu.
- Chuyển tiền từ nguồn tiền hiện có vào mục tiêu.

Khi mục tiêu đạt 100%:

1. Hệ thống phát event `GoalCompleted`.
2. Cập nhật trạng thái mục tiêu thành `completed`.
3. Gửi thông báo chúc mừng nếu người dùng bật thông báo.

## 8. Trang tổng quan

### 8.1. Nội dung chính

Trang tổng quan cần hiển thị:

- Tổng tiền hiện có.
- Tổng thu trong tháng.
- Tổng chi trong tháng.
- Số tiền còn lại so với tháng trước.
- Top hạng mục chi tiêu nhiều nhất.
- Giao dịch chưa phân loại.
- Hạn mức sắp vượt hoặc đã vượt.
- Tiến độ mục tiêu tài chính.

### 8.2. So sánh tháng

Ứng dụng cần so sánh tháng hiện tại với tháng trước:

- Tổng chi tăng/giảm bao nhiêu phần trăm.
- Tổng thu tăng/giảm bao nhiêu phần trăm.
- Hạng mục nào tăng mạnh nhất.
- Hạng mục nào giảm nhiều nhất.

Công thức phần trăm thay đổi:

```text
% thay đổi = ((Giá trị tháng này - Giá trị tháng trước) / Giá trị tháng trước) * 100
```

Nếu giá trị tháng trước bằng 0, không chia trực tiếp để tránh lỗi. Khi đó hiển thị theo dạng:

- `Không có dữ liệu tháng trước`.
- Hoặc `Tăng mới trong tháng này`.

### 8.3. Biểu đồ

Các biểu đồ nên có:

- Biểu đồ tròn cho tỷ trọng chi tiêu theo hạng mục.
- Biểu đồ cột cho so sánh chi tiêu giữa các tháng.
- Biểu đồ đường cho xu hướng số dư theo thời gian.
- Thanh tiến độ cho hạn mức và mục tiêu.

## 9. Log cập nhật tổng tiền

### 9.1. Mục đích

Log cập nhật tổng tiền giúp truy vết vì sao tổng tiền thay đổi.

Mỗi thay đổi số dư cần có log riêng, gồm:

- Nguồn tiền bị thay đổi.
- Số dư trước thay đổi.
- Số dư sau thay đổi.
- Số tiền thay đổi.
- Lý do thay đổi.
- Giao dịch liên quan nếu có.
- Người tạo thay đổi.
- Thời gian thay đổi.

### 9.2. Các loại cập nhật

- Tiền vào ngân hàng từ webhook.
- Tiền ra ngân hàng từ webhook.
- Người dùng thêm giao dịch thủ công.
- Người dùng chỉnh số dư tiền mặt.
- Người dùng sửa hoặc xóa mềm giao dịch.
- Đồng bộ lại dữ liệu từ nguồn ngoài.

Không nên sửa trực tiếp số dư mà không tạo log. Mọi thay đổi tài chính cần có lịch sử truy vết.

## 10. Kiến trúc Event-Driven

### 10.1. Nguyên tắc

Ứng dụng sử dụng Event-Driven để tách biệt các phần:

- Nhận dữ liệu giao dịch.
- Lưu giao dịch.
- Cập nhật số dư.
- Kiểm tra hạn mức.
- Cập nhật báo cáo.
- Gửi thông báo.

Mỗi hành động quan trọng phát ra event. Các module khác lắng nghe event và xử lý phần việc của mình.

### 10.2. Event chính

Các event nên có:

- `WebhookReceived`: nhận webhook từ SePay.
- `BankTransactionImported`: đã import giao dịch ngân hàng.
- `MoneyReceived`: ghi nhận tiền vào.
- `ExpenseDetected`: phát hiện tiền ra.
- `ExpenseCategorized`: giao dịch chi tiêu đã được phân loại.
- `ManualExpenseCreated`: người dùng thêm chi tiêu thủ công.
- `ManualIncomeCreated`: người dùng thêm thu nhập thủ công.
- `CashBalanceUpdated`: cập nhật số dư tiền mặt.
- `BalanceChanged`: số dư nguồn tiền thay đổi.
- `BudgetWarningTriggered`: gần vượt hạn mức.
- `BudgetExceeded`: đã vượt hạn mức.
- `GoalProgressUpdated`: cập nhật tiến độ mục tiêu.
- `GoalCompleted`: hoàn thành mục tiêu.
- `NotificationRequested`: yêu cầu gửi thông báo.

### 10.3. Luồng event mẫu cho giao dịch tiền ra

```text
WebhookReceived
→ BankTransactionImported
→ ExpenseDetected
→ BalanceChanged
→ UserCategorizesExpense
→ ExpenseCategorized
→ BudgetChecked
→ BudgetWarningTriggered hoặc BudgetExceeded nếu có
→ NotificationRequested nếu cần
→ DashboardUpdated
```

### 10.4. Yêu cầu idempotency

Webhook và event phải xử lý idempotent, nghĩa là cùng một giao dịch gửi lại nhiều lần không được tạo trùng dữ liệu.

Cần có khóa chống trùng dựa trên:

- Mã giao dịch ngân hàng.
- Mã tham chiếu SePay.
- Tài khoản nhận/gửi.
- Số tiền.
- Thời gian giao dịch.

## 11. Thông báo ứng dụng

### 11.1. Các loại thông báo

Ứng dụng cần hỗ trợ thông báo trên thiết bị cho:

- Giao dịch tiền vào lớn.
- Giao dịch tiền ra lớn.
- Giao dịch cần phân loại.
- Sắp vượt hạn mức.
- Đã vượt hạn mức.
- Mục tiêu tài chính hoàn thành.
- Nhắc cập nhật tiền mặt định kỳ.

### 11.2. Cài đặt thông báo

Người dùng có thể bật/tắt:

- Toàn bộ thông báo.
- Thông báo giao dịch.
- Thông báo hạn mức.
- Thông báo mục tiêu.
- Nhắc cập nhật tiền mặt.

Người dùng cũng có thể đặt:

- Ngưỡng tiền lớn cần thông báo.
- Ngưỡng phần trăm hạn mức cần cảnh báo.
- Lịch nhắc cập nhật tiền mặt.

## 12. Bảo mật và quyền riêng tư

Ứng dụng quản lý dữ liệu tài chính nên ưu tiên bảo mật ngay từ đầu.

### 12.1. Nguyên tắc bảo mật

- Tuân thủ các khuyến nghị bảo mật Android/Google mới nhất.
- Chỉ xin quyền thật sự cần dùng.
- Giải thích rõ lý do xin quyền trước khi yêu cầu quyền nhạy cảm.
- Không lưu token, API key hoặc secret dạng plain text.
- Mã hóa dữ liệu nhạy cảm khi lưu cục bộ.
- Mã hóa dữ liệu khi truyền qua mạng bằng HTTPS/TLS.
- Có cơ chế khóa ứng dụng bằng sinh trắc học hoặc mã PIN.
- Tự động khóa ứng dụng sau một thời gian không hoạt động.
- Không ghi dữ liệu nhạy cảm vào log debug.

### 12.2. Webhook SePay

Webhook cần được bảo vệ bằng:

- Chữ ký xác thực webhook nếu SePay hỗ trợ.
- Secret riêng cho webhook endpoint.
- Kiểm tra timestamp để chống replay attack.
- Kiểm tra idempotency để tránh ghi trùng.
- Rate limit endpoint webhook.
- Lưu raw payload có kiểm soát để phục vụ đối soát, không lưu thừa dữ liệu nhạy cảm.

### 12.3. Quyền đọc thông báo/email

Nếu triển khai đọc thông báo ngân hàng hoặc email:

- Đây là quyền rất nhạy cảm, không nên bật mặc định.
- Người dùng phải chủ động bật.
- Cần mô tả rõ dữ liệu nào được đọc và dùng để làm gì.
- Chỉ trích xuất nội dung liên quan giao dịch.
- Không tải toàn bộ nội dung thông báo/email lên server nếu không cần.
- Cho phép người dùng thu hồi quyền và xóa dữ liệu đã trích xuất.

## 13. Dữ liệu cốt lõi

Các entity chính:

### 13.1. User

- `id`
- `name`
- `email`
- `notification_settings`
- `security_settings`
- `created_at`
- `updated_at`

### 13.2. Account

Đại diện cho nguồn tiền.

- `id`
- `user_id`
- `type`: bank, cash, wallet, saving, investment
- `name`
- `balance`
- `currency`
- `provider`
- `external_account_id`
- `is_active`
- `created_at`
- `updated_at`

### 13.3. Transaction

- `id`
- `user_id`
- `account_id`
- `type`: income, expense, transfer, adjustment
- `amount`
- `currency`
- `category_id`
- `status`
- `description`
- `external_transaction_id`
- `occurred_at`
- `created_at`
- `updated_at`

### 13.4. Category

- `id`
- `user_id`
- `name`
- `icon`
- `color`
- `type`: income, expense, both
- `is_default`
- `is_archived`
- `created_at`
- `updated_at`

### 13.5. Budget

- `id`
- `user_id`
- `category_id`
- `month`
- `limit_amount`
- `warning_threshold_percent`
- `notification_enabled`
- `created_at`
- `updated_at`

### 13.6. Goal

- `id`
- `user_id`
- `name`
- `target_amount`
- `current_amount`
- `priority`
- `status`
- `start_date`
- `target_date`
- `created_at`
- `updated_at`

### 13.7. BalanceLog

- `id`
- `user_id`
- `account_id`
- `transaction_id`
- `before_balance`
- `after_balance`
- `changed_amount`
- `reason`
- `created_at`

### 13.8. EventLog

- `id`
- `event_type`
- `aggregate_id`
- `payload`
- `status`
- `retry_count`
- `created_at`
- `processed_at`

## 14. Quy tắc xử lý quan trọng

- Không xóa cứng giao dịch tài chính; chỉ nên dùng xóa mềm hoặc trạng thái hủy.
- Mọi thay đổi số dư phải tạo `BalanceLog`.
- Giao dịch từ webhook phải chống trùng.
- Giao dịch chưa phân loại vẫn ảnh hưởng đến số dư, nhưng chưa tính vào báo cáo hạng mục cụ thể.
- Khi sửa số tiền, ngày, nguồn tiền hoặc hạng mục của giao dịch, hệ thống phải tính lại số dư, hạn mức và báo cáo liên quan.
- Khi đổi hạng mục giao dịch, phải cập nhật lại hạn mức của cả hạng mục cũ và hạng mục mới.
- Khi người dùng điều chỉnh số dư thủ công, phải ghi rõ lý do điều chỉnh.
- Khi tháng mới bắt đầu, hạn mức tháng trước được giữ nguyên để báo cáo lịch sử.
- Không tự động dùng dữ liệu thông báo/email khi người dùng chưa cấp quyền rõ ràng.

## 15. Các màn hình đề xuất

### 15.1. Dashboard

- Tổng tiền hiện có.
- Tổng thu/chi trong tháng.
- Biểu đồ chi tiêu theo hạng mục.
- Cảnh báo hạn mức.
- Mục tiêu ưu tiên.
- Giao dịch chờ phân loại.

### 15.2. Giao dịch

- Danh sách giao dịch.
- Bộ lọc theo thời gian, hạng mục, nguồn tiền, trạng thái.
- Nút thêm log thủ công.
- Chỉnh sửa giao dịch.
- Gắn hạng mục cho giao dịch chưa phân loại.

### 15.3. Hạng mục

- Danh sách hạng mục.
- Thêm/sửa/ẩn hạng mục.
- Xem chi tiêu theo từng hạng mục.

### 15.4. Hạn mức

- Danh sách hạn mức theo tháng.
- Thiết lập hạn mức theo hạng mục.
- Cấu hình ngưỡng cảnh báo.
- Xem phần trăm đã sử dụng.

### 15.5. Mục tiêu

- Danh sách mục tiêu.
- Tạo mục tiêu mới.
- Cập nhật tiến độ.
- Đặt mức độ ưu tiên.
- Xem mục tiêu đã hoàn thành.

### 15.6. Cài đặt

- Quản lý tài khoản/ngân hàng.
- Cấu hình SePay/webhook.
- Cài đặt thông báo.
- Cài đặt bảo mật.
- Sao lưu/khôi phục dữ liệu.
- Xuất dữ liệu.

## 16. MVP đề xuất

Giai đoạn đầu nên tập trung vào các chức năng cốt lõi:

1. Quản lý nguồn tiền ngân hàng và tiền mặt.
2. Nhận webhook SePay cho biến động số dư.
3. Tạo log giao dịch tự động và thủ công.
4. Phân loại giao dịch chi tiêu.
5. Đặt hạn mức theo hạng mục.
6. Cảnh báo sắp vượt/vượt hạn mức.
7. Dashboard tổng quan tháng hiện tại.
8. Mục tiêu tài chính cơ bản.

Các chức năng nên để giai đoạn sau:

- Đọc thông báo ngân hàng.
- Đọc email biến động số dư.
- Import sao kê.
- AI tự phân loại giao dịch.
- Đồng bộ nhiều thiết bị.
- Chia sẻ ngân sách gia đình.

## 17. Yêu cầu kỹ thuật khi triển khai

- Thiết kế theo module/domain rõ ràng: account, transaction, category, budget, goal, notification, reporting.
- Không viết logic tài chính trực tiếp trong UI.
- Không viết hàm mới để đè lên hàm lỗi; phải sửa đúng hàm đang gây lỗi.
- Tách adapter nguồn dữ liệu ngoài khỏi domain chính.
- Event handler phải có retry và chống xử lý trùng.
- Các phép tính tiền nên dùng kiểu dữ liệu chính xác, không dùng floating-point cho tiền tệ.
- Các thao tác cập nhật số dư cần chạy trong transaction để tránh lệch dữ liệu.
- Có test cho các rule quan trọng: chống trùng webhook, tính hạn mức, cập nhật số dư, sửa giao dịch.

## 18. Câu hỏi cần quyết định thêm

Các điểm cần chốt trước khi triển khai chi tiết:

1. Dữ liệu sẽ lưu local-only, cloud-only hay đồng bộ cả hai?
2. Backend webhook SePay sẽ đặt ở đâu?
3. Một người dùng có thể liên kết nhiều tài khoản ngân hàng không?
4. Có cần hỗ trợ nhiều loại tiền tệ không?
5. Có cần đăng nhập tài khoản hay app chỉ dùng offline cá nhân?
6. Tiền trong mục tiêu có bị trừ khỏi tổng tiền khả dụng hay chỉ là số theo dõi?
7. Có cần tính chuyển khoản nội bộ giữa tiền mặt và ngân hàng không?
8. Có cần xuất báo cáo theo tháng/quý/năm không?


