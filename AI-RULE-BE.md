# AI Rule Backend - Smart Learnly

## Mục tiêu

1. File này là quy tắc bắt buộc khi AI hoặc developer chỉnh sửa backend Smart Learnly.
2. Luôn viết code thật, đúng kiến trúc Spring Boot hiện tại, không tạo mock tạm để qua test/build.
3. Luôn ưu tiên YAGNI, KISS, DRY.
4. Luôn bám theo domain và package hiện có, không tạo cấu trúc mới nếu chưa cần.
5. Không áp dụng rule Servlet/JSP/DAO cũ cho dự án này; backend hiện tại là Spring Boot REST API.

## Tech stack

1. Dự án dùng Java 17, Spring Boot 4, Maven, PostgreSQL, Flyway.
2. Dự án dùng Spring Data JPA, Spring Security, OAuth2 Resource Server, Jakarta Validation, Spring Mail, Springdoc OpenAPI, Lombok.
3. Package gốc là `com.smartlearnly.backend`.
4. Lệnh kiểm tra chính:

```bash
mvn test
mvn -DskipTests package
```

5. Nếu repo có Maven wrapper thì ưu tiên:

```bash
./mvnw test
./mvnw -DskipTests package
```

## Package và domain

1. Luôn tách code theo domain nghiệp vụ.
2. Các domain hiện có gồm: `auth`, `course`, `commerce`, `enrollment`, `classroom`, `file`, `user`, `common`, `payment`, `learning`.
3. Trong mỗi domain, ưu tiên cấu trúc:

```text
<domain>/
├── controller/
├── service/
├── repository/
├── dto/
└── entity/
```

4. Code dùng chung đặt trong `common`.
5. Không đặt business logic vào `controller`.
6. Không đặt query persistence phức tạp vào `controller`.
7. Không tạo package `utils` chung chung nếu có thể đặt vào domain cụ thể.

## Jakarta và import

1. Luôn dùng `jakarta.*`, không dùng `javax.*`.
2. Entity dùng `jakarta.persistence.*`.
3. Validation dùng `jakarta.validation.*`.
4. Không import class unused.
5. Không dùng wildcard import.

## Entity

1. Entity luôn là JPA entity, dùng `@Entity`, `@Table` khi map bảng rõ ràng.
2. Luôn dùng Lombok hợp lý, thường là `@Getter`, `@Setter`, `@NoArgsConstructor`.
3. Không dùng primitive type cho field có thể null; ưu tiên wrapper như `Boolean`, `Integer`, `Long`.
4. ID ưu tiên `UUID` như pattern hiện tại.
5. Timestamp ưu tiên `java.time.Instant`, không dùng `java.sql.Date` trong dự án này nếu không có yêu cầu legacy rõ ràng.
6. Field audit nên dùng `createdAt`, `updatedAt`, cập nhật qua `@PrePersist`, `@PreUpdate` khi phù hợp.
7. Relationship JPA chỉ dùng khi có lợi ích rõ ràng; mặc định dùng `FetchType.LAZY`.
8. Không expose entity trực tiếp ra API response.
9. Không đặt logic nghiệp vụ phức tạp trong entity.

Ví dụ entity đúng hướng:

```java
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "categories", schema = "public")
public class Category {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "is_active", nullable = false)
    private Boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (active == null) {
            active = true;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
```

## DTO và API response

1. Request/Response DTO đặt trong package `dto` của domain.
2. Ưu tiên Java `record` cho DTO immutable.
3. Request DTO phải dùng validation annotation nếu có input từ user.
4. Response DTO không được trả entity trực tiếp.
5. Response chuẩn phải bọc bằng `ApiResponse<T>`.
6. Danh sách có phân trang dùng `PageResponse<T>` nếu endpoint hỗ trợ paging.
7. Field name trong DTO phải ổn định vì frontend phụ thuộc vào contract.
8. Không đổi response contract nếu chưa kiểm tra frontend.

Ví dụ DTO:

```java
public record CreateCategoryRequest(
        @NotBlank
        @Size(max = 150)
        String name,
        String slug,
        UUID parentId,
        Boolean active,
        Integer sortOrder
) {
}
```

Ví dụ response wrapper:

