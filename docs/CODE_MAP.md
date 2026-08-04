# Bản đồ mã nguồn Backend

Tài liệu này trả lời câu hỏi: **muốn sửa một nghiệp vụ thì mở file nào trước?**
Nó được cập nhật sau mỗi batch tổ chức lại mã nguồn.

## Cách đọc nhanh

- `controller`: nhận HTTP request, kiểm tra dữ liệu đầu vào và gọi service.
- `service`: xử lý nghiệp vụ và transaction.
- `repository`: đọc/ghi cơ sở dữ liệu.
- `dto`: cấu trúc request/response của API.
- `entity`: mô hình dữ liệu lưu trong cơ sở dữ liệu.
- `config`: cấu hình tích hợp hoặc framework.

## Luồng thanh toán và checkout

| Muốn sửa nghiệp vụ | File nên mở đầu tiên | File liên quan |
| --- | --- | --- |
| Tạo đơn checkout | `commerce/checkout/service/CheckoutService.java` | `CheckoutController.java`, `CheckoutRequest.java`, `CheckoutResponse.java` |
| Xác định khóa học/lớp và giá khi checkout | `commerce/checkout/service/CheckoutItemService.java` | entity/repository trong `commerce` |
| Xem hoặc hủy đơn | `commerce/order/controller/OrderController.java` | `commerce/order/service/OrderService.java` |
| Tra cứu giao dịch | `commerce/transaction/controller/TransactionController.java` | `commerce/transaction/service/TransactionQueryService.java` |
| Tạo hướng dẫn chuyển khoản SePay | `payment/sepay/service/DefaultSePayPaymentInstructionService.java` | `SePayPaymentInstructionService.java`, `config/SePayProperties.java` |
| Nhận webhook SePay | `payment/sepay/controller/SePayWebhookController.java` | `service/SePayWebhookSignatureVerifier.java`, `service/SePayWebhookService.java` |
| Ghép giao dịch ngân hàng với đơn | `payment/sepay/service/SePayPaymentMatchingService.java` | `service/SePayReconciliationService.java` |
| Tự đối soát giao dịch | `payment/sepay/service/SePayReconciliationScheduler.java` | `SePayReconciliationService.java`, `SePayTransactionClient.java` |

Đường dẫn Java đầy đủ bắt đầu từ:
`src/main/java/com/smartlearnly/backend/`.

## Xác thực, người dùng và cấu hình hệ thống

| Muốn sửa nghiệp vụ | File nên mở đầu tiên | File liên quan |
| --- | --- | --- |
| Đăng nhập email/mật khẩu | `auth/login/service/AuthLoginService.java` | `auth/session/controller/AuthSessionController.java` |
| Đăng ký và xác thực OTP | `auth/registration/service/AuthRegistrationService.java` | `auth/registration/{controller,dto,entity,repository}/` |
| Quên/đặt lại/đổi mật khẩu | `auth/password/service/AuthPasswordService.java` | `auth/password/{controller,dto,entity,repository}/` |
| Tạo, xoay hoặc thu hồi session | `auth/session/service/AuthSessionService.java` | `JwtTokenService.java`, `AuthSessionHttpSupport.java` |
| Đăng nhập Google | `auth/google/service/GoogleAuthService.java` | `GoogleIdTokenService.java`, `GoogleAuthController.java` |
| Xem/cập nhật hồ sơ cá nhân | `auth/profile/service/AuthProfileService.java` | `AuthProfileController.java`, `auth/profile/dto/` |
| Quản trị người dùng | `user/service/AdminUserService.java` | `AdminUserController.java`, `user/dto/` |
| Hồ sơ trainer công khai | `user/service/PublicTrainerProfileService.java` | `PublicTrainerController.java` |
| Cấu hình email | `admin/settings/controller/EmailSettingsController.java` | `SystemSettingsService.java` |
| Cấu hình Google OAuth/Meet | `admin/settings/controller/GoogleSettingsController.java` | `SystemSettingsService.java` |
| Cấu hình AI | `admin/settings/controller/AiSettingsController.java` | `SystemSettingsService.java` |
| Cấu hình/đối soát SePay | `admin/settings/controller/SePaySettingsController.java` | `payment/sepay/service/SePayReconciliationService.java` |

