# TASKS - Dự án ứng dụng quản lý chi tiêu

Tài liệu này là backlog triển khai theo kiến trúc **local-first** đã chốt trong `ARCHITECTURE.md`.

## Quy ước task

- `P0`: bắt buộc cho MVP local-first.
- `P1`: quan trọng nhưng có thể làm sau MVP đầu tiên.
- `P2`: mở rộng/nâng cao.
- `FE`: Android UI.
- `CORE`: domain logic/local database.
- `SYNC`: đồng bộ dữ liệu ngoài.
- `SEC`: bảo mật/quyền riêng tư.
- `QA`: kiểm thử/chất lượng.
- `OPS`: build/release/tài liệu.

## Quyết định kiến trúc áp dụng cho mọi task

- Database chính nằm trên thiết bị Android của người dùng.
- MVP không bắt buộc backend/server của chủ app.
- App phải dùng được offline với dữ liệu đã có.
- SePay pull sync là hướng đồng bộ chính khi người dùng muốn lấy dữ liệu ngân hàng.
- Webhook URL là chế độ nâng cao: người dùng tự nhập public URL nếu họ có hạ tầng riêng.
- Không hardcode token SePay của chủ app vào APK.
- Mọi nguồn dữ liệu ngoài phải đi qua pipeline normalize → deduplicate → validate → save → update balance.

## Milestone tổng quan

1. `M0 - Chốt nền tảng`: chốt Android stack, rule tiền tệ, local-first scope.
2. `M1 - Android foundation`: tạo app, kiến trúc module, local database, bảo mật cơ bản.
3. `M2 - Domain tài chính`: account, category, transaction, balance log, local events.
4. `M3 - Giao dịch thủ công`: thêm/sửa/lọc/phân loại giao dịch offline.
5. `M4 - SePay pull sync`: token, sync state, fetch API, chống trùng, import thiếu.
6. `M5 - Hạn mức và thông báo`: budget, cảnh báo local, notification channels.
7. `M6 - Dashboard và báo cáo`: tổng quan, so sánh tháng, biểu đồ.
8. `M7 - Mục tiêu tài chính`: goal, ưu tiên, tiến độ.
9. `M9 - Cài đặt và bảo mật`: PIN/biometric, backup/export, privacy.
10. `M11 - Kiểm thử và release MVP`: test domain, sync, build internal.
11. `M10 - Webhook URL optional`: cấu hình URL public do user tự cung cấp.
12. `M11 - Tính năng mở rộng`: import file, notification reader, email, AI.

---

## M0 - Chốt nền tảng

### T-0001 - Chốt scope MVP local-first `[P0][CORE]`

**Mục tiêu:** Khóa phạm vi MVP không phụ thuộc server.

**Checklist:**

- [x] Xác nhận database chính là local database trên Android.
- [x] Xác nhận không cần backend/server trong MVP.
- [x] Xác nhận 3 mode dữ liệu: manual, pull sync, webhook URL optional.
- [x] Xác nhận webhook không chặn MVP.
- [x] Ghi quyết định vào `ARCHITECTURE.md` nếu có thay đổi.

**Acceptance criteria:**

- [x] Team có thể bắt đầu code Android mà không cần chờ backend.
- [x] Task P0 không phụ thuộc server public.

### T-0002 - Chốt Android stack `[P0][FE][OPS]`

**Mục tiêu:** Chọn stack Android ổn định cho MVP.

**Đề xuất:** Kotlin, Jetpack Compose, Material 3, Room, Coroutines/Flow, WorkManager, DataStore, Android Keystore.

**Checklist:**

- [x] Chốt ngôn ngữ Kotlin.
- [x] Chốt UI bằng Jetpack Compose.
- [x] Chốt local database Room.
- [x] Chốt background sync WorkManager.
- [x] Chốt settings bằng DataStore.
- [x] Chốt dependency injection nếu dùng.

**Acceptance criteria:**

- [x] Có stack rõ trong README hoặc docs.
- [x] Không có lựa chọn công nghệ mâu thuẫn nhau.

### T-0003 - Chốt Android SDK targets `[P0][OPS]`

**Mục tiêu:** Chọn thông số build Android.

**Checklist:**

- [x] Chốt `minSdk`.
- [x] Chốt `targetSdk`.
- [x] Chốt `compileSdk`.
- [x] Chốt application id/package name.
- [x] Chốt versioning nội bộ.

**Acceptance criteria:**

- [x] Project build được theo SDK đã chốt.
- [x] Cấu hình phù hợp để test trên thiết bị phổ biến.

### T-0004 - Chốt rule tiền tệ `[P0][CORE]`

**Mục tiêu:** Tránh sai số khi tính tiền.

**Checklist:**

- [x] Lưu tiền VND bằng integer/Long theo đơn vị đồng.
- [x] Không dùng Float/Double cho tiền.
- [x] Chốt MVP hỗ trợ VND trước.
- [x] Chốt format hiển thị tiền.
- [x] Chốt timezone xử lý ngày giao dịch.

**Acceptance criteria:**

- [x] Tất cả entity dùng kiểu tiền nhất quán.
- [x] Báo cáo tháng không sai vì timezone.

---

## M1 - Android foundation

### T-0101 - Khởi tạo Android project `[P0][FE][OPS]`

**Mục tiêu:** Dựng app Android chạy được.

**Checklist:**

