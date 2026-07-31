package com.smartlearnly.backend.payment.sepay;

import com.smartlearnly.backend.common.api.ApiResponse;
import com.smartlearnly.backend.common.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/sepay-events")
@Tag(name = "SePay Events", description = "Admin SePay webhook event monitoring")
@SecurityRequirement(name = "bearerAuth")
public class SePayAdminController {
    private static final int MAX_PAGE_SIZE = 100;

    private final SePayWebhookEventRepository sePayWebhookEventRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TMO')")
    @Operation(summary = "List SePay webhook events")
    public ApiResponse<PageResponse<SePayWebhookEventResponse>> listEvents(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Size(max = 30) String status
    ) {
        int pageSize = Math.min(size, MAX_PAGE_SIZE);
        int offset = page * pageSize;
        long totalItems = sePayWebhookEventRepository.countEvents(status);
        List<SePayWebhookEventResponse> items =
                sePayWebhookEventRepository.findEvents(status, pageSize, offset);
        int totalPages = pageSize == 0 ? 0 : (int) Math.ceil((double) totalItems / pageSize);

        return ApiResponse.success(
                "SePay events loaded successfully",
                new PageResponse<>(items, page, pageSize, totalItems, totalPages)
        );
    }
}
