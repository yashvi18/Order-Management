package com.ecommerce.oms.order;

import com.ecommerce.oms.auth.User;
import com.ecommerce.oms.inventory.StockAllocation;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "orders", uniqueConstraints = @UniqueConstraint(name = "uk_orders_customer_idempotency",
        columnNames = {"customer_id", "idempotency_key"}))
@Getter
@Setter
@NoArgsConstructor
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @OneToMany(mappedBy = "customerOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "customerOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<OrderStatusHistory> history = new ArrayList<>();

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal discountTotal;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal taxTotal;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal grandTotal;

    @Column(length = 32)
    private String discountCode;

    @Embedded
    private Address shippingAddress;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    private Instant placedAt;

    private Instant updatedAt;

    private Instant deliveredAt;

    /** Optimistic lock: a customer cancel racing a staff "PACKED" update makes one of them fail with 409. */
    @Version
    private long version;

    public void addItem(OrderItem item) {
        item.setCustomerOrder(this);
        items.add(item);
    }

    public void markPlaced(String actor, Instant at) {
        if (status != null) {
            throw new IllegalStateException("Order already placed");
        }
        record(null, OrderStatus.PLACED, actor, "Order placed", at);
        placedAt = updatedAt;
    }

    public void transitionTo(OrderStatus target, String actor, String note, Instant at) {
        OrderStateMachine.assertTransition(status, target);
        record(status, target, actor, note, at);
    }

    public List<StockAllocation> stockAllocations() {
        return items.stream()
                .flatMap(item -> item.getAllocations().stream().map(a -> new StockAllocation(
                        item.getProduct().getId(), a.getWarehouse().getId(), a.getQuantity())))
                .toList();
    }

    public String warehouseCodes() {
        return items.stream().flatMap(item -> item.getAllocations().stream())
                .map(a -> a.getWarehouse().getCode()).distinct().sorted().collect(Collectors.joining(", "));
    }

    private void record(OrderStatus from, OrderStatus to, String actor, String note, Instant at) {
        history.add(new OrderStatusHistory(this, from, to, actor, note, at));
        status = to;
        updatedAt = at;
        if (to == OrderStatus.DELIVERED) {
            deliveredAt = at;
        }
    }
}