## Khóa học, curriculum, lớp học và ghi danh

| Muốn sửa nghiệp vụ | Mở file đầu tiên | File liên quan |
| --- | --- | --- |
| Catalog khóa học công khai | course/catalog/service/CourseQueryService.java | course/catalog/controller/CourseController.java |
| Danh mục khóa học | course/category/service/CategoryService.java | course/category/controller/ |
| Metadata và xuất bản khóa học | course/authoring/service/CourseAdminService.java | course/authoring/controller/AdminCourseController.java |
| Quyền đọc/sửa hoặc khóa truy cập khóa học | course/access/service/CourseAccessService.java | CourseAccessAdminService.java |
| Nội dung xem thử | course/preview/service/PreviewLessonService.java | course/preview/controller/ |
| Section/module master curriculum | curriculum/admin/service/CurriculumSectionAdminService.java | MasterCurriculumAccessService.java |
| Lesson/resource master curriculum | curriculum/admin/service/CurriculumLessonAdminService.java | curriculum/admin/controller/AdminCourseLessonController.java |
| Quản trị lớp | classroom/admin/service/ClassAdminService.java | classroom/admin/controller/AdminClassController.java |
| Lớp được phân công cho giảng viên | classroom/trainer/service/ClassTrainerService.java | classroom/trainer/controller/TrainerClassController.java |
| Phiên học, lịch tuần và Google Meet | classroom/schedule/service/ClassSessionScheduleService.java | ScheduleService.java, GoogleMeetService.java |
| Lịch khai giảng công khai | classroom/opening/service/OpeningScheduleService.java | classroom/opening/controller/OpeningScheduleController.java |
| Analytics lớp học | classroom/analytics/service/ClassAnalyticsService.java | classroom/analytics/repository/ClassAnalyticsRepository.java |

## Bản đồ domain hiện tại

| Domain | Trách nhiệm chính | Batch tổ chức |
| --- | --- | --- |
| `commerce`, `payment`, `invoice` | Đơn hàng, giao dịch, hóa đơn, SePay | Batch 1 |
| `auth`, `user`, phần quyền/settings | Xác thực, người dùng, quyền và cấu hình hệ thống | Batch 2 |
| `course`, `classroom`, `curriculum`, `enrollment` | Khóa học, lớp, giáo trình và ghi danh | Batch 3 |
| `learning`, `lessonprogress`, `test`, `question`, `flashtest`, `flashcard` | Học tập, bài kiểm tra và tiến độ | Batch 4 |
| `assignment`, `file`, `videoai` | Bài tập, nội dung, file và AI | Batch 5 |
| `notification`, `dashboard`, `admin` | Thông báo, báo cáo và quản trị | Batch 6 |
| `common` | Security, exception, API response, audit và tiện ích dùng chung | Batch 7 |

## Di chuyển đã hoàn thành

### Batch 0 — Checkout

| Đường dẫn cũ | Đường dẫn mới |
| --- | --- |
| `commerce/controller/OrderController.java` (endpoint checkout) | `commerce/checkout/controller/CheckoutController.java` |
| `commerce/dto/Checkout*.java` | `commerce/checkout/dto/Checkout*.java` |
| `commerce/service/CheckoutService.java` | `commerce/checkout/service/CheckoutService.java` |
| logic xác định item nằm trong `CheckoutService` | `commerce/checkout/service/CheckoutItemService.java` |
| `commerce/service/CheckoutServiceTest.java` | `commerce/checkout/service/CheckoutServiceTest.java` |

Endpoint `/api/v1/orders/checkout` và JSON contract được giữ nguyên.

### Batch 1 — Order, Transaction và SePay