- [x] Tạo Android project.
- [x] Cấu hình Gradle/Kotlin.
- [x] Cấu hình Compose/Material 3.
- [x] Tạo app theme cơ bản.
- [x] Tạo màn hình placeholder đầu tiên.

**Acceptance criteria:**

- [x] App build thành công.
- [ ] App mở được trên emulator/device.

### T-0102 - Thiết lập kiến trúc module/package `[P0][FE][CORE]`

**Mục tiêu:** Tách UI, domain, data rõ ràng.

**Checklist:**

- [x] Tạo package `core`.
- [x] Tạo package `account`.
- [x] Tạo package `transaction`.
- [x] Tạo package `category`.
- [x] Tạo package `budget`.
- [x] Tạo package `goal`.
- [x] Tạo package `sync`.
- [x] Tạo package `notification`.
- [x] Tạo package `reporting`.
- [x] Tạo package `settings`.

**Acceptance criteria:**

- [x] UI không chứa logic tài chính chính.
- [x] Domain logic có thể test độc lập.

### T-0103 - Thiết lập Room database `[P0][CORE]`

**Mục tiêu:** Có local database làm nguồn dữ liệu chính.

**Checklist:**

- [x] Tạo Room database class.
- [x] Tạo migration strategy.
- [x] Tạo DAO base pattern.
- [x] Cấu hình database transaction.
- [x] Chuẩn bị seed data local.

**Acceptance criteria:**

- [x] App tạo database local khi chạy lần đầu.
- [x] Có thể migration version database an toàn.

### T-0104 - Thiết lập DataStore settings `[P0][CORE]`

**Mục tiêu:** Lưu cấu hình app nhẹ, không phải dữ liệu tài chính chính.

**Checklist:**

- [x] Tạo settings repository.
- [ ] Lưu tùy chọn theme nếu có.
- [x] Lưu tùy chọn sync.
- [x] Lưu tùy chọn notification.
- [x] Không lưu token nhạy cảm trực tiếp bằng plain DataStore.

**Acceptance criteria:**

- [ ] Settings đọc/ghi được qua Flow.
- [x] Token/secret không lưu plain text trong DataStore.

### T-0105 - Thiết lập bảo mật secret local `[P0][SEC]`

**Mục tiêu:** Chuẩn bị nơi lưu token SePay an toàn.

**Checklist:**

- [x] Tạo wrapper dùng Android Keystore.
- [x] Lưu secret/token ở storage mã hóa.
- [x] Tạo hàm xóa token.
- [x] Không log token.
- [x] Có test/kiểm tra manual cho lưu/xóa token.

**Acceptance criteria:**

- [x] Token SePay không xuất hiện plain text trong log.
- [x] User có thể xóa token khỏi thiết bị.

### T-0106 - Thiết lập navigation `[P0][FE]`

**Mục tiêu:** Có khung điều hướng app.

**Checklist:**

- [x] Tạo navigation graph.
- [x] Tạo tab hoặc bottom navigation.
- [x] Thêm route Dashboard.
- [x] Thêm route Transactions.
- [x] Thêm route Budgets.
- [x] Thêm route Goals.
- [x] Thêm route Settings.

**Acceptance criteria:**

- [x] User chuyển được giữa các màn chính.

### T-0107 - Thiết lập code quality `[P0][QA][OPS]`

**Mục tiêu:** Có command build/test/lint ổn định.

**Checklist:**

- [ ] Cấu hình formatter/lint phù hợp.
- [x] Tạo unit test sample.
- [ ] Tạo README lệnh build/test.
- [x] Cấu hình gitignore.

**Acceptance criteria:**

- [x] Có thể chạy build và test bằng command rõ ràng.

---

## M2 - Domain tài chính cốt lõi

### T-0201 - Tạo entity Account `[P0][CORE]`

**Mục tiêu:** Quản lý nguồn tiền local.

**Checklist:**

- [x] Entity `Account`.
- [x] Field `type`: bank, cash, wallet, saving, investment.
- [x] Field `balance` dạng Long.
- [x] Field `currency`.
- [x] Field `provider`.
- [x] Field `externalAccountId` cho SePay/ngân hàng.
- [x] Field `isActive`.
- [x] DAO create/update/list.

**Acceptance criteria:**

- [x] Tạo được tài khoản tiền mặt.
- [x] Tạo được tài khoản ngân hàng.
- [x] Tính tổng số dư local chính xác.

### T-0202 - Tạo entity Category `[P0][CORE]`

**Mục tiêu:** Quản lý hạng mục thu/chi.

**Checklist:**

- [x] Entity `Category`.
- [x] Field name/icon/color/type.
- [x] Field `isDefault`.
- [x] Field `isArchived`.
- [x] Seed category mặc định.
- [x] DAO create/update/archive/list.

**Acceptance criteria:**

- [x] Lần đầu mở app có hạng mục mặc định.
- [x] Không xóa cứng category đã dùng.

### T-0203 - Tạo entity Transaction `[P0][CORE]`

**Mục tiêu:** Lưu giao dịch tài chính local.

**Checklist:**

- [x] Entity `Transaction`.
- [x] Type: income, expense, transfer, adjustment.
- [x] Status: pending_category, categorized, ignored, duplicated, adjusted.
- [x] Field source: manual, sepay_api, webhook_endpoint, import_file.
- [x] Field external id/reference.
- [x] Field occurredAt.
- [ ] DAO create/update/list/filter.

