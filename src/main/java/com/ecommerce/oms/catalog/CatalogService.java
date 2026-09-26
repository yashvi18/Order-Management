package com.ecommerce.oms.catalog;

import com.ecommerce.oms.catalog.CatalogDtos.CategoryRequest;
import com.ecommerce.oms.catalog.CatalogDtos.CategoryResponse;
import com.ecommerce.oms.catalog.CatalogDtos.ProductRequest;
import com.ecommerce.oms.catalog.CatalogDtos.ProductResponse;
import com.ecommerce.oms.common.BadRequestException;
import com.ecommerce.oms.common.ConflictException;
import com.ecommerce.oms.common.Money;
import com.ecommerce.oms.common.NotFoundException;
import com.ecommerce.oms.common.PageResponse;
import com.ecommerce.oms.common.Pageables;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

    private static final Set<String> SORTABLE = Set.of("id", "name", "price", "createdAt");

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CatalogService(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        if (categoryRepository.existsByNameIgnoreCase(request.name().trim())) {
            throw new ConflictException("Category '" + request.name() + "' already exists");
        }
        Category category = new Category();
        apply(category, request);
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        Category category = requireCategory(id);
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(request.name().trim(), id)) {
            throw new ConflictException("Category '" + request.name() + "' already exists");
        }
        if (id.equals(request.parentId())) {
            throw new BadRequestException("A category cannot be its own parent");
        }
        apply(category, request);
        return CategoryResponse.from(category);
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category category = requireCategory(id);
        if (productRepository.existsByCategoryId(id)) {
            throw new ConflictException("Category has products; move or deactivate them first");
        }
        if (categoryRepository.existsByParentId(id)) {
            throw new ConflictException("Category has sub-categories");
        }
        categoryRepository.delete(category);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories() {
        return categoryRepository.findAll(Sort.by("name")).stream().map(CategoryResponse::from).toList();
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        if (productRepository.existsBySkuIgnoreCase(request.sku().trim())) {
            throw new ConflictException("SKU '" + request.sku() + "' already exists");
        }
        Product product = new Product();
        apply(product, request);
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = productRepository.findById(id).orElseThrow(() -> new NotFoundException("Product not found"));
        if (productRepository.existsBySkuIgnoreCaseAndIdNot(request.sku().trim(), id)) {
            throw new ConflictException("SKU '" + request.sku() + "' already exists");
        }
        apply(product, request);
        return ProductResponse.from(product);
    }

    @Transactional
    public void deactivateProduct(Long id) {
        productRepository.findById(id).orElseThrow(() -> new NotFoundException("Product not found")).setActive(false);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> searchProducts(Long categoryId, String q, BigDecimal minPrice,
            BigDecimal maxPrice, boolean activeOnly, Pageable pageable) {
        Pageables.requireSortableBy(pageable, SORTABLE);
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BadRequestException("minPrice must not exceed maxPrice");
        }
        return PageResponse.from(productRepository
                .findAll(ProductSpecifications.filter(categoryId, q, minPrice, maxPrice, activeOnly), pageable)
                .map(ProductResponse::from));
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long id, boolean activeOnly) {
        Product product = activeOnly
                ? productRepository.findByIdAndActiveTrue(id).orElse(null)
                : productRepository.findById(id).orElse(null);
        if (product == null) {
            throw new NotFoundException("Product not found");
        }
        return ProductResponse.from(product);
    }

    /** Used by cart/checkout; no readOnly flag because callers run inside write transactions. */
    @Transactional
    public Product requireActiveProduct(Long id) {
        return productRepository.findByIdAndActiveTrue(id).orElseThrow(() -> new NotFoundException("Product not found"));
    }

    private Category requireCategory(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> new NotFoundException("Category not found"));
    }

    private void apply(Category category, CategoryRequest request) {
        category.setName(request.name().trim());
        category.setDescription(request.description());
        category.setTaxRate(request.taxRate());
        category.setParent(request.parentId() == null ? null : requireCategory(request.parentId()));
    }

    private void apply(Product product, ProductRequest request) {
        product.setSku(request.sku().trim().toUpperCase(Locale.ROOT));
        product.setName(request.name().trim());
        product.setDescription(request.description());
        product.setPrice(Money.of(request.price()));
        product.setCategory(requireCategory(request.categoryId()));
        if (request.active() != null) {
            product.setActive(request.active());
        }
    }
}