```java
return ApiResponse.success("Category loaded successfully", categoryService.get(categoryId));
```

## Controller

1. Controller luôn dùng `@RestController`.
2. Endpoint luôn bắt đầu bằng `/api/v1`.
3. Dùng `@RequestMapping` ở class để gom resource cùng domain.
4. Dùng `@GetMapping`, `@PostMapping`, `@PatchMapping`, `@DeleteMapping` đúng HTTP verb.
5. Dùng `@Valid` cho `@RequestBody`.
6. Dùng `@PathVariable` cho resource id.
7. Dùng `@RequestParam` cho filter/search/pagination.
8. Dùng `@PreAuthorize` để bảo vệ role/method nếu endpoint không public.
9. Dùng `@Tag`, `@Operation`, `@SecurityRequirement` cho OpenAPI khi endpoint quan trọng hoặc cần auth.
10. Create endpoint nên trả `ResponseEntity.created(...)` nếu tạo resource mới.
11. Controller chỉ điều phối request/response, không chứa business logic.

Ví dụ controller:

```java
@Validated
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin/categories")
@Tag(name = "Admin Categories", description = "Administrator course-category management APIs.")
@SecurityRequirement(name = "bearerAuth")
public class AdminCategoryController {
    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "List categories")
    public ApiResponse<List<CategoryResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active
    ) {
        return ApiResponse.success(
                "Categories loaded successfully",
                categoryService.list(keyword, active, null)
        );
    }

    @PostMapping
    @Operation(summary = "Create category")
    public ResponseEntity<ApiResponse<CategoryResponse>> create(
            @Valid @RequestBody CreateCategoryRequest request
    ) {
        CategoryResponse category = categoryService.create(request);
        return ResponseEntity
                .created(URI.create("/api/v1/admin/categories/" + category.id()))
                .body(ApiResponse.success("Category created successfully", category));
    }
}
```

## Service

1. Business logic luôn nằm trong service.
2. Service dùng `@Service` và constructor injection qua `@RequiredArgsConstructor`.
3. Thao tác ghi dữ liệu phải dùng `@Transactional` khi có nhiều bước hoặc cần atomicity.
4. Query read-only phức tạp có thể dùng `@Transactional(readOnly = true)`.
5. Không trả entity từ service cho controller nếu controller là API public/admin response; map sang DTO.
6. Không nuốt exception rồi trả `null` hoặc `false` không rõ nghĩa.
7. Không hard-code role/status string rải rác; dùng enum/constant nếu đã có.
8. Với payment/order/enrollment, phải xử lý idempotency và trạng thái hợp lệ.

Ví dụ service:

```java
@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public CategoryResponse get(UUID categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Category not found"));
        return toResponse(category);
    }
}
```

## Repository

1. Repository dùng Spring Data JPA, không tự viết JDBC DAO kiểu cũ.
2. Repository interface extend `JpaRepository` hoặc repository base phù hợp.
3. Method query phải đặt tên rõ nghĩa.
4. Query custom dùng `@Query` khi derived query quá dài hoặc khó đọc.
5. Không đưa business rule vào repository.
6. Với query lock/update quan trọng, dùng annotation phù hợp và service transaction rõ ràng.

## Database và migration

1. Thay đổi schema phải có Flyway migration.
2. Không chỉnh DB thủ công rồi bỏ qua migration.
3. Migration phải đặt tên rõ nghĩa, theo convention hiện có của dự án.
4. Không sửa migration đã chạy ở môi trường khác nếu không có lý do rất rõ ràng; tạo migration mới.
5. Không lưu dữ liệu nhạy cảm trong migration.
6. Column name dùng snake_case, Java field dùng camelCase.

## Security

1. Không commit secret, API key, mật khẩu thật, token, private key.
2. Endpoint cần auth phải được cấu hình qua Spring Security và/hoặc `@PreAuthorize`.
3. Không tin input từ frontend; luôn validate request ở backend.
4. Không trả thông tin nhạy cảm như password hash, refresh token, secret trong response.
5. Webhook payment phải validate payload, chống duplicate, chống replay nếu có dữ liệu hỗ trợ.
6. CORS/security config phải thay đổi thận trọng, không mở toàn bộ nếu không cần.