**Acceptance criteria:**

- [x] Tạo được giao dịch thu/chi local.
- [x] Giao dịch từ SePay có thể pending category.

### T-0204 - Tạo entity BalanceLog `[P0][CORE]`

**Mục tiêu:** Truy vết mọi thay đổi số dư.

**Checklist:**

- [x] Entity `BalanceLog`.
- [x] Lưu account id.
- [x] Lưu transaction id nếu có.
- [x] Lưu before/after balance.
- [x] Lưu changed amount.
- [x] Lưu reason/source.
- [x] DAO list by account/time.

**Acceptance criteria:**

- [x] Mỗi thay đổi số dư có BalanceLog.
- [x] Truy được vì sao số dư đổi.

### T-0205 - Tạo entity LocalEvent `[P0][CORE]`

**Mục tiêu:** Hỗ trợ event-driven nội bộ không cần server.

**Checklist:**

- [x] Entity `LocalEvent` hoặc in-memory event bus + log tùy chọn.
- [x] Event type.
- [x] Aggregate id.
- [x] Payload.
- [x] Status/retry nếu cần.
- [x] Created/processed time.

**Acceptance criteria:**

- [x] Domain có thể phát event sau nghiệp vụ chính.
- [x] Budget/report/notification không bị viết cứng vào UI.

### T-0206 - Implement BalanceService `[P0][CORE]`

**Mục tiêu:** Tập trung logic cập nhật số dư.

**Checklist:**

- [x] Cộng tiền vào account.
- [x] Trừ tiền khỏi account.
- [x] Điều chỉnh số dư thủ công.
- [x] Tạo BalanceLog tự động.
- [x] Chạy trong Room transaction.
- [x] Không cho sửa balance trực tiếp từ UI.

**Acceptance criteria:**

- [x] Balance và BalanceLog luôn khớp.
- [x] Test pass cho income/expense/adjustment.

### T-0207 - Implement TransactionImportPipeline `[P0][CORE][SYNC]`

**Mục tiêu:** Mọi nguồn dữ liệu ngoài dùng chung pipeline import.

**Checklist:**

- [x] Normalize transaction input.
- [x] Deduplicate theo external id/reference/amount/date/account/source.
- [x] Validate amount/date/account.
- [x] Save transaction.
- [x] Update balance qua BalanceService.
- [x] Emit local event.

**Acceptance criteria:**

- [x] Import cùng giao dịch 2 lần không đổi balance lần hai.
- [x] Manual/SePay/import file có thể dùng chung logic nền.

---

## M3 - Giao dịch thủ công

### T-0301 - Màn hình danh sách giao dịch `[P0][FE]`

**Mục tiêu:** Hiển thị lịch sử thu/chi local.

**Checklist:**

- [ ] Danh sách giao dịch mới nhất trước.
- [ ] Hiển thị số tiền, ngày, nguồn tiền, hạng mục.
- [ ] Phân biệt thu/chi.
- [ ] Hiển thị trạng thái chưa phân loại.
- [ ] Loading/empty/error state.

**Acceptance criteria:**

- [ ] User xem được lịch sử offline.
- [ ] Giao dịch pending dễ nhận biết.

### T-0302 - Form thêm giao dịch thủ công `[P0][FE][CORE]`

**Mục tiêu:** User nhập thu/chi không cần kết nối ngân hàng.

**Checklist:**

- [ ] Chọn loại giao dịch.
- [x] Chọn account.
- [ ] Nhập số tiền.
- [x] Chọn category.
- [ ] Chọn thời gian.
- [ ] Nhập ghi chú.
- [ ] Validate số tiền > 0.

**Acceptance criteria:**

- [ ] Expense trừ đúng balance.
- [ ] Income cộng đúng balance.
- [ ] Có BalanceLog.

### T-0303 - Form cập nhật tiền mặt `[P0][FE][CORE]`

**Mục tiêu:** User điều chỉnh số dư tiền mặt định kỳ.

**Checklist:**

- [x] Chọn account cash.
- [x] Nhập số dư thực tế.
- [ ] Hiển thị chênh lệch.
- [ ] Bắt buộc ghi lý do nếu chênh lệch.
- [ ] Tạo transaction adjustment.
- [x] Tạo BalanceLog.

**Acceptance criteria:**

- [ ] Số dư tiền mặt cập nhật đúng.
- [ ] Lịch sử điều chỉnh rõ ràng.

### T-0304 - Phân loại giao dịch pending `[P0][FE][CORE]`

**Mục tiêu:** Gắn category cho giao dịch chưa phân loại.

**Checklist:**

- [ ] Danh sách pending transactions.
- [x] Chọn category.
- [ ] Lưu status categorized.
- [ ] Emit event ExpenseCategorized.
- [ ] Cập nhật báo cáo/hạn mức liên quan.

**Acceptance criteria:**

- [ ] Giao dịch pending chuyển thành categorized.
- [ ] Budget checker chạy sau phân loại.

### T-0305 - Bộ lọc giao dịch `[P1][FE]`

**Mục tiêu:** Lọc dữ liệu giao dịch local.

**Checklist:**

- [ ] Filter theo thời gian.
- [ ] Filter theo account.
- [ ] Filter theo category.
- [ ] Filter theo type.
- [ ] Filter theo status.

**Acceptance criteria:**

- [ ] User lọc được giao dịch tháng hiện tại.
- [ ] Có reset filter.

