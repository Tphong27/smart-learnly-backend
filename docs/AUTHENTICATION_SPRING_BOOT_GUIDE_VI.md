# Giải thích toàn bộ luồng Authentication trong Smart Learnly

Tài liệu này dành cho người mới học Spring Boot. Mục tiêu không chỉ là mô tả hệ thống làm gì, mà còn giải thích vì sao code sử dụng từng annotation, từng tầng và từng kỹ thuật.

Nội dung được trình bày theo thứ tự học từ nền tảng thấp lên tầng HTTP:

```text
PostgreSQL
  ↓
JPA Entity
  ↓
Repository
  ↓
Service
  ↓
Controller
  ↓
Spring Security
  ↓
Frontend/client
```

Khi phân tích một đoạn code, tài liệu sẽ cố gắng trả lời năm câu hỏi:

1. Khái niệm đó là gì?
2. Annotation hoặc kỹ thuật đó dùng để làm gì?
3. Code hiện tại của dự án chạy như thế nào?
4. Giá trị của các biến thay đổi ra sao?
5. Trường hợp nào thành công hoặc trả lỗi?

---

## Cách đọc tài liệu để không bị rối

Tài liệu khá dài vì giải thích cả kiến thức Spring Boot lẫn code thực tế. Không cần học thuộc trong một lần.

Nếu mới bắt đầu, nên đọc theo ba lượt:

```text
Lượt 1
→ Phần I, II, III
→ hiểu database, Entity, Repository, Controller, Service

Lượt 2
→ Phần IV, V, VI, VII, VIII
→ hiểu đăng ký, đăng nhập, JWT, refresh và Spring Security

Lượt 3
→ Phần IX trở đi
→ hiểu reset password, Google, profile, exception và các lưu ý bảo mật
```

Danh sách các phần:

1. Database và schema authentication.
2. JPA Entity và Repository.
3. Controller, DTO, Service và các annotation nền tảng.
4. Đăng ký và xác minh email.
5. Đăng nhập và từng nhánh `if/else`.
6. Access token và refresh token.
7. Spring Security và phân quyền.
8. Refresh-token rotation và logout.
9. Quên, reset và đổi mật khẩu.
10. Google Login.
11. Profile liên quan authentication.
12. Exception, `try/catch` và HTTP response.
13. CORS, CSRF và email bất đồng bộ.
14. Admin tạo user.
15. Các điểm cần chú ý trong thiết kế hiện tại.
16. Sơ đồ tóm tắt.
17. Danh sách source và unit test nên mở.

---

## 1. Bức tranh tổng thể

Authentication trả lời câu hỏi:

> Người đang gửi request là ai?

Authorization trả lời câu hỏi:

> Người đó có được phép thực hiện hành động này không?

Ví dụ:

```text
an@gmail.com đăng nhập đúng mật khẩu
→ Authentication thành công
→ Hệ thống biết đây là user Nguyễn Văn An

Nguyễn Văn An có role TRAINEE nhưng gọi API quản trị
→ Authentication vẫn thành công
→ Authorization thất bại vì TRAINEE không có quyền ADMIN
```

Luồng request tổng quát của dự án:

```text
Frontend gửi HTTP request
  ↓
CORS filter kiểm tra origin
  ↓
AuthRateLimitFilter giới hạn request auth theo IP
  ↓
Spring Security kiểm tra endpoint public/protected
  ↓
AuthController nhận JSON hoặc cookie
  ↓
DTO validation kiểm tra dữ liệu đầu vào
  ↓
AuthService thực hiện nghiệp vụ
  ↓
Repository đọc/ghi PostgreSQL
  ↓
GlobalExceptionHandler hoặc ApiResponse tạo HTTP response
```

### Các loại thông tin bí mật trong hệ thống

| Loại | Lưu ở đâu? | Cách bảo vệ |
|---|---|---|
| Mật khẩu | `users.password_hash` | BCrypt |
| OTP email | `otp_verifications.otp_hash` | BCrypt |
| Refresh token | Cookie chứa token gốc, DB chứa hash | SecureRandom + SHA-256 |
| Reset-password token | Email chứa token gốc, DB chứa hash | SecureRandom + SHA-256 |
| Access token | Frontend giữ JWT, không lưu DB | JWT ký bằng `JWT_SECRET` |

Điểm cần nhớ:

```text
Mật khẩu/OTP cần passwordEncoder.matches()
→ dùng BCrypt

Refresh/reset token có độ ngẫu nhiên rất cao và cần truy vấn chính xác theo hash
→ dùng SHA-256

Access token cần tự mang thông tin user và hết hạn nhanh
→ dùng JWT
```

---

# Phần I — Database

## 2. Flyway và cách database được tạo

Các bảng authentication được định nghĩa trong:

```text
src/main/resources/db/migration/
├── V0__auth_database_baseline.sql
├── V2__identity_auth_support.sql
├── V3__auth_session_foundation.sql
└── V4__complete_auth_foundation.sql
```

Môi trường dev cấu hình tại:

```text
src/main/resources/application-dev.yml
```

Các cấu hình quan trọng:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate

  flyway:
    enabled: true
    locations: classpath:db/migration
```

### `ddl-auto: validate` nghĩa là gì?

Hibernate không tự tạo hoặc sửa bảng. Nó chỉ so sánh Entity Java với schema hiện tại.

Ví dụ Entity yêu cầu cột `email`, nhưng database không có cột đó:

```text
Backend khởi động
  ↓
Hibernate validate schema
  ↓
Phát hiện Entity không khớp database
  ↓
Ứng dụng khởi động thất bại
```

Flyway mới là thành phần chạy SQL migration để tạo hoặc cập nhật schema.

### Vì sao không dùng `ddl-auto: update`?

`update` để Hibernate tự sửa database, thuận tiện khi học nhưng khó kiểm soát trong dự án nhóm. Flyway giữ lịch sử rõ ràng:

```text
V0 → tạo auth baseline
V2 → thêm refresh/reset token
V3 → thêm khóa tài khoản và login history
V4 → thêm OTP
```

---

## 3. Enum role và status

Migration V0 tạo enum PostgreSQL:

```sql
CREATE TYPE public.user_role AS ENUM (
    'GUEST', 'TRAINEE', 'TRAINER', 'TMO', 'SME', 'ADMIN'
);

