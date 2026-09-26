package com.ecommerce.oms.support;

import com.ecommerce.oms.auth.Role;
import com.ecommerce.oms.auth.User;
import com.ecommerce.oms.auth.UserRepository;
import com.ecommerce.oms.catalog.Category;
import com.ecommerce.oms.catalog.CategoryRepository;
import com.ecommerce.oms.catalog.Product;
import com.ecommerce.oms.catalog.ProductRepository;
import com.ecommerce.oms.inventory.InventoryItem;
import com.ecommerce.oms.inventory.InventoryItemRepository;
import com.ecommerce.oms.inventory.Warehouse;
import com.ecommerce.oms.inventory.WarehouseRepository;
import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Builds persistent test data directly through repositories. Later tasks add more creators here. */
public class TestFixtures {

    public static final String PASSWORD = "password123";

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private InventoryItemRepository inventoryItemRepository;

    public User customer(String email) {
        return user(email, Role.CUSTOMER, null);
    }

    public User admin() {
        return user("admin@test.local", Role.ADMIN, null);
    }

    public User user(String email, Role role, Long warehouseId) {
        return userRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
            User user = new User();
            user.setEmail(email.toLowerCase(Locale.ROOT));
            user.setPasswordHash(passwordEncoder.encode(PASSWORD));
            user.setFullName("Test " + role.name());
            user.setRole(role);
            user.setWarehouseId(warehouseId);
            return userRepository.save(user);
        });
    }

    public Category category(String name, String taxRate) {
        Category category = new Category();
        category.setName(name);
        category.setTaxRate(new BigDecimal(taxRate));
        return categoryRepository.save(category);
    }

    public Product product(Category category, String sku, String name, String price) {
        Product product = new Product();
        product.setSku(sku);
        product.setName(name);
        product.setDescription(name + " description");
        product.setPrice(new BigDecimal(price));
        product.setCategory(category);
        return productRepository.save(product);
    }

    public Product deactivate(Product product) {
        product.setActive(false);
        return productRepository.save(product);
    }

    public Warehouse warehouse(String code) {
        Warehouse warehouse = new Warehouse();
        warehouse.setCode(code);
        warehouse.setName("Warehouse " + code);
        warehouse.setCity("City " + code);
        return warehouseRepository.save(warehouse);
    }

    public Warehouse deactivate(Warehouse warehouse) {
        warehouse.setActive(false);
        return warehouseRepository.save(warehouse);
    }

    public InventoryItem stock(Product product, Warehouse warehouse, int onHand, int reserved) {
        InventoryItem item = new InventoryItem();
        item.setProduct(product);
        item.setWarehouse(warehouse);
        item.setOnHand(onHand);
        item.setReserved(reserved);
        return inventoryItemRepository.save(item);
    }

    public InventoryItem inventory(Product product, Warehouse warehouse) {
        return inventoryItemRepository.findByProductIdAndWarehouseId(product.getId(), warehouse.getId()).orElseThrow();
    }

    public User staff(String email, Warehouse warehouse) {
        return user(email, Role.WAREHOUSE_STAFF, warehouse.getId());
    }
}