### T-0306 - Sửa giao dịch `[P1][FE][CORE]`

**Mục tiêu:** Sửa giao dịch mà không lệch số dư.

**Checklist:**

- [ ] Sửa amount/date/category/note.
- [ ] Nếu amount/account đổi, tính lại balance bằng service.
- [ ] Nếu category/date đổi, tính lại budget/report.
- [ ] Tạo log điều chỉnh.

**Acceptance criteria:**

- [ ] Sửa giao dịch không làm sai số dư.
- [ ] Có audit trail đủ dùng.

### T-0307 - Đánh dấu bỏ qua giao dịch `[P1][FE][CORE]`

**Mục tiêu:** Loại giao dịch khỏi báo cáo mà không xóa cứng.

**Checklist:**

- [ ] Action ignore.
- [ ] Status ignored.
- [ ] Không xóa transaction.
- [ ] Không tính vào report/budget.

**Acceptance criteria:**

- [ ] Giao dịch ignored vẫn xem lại được.
- [ ] Báo cáo không tính ignored.

---

## M4 - SePay pull sync

### T-0401 - Màn hình cấu hình SePay token `[P0][FE][SEC][SYNC]`

**Mục tiêu:** User tự nhập token SePay của họ.

**Checklist:**

- [ ] Form nhập token.
- [ ] Giải thích token lưu trên thiết bị.
- [ ] Nút kiểm tra kết nối.
- [ ] Nút xóa token.
- [ ] Lưu token bằng secure storage.

**Acceptance criteria:**

- [x] Không hardcode token trong app.
- [x] User có thể xóa token bất kỳ lúc nào.

### T-0402 - Tạo entity SyncState `[P0][CORE][SYNC]`

**Mục tiêu:** Theo dõi lần sync cuối và lỗi sync.

**Checklist:**

- [x] Entity `SyncState`.
- [x] Source: sepay_api, webhook_endpoint, import_file.
- [x] Account id.
- [x] Last synced at.
- [x] Last cursor/since id.
- [x] Last transaction date.
- [x] Status và last error.

**Acceptance criteria:**

- [x] App biết lần sync cuối.
- [x] Sync lỗi có thông tin để hiển thị cho user.

### T-0403 - SePay API client `[P0][SYNC]`

**Mục tiêu:** Gọi API danh sách giao dịch SePay từ Android.

**Checklist:**

- [x] Tạo HTTP client.
- [x] Gắn token từ secure storage.
- [x] Gọi endpoint danh sách giao dịch.
- [x] Hỗ trợ filter ngày/account nếu API cho phép.
- [x] Hỗ trợ pagination/cursor/since id nếu API cho phép.
- [x] Parse response an toàn.

**Acceptance criteria:**

- [x] App lấy được danh sách giao dịch từ SePay bằng token user.
- [x] Lỗi API hiển thị thân thiện.

### T-0404 - Manual sync button `[P0][FE][SYNC]`

**Mục tiêu:** User bấm đồng bộ để kéo giao dịch thiếu.

**Checklist:**

- [x] Nút Đồng bộ trên Dashboard/Settings.
- [x] Hiển thị loading.
- [x] Gọi SePaySyncUseCase.
- [x] Hiển thị số giao dịch mới import.
- [x] Hiển thị lỗi nếu token/mạng lỗi.

**Acceptance criteria:**

- [x] Bấm sync import được giao dịch mới.
- [x] Bấm lại không tạo trùng.

### T-0405 - Auto sync khi mở app `[P0][SYNC][FE]`

**Mục tiêu:** App tự sync khi mở nếu điều kiện phù hợp.

**Checklist:**

- [ ] Kiểm tra có token SePay.
- [ ] Kiểm tra có mạng.
- [ ] Kiểm tra đã quá khoảng sync tối thiểu.
- [ ] Chạy sync không block UI.
- [ ] Lưu trạng thái sync.

**Acceptance criteria:**

- [ ] Mở app có mạng sẽ tự kéo dữ liệu thiếu theo rule.
- [ ] Không spam API khi mở app liên tục.

### T-0406 - Import giao dịch SePay tiền vào `[P0][SYNC][CORE]`

**Mục tiêu:** Tiền vào từ SePay được lưu và cộng balance.

**Checklist:**

- [ ] Normalize payload SePay thành income.
- [ ] Map vào account bank local.
- [ ] Deduplicate.
- [ ] Tạo transaction source sepay_api.
- [ ] Cộng balance.
- [x] Tạo BalanceLog.

**Acceptance criteria:**

- [ ] Tiền vào làm tăng số dư đúng.
- [ ] Sync lại không cộng lần hai.

### T-0407 - Import giao dịch SePay tiền ra `[P0][SYNC][CORE]`

**Mục tiêu:** Tiền ra từ SePay được lưu, trừ balance và chờ phân loại.

**Checklist:**

- [ ] Normalize payload SePay thành expense.
- [ ] Status pending_category nếu chưa đoán được category.
- [ ] Deduplicate.
- [ ] Trừ balance.
- [x] Tạo BalanceLog.
- [ ] Emit ExpenseDetected.

**Acceptance criteria:**

- [ ] Tiền ra làm giảm số dư đúng.
- [ ] Giao dịch xuất hiện trong pending category.

### T-0408 - Background sync bằng WorkManager `[P1][SYNC]`

**Mục tiêu:** Sync nền khi Android cho phép.

