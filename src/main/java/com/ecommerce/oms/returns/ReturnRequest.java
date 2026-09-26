package com.ecommerce.oms.returns;

import com.ecommerce.oms.order.CustomerOrder;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "return_requests")
@Getter
@Setter
@NoArgsConstructor
public class ReturnRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder customerOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReturnStatus status = ReturnStatus.REQUESTED;

    @Column(nullable = false, length = 500)
    private String reason;

    @OneToMany(mappedBy = "returnRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ReturnItem> items = new ArrayList<>();

    @Column(precision = 19, scale = 2)
    private BigDecimal refundAmount;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant resolvedAt;

    @Column(length = 254)
    private String resolvedBy;

    @Column(length = 500)
    private String resolutionNote;

    /** Two staff members receiving the same return concurrently: the second commit fails with 409. */
    @Version
    private long version;

    public void addItem(ReturnItem item) {
        item.setReturnRequest(this);
        items.add(item);
    }
}
