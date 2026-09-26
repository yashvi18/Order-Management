package com.ecommerce.oms.order;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {

    Optional<CustomerOrder> findByCustomerIdAndIdempotencyKey(Long customerId, String idempotencyKey);

    Optional<CustomerOrder> findByIdAndCustomerId(Long id, Long customerId);

    Page<CustomerOrder> findByCustomerId(Long customerId, Pageable pageable);

    Page<CustomerOrder> findByStatus(OrderStatus status, Pageable pageable);

    @Query(value = "select distinct o from CustomerOrder o join o.items i join i.allocations a "
            + "where a.warehouse.id = :warehouseId and o.status = :status",
            countQuery = "select count(distinct o) from CustomerOrder o join o.items i join i.allocations a "
                    + "where a.warehouse.id = :warehouseId and o.status = :status")
    Page<CustomerOrder> findForWarehouse(@Param("warehouseId") Long warehouseId, @Param("status") OrderStatus status,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o where o.id = :id")
    Optional<CustomerOrder> lockById(@Param("id") Long id);
}