**Checklist:**

- [ ] Worker sync SePay.
- [ ] Chỉ chạy khi có network.
- [ ] Tùy chọn chỉ Wi-Fi.
- [ ] Khoảng sync 6h/12h/24h.
- [ ] Backoff khi lỗi.

**Acceptance criteria:**

- [ ] Sync nền hoạt động trên thiết bị test.
- [ ] App không hứa realtime tuyệt đối.

---

## M5 - Hạng mục và hạn mức

### T-0501 - Màn hình quản lý hạng mục `[P0][FE][CORE]`

**Mục tiêu:** User quản lý category local.

**Checklist:**

- [ ] List category.
- [ ] Create category.
- [ ] Update name/icon/color.
- [ ] Archive category.
- [ ] Ẩn archived khỏi lựa chọn mặc định.

**Acceptance criteria:**

- [ ] User tạo/sửa/ẩn hạng mục được.

### T-0502 - Tạo entity Budget `[P0][CORE]`

**Mục tiêu:** Lưu hạn mức theo tháng/category local.

**Checklist:**

- [x] Entity Budget.
- [ ] Unique category/month.
- [x] Limit amount.
- [ ] Warning threshold percent.
- [ ] Notification enabled.
- [ ] DAO CRUD.

**Acceptance criteria:**

- [ ] Mỗi category có một budget mỗi tháng.
- [ ] Budget tháng cũ giữ lịch sử.

### T-0503 - Màn hình hạn mức `[P0][FE]`

**Mục tiêu:** User đặt và xem hạn mức.

**Checklist:**

- [ ] List budget theo tháng.
- [ ] Tạo/sửa budget.
- [x] Chọn category.
- [ ] Nhập limit.
- [ ] Chọn threshold.
- [ ] Hiển thị percent used.

**Acceptance criteria:**

- [ ] User đặt được hạn mức.
- [ ] UI hiển thị gần vượt/vượt rõ ràng.

### T-0504 - BudgetChecker service `[P0][CORE]`

**Mục tiêu:** Kiểm tra hạn mức sau giao dịch chi tiêu.

**Checklist:**

- [x] Tính tổng chi category/month.
- [x] Bỏ qua transaction ignored.
- [x] So sánh threshold.
- [x] Emit BudgetWarningTriggered.
- [x] Emit BudgetExceeded.
- [x] Chống spam cùng ngưỡng.

**Acceptance criteria:**

- [x] Gần vượt/vượt hạn mức tạo event đúng.
- [x] Cùng ngưỡng không thông báo lặp liên tục.

---

## M6 - Thông báo local

### T-0601 - Notification settings `[P0][CORE][FE]`

**Mục tiêu:** User kiểm soát thông báo.

**Checklist:**

- [ ] Bật/tắt toàn bộ notification.
- [ ] Bật/tắt budget notification.
- [ ] Bật/tắt goal notification.
- [ ] Bật/tắt nhắc cập nhật tiền mặt.
- [ ] Lưu bằng DataStore.

**Acceptance criteria:**

- [ ] Notification service tôn trọng settings.

### T-0602 - Android notification channels `[P0][FE]`

**Mục tiêu:** App hiển thị notification đúng chuẩn Android.

**Checklist:**

- [ ] Xin quyền notification nếu Android yêu cầu.
- [ ] Tạo channel budget.
- [ ] Tạo channel goal.
- [ ] Tạo channel reminder.
- [ ] Tap notification mở đúng màn.

**Acceptance criteria:**

- [ ] Notification hiển thị trên thiết bị test.

### T-0603 - Local NotificationService `[P0][CORE][FE]`

**Mục tiêu:** Gửi thông báo từ local event.

**Checklist:**

- [ ] Handle BudgetWarningTriggered.
- [ ] Handle BudgetExceeded.
- [ ] Handle GoalCompleted.
- [ ] Tạo nội dung thông báo rõ ràng.
- [ ] Không thông báo nếu user tắt.

**Acceptance criteria:**

- [ ] Budget warning/exceeded tạo notification local.

### T-0604 - Nhắc cập nhật tiền mặt `[P1][FE]`

**Mục tiêu:** Nhắc user kiểm tra tiền mặt định kỳ.

**Checklist:**

- [ ] Cho chọn lịch nhắc.
- [ ] Schedule bằng WorkManager/Alarm phù hợp.
- [ ] Tap mở màn cập nhật tiền mặt.

**Acceptance criteria:**

- [ ] User nhận nhắc theo lịch cài đặt.

---

## M7 - Dashboard và báo cáo

### T-0701 - ReportingRepository local `[P0][CORE]`

**Mục tiêu:** Tính số liệu dashboard từ Room.

**Checklist:**

- [ ] Tổng tiền hiện có.
- [ ] Tổng thu tháng.
- [ ] Tổng chi tháng.
- [ ] Chi theo category.
- [ ] Pending transactions count/list.
- [x] Budget status.
- [ ] Goal ưu tiên.

**Acceptance criteria:**

- [ ] Dashboard lấy dữ liệu từ local database.
- [ ] Ignored transaction không tính vào report.

### T-0702 - So sánh tháng `[P0][CORE]`

**Mục tiêu:** So sánh tháng hiện tại với tháng trước.

**Checklist:**

