package com.ecommerce.oms.inventory;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    Optional<InventoryItem> findByProductIdAndWarehouseId(Long productId, Long warehouseId);

    List<InventoryItem> findByProductIdOrderByWarehouseId(Long productId);

    List<InventoryItem> findByWarehouseIdOrderByProductId(Long warehouseId);

    /**
     * SELECT ... FOR UPDATE on every stock row of the given products, always in id order so concurrent
     * checkouts acquire locks in the same sequence (no deadlocks). No joins: only inventory rows get locked.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.product.id in :productIds order by i.id")
    List<InventoryItem> lockAllByProductIds(@Param("productIds") Collection<Long> productIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.product.id = :productId and i.warehouse.id = :warehouseId")
    Optional<InventoryItem> lockByProductAndWarehouse(@Param("productId") Long productId,
            @Param("warehouseId") Long warehouseId);

    @Query("select coalesce(sum(i.onHand - i.reserved), 0) from InventoryItem i "
            + "where i.product.id = :productId and i.warehouse.active = true")
    long availableForProduct(@Param("productId") Long productId);
}
