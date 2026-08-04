# Thiết lập luồng SePay cho Smart Learnly

Tài liệu này áp dụng cho luồng chuyển khoản VietQR + webhook ngân hàng đang có
sẵn trong `smart-learnly-backend` và `smart-learnly-frontend`. Đây không phải
luồng SePay Payment Gateway redirect/IPN.

## 1. Luồng đã có trong source

```text
Trainee bấm mua
  -> POST /api/v1/orders/checkout
  -> Order PENDING + Transaction PENDING + SePayOrder WAITING_PAYMENT
  -> Backend trả VietQR, số tiền, paymentCode và transferContent
  -> Frontend hiển thị QR và poll GET /api/v1/orders/{orderId}
  -> Người học chuyển khoản
  -> SePay POST webhook HMAC đến backend
  -> Backend kiểm tra chữ ký, timestamp và event trùng
  -> Backend khớp paymentCode + số tiền + tài khoản nhận
  -> Transaction SUCCESS + Order PAID + SePayOrder MATCHED
  -> Course/Class enrollment ACTIVE + invoice + notification
  -> Frontend thấy PAID và chuyển sang trang kết quả
```

Nếu webhook bị lỡ, scheduler gọi SePay API v2 để đối soát các đơn đang chờ.

Các điểm chính trong code:

- Checkout: `commerce/service/CheckoutService.java`
- Webhook: `payment/sepay/SePayWebhookController.java`
- Xác thực HMAC: `payment/sepay/SePayWebhookSignatureVerifier.java`
- Matching: `payment/sepay/SePayPaymentMatchingService.java`
- Đối soát: `payment/sepay/SePayReconciliationService.java`
- Frontend checkout: `src/features/checkout/pages/CheckoutPage.jsx`

## 2. Chuẩn bị trên SePay

1. Tạo tài khoản SePay và liên kết tài khoản ngân hàng nhận tiền.
2. Tạo API token có quyền đọc giao dịch cho SePay API v2. Token này chỉ dùng ở
   backend.
3. Chuẩn bị một domain HTTPS public cho backend. Khi chạy local, dùng tunnel
   HTTPS như ngrok hoặc Cloudflare Tunnel trỏ vào cổng `8080`.
4. Nên bắt đầu bằng SePay Test mode trước khi dùng giao dịch thật.

Backend webhook URL:

```text
https://<public-backend-domain>/api/v1/payments/webhooks/sepay
```

Không dùng URL frontend, `localhost`, IP private hoặc URL có thêm JWT.

## 3. Cấu hình backend

Cách phù hợp nhất cho production là secret manager hoặc environment variables:

```text
SEPAY_WEBHOOK_SECRET=<secret-HMAC-dùng-chung-với-SePay>
SEPAY_API_TOKEN=<token-đọc-SePay-API-v2>
SEPAY_API_BASE_URL=https://userapi.sepay.vn

SEPAY_ACCOUNT_NUMBER=<số-tài-khoản-hoặc-VA-nhận-tiền>
SEPAY_BANK_NAME=<VietQR-bank-identifier>
SEPAY_ACCOUNT_NAME=<tên-chủ-tài-khoản>

SEPAY_PAYMENT_CODE_PREFIX=SLP
SEPAY_TRANSFER_CONTENT_TEMPLATE=
SEPAY_RECONCILIATION_INTERVAL=PT5M
CHECKOUT_EXPIRATION=PT30M
ORDER_EXPIRATION_INTERVAL=PT1M
```

`SEPAY_BANK_NAME` phải là mã/tên ngắn VietQR nhận diện được, ví dụ `MBBank`,
`Vietcombank`, `VCB` hoặc BIN phù hợp; không nên dùng nhãn tùy ý như
`Ngân hàng của công ty`.

Với tài khoản VietinBank cá nhân, SePay yêu cầu nội dung giao dịch bắt đầu bằng
`SEVQR`. Khi `SEPAY_BANK_NAME=VietinBank` và
`SEPAY_TRANSFER_CONTENT_TEMPLATE` để trống, backend tự tạo nội dung dạng
`SEVQR SLPxxxxxxxxxxxx`, đưa nguyên chuỗi này vào QR và trả về frontend. Mã
`SLPxxxxxxxxxxxx` vẫn được giữ riêng để backend matching. Có thể cấu hình rõ
ràng bằng `SEPAY_TRANSFER_CONTENT_TEMPLATE=SEVQR {paymentCode}`; template bắt
buộc phải chứa `{paymentCode}`.