- [ ] Tính tổng thu/chi tháng trước.
- [ ] Tính tổng thu/chi tháng này.
- [ ] Xử lý tháng trước bằng 0.
- [ ] Category tăng mạnh nhất.
- [ ] Category giảm nhiều nhất.

**Acceptance criteria:**

- [ ] Không lỗi chia cho 0.
- [ ] Có label rõ khi thiếu dữ liệu tháng trước.

### T-0703 - Màn hình Dashboard `[P0][FE]`

**Mục tiêu:** Hiển thị tổng quan tài chính.

**Checklist:**

- [x] Card tổng tiền.
- [x] Card thu/chi tháng.
- [x] Giao dịch chờ phân loại.
- [ ] Hạn mức cảnh báo.
- [x] Mục tiêu ưu tiên.
- [x] Nút Đồng bộ SePay.
- [x] Hiển thị trạng thái sync gần nhất.

**Acceptance criteria:**

- [x] User hiểu tình hình tài chính trong một màn.
- [x] Dashboard dùng được offline.

### T-0704 - Biểu đồ báo cáo `[P1][FE]`

**Mục tiêu:** Trực quan hóa dữ liệu.

**Checklist:**

- [x] Pie/progress chart category.
- [x] Bar chart tháng.
- [x] Balance trend chart.
- [x] Progress budget/goal.

**Acceptance criteria:**

- [x] Biểu đồ rõ trên màn hình nhỏ.

---

## M8 - Mục tiêu tài chính

### T-0801 - Tạo entity Goal `[P0][CORE]`

**Mục tiêu:** Lưu mục tiêu tài chính local.

**Checklist:**

- [x] Entity Goal.
- [x] Target amount.
- [x] Current amount.
- [x] Priority.
- [x] Status.
- [x] Start/target date.
- [x] DAO CRUD.

**Acceptance criteria:**

- [x] User tạo được active goal.
- [x] Goal có trạng thái completed/paused/cancelled.

### T-0802 - GoalService `[P0][CORE]`

**Mục tiêu:** Quản lý tiến độ và hoàn thành goal.

**Checklist:**

- [x] Update progress.
- [x] Validate amount.
- [x] Tự completed khi đạt 100%.
- [x] Emit GoalCompleted.
- [x] Không làm lệch account balance nếu chỉ là tracking.

**Acceptance criteria:**

- [x] Goal đạt target chuyển completed.
- [x] Notification có thể được kích hoạt.

### T-0803 - Màn hình mục tiêu `[P0][FE]`

**Mục tiêu:** User quản lý mục tiêu.

**Checklist:**

- [x] List goals.
- [x] Create goal.
- [x] Update progress.
- [x] Set priority.
- [x] View completed goals.

**Acceptance criteria:**

- [x] User thấy progress bằng phần trăm/progress bar.

### T-0804 - Gắn income với goal `[P1][CORE][FE]`

**Mục tiêu:** Dùng giao dịch thu nhập để cập nhật goal.

**Checklist:**

- [x] Link transaction với goal.
- [x] Chống link trùng.
- [x] Cộng progress.
- [x] Unlink trả progress đúng.

**Acceptance criteria:**

- [x] Goal progress thay đổi đúng khi link/unlink.

---

## M9 - Cài đặt và bảo mật

### T-0901 - Màn hình Settings `[P0][FE]`

**Mục tiêu:** Quản lý cấu hình app.

**Checklist:**

- [x] Account settings.
- [x] SePay token settings.
- [x] Sync settings.
- [x] Notification settings.
- [x] Security settings.
- [x] Backup/export entry.
- [x] Webhook URL optional entry.

**Acceptance criteria:**

- [x] User tìm được các cài đặt chính.

### T-0902 - Khóa app PIN/biometric `[P1][SEC][FE]`

**Mục tiêu:** Bảo vệ dữ liệu tài chính trên thiết bị.

**Checklist:**

- [ ] Bật/tắt khóa app.
- [ ] Biometric prompt.
- [ ] PIN fallback.
- [ ] Auto-lock sau idle.

**Acceptance criteria:**

- [ ] App yêu cầu xác thực khi mở lại theo rule.

### T-0903 - Mã hóa database local `[P1][SEC][CORE]`

**Mục tiêu:** Tăng bảo vệ dữ liệu tài chính lưu trên máy.

**Checklist:**

- [ ] Chọn giải pháp mã hóa Room/SQLite.
- [ ] Quản lý khóa an toàn.
- [ ] Migration từ database chưa mã hóa nếu cần.
- [ ] Test mở app sau restart.

**Acceptance criteria:**

- [ ] Database không đọc plain text dễ dàng từ file app data.

### T-0904 - Export/backup mã hóa `[P1][SEC][OPS]`

**Mục tiêu:** Tránh mất dữ liệu khi mất máy/đổi máy.

**Checklist:**

- [ ] Export file backup mã hóa.
- [ ] User đặt mật khẩu backup.
- [ ] Import backup.
- [ ] Validate backup version.
- [ ] Cảnh báo user tự bảo quản file/mật khẩu.

**Acceptance criteria:**

- [ ] Export rồi import sang máy/test database khác giữ đúng dữ liệu.

### T-0905 - Privacy note trong app/docs `[P0][SEC][OPS]`

**Mục tiêu:** Minh bạch dữ liệu nằm ở đâu.

**Checklist:**

- [x] Nêu dữ liệu tài chính lưu trên thiết bị.
- [x] Nêu token SePay do user cung cấp.
- [x] Nêu không có server trung tâm trong MVP.
- [x] Nêu webhook URL là hạ tầng riêng của user.
- [x] Nêu cách xóa dữ liệu/token.

