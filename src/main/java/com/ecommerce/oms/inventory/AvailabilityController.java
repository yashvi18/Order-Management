package com.ecommerce.oms.inventory;

import com.ecommerce.oms.catalog.CatalogService;
import com.ecommerce.oms.inventory.InventoryDtos.AvailabilityResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Public, aggregated availability — customers never see per-warehouse stock. */
@RestController
public class AvailabilityController {

    private final CatalogService catalogService;
    private final InventoryService inventoryService;

    public AvailabilityController(CatalogService catalogService, InventoryService inventoryService) {
        this.catalogService = catalogService;
        this.inventoryService = inventoryService;
    }

    @GetMapping("/api/catalog/products/{id}/availability")
    public AvailabilityResponse availability(@PathVariable Long id) {
        catalogService.getProduct(id, true);
        long available = inventoryService.availableForProduct(id);
        return new AvailabilityResponse(id, available, available > 0);
    }
}