CREATE TYPE public.user_status AS ENUM (
    'pending_verify', 'active', 'inactive', 'banned'
);
```

### Role

| Role | Ý nghĩa |
|---|---|
| `GUEST` | Khách chưa đăng nhập |
| `TRAINEE` | Học viên |
| `TRAINER` | Giảng viên |
| `TMO` | Quản lý đào tạo |
| `SME` | Chuyên gia nội dung |
| `ADMIN` | Quản trị hệ thống |

### Status

| Status | Có đăng nhập bằng email/mật khẩu không? |
|---|---|
| `pending_verify` | Không, cần xác minh email |
| `active` | Có |
| `inactive` | Không |
| `banned` | Không |

Enum ở database giúp chặn giá trị không hợp lệ.

Ví dụ:

```sql
UPDATE users SET role = 'STUDENT';
```

PostgreSQL từ chối vì `STUDENT` không nằm trong `user_role`.

---

## 4. Bảng `roles`

`roles` chứa danh mục role và mô tả:

```text
roles
├── id
├── name
├── description
└── created_at
```

Tuy nhiên, code hiện tại không dùng khóa ngoại từ `users` đến `roles`. Phân quyền thực tế dựa trên cột enum:

```text
users.role
```

Vì vậy có thể hiểu:

```text
roles table = danh mục/mô tả
users.role  = dữ liệu đang được Spring Security sử dụng
```

---

## 5. Bảng `users`

Đây là bảng trung tâm của authentication.

| Cột | Ý nghĩa |
|---|---|
| `id` | UUID nội bộ |
| `auth_user_id` | ID từ hệ thống identity bên ngoài nếu có |
| `email` | Email đăng nhập |
| `password_hash` | Mật khẩu BCrypt |
| `google_id` | Google subject ID |
| `full_name` | Tên hiển thị |
| `avatar_url` | URL ảnh đã upload hoặc ảnh Google |
| `phone_number` | Số điện thoại |
| `role` | Role hiện tại |
| `status` | Trạng thái tài khoản |
| `bio` | Giới thiệu |
| `email_verified_at` | Thời điểm xác minh email |
| `password_changed_at` | Lần đổi mật khẩu gần nhất |
| `failed_login_attempts` | Số lần đăng nhập sai liên tiếp |
| `locked_until` | Bị khóa đến thời điểm nào |
| `last_login_at` | Đăng nhập thành công gần nhất |
| `created_at` | Thời điểm tạo |
| `updated_at` | Thời điểm cập nhật |
| `deleted_at` | Soft delete |

### Unique email không phân biệt hoa thường

Database có partial unique index:

```sql
CREATE UNIQUE INDEX uq_users_email_lower
ON public.users (lower(email))
WHERE deleted_at IS NULL;
```

Ví dụ database đã có:

```text
email = An@Gmail.com
deleted_at = null
```

Không thể tạo thêm:

```text
email = an@gmail.com
```

Nhưng nếu user cũ đã soft delete:

```text
deleted_at = 2026-08-02T10:00:00Z
```

thì index không còn tính dòng đó và email có thể được sử dụng lại.

### Vì sao `password_hash` được phép null?

Tài khoản Google có thể đăng nhập mà chưa từng tạo mật khẩu cục bộ:

```text
google_id = 109123...
password_hash = null
```

Nếu user đặt lại mật khẩu bằng Forgot Password, `password_hash` mới được gán.

---

## 6. Bảng `otp_verifications`

Một user có thể yêu cầu nhiều OTP, nhưng chỉ OTP mới nhất chưa bị vô hiệu hóa mới dùng được.

| Cột | Ý nghĩa |
|---|---|
| `user_id` | User sở hữu OTP |
| `email` | Email nhận OTP |
| `otp_hash` | BCrypt của OTP |
| `purpose` | Hiện tại là `email_verify` |
| `expires_at` | Hết hạn |
| `verified_at` | Đã sử dụng/vô hiệu hóa lúc nào |
| `attempts` | Số lần nhập sai |
| `max_attempts` | Số lần thử tối đa |

Ví dụ OTP gửi qua email:

```text
038215
```

Database chỉ lưu:

```text
$2a$10$C7p...
```

Điều kiện OTP dùng được:

```text
verified_at == null
AND expires_at > now
AND attempts < max_attempts
```

---

## 7. Bảng `refresh_tokens`

Refresh token đại diện cho phiên đăng nhập dài hạn.

| Cột | Ý nghĩa |
|---|---|
| `user_id` | Chủ phiên |
| `token_hash` | SHA-256 của token gốc |
| `device_info` | User-Agent |
| `ip_address` | IP tạo phiên |
| `expires_at` | Hết hạn |
| `revoked_at` | Bị thu hồi lúc nào |

Refresh token dùng được khi:

```text
revoked_at == null
AND expires_at > now
```

Database không lưu token gốc. Nếu database bị đọc trái phép, kẻ tấn công không thể lấy trực tiếp cookie refresh token từ `token_hash`.

---

## 8. Bảng `password_reset_tokens`

| Cột | Ý nghĩa |
|---|---|
| `user_id` | User muốn đặt lại mật khẩu |
| `token_hash` | SHA-256 token |
| `expires_at` | Hết hạn, mặc định 30 phút |
| `used_at` | Đã dùng lúc nào |

Token hợp lệ khi:

```text
used_at == null
AND expires_at > now
```

Sau khi reset thành công:

```text
used_at = now
```

nên cùng một link không thể dùng lần hai.

---

## 9. Bảng `login_history`

Bảng này ghi lại các lần đăng nhập:

```text
email
user_id nếu tìm thấy user
ip_address
user_agent
login_method = email/google
status = success/failed/blocked
created_at
```

Khóa ngoại dùng `ON DELETE SET NULL`. Khi user bị xóa, lịch sử vẫn được giữ nhưng `user_id` trở thành null.

Ngoài `login_history`, dự án còn ghi `audit_logs`. Hai loại dữ liệu có mục đích khác nhau:

```text
login_history
→ tập trung vào các lần đăng nhập