**Acceptance criteria:**

- [x] User hiểu app xử lý dữ liệu cá nhân thế nào.

---

## M10 - Webhook URL optional

### T-1001 - Cấu hình webhook URL do user nhập `[P2][FE][SYNC]`

**Mục tiêu:** Cho user lưu public webhook URL riêng để cấu hình SePay.

**Checklist:**

- [x] Form nhập webhook URL.
- [x] Form nhập secret nếu có.
- [x] Validate URL format.
- [x] Copy URL để dán sang SePay.
- [x] Giải thích app không vận hành URL này thay user.

**Acceptance criteria:**

- [x] User lưu/xóa được webhook URL.
- [x] UI giải thích rõ đây là hạ tầng riêng của user.

### T-1002 - External webhook endpoint sync adapter `[P2][SYNC]`

**Mục tiêu:** Nếu user có endpoint riêng cho phép đọc lại dữ liệu, app có thể sync từ endpoint đó.

**Checklist:**

- [ ] Cấu hình API đọc dữ liệu từ endpoint riêng.
- [ ] Cấu hình auth/secret.
- [x] Fetch transaction list.
- [ ] Đi qua TransactionImportPipeline.
- [ ] Lưu SyncState riêng source webhook_endpoint.

**Acceptance criteria:**

- [ ] App import được dữ liệu từ endpoint user cung cấp mà không tạo trùng.

### T-1003 - Tài liệu hướng dẫn webhook user-owned `[P2][OPS]`

**Mục tiêu:** Hướng dẫn người dùng nâng cao tự cấu hình webhook.

**Checklist:**

- [ ] Giải thích webhook cần URL public.
- [ ] Giải thích điện thoại không nên làm public server.
- [ ] Đưa ví dụ server/NAS/tunnel.
- [ ] Giải thích bảo mật secret/signature.
- [ ] Giải thích fallback sang pull sync.

**Acceptance criteria:**

- [ ] Người dùng hiểu khi nào cần webhook và rủi ro đi kèm.

---

## M11 - Kiểm thử và release MVP

### T-1101 - Unit test BalanceService `[P0][QA]`

**Checklist:**

- [x] Income cộng balance.
- [x] Expense trừ balance.
- [x] Adjustment cập nhật balance.
- [x] BalanceLog tạo đúng.
- [x] Room transaction rollback khi lỗi.

**Acceptance criteria:**

- [x] Các case số dư chính pass.

### T-1102 - Unit test ImportPipeline dedup `[P0][QA]`

**Checklist:**

- [x] Import transaction mới.
- [x] Import duplicate external id.
- [x] Import duplicate reference/amount/date/account.
- [x] Balance không đổi lần hai.
- [x] Duplicate được ghi nhận hoặc bỏ qua đúng rule.

**Acceptance criteria:**

- [x] Không nguồn sync nào cộng/trừ trùng.

### T-1103 - Unit test BudgetChecker `[P0][QA]`

**Checklist:**

- [x] Dưới ngưỡng không cảnh báo.
- [x] Vượt threshold tạo warning.
- [x] Vượt limit tạo exceeded.
- [x] Ignored transaction không tính.
- [x] Không spam cùng ngưỡng.

**Acceptance criteria:**

- [x] Budget event đúng rule.

### T-1104 - Test SePay sync `[P0][QA][SYNC]`

**Checklist:**

- [x] Token thiếu/sai.
- [x] Mất mạng.
- [x] API trả rỗng.
- [x] API trả nhiều page.
- [x] Sync lại không trùng.

**Acceptance criteria:**

- [x] Sync lỗi không làm hỏng local data.

### T-1105 - UI smoke test MVP `[P0][QA][FE]`

**Checklist:**

- [ ] Mở app lần đầu.
- [ ] Tạo account cash/bank.
- [ ] Thêm expense thủ công.
- [ ] Thêm income thủ công.
- [ ] Phân loại pending.
- [ ] Đặt budget.
- [ ] Xem dashboard.
- [ ] Tạo goal.
- [ ] Lưu token SePay.
- [ ] Bấm sync.

**Acceptance criteria:**

- [ ] Luồng MVP không crash trên emulator/device.

### T-1106 - Build internal APK/AAB `[P0][OPS]`

**Checklist:**

- [ ] Cấu hình app name/icon.
- [ ] Cấu hình version.
- [ ] Build debug/internal.
- [ ] Ghi changelog.
- [ ] Tài liệu cách cài cho tester.

**Acceptance criteria:**

- [ ] File build cài được trên thiết bị test.

---

## M12 - Tính năng mở rộng sau MVP

### T-1201 - Import sao kê CSV/Excel `[P2][SYNC][FE]`

**Checklist:**

- [ ] Chọn file.
- [ ] Map cột.
- [ ] Preview trước import.
- [ ] Import qua pipeline.
- [ ] Báo lỗi dòng sai.

**Acceptance criteria:**

- [ ] Import file mẫu không tạo trùng.

### T-1202 - Đọc thông báo ngân hàng `[P2][FE][SEC][SYNC]`

**Checklist:**

- [ ] Xin quyền Notification Listener rõ ràng.
- [ ] Chỉ đọc app ngân hàng user chọn.
- [ ] Parser thông báo.
- [ ] Import qua pipeline.
- [ ] Cho tắt và xóa dữ liệu trích xuất.

