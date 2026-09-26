package com.ecommerce.oms.catalog;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record CategoryRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 500) String description,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") @Digits(integer = 1, fraction = 4) BigDecimal taxRate,
            Long parentId) {
    }

    public record CategoryResponse(Long id, String name, String description, BigDecimal taxRate, Long parentId) {
        static CategoryResponse from(Category category) {
            return new CategoryResponse(category.getId(), category.getName(), category.getDescription(),
                    category.getTaxRate(), category.getParent() == null ? null : category.getParent().getId());
        }
    }

    public record ProductRequest(
            @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9-]+") String sku,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal price,
            @NotNull Long categoryId,
            Boolean active) {
    }

    public record ProductResponse(Long id, String sku, String name, String description, BigDecimal price,
            Long categoryId, String categoryName, boolean active) {
        static ProductResponse from(Product product) {
            return new ProductResponse(product.getId(), product.getSku(), product.getName(), product.getDescription(),
                    product.getPrice(), product.getCategory().getId(), product.getCategory().getName(),
                    product.isActive());
        }
    }
}
