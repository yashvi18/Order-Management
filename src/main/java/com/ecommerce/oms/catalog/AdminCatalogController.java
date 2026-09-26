package com.ecommerce.oms.catalog;

import com.ecommerce.oms.catalog.CatalogDtos.CategoryRequest;
import com.ecommerce.oms.catalog.CatalogDtos.CategoryResponse;
import com.ecommerce.oms.catalog.CatalogDtos.ProductRequest;
import com.ecommerce.oms.catalog.CatalogDtos.ProductResponse;
import com.ecommerce.oms.common.PageResponse;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminCatalogController {

    private final CatalogService catalogService;

    public AdminCatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @PostMapping("/categories")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        CategoryResponse created = catalogService.createCategory(request);
        return ResponseEntity.created(URI.create("/api/admin/categories/" + created.id())).body(created);
    }

    @PutMapping("/categories/{id}")
    public CategoryResponse updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return catalogService.updateCategory(id, request);
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        catalogService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/categories")
    public List<CategoryResponse> listCategories() {
        return catalogService.listCategories();
    }

    @PostMapping("/products")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = catalogService.createProduct(request);
        return ResponseEntity.created(URI.create("/api/admin/products/" + created.id())).body(created);
    }

    @PutMapping("/products/{id}")
    public ProductResponse updateProduct(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return catalogService.updateProduct(id, request);
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> deactivateProduct(@PathVariable Long id) {
        catalogService.deactivateProduct(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/products")
    public PageResponse<ProductResponse> listProducts(@RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String q, @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice, @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return catalogService.searchProducts(categoryId, q, minPrice, maxPrice, false, pageable);
    }

    @GetMapping("/products/{id}")
    public ProductResponse getProduct(@PathVariable Long id) {
        return catalogService.getProduct(id, false);
    }
}
