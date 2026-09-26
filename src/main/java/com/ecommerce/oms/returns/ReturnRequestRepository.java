package com.ecommerce.oms.returns;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    List<ReturnRequest> findByCustomerOrderIdOrderByIdAsc(Long orderId);

    List<ReturnRequest> findByStatusOrderByIdAsc(ReturnStatus status);

    @Query("select distinct r from ReturnRequest r join r.customerOrder o join o.items i join i.allocations a "
            + "where a.warehouse.id = :warehouseId and r.status = :status order by r.id")
    List<ReturnRequest> findForWarehouse(@Param("warehouseId") Long warehouseId, @Param("status") ReturnStatus status);

    @Query("select coalesce(sum(ri.quantity), 0) from ReturnItem ri "
            + "where ri.orderItem.id = :orderItemId and ri.returnRequest.status = :status")
    long sumQuantityByOrderItemAndStatus(@Param("orderItemId") Long orderItemId, @Param("status") ReturnStatus status);
}
