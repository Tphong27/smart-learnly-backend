# Phase 01 - Confirm FE contract and gap matrix

## Context Links
- FE `src/features/checkout/pages/AdminTransactionsPage.jsx`
- FE `src/services/admin-monitoring.service.js`
- BE `src/main/java/com/smartlearnly/backend/commerce/controller/TransactionController.java`
- BE `src/main/java/com/smartlearnly/backend/commerce/service/TransactionQueryService.java`

## Overview
- Priority: High
- Current status: Completed
- Brief description: Xác nhận những API payment FE đang gọi thật và map với backend hiện tại.

## Key Insights
- FE admin transactions gọi `GET /transactions/filter-options` ngay khi mount page.
- Backend đã có logic lấy filter options nhưng chưa có route controller tương ứng.
- FE có service `GET /reconciliation-runs` nhưng chưa thấy UI đang dùng, nên chưa ưu tiên trong scope này.

## Requirements
- Functional: expose API filter options theo đúng dữ liệu BE hiện có.
- Non-functional: không phá vỡ các endpoint transaction đang chạy.

## Architecture
- `TransactionController` gọi `TransactionQueryService#getFilterOptions()`.
- Response bọc trong `ApiResponse<TransactionFilterOptionsResponse>`.

## Related Code Files
- Modify:
  - `src/main/java/com/smartlearnly/backend/commerce/controller/TransactionController.java`
  - `src/test/java/com/smartlearnly/backend/commerce/service/TransactionQueryServiceTest.java`
- Create:
  - `src/test/java/com/smartlearnly/backend/commerce/controller/TransactionControllerTest.java`
- Delete:
  - None

## Implementation Steps
1. Thêm `GET /api/v1/transactions/filter-options` trong controller.
2. Giữ cùng auth boundary với service: chỉ ADMIN/TMO.
3. Thêm unit test cho `getFilterOptions()`.
4. Thêm HTTP contract/security test cho controller endpoint.
5. Chạy compile/test liên quan.

## Todo List
- [x] Xác nhận FE call đang dùng thật
- [x] Xác nhận service BE đã có logic
- [x] Chốt scope implement tối thiểu

## Success Criteria
- Gap matrix chỉ còn 1 gap active cần sửa: `/transactions/filter-options`.

## Risk Assessment
- Rủi ro thấp; thay đổi nhỏ ở controller/test.
- Cần tránh mở quyền cho trainee.

## Security Considerations
- Endpoint phải tiếp tục yêu cầu authentication và chỉ cho ADMIN/TMO.

## Next Steps
- Sang phase 02 để code và test.