Không đặt `SEPAY_WEBHOOK_SECRET` hoặc `SEPAY_API_TOKEN` trong `.env` của Vite
hay bất kỳ biến `VITE_*` nào.

### Chạy local bằng PowerShell

Copy `run-dev.example.ps1` thành `run-dev.ps1`, thay placeholder bằng giá trị
local rồi chạy:

```powershell
.\run-dev.ps1
```

`run-dev.ps1` đã nằm trong `.gitignore`.

Frontend dùng API local:

```text
VITE_API_URL=http://localhost:8080/api/v1
```

Sau đó chạy frontend bằng `npm run dev` trong `smart-learnly-frontend`.

### Cấu hình qua Admin UI

Admin có thể vào:

```text
/admin/settings -> tab SePay Bank
```

Tại đây có thể nhập thông tin tài khoản hiển thị, API token, webhook secret và
chạy đối soát thủ công. Giá trị lưu trong database được ưu tiên hơn environment
fallback và có hiệu lực ngay.

Muốn lưu secret qua Admin UI, backend phải có `SETTINGS_ENCRYPTION_KEY` là khóa
base64 giải mã thành đúng 32 byte. Có thể tạo một khóa mới bằng PowerShell:

```powershell
$bytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes)
```

Lưu khóa này trong secret manager. Không đổi hoặc làm mất khóa khi database còn
secret đã mã hóa, nếu không backend sẽ không giải mã được cấu hình cũ.

## 4. Tạo webhook trên SePay

Trong SePay Dashboard, tạo webhook với cấu hình:

| Trường | Giá trị |
|---|---|
| URL | `https://<backend>/api/v1/payments/webhooks/sepay` |
| Sự kiện | Chỉ tiền vào (`In_only`) |
| Content-Type | JSON |
| Tài khoản | Đúng tài khoản/VA dùng cho checkout |
| Tiền tố mã thanh toán | `SLP` |
| Bỏ qua giao dịch không có mã | Bật |
| Xác thực | HMAC-SHA256 |
| Secret Key | Trùng `SEPAY_WEBHOOK_SECRET` |
| Retry khi lỗi | Bật |

Prefix trên SePay phải trùng chính xác với `SEPAY_PAYMENT_CODE_PREFIX`. Source
hiện tạo mã dạng `SLP` + 12 ký tự chữ/số.

SePay sẽ gửi:

```text
X-SePay-Signature: sha256=<hex-hmac>
X-SePay-Timestamp: <unix-seconds>
```

Backend ký lại chuỗi `{timestamp}.{raw_body}` và chỉ chấp nhận timestamp lệch
tối đa 5 phút. Vì vậy máy chủ phải đồng bộ thời gian bằng NTP.

## 5. Kiểm thử end-to-end

1. Khởi động database, backend và frontend; xác nhận Flyway đã chạy migration
   payment, đặc biệt bảng `orders`, `transactions`, `sepay_orders` và
   `sepay_webhook_events`.
2. Đăng nhập tài khoản `TRAINEE`.
3. Chọn một Course `PUBLISHED` có giá lớn hơn 0, hoặc một Class `UPCOMING` còn
   chỗ và có giá lớn hơn 0.
4. Bấm mua để tạo checkout. Trang phải hiển thị QR, số tài khoản, số tiền và
   nội dung chuyển khoản đầy đủ. Với VietinBank cá nhân, nội dung phải có dạng
   `SEVQR SLP...`.
5. Trong SePay Test mode, mô phỏng giao dịch tiền vào đúng tài khoản, đúng số
   tiền và có đúng toàn bộ nội dung chuyển khoản. Khi kiểm thử live, chuyển một
   khoản thật nhỏ theo đúng dữ liệu checkout, không xóa tiền tố `SEVQR`.
6. Mở nhật ký webhook trên SePay: request phải nhận HTTP 200 cùng
   `{"success":true}`.
7. Frontend poll mỗi 4 giây. Kết quả mong đợi:

```text
Order: PAID
Transaction: SUCCESS
SePayOrder: MATCHED
Enrollment: ACTIVE
Webhook event: PROCESSED
```

8. Kiểm tra lịch sử giao dịch của learner và invoice.
9. Gửi lại cùng webhook/event id để xác nhận idempotency: không được tạo thêm
   enrollment hoặc invoice.

API kiểm tra nhanh (cần bearer token của đúng user/admin):

