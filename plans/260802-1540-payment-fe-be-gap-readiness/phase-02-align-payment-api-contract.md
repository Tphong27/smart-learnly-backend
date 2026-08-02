# Phase 02 - Align payment API contract

## Context Links
- FE `src/features/checkout/pages/AdminTransactionsPage.jsx`
- BE `src/main/java/com/smartlearnly/backend/commerce/controller/TransactionController.java`
- BE `src/main/java/com/smartlearnly/backend/commerce/dto/TransactionFilterOptionsResponse.java`

## Overview
- Priority: High
- Current status: Completed
- Brief description: Đã bổ sung route payment còn thiếu để khớp contract FE đang dùng.

## Key Insights
- Dữ liệu filter options đã sẵn từ repository/service.
- Chỉ cần expose route đúng path FE gọi.

## Requirements
- Functional requirements:
  - Add `GET /api/v1/transactions/filter-options`
  - Return `statuses`, `paymentGateways`, `currencies`
- Non-functional requirements:
  - Keep code small, readable, no extra abstractions
  - Preserve existing API response wrapper pattern

## Architecture
- Controller delegates trực tiếp sang `transactionQueryService.getFilterOptions()`.
- Tests bao phủ authorization và response contract.

## Related Code Files
- List of files to modify:
  - `src/main/java/com/smartlearnly/backend/commerce/controller/TransactionController.java`
  - `src/test/java/com/smartlearnly/backend/commerce/service/TransactionQueryServiceTest.java`
- List of files to create:
  - `src/test/java/com/smartlearnly/backend/commerce/controller/TransactionControllerTest.java`
- List of files to delete:
  - None

## Implementation Steps
1. Thêm controller mapping mới.
2. Thêm service test cho role/data.
3. Thêm controller test cho authz/contract.
4. Chạy test liên quan.

## Todo List
- [x] Add controller endpoint
- [x] Add service test
- [x] Add controller test
- [x] Run tests

## Success Criteria
- FE admin transactions page có thể load filter options từ BE.
- Endpoint trả HTTP 200 cho ADMIN/TMO, 403 cho trainee, 401 khi anonymous.

## Risk Assessment
- Nếu project security config khác kỳ vọng, controller test có thể cần mock service ở cấp integration.

## Security Considerations
- Không expose filter options cho trainee.

## Next Steps
- Sau khi pass test, review nhanh rồi báo kết quả cho user.