audit_logs
→ lịch sử hành động bảo mật và nghiệp vụ rộng hơn
```

---

## 10. Các bảng auth chưa nằm trong luồng chính

Schema còn có:

```text
email_verification_tokens
user_security_limits
```

Nhưng source hiện tại:

- Xác minh email dùng `otp_verifications`.
- Rate limit dùng `ConcurrentHashMap` trong `AuthRateLimitFilter`.
- `EmailVerificationTokenRepository` tồn tại nhưng không được `AuthService` gọi.
- Không có Entity/Repository đang sử dụng `user_security_limits`.

Khi giải thích luồng hiện tại, không nên nhầm hai bảng này với cơ chế đang hoạt động.

---

# Phần II — JPA Entity và Repository

## 11. `@Entity` và `@Table`

Trong `UserAccount`:

```java
@Entity
@Table(name = "users", schema = "public")
public class UserAccount {
}
```

`@Entity` nói với JPA rằng class này được lưu trong database.

`@Table` ánh xạ:

```text
Java class UserAccount
        ↕
PostgreSQL table public.users
```

Nếu thiếu `@Entity`, `JpaRepository<UserAccount, UUID>` không thể quản lý class này.

---

## 12. Lombok annotations

```java
@Getter
@Setter
@NoArgsConstructor
```

Lombok tự sinh getter, setter và constructor rỗng.

Ví dụ `@Getter` sinh tương đương:

```java
public String getEmail() {
    return email;
}
```

JPA cần constructor không tham số để tạo Entity khi đọc dữ liệu:

```java
UserAccount user = new UserAccount();
user.setEmail(valueFromDatabase);
```

---

## 13. `@Id`, UUID và lifecycle callback

```java
@Id
private UUID id;
```

`@Id` đánh dấu khóa chính.

`UserAccount` không dùng `@GeneratedValue`; nó sinh UUID trong `@PrePersist`:

```java
@PrePersist
void prePersist() {
    if (id == null) {
        id = UUID.randomUUID();
    }
}
```

Trước `save`:

```text
user.id = null
```

Ngay trước INSERT:

```text
user.id = UUID mới
```

`@PreUpdate` tự cập nhật `updatedAt` trước UPDATE.

---

## 14. `@Column` và `@ColumnTransformer`

```java
@Column(name = "password_hash")
private String passwordHash;
```

Ánh xạ camelCase của Java với snake_case của SQL.

Role/status là String trong Java nhưng enum trong PostgreSQL:

```java
@ColumnTransformer(write = "?::user_role")
private String role;
```

Nếu Java gửi `TRAINEE`, SQL ghi tương đương:

```sql
'TRAINEE'::user_role
```

---

## 15. `@ManyToOne`, `@JoinColumn` và `FetchType.LAZY`

Trong `OtpVerification` và `RefreshToken`:

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "user_id", nullable = false)
private UserAccount user;
```

Quan hệ:

```text
Một User
├── nhiều OTP
├── nhiều refresh token
└── nhiều password reset token
```

Từ phía token là “nhiều token thuộc một user”, nên dùng `@ManyToOne`.

`FetchType.LAZY` nghĩa là Hibernate chỉ tải User khi code thực sự gọi `token.getUser()`.

---

## 16. `@MappedSuperclass`

`PasswordResetToken` và `EmailVerificationToken` có các cột giống nhau. Dự án gom phần chung vào:

```java
@MappedSuperclass
public abstract class AbstractAuthToken {
    UUID id;
    UserAccount user;
    String tokenHash;
    Instant expiresAt;
    Instant usedAt;
    Instant createdAt;
}
```

`@MappedSuperclass` không tự tạo một bảng `abstract_auth_token`. Nó chỉ cho phép Entity con kế thừa mapping.

```text
AbstractAuthToken
├── PasswordResetToken → password_reset_tokens
└── EmailVerificationToken → email_verification_tokens
```

---

## 17. `JpaRepository`

```java
public interface UserRepository
        extends JpaRepository<UserAccount, UUID> {
}
```

Hai generic parameter:

```text
UserAccount = Entity được quản lý
UUID        = kiểu khóa chính
```

Spring Data tự cung cấp:

```java
save(user);
findById(id);
findAll();
delete(user);
count();
```

### Derived query

```java
findByEmailIgnoreCaseAndDeletedAtIsNull(email)
```

Spring tách tên hàm:

```text
findBy
Email
IgnoreCase
And
DeletedAt
IsNull
```

Tương đương ý tưởng SQL:

```sql
SELECT *
FROM users
WHERE lower(email) = lower(?)
  AND deleted_at IS NULL;
```

### `Optional<UserAccount>`

Repository trả `Optional` vì user có thể tồn tại hoặc không.

```java
Optional<UserAccount> result = repository.findBy...();
```

```text
Tìm thấy     → Optional chứa UserAccount
Không tìm thấy → Optional.empty()
```

---

# Phần III — Controller, DTO và Service

## 18. Spring Container và Dependency Injection

Các annotation:

```java
@RestController
@Service
@Component
@Configuration
```

làm class trở thành Bean do Spring quản lý.

```text
Spring Container
├── AuthController
├── AuthService
├── AuthSessionService
├── UserRepository
├── PasswordEncoder
└── EmailService
```

`@RequiredArgsConstructor` của Lombok sinh constructor cho các field `final`.

Spring dùng constructor đó để inject dependency:

```text
AuthController
└── AuthService
    ├── UserRepository
    ├── PasswordEncoder
    ├── EmailService
    └── AuthSessionService
```

---

## 19. `@RestController`, `@RequestMapping`, `@PostMapping`

```java
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
}
```

`@RestController` làm giá trị trả về được serialize thành JSON.

```java
@PostMapping("/register")
```

Ghép thành:

```text
POST /api/v1/auth/register
```

Nếu gọi GET vào URL này, backend trả `405 METHOD_NOT_ALLOWED`.

---

## 20. `@RequestBody`, Java record và Jackson

Frontend gửi:

```json
{
  "fullName": "Nguyễn Văn An",
  "email": "an@gmail.com",
  "password": "Strong@123",
  "confirmPassword": "Strong@123"
}
```

`@RequestBody` yêu cầu Jackson chuyển JSON thành:

```java
new RegisterRequest(
    "Nguyễn Văn An",
    "an@gmail.com",
    "Strong@123",
    "Strong@123"
);
```

Java `record` tự sinh accessor:

```java
request.fullName();
request.email();
request.password();
```

---

## 21. Bean Validation và `@Valid`

`RegisterRequest` sử dụng:

```text
@NotBlank → không null, không rỗng, không chỉ có khoảng trắng
@Email    → đúng định dạng email
@Size     → giới hạn độ dài
@Pattern  → kiểm tra regex độ mạnh mật khẩu
```

`@Valid @RequestBody` làm validation chạy trước Controller method.

```text
JSON
  ↓ Jackson
RegisterRequest
  ↓ @Valid
Validation thành công → gọi AuthController.register()
Validation thất bại  → GlobalExceptionHandler trả 400
```

---

## 22. `@Service`

`AuthService` chứa business rule, ví dụ:

- Email có được trùng không?
- User chưa xác minh có login được không?
- Sai bao nhiêu lần thì khóa?
- OTP có hết hạn không?
- Reset token có dùng lại được không?

Controller không nên chứa các quy tắc này. Controller chỉ nhận/trả HTTP.

---

## 23. `@Transactional`

`@Transactional` gom nhiều thao tác database thành một đơn vị.

Ví dụ đăng ký cần:

```text
INSERT user
INSERT OTP
INSERT audit log
```

Nếu một bước lỗi:

```text
Không có transaction
→ có thể user đã lưu nhưng OTP chưa lưu

Có transaction
→ rollback toàn bộ
```

Thông thường `RuntimeException`, bao gồm `BusinessException`, làm transaction rollback.

Một số method cố ý dùng:

```java
@Transactional(noRollbackFor = BusinessException.class)
```

để dữ liệu như số lần nhập sai vẫn được lưu trước khi trả lỗi.

---

# Phần IV — Luồng đăng ký và xác minh email

## 24. Đăng ký end-to-end

Request ví dụ:

```json
{
  "fullName": " Nguyễn Văn An ",
  "email": " AN@GMAIL.COM ",
  "password": "Strong@123",
  "confirmPassword": "Strong@123"
}
```

### Bước 1: Rate limit

`AuthRateLimitFilter` đếm request auth theo IP. Mặc định tối đa 5 request trong 15 phút. Dev profile hiện tắt filter này.

### Bước 2: Security

`/api/v1/auth/register` là endpoint `permitAll()`, không cần access token.

### Bước 3: DTO validation

Mật khẩu phải:

```text
8–100 ký tự
có chữ thường
có chữ hoa
có số
có ký tự đặc biệt
```

### Bước 4: Controller

```java
authService.register(request);
```

Controller không tự tạo Entity.

### Bước 5: So sánh mật khẩu xác nhận

```java
validatePasswordConfirmation(
    request.password(),
    request.confirmPassword()
);
```

Nếu khác nhau:

```text
400 INVALID_REQUEST
Password confirmation does not match
```

### Bước 6: Chuẩn hóa email

```text
" AN@GMAIL.COM "
  ↓ trim
"AN@GMAIL.COM"
  ↓ lowercase
"an@gmail.com"
```

### Bước 7: Kiểm tra trùng email

```java
findByEmailIgnoreCaseAndDeletedAtIsNull(email)
```

Nếu có user chưa soft delete:

```text
409 CONFLICT
Email already exists
```

### Bước 8: Tạo Entity

```text
email = an@gmail.com
fullName = Nguyễn Văn An
passwordHash = BCrypt("Strong@123")
role = TRAINEE
status = pending_verify
failedLoginAttempts = 0
```

Mật khẩu gốc không được lưu.

### Bước 9: Lưu user

`userRepository.save(user)` kích hoạt `@PrePersist`, sinh UUID và timestamp.

### Bước 10: Tạo OTP

`issueVerificationOtp(savedUser)`:

1. Đếm số OTP đã yêu cầu trong cửa sổ 15 phút.
2. Từ chối nếu đã đạt 3 request.
3. Đặt `verified_at` cho OTP cũ để vô hiệu hóa.
4. Sinh số ngẫu nhiên 6 chữ số.
5. BCrypt OTP.
6. Lưu với hạn 15 phút, tối đa 5 lần thử.
7. Gửi email bất đồng bộ.

Database sau đăng ký:

```text
users
└── an@gmail.com, TRAINEE, pending_verify

otp_verifications
└── an@gmail.com, otp_hash=$2a$..., attempts=0
```

---

## 25. Xác minh email

Request:

```json
{
  "email": "an@gmail.com",
  "otpCode": "038215"
}
```

### Tìm OTP

Repository lấy OTP mới nhất:

```text
email = an@gmail.com
purpose = email_verify
verified_at IS NULL
ORDER BY created_at DESC
```

### Kiểm tra sử dụng được

```text
verified_at == null
expires_at > now
attempts < maxAttempts
```

### OTP sai

```java
if (!passwordEncoder.matches(rawOtp, otpHash)) {
    attempts = attempts + 1;
    save(otp);
    throw BusinessException;
}
```

Method dùng `noRollbackFor = BusinessException.class`, nên `attempts + 1` không bị rollback.

### OTP đúng

```text
users.email_verified_at = now
users.status = active
otp_verifications.verified_at = now
```

User đã có thể đăng nhập.

---

# Phần V — Luồng đăng nhập

## 26. Request đăng nhập

```http
POST /api/v1/auth/login
Content-Type: application/json
User-Agent: Mozilla/5.0 ...
X-Forwarded-For: 203.0.113.10
```

```json
{
  "email": " AN@GMAIL.COM ",
  "password": "Strong@123"
}
```

Controller truyền ba nhóm dữ liệu vào Service:

```text
request.email/password
deviceInfo = User-Agent
ipAddress = IP đầu tiên trong X-Forwarded-For hoặc remoteAddr
```

`login()` dùng:

```java
@Transactional(noRollbackFor = BusinessException.class)
```

để giữ `failed_login_attempts`, `locked_until`, `login_history` và audit log dù cuối method ném lỗi.

---

## 27. Nhánh 1 — Email không tồn tại

Service chuẩn hóa:

```text
" AN@GMAIL.COM " → "an@gmail.com"
```

Repository không tìm thấy user.

Backend ghi:

```text
login_history
├── user_id = null
├── email = an@gmail.com
├── login_method = email
└── status = failed
```

Sau đó trả:

```text
401 INVALID_CREDENTIALS
```

Backend không trả “Email does not exist”, vì thông báo đó làm lộ danh sách tài khoản.

---

## 28. Nhánh 2 — Tài khoản đang bị khóa

Điều kiện:

```java
lockedUntil != null
&& lockedUntil.isAfter(now)
```

Ví dụ:

```text
now = 10:00
lockedUntil = 10:25
```

Kết quả:

```text
login_history.status = blocked
423 ACCOUNT_LOCKED
```

Backend chưa kiểm tra mật khẩu vì tài khoản vẫn trong thời gian khóa.

---

## 29. Nhánh 3 — Status không phải `active`

Logic hiện tại:

```text
status = pending_verify hoặc chưa verified
→ 403 EMAIL_NOT_VERIFIED

status = inactive hoặc banned
→ 403 ACCOUNT_INACTIVE
```

Mật khẩu chưa được kiểm tra ở nhánh này.

---

## 30. Nhánh 4 — Mật khẩu sai

Backend không giải mã BCrypt. Nó gọi:

```java
passwordEncoder.matches(
    request.password(),
    user.getPasswordHash()
);
```

Ví dụ:

```text
raw password = Wrong@123
stored hash = BCrypt(Strong@123)
matches = false
```

Service tăng số lần sai:

```text
failed_login_attempts = failed_login_attempts + 1
```

Mặc định sau 5 lần:

```text
locked_until = now + 30 phút
failed_login_attempts = 0
```

Ví dụ biến đổi:

```text
Lần 1: attempts 0 → 1
Lần 2: attempts 1 → 2
Lần 3: attempts 2 → 3
Lần 4: attempts 3 → 4
Lần 5: attempts 4 → 5
       → locked_until = now + 30 phút
       → attempts được đặt lại 0
```

Response:

```text
Sai nhưng chưa khóa → 401 INVALID_CREDENTIALS
Vừa đạt giới hạn   → 423 ACCOUNT_LOCKED
```

---

## 31. Nhánh 5 — Đăng nhập thành công

Khi BCrypt matches:

```text
failed_login_attempts = 0
locked_until = null
last_login_at = now
```

Backend ghi:

```text
login_history.status = success
audit action = LOGIN_SUCCEEDED
```

Sau đó gọi:

```java
authSessionService.issue(user, deviceInfo, ipAddress)
```

---

# Phần VI — Access token và refresh token

## 32. Phát hành refresh token

`AuthSessionService.issue()` sinh 48 byte ngẫu nhiên bằng `SecureRandom` rồi chuyển sang Base64 URL-safe.

```text
rawRefreshToken = chuỗi ngẫu nhiên dài
```

Database lưu:

```text
token_hash = SHA-256(rawRefreshToken)
device_info = tối đa 255 ký tự
ip_address = tối đa 45 ký tự
expires_at = now + 7 ngày
```

Service trả token gốc cho Controller trong `IssuedSession`, nhưng JSON response không chứa refresh token.

---

## 33. Refresh token cookie

Controller đặt header `Set-Cookie`:

```text
Name = slp_refresh_token
HttpOnly = true
SameSite = Lax
Path = /api/v1/auth
MaxAge = refresh-token TTL
Secure = phụ thuộc cấu hình
```

### `HttpOnly`

JavaScript không đọc được cookie bằng `document.cookie`. Điều này giảm nguy cơ XSS lấy refresh token.

### `Secure`

Nếu `true`, trình duyệt chỉ gửi cookie qua HTTPS. Dev thường để `false`; production nên để `true`.

### `SameSite=Lax`

Giảm một số request cross-site tự động, hỗ trợ hạn chế CSRF.

### `Path=/api/v1/auth`

Cookie chỉ được gửi tới các URL dưới `/api/v1/auth`, không gửi tới mọi API của hệ thống.

---

## 34. Tạo access token JWT

`JwtTokenService` tạo các claim:

```json
{
  "sub": "user-uuid",
  "user_id": "user-uuid",
  "email": "an@gmail.com",
  "roles": ["TRAINEE"],
  "iat": "thời điểm phát hành",
  "exp": "thời điểm hết hạn"
}
```

Mặc định:

```text
access token = 15 phút
refresh token = 7 ngày
```

Dev profile override access token thành 2 giờ.

JWT được ký bằng HMAC-SHA256 với `JWT_SECRET`. Secret phải có ít nhất 32 ký tự.

JWT không được lưu vào database. Backend xác minh chữ ký và thời hạn khi nhận lại token.

---

## 35. Response đăng nhập

Body:

```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "eyJ...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "user": {
      "id": "...",
      "email": "an@gmail.com",
      "role": "TRAINEE",
      "status": "active"
    }
  }
}
```

Header:

```http
Set-Cookie: slp_refresh_token=...; HttpOnly; SameSite=Lax; Path=/api/v1/auth
```

```text
Access token  → frontend dùng trong Authorization header
Refresh token → trình duyệt tự giữ trong HttpOnly cookie
```

---

# Phần VII — Spring Security và request đã đăng nhập

## 36. `SecurityFilterChain`

`SecurityConfig` tạo hai chain tùy `app.security.authentication-mode`:

```text
basic → HTTP Basic
jwt   → OAuth2 Resource Server JWT
```

Ứng dụng hiện mặc định dùng JWT.

`SessionCreationPolicy.STATELESS` nghĩa là backend không tạo HTTP session cho user.

Mỗi request protected phải có access token:

```http
Authorization: Bearer eyJ...
```

---

## 37. Endpoint public và protected

Public auth endpoints:

```text
/register
/login
/google
/google/config
/refresh
/logout
/forgot-password
/reset-password
/verify-email
/resend-verification
```

Protected:

```text
GET  /auth/profile
PATCH /auth/profile
POST /auth/profile/avatar
POST /auth/change-password
```

Các endpoint còn lại áp dụng `.anyRequest().authenticated()` nếu không khớp rule cụ thể.

---

## 38. JWT được kiểm tra như thế nào?

Spring Resource Server:

```text
Đọc Authorization header
  ↓
Tách Bearer token
  ↓
JwtDecoder kiểm tra chữ ký
  ↓
Kiểm tra exp
  ↓
Tạo JwtAuthenticationToken
  ↓
Lưu vào SecurityContextHolder
```