```text
GET /api/v1/orders/{orderId}
GET /api/v1/transactions/{transactionId}
GET /api/v1/transactions/{transactionId}/invoice
GET /api/v1/sepay-events
```

Nếu cố ý tắt webhook rồi tạo giao dịch thử, bật lại backend và vào Admin
Settings bấm **Run SePay reconciliation now**. Luồng phải được khôi phục từ
SePay API v2.

## 6. Lỗi thường gặp

### Checkout báo cấu hình SePay chưa đầy đủ

Thiếu một trong ba giá trị account number, VietQR bank identifier hoặc account
name. Kiểm tra cả environment và override trong Admin Settings.

### Tiền đã vào VietinBank nhưng SePay không có giao dịch

Với tài khoản VietinBank cá nhân, kiểm tra nội dung chuyển khoản có bắt đầu
bằng `SEVQR` hay không. Giao dịch chỉ dùng `SLP...` có thể vẫn vào tài khoản
ngân hàng nhưng SePay không nhận được biến động để gửi webhook và API đối soát
cũng không tìm thấy. Sửa QR rồi tạo **đơn mới** để test; giao dịch cũ không tự
xuất hiện lại chỉ vì backend đã được sửa.

### QR hiển thị nhưng app ngân hàng không đọc đúng

Kiểm tra `SEPAY_BANK_NAME`, số tài khoản/VA và quy tắc riêng của ngân hàng. Một
số ngân hàng bắt buộc VA hoặc chuỗi đặc biệt trong nội dung chuyển khoản; khi đó
cần điều chỉnh cấu hình/QR template trước khi production.

### Webhook trả 401

- Secret trên SePay và backend không giống nhau.
- Webhook không chọn HMAC-SHA256.
- Proxy/middleware đã thay đổi raw body.
- Đồng hồ server lệch quá 5 phút.

### Webhook trả 503

Backend chưa có webhook secret hiệu lực, hoặc secret DB không thể giải mã vì
thiếu/sai `SETTINGS_ENCRYPTION_KEY`.

### Webhook 200 nhưng đơn không PAID

Mở `/api/v1/admin/sepay-events`. Trạng thái `MISMATCHED` thường do sai payment
code, sai số tiền, sai tài khoản nhận, giao dịch tiền ra, đơn hết hạn hoặc mã
giao dịch ngân hàng đã được dùng.

### Đơn hết hạn

Mặc định checkout hết hạn sau 30 phút. Scheduler mỗi phút chuyển order sang
`EXPIRED`, transaction sang `FAILED` và SePayOrder sang `EXPIRED`. Tạo checkout
mới thay vì sửa trực tiếp trạng thái database.

### Reconciliation không chạy

Kiểm tra `SEPAY_API_TOKEN`, base URL `https://userapi.sepay.vn`, quyền đọc giao
dịch và log HTTP 401/422/429. Scheduler mặc định chạy mỗi 5 phút và bỏ qua khi
không có API token.

## 7. Checklist production

- [ ] Backend dùng HTTPS public; webhook URL không đi qua frontend.
- [ ] HMAC-SHA256 và retry được bật.
- [ ] Webhook chỉ nhận tiền vào, đúng tài khoản và prefix `SLP`.
- [ ] Secret/token nằm trong secret manager hoặc được mã hóa ở Admin Settings.
- [ ] SePay API v2 reconciliation hoạt động.
- [ ] Đồng hồ server có NTP.
- [ ] Đã test đúng tiền, sai tiền, sai code, duplicate, timeout và webhook bị lỡ.
- [ ] Đã xác nhận chỉ backend cấp enrollment sau payment match.
- [ ] Có cảnh báo/giám sát cho webhook `FAILED` và `MISMATCHED`.

## 8. Tài liệu SePay chính thức

- Webhook: <https://developer.sepay.vn/vi/sepay-webhooks/tich-hop-webhook>
- Xác thực HMAC: <https://developer.sepay.vn/vi/sepay-webhooks/xac-thuc>
- Tạo webhook: <https://developer.sepay.vn/vi/sepay-webhooks/tao-webhook>
- API giao dịch v2: <https://developer.sepay.vn/vi/sepay-api/v2/giao-dich/danh-sach>
- Đối soát: <https://developer.sepay.vn/vi/sepay-webhooks/doi-soat-giao-dich>
- Tạo VietQR: <https://developer.sepay.vn/vi/tien-ich-khac/tao-qr-code>
- Kết nối VietinBank: <https://docs.sepay.vn/ket-noi-vietinbank.html>
