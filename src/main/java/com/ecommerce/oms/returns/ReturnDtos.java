package com.ecommerce.oms.returns;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ReturnDtos {

    private ReturnDtos() {
    }

    public record ReturnLine(@NotNull Long orderItemId, @NotNull @Min(1) Integer quantity) {
    }

    public record CreateReturnRequest(@NotEmpty List<@Valid ReturnLine> items, @NotBlank @Size(max = 500) String reason) {
    }

    public record ResolveReturnRequest(@Size(max = 500) String note) {
    }

    public record ReturnItemResponse(Long orderItemId, String sku, int quantity) {
    }

    public record ReturnResponse(Long id, Long orderId, ReturnStatus status, String reason, BigDecimal refundAmount,
            List<ReturnItemResponse> items, Instant createdAt, Instant resolvedAt, String resolvedBy,
            String resolutionNote) {
        static ReturnResponse from(ReturnRequest r) {
            return new ReturnResponse(r.getId(), r.getCustomerOrder().getId(), r.getStatus(), r.getReason(),
                    r.getRefundAmount(),
                    r.getItems().stream().map(i -> new ReturnItemResponse(i.getOrderItem().getId(),
                            i.getOrderItem().getSku(), i.getQuantity())).toList(),
                    r.getCreatedAt(), r.getResolvedAt(), r.getResolvedBy(), r.getResolutionNote());
        }
    }
}
