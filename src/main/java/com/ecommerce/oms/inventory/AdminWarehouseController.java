package com.ecommerce.oms.inventory;

import com.ecommerce.oms.inventory.InventoryDtos.WarehouseRequest;
import com.ecommerce.oms.inventory.InventoryDtos.WarehouseResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/warehouses")
public class AdminWarehouseController {

    private final WarehouseService warehouseService;

    public AdminWarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    @PostMapping
    public ResponseEntity<WarehouseResponse> create(@Valid @RequestBody WarehouseRequest request) {
        WarehouseResponse created = warehouseService.create(request);
        return ResponseEntity.created(URI.create("/api/admin/warehouses/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public WarehouseResponse update(@PathVariable Long id, @Valid @RequestBody WarehouseRequest request) {
        return warehouseService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        warehouseService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<WarehouseResponse> list() {
        return warehouseService.list();
    }
}
