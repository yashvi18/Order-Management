package com.ecommerce.oms.order;

import com.ecommerce.oms.catalog.Product;
import com.ecommerce.oms.common.Money;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Snapshot of what was bought: price, tax and discount share are frozen at checkout. */
@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder customerOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(nullable = false, length = 200)
    private String productName;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal taxRate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal lineSubtotal;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal lineDiscount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal lineTax;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal lineTotal;

    @Column(nullable = false)
    private int returnedQuantity;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal refundedAmount = Money.ZERO;

    @OneToMany(mappedBy = "orderItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<OrderAllocation> allocations = new ArrayList<>();

    public void addAllocation(OrderAllocation allocation) {
        allocation.setOrderItem(this);
        allocations.add(allocation);
    }
}
