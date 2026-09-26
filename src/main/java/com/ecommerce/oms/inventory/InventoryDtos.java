package com.ecommerce.oms.inventory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class InventoryDtos {

    private InventoryDtos() {
    }

    public record WarehouseRequest(
            @NotBlank @Size(max = 32) @Pattern(regexp = "[A-Za-z0-9-]+") String code,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 100) String city) {
    }

    public record WarehouseResponse(Long id, String code, String name, String city, boolean active) {
        static WarehouseResponse from(Warehouse w) {
            return new WarehouseResponse(w.getId(), w.getCode(), w.getName(), w.getCity(), w.isActive());
        }
    }

    public record SetStockRequest(@NotNull @Min(0) Integer onHand) {
    }

    public record AdjustStockRequest(@NotNull Integer delta) {
    }

    public record InventoryResponse(Long id, Long productId, String sku, Long warehouseId, String warehouseCode,
            int onHand, int reserved, int available) {
        static InventoryResponse from(InventoryItem i) {
            return new InventoryResponse(i.getId(), i.getProduct().getId(), i.getProduct().getSku(),
                    i.getWarehouse().getId(), i.getWarehouse().getCode(), i.getOnHand(), i.getReserved(), i.available());
        }
    }

    public record AvailabilityResponse(Long productId, long available, boolean inStock) {
    }
}
