# Payment FE/BE gap readiness plan

## Overview
- Status: Completed
- Priority: High
- Goal: đối chiếu flow payment frontend với backend, bổ sung API còn thiếu đang được FE dùng thực tế, rồi xác nhận bằng test.

## Phases
1. Completed - Confirm FE contract and gap matrix
   - FE đang dùng `GET /transactions/filter-options` ở trang admin transactions.
   - BE đã có `TransactionQueryService#getFilterOptions()` nhưng chưa expose controller endpoint.
2. Completed - Align API contract
   - Đã thêm endpoint `GET /api/v1/transactions/filter-options` cho ADMIN/TMO.
3. Completed - Validate
   - Đã thêm test cho controller/service/repository và chạy test liên quan.

## Key dependencies
- FE: `src/features/checkout/pages/AdminTransactionsPage.jsx`
- FE service: `src/services/admin-monitoring.service.js`
- BE controller: `src/main/java/com/smartlearnly/backend/commerce/controller/TransactionController.java`
- BE service: `src/main/java/com/smartlearnly/backend/commerce/service/TransactionQueryService.java`
- BE tests: `src/test/java/com/smartlearnly/backend/commerce/service/TransactionQueryServiceTest.java`

## Success criteria
- FE-used endpoint tồn tại ở BE.
- Chỉ ADMIN/TMO truy cập được filter options.
- Test liên quan pass.
- Không thay đổi contract hiện có của các endpoint payment khác.