**Acceptance criteria:**

- [ ] Chức năng chỉ chạy khi user bật rõ ràng.

### T-1203 - Đọc email biến động số dư `[P2][SEC][SYNC]`

**Checklist:**

- [ ] Thiết kế OAuth consent.
- [ ] Chỉ đọc sender ngân hàng user chọn.
- [ ] Parser email.
- [ ] Import qua pipeline.
- [ ] Revoke quyền.

**Acceptance criteria:**

- [ ] Không đọc email ngoài phạm vi user cho phép.

### T-1204 - AI/rule gợi ý phân loại `[P2][CORE]`

**Checklist:**

- [ ] Rule keyword trước.
- [ ] Gợi ý category.
- [ ] User xác nhận trước khi áp dụng.
- [ ] Học từ lịch sử sửa category.

**Acceptance criteria:**

- [ ] Không tự đổi dữ liệu khi user chưa xác nhận.

### T-1205 - Xuất báo cáo `[P2][FE][OPS]`

**Checklist:**

- [ ] Xuất CSV.
- [ ] Xuất Excel nếu cần.
- [ ] Chọn khoảng thời gian.
- [ ] Chọn loại dữ liệu.

**Acceptance criteria:**

- [ ] File xuất có số liệu khớp dashboard.

---

## Thứ tự thực hiện MVP khuyến nghị

1. T-0001 → T-0004.
2. T-0101 → T-0107.
3. T-0201 → T-0207.
4. T-0301 → T-0304.
5. T-0401 → T-0407.
6. T-0501 → T-0504.
7. T-0601 → T-0603.
8. T-0701 → T-0703.
9. T-0801 → T-0803.
10. T-0901 và T-0905.
11. T-1101 → T-1106.

---

## M12 - Event model chuẩn hóa

### T-1201 - Event registry tập trung `[P0][CORE]`

**Mục tiêu:** Mọi tính năng create/update/delete/system effect đều có event khai báo tập trung.

**Checklist:**

- [x] Tạo `DomainEventType` làm registry duy nhất.
- [x] Gắn feature/action cho từng event.
- [x] Bao phủ account/category/transaction/budget/goal/sync/settings/security/webhook/backup.
- [x] Không dùng string event rải rác trong code.

**Acceptance criteria:**

- [x] Dev muốn thêm/sửa/xóa tính năng phải thêm event tại một nơi.

### T-1202 - Local event store `[P0][CORE]`

**Mục tiêu:** Lưu event local để audit/retry side-effect.

**Checklist:**

- [x] Entity `DomainEvent`.
- [x] DAO insert/list/pending/mark dispatched/mark failed.
- [x] Migration tạo bảng `domain_events`.
- [x] Converter cho `DomainEventType`.

**Acceptance criteria:**

- [x] Event được lưu trong Room local DB.

### T-1203 - Publish event trong luồng domain chính `[P0][CORE]`

**Mục tiêu:** Các thao tác quan trọng phát event thay vì xử lý rải rác.

**Checklist:**

- [x] Account create/balance change.
- [x] Transaction create/import/duplicate/update.
- [x] Budget create/update/warning/exceeded.
- [x] Goal create/progress/completed/pause/resume/link/unlink.
- [x] Settings/security/webhook publish event.
- [x] Sync started/completed/failed publish event.

**Acceptance criteria:**

- [x] Luồng core có event audit thống nhất.


## Definition of Done chung

Một task được xem là hoàn thành khi:

- [ ] Code/tài liệu đúng phạm vi task.
- [ ] Không thêm phụ thuộc server cho task P0.
- [ ] Dữ liệu tài chính chính lưu local.
- [ ] Logic tài chính nằm trong service/domain, không nằm trực tiếp trong UI.
- [ ] Mọi thay đổi balance tạo BalanceLog.
- [ ] Dữ liệu ngoài đi qua ImportPipeline và chống trùng.
- [ ] Token/secret không bị hardcode hoặc log ra.
- [ ] UI có loading/empty/error state nếu cần.
- [ ] Có test phù hợp với mức độ rủi ro.
- [ ] Nếu sửa lỗi, sửa đúng nguyên nhân gốc; không viết hàm mới để đè hàm lỗi.

## Rủi ro cần theo dõi

- SePay API thực tế có thể khác giả định ban đầu.
- Token SePay lưu trên thiết bị phải bảo vệ kỹ.
- Android background sync không đảm bảo realtime trên mọi thiết bị.
- Sửa giao dịch có thể làm lệch balance nếu không xử lý bằng transaction.
- Dedup sai có thể bỏ sót hoặc tạo trùng giao dịch.
- Mã hóa database/backup có thể làm phức tạp migration.
- Người dùng mất máy mà không backup sẽ mất dữ liệu.
- Webhook URL do user tự vận hành có rủi ro bảo mật riêng.

## Ghi chú triển khai

- Ưu tiên manual local + Room trước, rồi mới SePay sync.
- Không xây backend trong MVP nếu không có yêu cầu mới.
- Webhook là optional P2, không chặn release MVP.
- Pull sync khi mở app và nút Đồng bộ là trải nghiệm chính.
- WorkManager chỉ là bổ trợ, không hứa realtime.
- Luôn ưu tiên quyền riêng tư: dữ liệu của ai nằm trên máy người đó.


