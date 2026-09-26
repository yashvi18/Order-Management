package com.ecommerce.oms.payment;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    List<Refund> findByPaymentOrderIdOrderByIdAsc(Long orderId);
}
