package com.ecommerce.oms.discount;

import com.ecommerce.oms.discount.DiscountDtos.DiscountRequest;
import com.ecommerce.oms.discount.DiscountDtos.DiscountResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/discounts")
public class AdminDiscountController {

    private final DiscountService discountService;

    public AdminDiscountController(DiscountService discountService) {
        this.discountService = discountService;
    }

    @PostMapping
    public ResponseEntity<DiscountResponse> create(@Valid @RequestBody DiscountRequest request) {
        DiscountResponse created = discountService.create(request);
        return ResponseEntity.created(URI.create("/api/admin/discounts/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public DiscountResponse update(@PathVariable Long id, @Valid @RequestBody DiscountRequest request) {
        return discountService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        discountService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<DiscountResponse> list() {
        return discountService.list();
    }
}
