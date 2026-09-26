package com.ecommerce.oms.catalog;

import com.ecommerce.oms.catalog.CatalogDtos.CategoryResponse;
import com.ecommerce.oms.catalog.CatalogDtos.ProductResponse;
import com.ecommerce.oms.common.PageResponse;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog")
public class PublicCatalogController {

    private final CatalogService catalogService;

    public PublicCatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return catalogService.listCategories();
    }

    @GetMapping("/products")
    public PageResponse<ProductResponse> products(@RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String q, @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice, @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return catalogService.searchProducts(categoryId, q, minPrice, maxPrice, true, pageable);
    }

    @GetMapping("/products/{id}")
    public ProductResponse product(@PathVariable Long id) {
        return catalogService.getProduct(id, true);
    }
}
