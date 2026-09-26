package com.ecommerce.oms.order;

import com.ecommerce.oms.payment.PaymentSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record AddressDto(
            @NotBlank @Size(max = 200) String line1,
            @Size(max = 200) String line2,
            @NotBlank @Size(max = 100) String city,
            @NotBlank @Size(max = 100) String state,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9 -]{3,12}") String postalCode,
            @NotBlank @Pattern(regexp = "[A-Z]{2}", message = "must be a 2-letter ISO country code") String country) {

        public AddressDto {
            // Normalised before bean validation runs, so "in" is accepted as "IN".
            country = country == null ? null : country.trim().toUpperCase(Locale.ROOT);
        }

        public Address toAddress() {
            return new Address(line1, line2, city, state, postalCode, country);
        }

        public static AddressDto from(Address a) {
            return a == null ? null
                    : new AddressDto(a.getLine1(), a.getLine2(), a.getCity(), a.getState(), a.getPostalCode(), a.getCountry());
        }
    }

    public record CheckoutRequest(
            @NotNull @Valid AddressDto shippingAddress,
            @NotBlank @Size(max = 100) String paymentToken,
            @Size(max = 32) String discountCode) {
    }

    public record CancelOrderRequest(@Size(max = 500) String reason) {
    }

    public record StatusUpdateRequest(@NotNull OrderStatus status, @Size(max = 500) String note) {
    }

    public record AllocationResponse(Long warehouseId, String warehouseCode, int quantity) {
    }

    public record OrderItemResponse(Long id, Long productId, String sku, String productName, BigDecimal unitPrice,
            int quantity, BigDecimal taxRate, BigDecimal lineSubtotal, BigDecimal lineDiscount, BigDecimal lineTax,
            BigDecimal lineTotal, int returnedQuantity, BigDecimal refundedAmount, List<AllocationResponse> allocations) {
    }

    public record StatusHistoryResponse(OrderStatus fromStatus, OrderStatus toStatus, String actor, String note,
            Instant changedAt) {
    }

    public record OrderResponse(Long id, String orderNumber, OrderStatus status, List<OrderItemResponse> items,
            BigDecimal subtotal, BigDecimal discountTotal, BigDecimal taxTotal, BigDecimal grandTotal,
            String discountCode, AddressDto shippingAddress, List<StatusHistoryResponse> history,
            PaymentSummary payment, Instant placedAt, Instant updatedAt, Instant deliveredAt) {
    }

    public record OrderSummaryResponse(Long id, String orderNumber, OrderStatus status, BigDecimal grandTotal,
            int itemCount, Instant placedAt) {
    }
}
