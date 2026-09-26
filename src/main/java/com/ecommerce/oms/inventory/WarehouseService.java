package com.ecommerce.oms.inventory;

import com.ecommerce.oms.common.ConflictException;
import com.ecommerce.oms.common.NotFoundException;
import com.ecommerce.oms.inventory.InventoryDtos.WarehouseRequest;
import com.ecommerce.oms.inventory.InventoryDtos.WarehouseResponse;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;

    public WarehouseService(WarehouseRepository warehouseRepository) {
        this.warehouseRepository = warehouseRepository;
    }

    @Transactional
    public WarehouseResponse create(WarehouseRequest request) {
        if (warehouseRepository.existsByCodeIgnoreCase(request.code())) {
            throw new ConflictException("Warehouse code '" + request.code() + "' already exists");
        }
        Warehouse warehouse = new Warehouse();
        warehouse.setCode(request.code().toUpperCase(Locale.ROOT));
        warehouse.setName(request.name().trim());
        warehouse.setCity(request.city().trim());
        return WarehouseResponse.from(warehouseRepository.save(warehouse));
    }

    @Transactional
    public WarehouseResponse update(Long id, WarehouseRequest request) {
        Warehouse warehouse = require(id);
        if (!warehouse.getCode().equalsIgnoreCase(request.code()) && warehouseRepository.existsByCodeIgnoreCase(request.code())) {
            throw new ConflictException("Warehouse code '" + request.code() + "' already exists");
        }
        warehouse.setCode(request.code().toUpperCase(Locale.ROOT));
        warehouse.setName(request.name().trim());
        warehouse.setCity(request.city().trim());
        return WarehouseResponse.from(warehouse);
    }

    @Transactional
    public void deactivate(Long id) {
        require(id).setActive(false);
    }

    @Transactional(readOnly = true)
    public List<WarehouseResponse> list() {
        return warehouseRepository.findAll(Sort.by("code")).stream().map(WarehouseResponse::from).toList();
    }

    private Warehouse require(Long id) {
        return warehouseRepository.findById(id).orElseThrow(() -> new NotFoundException("Warehouse not found"));
    }
}