Nếu chữ ký sai hoặc token hết hạn, request không vào Controller và nhận 401.

---

## 39. Chuyển role thành authority

JWT:

```json
"roles": ["ADMIN"]
```

`JwtRolesConverter` thêm prefix:

```text
ADMIN → ROLE_ADMIN
```

Vì Spring `hasRole("ADMIN")` thực chất kiểm tra authority `ROLE_ADMIN`.

Ví dụ:

```java
.requestMatchers("/api/v1/admin/**")
.hasRole("ADMIN")
```

```text
ROLE_ADMIN   → được phép
ROLE_TRAINEE → 403 FORBIDDEN
```

---

## 40. `SecurityContextHolder` và current user

`SecurityContextAuthenticatedUserResolver` đọc Authentication hiện tại.

Nếu là JWT, nó lấy:

```text
user_id claim
sub/auth-user-id claim
email claim
authorities/roles
```

rồi tạo:

```java
CurrentUser(
    id,
    authUserId,
    email,
    roles
)
```

Khi cần Entity đầy đủ, Service tìm theo thứ tự:

```text
1. users.id
2. users.auth_user_id
3. users.email
```

Điều này quan trọng vì backend không tin `userId` do frontend tự gửi.

Ví dụ endpoint profile không nhận:

```json
{ "userId": "user-khac" }
```

Nó xác định user từ token đã được xác minh.

---

## 41. Phân biệt 401 và 403

```text
401 UNAUTHENTICATED
→ chưa đăng nhập
→ token thiếu/sai/hết hạn

403 FORBIDDEN
→ đã đăng nhập
→ không đủ role/quyền
```

Ví dụ:

```text
Không gửi token khi gọi /auth/profile
→ 401

TRAINEE gửi token hợp lệ nhưng gọi /admin/users
→ 403
```

---

# Phần VIII — Refresh và logout

## 42. Refresh-token rotation

Endpoint:

```text
POST /api/v1/auth/refresh
```

Trình duyệt tự gửi cookie. Controller đọc `slp_refresh_token`.

Service:

```text
raw token từ cookie
  ↓ SHA-256
tìm refresh_tokens.token_hash
  ↓
kiểm tra revoked_at == null
  ↓
kiểm tra expires_at > now
  ↓
đặt token cũ revoked_at = now
  ↓
tạo access token mới
  ↓
tạo refresh token mới
```

Đây gọi là refresh-token rotation.

Token cũ không thể dùng lại sau lần refresh thành công.

---

## 43. Logout

Endpoint:

```text
POST /api/v1/auth/logout
```

Backend:

1. Hash refresh token từ cookie.
2. Tìm record tương ứng.
3. Đặt `revoked_at = now`.
4. Ghi audit `LOGOUT_SUCCEEDED` nếu xác định được user.
5. Trả `Set-Cookie` với `MaxAge=0` để xóa cookie.

Điểm cần hiểu:

```text
Refresh token bị revoke ngay
Access token cũ vẫn dùng được đến khi exp
```

Lý do: access JWT không được lưu trong DB và chưa có blacklist.

---

# Phần IX — Quên, reset và đổi mật khẩu

## 44. Forgot Password

Endpoint luôn trả thông báo chung:

```text
If the account exists, password reset instructions...
```

Nhằm tránh lộ email nào tồn tại.

Nếu tìm thấy user chưa soft delete:

```text
Đánh dấu reset token cũ là used
  ↓
Sinh 32 byte ngẫu nhiên
  ↓
Lưu SHA-256 vào DB
  ↓
expires_at = now + 30 phút
  ↓
Gửi raw token trong email link
```

Ví dụ:

```text
Email link:
http://localhost:5173/reset-password?token=<raw-token>

Database:
token_hash = SHA-256(<raw-token>)
```

---

## 45. Reset Password

Request:

```json
{
  "token": "raw-token-from-email",
  "newPassword": "NewStrong@123",
  "confirmPassword": "NewStrong@123"
}
```

Service:

```text
So sánh hai mật khẩu
  ↓
SHA-256 raw token
  ↓
Tìm token_hash
  ↓
Kiểm tra chưa used và chưa expired
  ↓
BCrypt mật khẩu mới
  ↓
password_changed_at = now
  ↓
revoke tất cả refresh token
  ↓
reset token used_at = now
```

Access token cũ vẫn tồn tại đến `exp`, tương tự logout.

---

## 46. Change Password

Endpoint yêu cầu access token vì backend cần biết user hiện tại.

Các nhánh:

```text
password_hash null
→ tài khoản chưa hỗ trợ local password

currentPassword sai
→ 401 INVALID_CREDENTIALS

newPassword giống mật khẩu cũ
→ 422 BUSINESS_RULE_VIOLATION

thành công
→ BCrypt password mới
→ password_changed_at = now
→ revoke tất cả refresh token
```

---

# Phần X — Google Login

## 47. Xác minh Google ID token

Frontend gửi:

```json
{
  "idToken": "google-id-token"
}
```

`GoogleIdTokenService` dùng Google JWK để kiểm tra chữ ký.

Các điều kiện:

```text
token chưa hết hạn
issuer là accounts.google.com
audience chứa Google client ID hiện tại
email tồn tại
email_verified = true
```

Google client ID được lấy:

```text
system_settings trong database
  ↓ nếu không có
GOOGLE_CLIENT_ID từ environment
```

---

## 48. Link hoặc tạo Google user

### Tìm thấy `google_id`

Dùng user đó.

### Không tìm thấy `google_id`, nhưng email đã tồn tại

```text
user.google_id = Google subject
```

Nếu chưa xác minh:

```text
email_verified_at = now
status = active
```

Nếu chưa có avatar và Google có picture, backend dùng Google avatar.

### Chưa có user

Tạo:

```text
email = Google email
google_id = Google subject
full_name = Google name hoặc email
avatar_url = Google picture
role = TRAINEE
status = active
email_verified_at = now
password_hash = null
```

Sau đó phát hành JWT và refresh token như login thường.

---

# Phần XI — Profile và authentication liên quan

## 49. Lấy user hiện tại

`GET /api/v1/auth/profile`:

```text
JWT đã được Security xác minh
  ↓
AuthenticatedUserResolver lấy user_id
  ↓
UserRepository tìm UserAccount
  ↓
map thành UserProfileResponse
```