| Đường dẫn cũ | Đường dẫn mới |
| --- | --- |
| `commerce/controller/OrderController.java` | `commerce/order/controller/OrderController.java` |
| `commerce/service/OrderService.java` | `commerce/order/service/OrderService.java` |
| `commerce/service/OrderExpirationScheduler.java` | `commerce/order/service/OrderExpirationScheduler.java` |
| `commerce/dto/Order*.java`, `SePayOrderSummaryResponse.java` | `commerce/order/dto/` |
| `commerce/controller/TransactionController.java` | `commerce/transaction/controller/TransactionController.java` |
| `commerce/service/TransactionQueryService.java` | `commerce/transaction/service/TransactionQueryService.java` |
| `commerce/dto/Transaction*.java`, `InvoiceResponse.java` | `commerce/transaction/dto/` |
| các class phẳng trong `payment/sepay/` | `payment/sepay/{controller,dto,repository,service,config}/` theo vai trò |

Các field `SePayProperties` không được dùng trong `SePayReconciliationService` và
`SePayWebhookSignatureVerifier` đã được xóa sau khi tìm kiếm toàn bộ source/test.
`reconciliationInterval` trong configuration properties được giữ ở loại
`FRAMEWORK_OR_DYNAMIC` vì Spring bind trực tiếp từ cấu hình.

### Batch 2 — Auth, User và System Settings

| Đường dẫn cũ | Đường dẫn mới |
| --- | --- |
| `auth/controller/AuthController.java` | năm controller trong `auth/{session,google,registration,password,profile}/controller/` |
| `auth/service/AuthService.java` | `AuthLoginService`, `GoogleAuthService`, `AuthRegistrationService`, `AuthPasswordService`, `AuthProfileService` và `AuthSessionService` |
| `auth/dto/*.java` | DTO đặt trong `auth/<feature>/dto/` theo nghiệp vụ |
| `auth/entity/OtpVerification.java` và repository | `auth/registration/{entity,repository}/` |
| `auth/entity/PasswordResetToken.java` và repository | `auth/password/{entity,repository}/` |
| `auth/entity/RefreshToken.java` và repository | `auth/session/{entity,repository}/` |
| `auth/service/GoogleIdTokenService.java` | `auth/google/service/GoogleIdTokenService.java` |
| `auth/service/JwtTokenService.java` | `auth/session/service/JwtTokenService.java` |
| `admin/settings/controller/AdminSettingsController.java` | `EmailSettingsController`, `GoogleSettingsController`, `AiSettingsController`, `SePaySettingsController` |

`EmailVerificationToken`, `EmailVerificationTokenRepository` và lớp cha
`AbstractAuthToken` được phân loại `CONFIRMED_UNUSED` rồi xóa: tìm kiếm toàn bộ
source/test chỉ thấy chính phần khai báo; luồng xác thực email đang dùng
`OtpVerification`. Migration cũ được giữ nguyên để không thay đổi lịch sử database.

`LoginHistory` và `LoginHistoryRepository` vẫn ở domain cha `auth` vì cả đăng
nhập email và Google đều dùng chung. `SystemSettingsService` vẫn là một service
trung tâm vì nhiều integration đọc cùng kho key/value; không tạo wrapper
pass-through cho từng nhóm cấu hình.

### Batch 3 — Course, Curriculum, Classroom và Enrollment

| Đường dẫn cũ | Đường dẫn mới |
| --- | --- |
| course/controller/CourseController.java và course/service/CourseQueryService.java | course/catalog/{controller,service}/ |
| category controller/service/dto/seed | course/category/ |
| preview controller/service/dto | course/preview/ |
| CourseAdminController, CourseAdminService, mapper và DTO metadata | course/authoring/{controller,service,mapper,dto}/ |
| course access controller/service/dto | course/access/ |
| CourseContentAdminService.java | ba service ở curriculum/admin/service/ |
| DTO section/module/lesson trong course/dto | curriculum/dto/ |
| ClassController.java | classroom/admin/controller/AdminClassController.java và classroom/trainer/controller/TrainerClassController.java |
| analytics, opening schedule, lịch tuần và Google Meet | classroom/{analytics,opening,schedule}/ |

