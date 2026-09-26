package com.ecommerce.oms.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "order_status_history")
@Getter
@Setter
@NoArgsConstructor
public class OrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder customerOrder;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus toStatus;

    @Column(nullable = false, length = 254)
    private String actor;

    @Column(length = 500)
    private String note;

    @Column(nullable = false)
    private Instant changedAt;

    public OrderStatusHistory(CustomerOrder customerOrder, OrderStatus fromStatus, OrderStatus toStatus,
            String actor, String note, Instant changedAt) {
        this.customerOrder = customerOrder;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actor = actor;
        this.note = note;
        this.changedAt = changedAt;
    }
}
