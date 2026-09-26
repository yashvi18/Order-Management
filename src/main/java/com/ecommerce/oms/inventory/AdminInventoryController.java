package com.ecommerce.oms.inventory;

import com.ecommerce.oms.common.BadRequestException;
import com.ecommerce.oms.inventory.InventoryDtos.AdjustStockRequest;
import com.ecommerce.oms.inventory.InventoryDtos.InventoryResponse;
import com.ecommerce.oms.inventory.InventoryDtos.SetStockRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/inventory")
public class AdminInventoryController {

    private final InventoryService inventoryService;

    public AdminInventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PutMapping("/warehouses/{warehouseId}/products/{productId}")
    public InventoryResponse setStock(@PathVariable Long warehouseId, @PathVariable Long productId,
            @Valid @RequestBody SetStockRequest request) {
        return inventoryService.setStock(warehouseId, productId, request.onHand());
    }

    @PostMapping("/warehouses/{warehouseId}/products/{productId}/adjustments")
    public InventoryResponse adjust(@PathVariable Long warehouseId, @PathVariable Long productId,
            @Valid @RequestBody AdjustStockRequest request) {
        return inventoryService.adjustStock(warehouseId, productId, request.delta());
    }

    @GetMapping
    public List<InventoryResponse> list(@RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long warehouseId) {
        if (productId != null) {
            return inventoryService.listByProduct(productId);
        }
        if (warehouseId != null) {
            return inventoryService.listByWarehouse(warehouseId);
        }
        throw new BadRequestException("Provide productId or warehouseId");
    }
}
