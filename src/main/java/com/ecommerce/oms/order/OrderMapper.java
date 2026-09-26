package com.ecommerce.oms.order;

import com.ecommerce.oms.order.OrderDtos.AddressDto;
import com.ecommerce.oms.order.OrderDtos.AllocationResponse;
import com.ecommerce.oms.order.OrderDtos.OrderItemResponse;
import com.ecommerce.oms.order.OrderDtos.OrderResponse;
import com.ecommerce.oms.order.OrderDtos.OrderSummaryResponse;
import com.ecommerce.oms.order.OrderDtos.StatusHistoryResponse;
import com.ecommerce.oms.payment.PaymentService;
import org.springframework.stereotype.Component;

/** Must be called inside a transaction (touches lazy associations). */
@Component
public class OrderMapper {

    private final PaymentService paymentService;

    public OrderMapper(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    public OrderResponse toResponse(CustomerOrder order) {
        return new OrderResponse(order.getId(), order.getOrderNumber(), order.getStatus(),
                order.getItems().stream().map(OrderMapper::toItem).toList(),
                order.getSubtotal(), order.getDiscountTotal(), order.getTaxTotal(), order.getGrandTotal(),
                order.getDiscountCode(), AddressDto.from(order.getShippingAddress()),
                order.getHistory().stream().map(h -> new StatusHistoryResponse(h.getFromStatus(), h.getToStatus(),
                        h.getActor(), h.getNote(), h.getChangedAt())).toList(),
                paymentService.findSummary(order.getId()).orElse(null),
                order.getPlacedAt(), order.getUpdatedAt(), order.getDeliveredAt());
    }

    public OrderSummaryResponse toSummary(CustomerOrder order) {
        int itemCount = order.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
        return new OrderSummaryResponse(order.getId(), order.getOrderNumber(), order.getStatus(),
                order.getGrandTotal(), itemCount, order.getPlacedAt());
    }

    private static OrderItemResponse toItem(OrderItem item) {
        return new OrderItemResponse(item.getId(), item.getProduct().getId(), item.getSku(), item.getProductName(),
                item.getUnitPrice(), item.getQuantity(), item.getTaxRate(), item.getLineSubtotal(),
                item.getLineDiscount(), item.getLineTax(), item.getLineTotal(), item.getReturnedQuantity(),
                item.getRefundedAmount(),
                item.getAllocations().stream().map(a -> new AllocationResponse(a.getWarehouse().getId(),
                        a.getWarehouse().getCode(), a.getQuantity())).toList());
    }
}