Không trả `password_hash`, refresh token hoặc các trường bảo mật nội bộ.

---

## 50. Update Profile

Service chỉ cập nhật field request khác null.

```java
if (request.fullName() != null) {
    ...
}
```

Ví dụ request chỉ có phone:

```json
{
  "phoneNumber": "+84901234567"
}
```

Tên, bio và avatar cũ được giữ nguyên.

Nếu tất cả field đều null:

```text
400 INVALID_REQUEST
At least one profile field must be provided
```

---

## 51. Upload avatar

`POST /api/v1/auth/profile/avatar` nhận multipart file.

Luồng:

```text
kiểm tra JPEG/PNG/WebP
  ↓
kiểm tra tối đa 5 MB
  ↓
upload storage
  ↓
nhận public URL
  ↓
UpdateProfileRequest(avatarUrl=URL)
  ↓
users.avatar_url được cập nhật
```

Frontend không gửi URL do người dùng tự nhập; URL được tạo từ kết quả upload.

---

# Phần XII — Exception, try/catch và response

## 52. Vì sao Service ít có try/catch?

Service ném `BusinessException`:

```java
throw new BusinessException(
    ErrorCode.INVALID_CREDENTIALS
);
```

Exception đi lên `GlobalExceptionHandler`.

```text
AuthService ném lỗi
  ↓
Controller không catch
  ↓
@RestControllerAdvice bắt lỗi
  ↓
chuyển thành ErrorResponse
```

Ví dụ:

```json
{
  "success": false,
  "status": 401,
  "code": "INVALID_CREDENTIALS",
  "message": "Invalid credentials",
  "path": "/api/v1/auth/login",
  "errors": []
}
```

Try/catch được dùng khi cần đổi lỗi thư viện thành lỗi nghiệp vụ.

Google login:

```text
JwtException
→ INVALID_CREDENTIALS

RestClientException
→ EXTERNAL_SERVICE_UNAVAILABLE
```

---

## 53. Các HTTP status auth chính

| Error code | HTTP | Ý nghĩa |
|---|---:|---|
| `VALIDATION_FAILED` | 400 | DTO không hợp lệ |
| `INVALID_OR_EXPIRED_TOKEN` | 400 | OTP/refresh/reset token sai hoặc hết hạn |
| `UNAUTHENTICATED` | 401 | Thiếu hoặc sai authentication |
| `INVALID_CREDENTIALS` | 401 | Email/mật khẩu/Google token sai |
| `EMAIL_NOT_VERIFIED` | 403 | Chưa xác minh email |
| `ACCOUNT_INACTIVE` | 403 | Tài khoản inactive/banned |
| `FORBIDDEN` | 403 | Không đủ role |
| `ACCOUNT_LOCKED` | 423 | Tài khoản đang bị khóa |
| `CONFLICT` | 409 | Email đã tồn tại |
| `RATE_LIMIT_EXCEEDED` | 429 | Vượt giới hạn request |

---

# Phần XIII — CORS, CSRF và email bất đồng bộ

## 54. CORS

`CorsConfig` cho phép origin cấu hình bởi `app.cors.allowed-origins`.

```java
configuration.setAllowCredentials(true);
```

Điều này cần thiết để trình duyệt gửi refresh cookie giữa frontend và backend.

Frontend cũng phải gọi request với credentials, ví dụ Axios `withCredentials: true`.

---

## 55. CSRF

SecurityConfig hiện disable CSRF:

```java
csrf(csrf -> csrf.disable())
```

Access token đi trong Authorization header, nhưng refresh token nằm trong cookie. `SameSite=Lax` giúp giảm một phần rủi ro CSRF cho cookie.

Trong hệ thống production có yêu cầu bảo mật cao hơn, có thể xem xét CSRF token riêng cho các endpoint dùng cookie như refresh/logout.

---

## 56. `@Async` cho email

`ResendSmtpEmailService` dùng:

```java
@Async("emailTaskExecutor")
```

`AsyncConfig` tạo thread pool:

```text
core threads = 2
max threads = 4
queue = 100
```

Luồng:

```text
HTTP thread
→ lưu user/OTP
→ gửi công việc email vào queue
→ tiếp tục trả response

email thread
→ kết nối SMTP
→ gửi email
```

Nếu SMTP chưa cấu hình, service hiện ghi log và bỏ qua email thay vì làm đăng ký thất bại.

---

# Phần XIV — Luồng Admin tạo user

## 57. Khác với người dùng tự đăng ký

### Tự đăng ký

```text
role = TRAINEE
status = pending_verify
password do user nhập
gửi OTP
```

### Admin tạo user

```text
role/status do admin chọn
backend sinh mật khẩu ngẫu nhiên
không nhập avatar/password/bio trong form admin
user dùng Forgot Password để tự đặt mật khẩu
```

Admin create không phải `/auth/register`; nó là `/admin/users` và chỉ ADMIN được phép ghi theo SecurityConfig.

---

# Phần XV — Các điểm cần đặc biệt chú ý

## 58. Access JWT chưa bị revoke ngay

Logout, reset password và change password đều revoke refresh token, nhưng không revoke access token hiện tại.

```text
Access token còn hiệu lực
→ vẫn gọi API được đến exp
```

Đây là trade-off của stateless JWT. Có thể giảm rủi ro bằng access-token TTL ngắn hoặc thêm token-version/blacklist.

## 59. Rate limit hiện ở RAM

`AuthRateLimitFilter` dùng `ConcurrentHashMap`:

```text
Restart backend → mất counters
Nhiều instance → counters không chia sẻ
```

Production nhiều instance thường dùng Redis hoặc database/shared gateway.

## 60. `X-Forwarded-For`

Controller/filter lấy IP đầu tiên trong `X-Forwarded-For` nếu có. Cách này chỉ an toàn khi backend đứng sau reverse proxy đáng tin và proxy kiểm soát header.

## 61. `isEmailVerified()`

Logic hiện tại:

```java
emailVerifiedAt != null
OR status != pending_verify
```

Vì vậy `inactive` và `banned` cũng được xem là đã xác minh email. Login vẫn bị chặn bởi status, nhưng cần hiểu hai khái niệm “verified” và “active” không hoàn toàn giống nhau.