## Error handling

1. Dùng exception chuẩn của dự án như `BusinessException`, `ErrorCode` nếu có sẵn.
2. Không trả string lỗi tùy ý từ controller.
3. Không catch exception rồi chỉ `System.out.println`.
4. Error response phải đi qua global exception handling hiện có.
5. Message lỗi nên rõ ràng nhưng không lộ thông tin nhạy cảm.
6. Với conflict nghiệp vụ, dùng lỗi conflict/business phù hợp thay vì generic 500.

## Validation

1. Request body phải có validation annotation khi có constraint.
2. Dùng `@NotNull`, `@NotBlank`, `@Size`, `@Email`, `@PositiveOrZero` đúng ngữ nghĩa.
3. Validate UUID/resource ownership ở service.
4. Validate trạng thái trước khi chuyển trạng thái, ví dụ order chỉ cancel khi pending.
5. Không dựa vào frontend validation để bảo vệ backend.

## OpenAPI

1. Endpoint public/admin quan trọng nên có `@Operation(summary = "...")`.
2. Controller nên có `@Tag` rõ domain.
3. Endpoint cần bearer auth nên có `@SecurityRequirement(name = "bearerAuth")` ở class hoặc method.
4. Nếu có response đặc biệt như 201, 409, 422, nên khai báo `@ApiResponses`.

## Pagination, search, filter

1. Endpoint list lớn phải hỗ trợ pagination nếu dữ liệu có thể tăng nhiều.
2. Request param dùng tên rõ ràng: `page`, `size`, `keyword`, `status`, `active`, `sort`.
3. Page index phải thống nhất với frontend; nếu dùng zero-based thì ghi rõ trong service/DTO.
4. Response phân trang dùng `PageResponse<T>`.
5. Không trả toàn bộ bảng nếu endpoint dành cho admin list lớn.

## Payment, order, enrollment

1. Không tự ý đổi enum trạng thái nếu frontend đang phụ thuộc.
2. Luôn kiểm tra enum hiện có trước khi thêm status mới.
3. Payment webhook phải idempotent.
4. Grant enrollment sau thanh toán phải tránh duplicate.
5. Cancel order phải kiểm tra owner và trạng thái.
6. Không tạo invoice/transaction giả để qua flow.
7. Nếu response cần field cho frontend, bổ sung DTO chính thức thay vì để frontend đoán.

## File upload

1. Upload phải validate MIME type, size, extension theo service hiện có.
2. Không tin `Content-Type` từ client nếu chưa kiểm chứng.
3. Không ghi file ngoài storage path được cấu hình.
4. Không expose đường dẫn local tuyệt đối trong API response.

## Testing và verification

1. Sau sửa backend, chạy test nếu có thể:

```bash
mvn test
```

2. Nếu cần kiểm tra compile/package:

```bash
mvn -DskipTests package
```

3. Nếu test fail, không bỏ qua để báo pass.
4. Nếu lỗi do môi trường hoặc test ngoài scope, báo rõ command và output liên quan.
5. Với endpoint mới/sửa, kiểm tra ít nhất: success, validation error, unauthorized/forbidden nếu endpoint cần auth, not found/conflict nếu có.
6. Với migration mới, đảm bảo app start hoặc test context load nếu có thể.

## Quy tắc khi sửa bug sau merge

1. Luôn đọc error đầy đủ trước khi sửa.
2. Luôn tìm root cause, không chỉ sửa symptom.
3. Kiểm tra duplicate class/method/bean/import sau merge conflict.
4. Không xóa code của domain khác nếu không thuộc scope.
5. Không đổi API contract khi chưa kiểm tra frontend hoặc tài liệu sprint.

## Không được làm

1. Không tạo DAO JDBC thủ công kiểu cũ cho dự án này.
2. Không dùng Servlet/JSP controller cho backend hiện tại.
3. Không expose entity trực tiếp ra REST response.
4. Không commit secret hoặc config nhạy cảm.
5. Không disable security để test cho nhanh.
6. Không ignore failing tests để báo hoàn thành.
7. Không tạo dữ liệu giả trong service để thay backend thật.
8. Không sửa rộng ngoài scope user yêu cầu.