CourseAdminService.audit(...) là CONFIRMED_UNUSED: tìm kiếm toàn bộ source/test
chỉ trả về phần khai báo. CourseContentAdminService được thay thế hoàn toàn bởi
ba service curriculum; đây không phải dead code. CoursePublicProjection,
ClassAdminProjection, entity và repository chính được giữ ở domain cha vì nhiều
feature còn dùng.

### Batch 4 — Learning, Progress, Test và Flashcard

| Đường dẫn cũ | Đường dẫn mới |
| --- | --- |
| `lessonprogress/controller/TraineeProgressController.java` | `lessonprogress/trainee/controller/TraineeProgressController.java` |
| `lessonprogress/service/TraineeProgressService.java` và DTO tiến độ học viên | `lessonprogress/trainee/{service,dto}/` |
| `learning/controller/LearningController.java` | `learning/content/controller/LearningController.java` |
| `learning/service/LearningContentService.java` và DTO dữ liệu học | `learning/content/{service,dto}/` |
| `test/controller/{TestAttemptController,StudentTestAnswerController}.java` | `test/attempt/controller/` |
| `test/service/{TestAttemptService,StudentTestAnswerService}.java` và DTO | `test/attempt/{service,dto}/` |
| `test/controller/{TestController,TestQuestionController,AdminTestQuestionController}.java` | `test/definition/controller/` |
| `test/service/{TestService,TestQuestionService}.java` và DTO | `test/definition/{service,dto}/` |
| `test/controller/TestMonitorController.java` | `test/monitor/controller/TestMonitorController.java` |
| `flashcard/controller/FlashcardLearningController.java` | `flashcard/learning/controller/FlashcardLearningController.java` |
| `flashcard/service/FlashcardLearningService.java`, DTO và test | `flashcard/learning/{service,dto}/` |

`LessonProgress` cùng repository, entity/repository của `test` và entity/repository
của `question` được giữ ở domain cha vì nhiều luồng học, chấm bài, flashcard
staging và AI cùng dùng. Question bank/AI authoring không di chuyển trước sang
Batch 4; chúng thuộc Batch 5 để không trộn refactor luồng học với luồng biên soạn.

`LearningContentService` đã xóa dependency `EnrollmentAccessService` không dùng
và tám helper mapping cũ không còn consumer (`toSectionResponseWithoutProgress`,
`toPreviewSectionResponse`, `orderedLessons`, `toLessonResponse`,
`isPublishedLesson`, `hasLessons`, `lessonStatus`, `calculateStats`). Tìm kiếm
toàn source/test chỉ trả về phần khai báo. Khối code comment-out legacy lấy
flashcard lesson cũng được xóa; API học hiện dùng service learning mới.

## Trạng thái refactor

| Batch | Trạng thái |
| --- | --- |
| 0 — Baseline và checkout | Hoàn thành: compile, Spring context và 753/753 test đạt |
| 1 — Commerce và Payment | Hoàn thành: 71 test mục tiêu và full suite 753/753 đạt |
| 2 — Auth, User và System Settings | Hoàn thành: 38 test mục tiêu, Spring context, 2 route contract test và full suite 755/755 đạt |
| 3 — Course, Curriculum, Classroom và Enrollment | Hoàn thành: import/package cũ không còn consumer, targeted test đạt và full suite 755/755 đạt |
| 4 — Learning, Progress, Test và Flashcard | Hoàn thành: targeted test đạt và full suite 755/755 đạt |
| 5–7 | Chưa bắt đầu |

## Quy tắc cập nhật tài liệu

Sau mỗi batch phải bổ sung:

1. Bảng đường dẫn cũ sang mới.
2. File/hàm đã xóa cùng bằng chứng không còn consumer.
3. Candidate chưa chắc chắn và lý do giữ lại.
4. Kết quả compile, test và contract check.
