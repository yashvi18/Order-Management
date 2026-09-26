package com.ecommerce.oms.order;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderAllocationRepository extends JpaRepository<OrderAllocation, Long> {

    boolean existsByOrderItem_CustomerOrder_IdAndWarehouse_Id(Long orderId, Long warehouseId);
}