## 62. Basic authentication mode

Dự án còn hỗ trợ Basic mode cho phát triển. `DatabaseUserDetailsService` lấy user theo email và tạo authority `ROLE_<role>`.

JWT mode mới là luồng chính hiện tại. Khi học hoặc test, cần kiểm tra đúng profile/config để không nhầm Basic authentication với login API phát hành JWT.

---

# Phần XVI — Tóm tắt để ghi nhớ

## 63. Đăng ký

```text
POST /register
→ validate DTO
→ normalize email
→ kiểm tra trùng
→ BCrypt password
→ users(pending_verify)
→ BCrypt OTP
→ otp_verifications
→ gửi email
```

## 64. Xác minh

```text
POST /verify-email
→ tìm OTP mới nhất
→ kiểm tra expiry/attempts
→ BCrypt.matches
→ email_verified_at = now
→ status = active
```

## 65. Đăng nhập

```text
POST /login
→ tìm email
→ kiểm tra locked_until
→ kiểm tra status
→ BCrypt.matches password
→ reset failed attempts
→ login_history
→ access JWT
→ refresh cookie
```

## 66. Gọi API protected

```text
Authorization: Bearer JWT
→ verify signature/exp
→ roles → ROLE_...
→ SecurityContext
→ kiểm tra authenticated/role
```

## 67. Refresh

```text
refresh cookie
→ SHA-256
→ tìm DB
→ revoke token cũ
→ tạo cặp token mới
```

## 68. Logout

```text
revoke refresh token
→ xóa cookie
→ access JWT sống đến exp
```

## 69. Quên mật khẩu

```text
raw reset token gửi email
SHA-256 lưu DB
→ reset password
→ BCrypt password mới
→ revoke all refresh token
```

---

# Phần XVII — Danh sách source nên mở khi học

## Database

```text
src/main/resources/db/migration/V0__auth_database_baseline.sql
src/main/resources/db/migration/V2__identity_auth_support.sql
src/main/resources/db/migration/V3__auth_session_foundation.sql
src/main/resources/db/migration/V4__complete_auth_foundation.sql
```

## Entity và Repository

```text
src/main/java/com/smartlearnly/backend/user/entity/UserAccount.java
src/main/java/com/smartlearnly/backend/user/repository/UserRepository.java
src/main/java/com/smartlearnly/backend/auth/entity/OtpVerification.java
src/main/java/com/smartlearnly/backend/auth/entity/RefreshToken.java
src/main/java/com/smartlearnly/backend/auth/entity/PasswordResetToken.java
src/main/java/com/smartlearnly/backend/auth/entity/LoginHistory.java
src/main/java/com/smartlearnly/backend/auth/repository/
```

## API và nghiệp vụ

```text
src/main/java/com/smartlearnly/backend/auth/controller/AuthController.java
src/main/java/com/smartlearnly/backend/auth/service/AuthService.java
src/main/java/com/smartlearnly/backend/auth/service/AuthSessionService.java
src/main/java/com/smartlearnly/backend/auth/service/JwtTokenService.java
src/main/java/com/smartlearnly/backend/auth/service/GoogleIdTokenService.java
```

## Security

```text
src/main/java/com/smartlearnly/backend/common/config/SecurityConfig.java
src/main/java/com/smartlearnly/backend/common/config/JwtRolesConverter.java
src/main/java/com/smartlearnly/backend/common/security/SecurityContextAuthenticatedUserResolver.java
src/main/java/com/smartlearnly/backend/common/ratelimit/AuthRateLimitFilter.java
```

## Error và email

```text
src/main/java/com/smartlearnly/backend/common/exception/GlobalExceptionHandler.java
src/main/java/com/smartlearnly/backend/common/exception/ErrorCode.java
src/main/java/com/smartlearnly/backend/auth/service/ResendSmtpEmailService.java
src/main/java/com/smartlearnly/backend/common/config/AsyncConfig.java
src/main/java/com/smartlearnly/backend/common/config/CorsConfig.java
```

## Unit tests đối chiếu

```text
src/test/java/com/smartlearnly/backend/auth/service/AuthServiceTest.java
src/test/java/com/smartlearnly/backend/auth/service/AuthSessionServiceTest.java
src/test/java/com/smartlearnly/backend/common/security/SecurityContextAuthenticatedUserResolverTest.java
src/test/java/com/smartlearnly/backend/common/ratelimit/AuthRateLimitFilterTest.java
```

Các test quan trọng đã có gồm:

```text
register tạo pending trainee và OTP
pending user bị từ chối login
login đúng tạo session và xóa số lần sai
sai mật khẩu 5 lần làm khóa tài khoản
refresh token chỉ lưu hash
rotate revoke token cũ
OTP sai làm tăng attempts
OTP đúng kích hoạt user
forgot password không tiết lộ email không tồn tại
reset token hết hạn bị từ chối
```

---

# Phụ lục — Từ điển thuật ngữ nhanh

| Thuật ngữ | Giải thích ngắn |
|---|---|
| Bean | Object do Spring tạo và quản lý |
| Dependency Injection | Spring truyền dependency vào constructor |
| DTO | Object nhận/trả dữ liệu API |
| Entity | Object ánh xạ với bảng database |
| Repository | Tầng truy cập database |
| Service | Tầng xử lý business rule |
| Controller | Tầng nhận HTTP request và trả response |
| Transaction | Nhóm thao tác DB cùng commit hoặc rollback |
| Authentication | Xác định người dùng là ai |
| Authorization | Kiểm tra người dùng có quyền gì |
| JWT | Token tự chứa claims và có chữ ký |
| Access token | Token ngắn hạn dùng gọi API |
| Refresh token | Token dài hạn dùng cấp access token mới |
| Claim | Một trường dữ liệu bên trong JWT |
| Authority | Quyền Spring Security dùng để kiểm tra truy cập |
| BCrypt | Thuật toán hash chậm cho password/OTP |
| SHA-256 | Hàm hash dùng tra cứu token ngẫu nhiên |
| Soft delete | Đánh dấu `deleted_at` thay vì xóa vật lý |
| CORS | Quy tắc cho phép frontend origin gọi backend |
| CSRF | Tấn công lợi dụng trình duyệt tự gửi cookie |
