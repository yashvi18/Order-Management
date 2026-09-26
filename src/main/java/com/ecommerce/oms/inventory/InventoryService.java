package com.ecommerce.oms.inventory;

import com.ecommerce.oms.catalog.Product;
import com.ecommerce.oms.catalog.ProductRepository;
import com.ecommerce.oms.common.ConflictException;
import com.ecommerce.oms.common.NotFoundException;
import com.ecommerce.oms.inventory.InventoryDtos.InventoryResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final InventoryItemRepository inventoryRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;

    public InventoryService(InventoryItemRepository inventoryRepository, WarehouseRepository warehouseRepository,
            ProductRepository productRepository) {
        this.inventoryRepository = inventoryRepository;
        this.warehouseRepository = warehouseRepository;
        this.productRepository = productRepository;
    }

    /**
     * Reserves stock for every product atomically (caller's transaction). Rows are locked FOR UPDATE, so two
     * concurrent checkouts can never both see the same unit as available. Throws InsufficientStockException,
     * which rolls back any reservation already made in this transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<StockAllocation> reserve(Map<Long, Integer> quantityByProductId) {
        Map<Long, List<InventoryItem>> rowsByProduct = inventoryRepository
                .lockAllByProductIds(quantityByProductId.keySet()).stream()
                .filter(row -> row.getWarehouse().isActive())
                .collect(Collectors.groupingBy(row -> row.getProduct().getId()));
        List<StockAllocation> allocations = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : new TreeMap<>(quantityByProductId).entrySet()) {
            allocations.addAll(allocate(entry.getKey(), entry.getValue(),
                    rowsByProduct.getOrDefault(entry.getKey(), List.of())));
        }
        return allocations;
    }

    /**
     * Greedy by available stock (desc): if one warehouse can ship the whole line it is picked alone,
     * otherwise the line is split across the fewest warehouses this heuristic finds.
     */
    private List<StockAllocation> allocate(Long productId, int requested, List<InventoryItem> rows) {
        int totalAvailable = rows.stream().mapToInt(InventoryItem::available).sum();
        if (totalAvailable < requested) {
            throw new InsufficientStockException(productId, requested, totalAvailable);
        }
        List<InventoryItem> ranked = rows.stream()
                .sorted(Comparator.comparingInt(InventoryItem::available).reversed().thenComparing(InventoryItem::getId))
                .toList();
        List<StockAllocation> result = new ArrayList<>();
        int remaining = requested;
        for (InventoryItem row : ranked) {
            if (remaining == 0) {
                break;
            }
            int take = Math.min(remaining, row.available());
            if (take <= 0) {
                continue;
            }
            row.setReserved(row.getReserved() + take);
            result.add(new StockAllocation(productId, row.getWarehouse().getId(), take));
            remaining -= take;
        }
        return result;
    }

    /** Order cancelled before shipping: reserved units become available again. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseReservations(List<StockAllocation> allocations) {
        apply(allocations, (row, quantity) -> {
            requireReserved(row, quantity);
            row.setReserved(row.getReserved() - quantity);
        });
    }

    /** Order shipped: units physically leave the warehouse. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void commitShipment(List<StockAllocation> allocations) {
        apply(allocations, (row, quantity) -> {
            requireReserved(row, quantity);
            row.setReserved(row.getReserved() - quantity);
            row.setOnHand(row.getOnHand() - quantity);
        });
    }

    /** Returned goods received back into a warehouse. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void restock(List<StockAllocation> allocations) {
        apply(allocations, (row, quantity) -> row.setOnHand(row.getOnHand() + quantity));
    }

    @Transactional
    public long availableForProduct(Long productId) {
        return inventoryRepository.availableForProduct(productId);
    }

    @Transactional
    public InventoryResponse setStock(Long warehouseId, Long productId, int onHand) {
        InventoryItem row = lockOrCreate(warehouseId, productId);
        if (onHand < row.getReserved()) {
            throw new ConflictException("On-hand quantity %d is below the %d units already reserved"
                    .formatted(onHand, row.getReserved()));
        }
        row.setOnHand(onHand);
        return InventoryResponse.from(row);
    }

    @Transactional
    public InventoryResponse adjustStock(Long warehouseId, Long productId, int delta) {
        InventoryItem row = lockOrCreate(warehouseId, productId);
        int newOnHand = row.getOnHand() + delta;
        if (newOnHand < row.getReserved()) {
            throw new ConflictException("Adjustment would leave %d on hand but %d units are reserved"
                    .formatted(newOnHand, row.getReserved()));
        }
        row.setOnHand(newOnHand);
        return InventoryResponse.from(row);
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> listByProduct(Long productId) {
        return inventoryRepository.findByProductIdOrderByWarehouseId(productId).stream().map(InventoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> listByWarehouse(Long warehouseId) {
        return inventoryRepository.findByWarehouseIdOrderByProductId(warehouseId).stream().map(InventoryResponse::from).toList();
    }

    private InventoryItem lockOrCreate(Long warehouseId, Long productId) {
        return inventoryRepository.lockByProductAndWarehouse(productId, warehouseId).orElseGet(() -> {
            Warehouse warehouse = warehouseRepository.findById(warehouseId)
                    .orElseThrow(() -> new NotFoundException("Warehouse not found"));
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new NotFoundException("Product not found"));
            InventoryItem row = new InventoryItem();
            row.setWarehouse(warehouse);
            row.setProduct(product);
            return inventoryRepository.save(row);
        });
    }

    /** Locks affected rows in the same id order as reserve() so no two transactions deadlock. */
    private void apply(List<StockAllocation> allocations, BiConsumer<InventoryItem, Integer> operation) {
        if (allocations.isEmpty()) {
            return;
        }
        Set<Long> productIds = allocations.stream().map(StockAllocation::productId).collect(Collectors.toSet());
        Map<String, InventoryItem> rows = inventoryRepository.lockAllByProductIds(productIds).stream()
                .collect(Collectors.toMap(row -> key(row.getProduct().getId(), row.getWarehouse().getId()), row -> row));
        for (StockAllocation allocation : allocations) {
            InventoryItem row = rows.get(key(allocation.productId(), allocation.warehouseId()));
            if (row == null) {
                throw new IllegalStateException("No inventory row for " + allocation);
            }
            operation.accept(row, allocation.quantity());
        }
    }

    private static void requireReserved(InventoryItem row, int quantity) {
        if (row.getReserved() < quantity) {
            throw new IllegalStateException("Inventory row %d has %d reserved, cannot consume %d"
                    .formatted(row.getId(), row.getReserved(), quantity));
        }
    }

    private static String key(Long productId, Long warehouseId) {
        return productId + ":" + warehouseId;
    }
}
