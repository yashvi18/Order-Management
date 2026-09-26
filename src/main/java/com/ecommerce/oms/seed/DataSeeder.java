package com.ecommerce.oms.seed;

import com.ecommerce.oms.auth.Role;
import com.ecommerce.oms.auth.User;
import com.ecommerce.oms.auth.UserRepository;
import com.ecommerce.oms.catalog.Category;
import com.ecommerce.oms.catalog.CategoryRepository;
import com.ecommerce.oms.catalog.Product;
import com.ecommerce.oms.catalog.ProductRepository;
import com.ecommerce.oms.discount.Discount;
import com.ecommerce.oms.discount.DiscountRepository;
import com.ecommerce.oms.discount.DiscountType;
import com.ecommerce.oms.inventory.InventoryItem;
import com.ecommerce.oms.inventory.InventoryItemRepository;
import com.ecommerce.oms.inventory.Warehouse;
import com.ecommerce.oms.inventory.WarehouseRepository;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Demo data for reviewers: two warehouses, three categories, six products, discount codes, one user per role. */
@Component
@ConditionalOnProperty(name = "oms.seed.enabled", havingValue = "true")
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final DiscountRepository discountRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository, CategoryRepository categoryRepository,
            ProductRepository productRepository, WarehouseRepository warehouseRepository,
            InventoryItemRepository inventoryItemRepository, DiscountRepository discountRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.discountRepository = discountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        Warehouse blr = warehouse("BLR-01", "Bengaluru Fulfilment Centre", "Bengaluru");
        Warehouse del = warehouse("DEL-01", "Delhi Fulfilment Centre", "New Delhi");

        user("admin@oms.local", "Admin@123", "Olivia Admin", Role.ADMIN, null);
        user("staff.blr@oms.local", "Staff@123", "Bala Staff", Role.WAREHOUSE_STAFF, blr.getId());
        user("staff.del@oms.local", "Staff@123", "Dev Staff", Role.WAREHOUSE_STAFF, del.getId());
        user("alice@example.com", "Customer@123", "Alice Customer", Role.CUSTOMER, null);

        Category electronics = category("Electronics", "0.1800");
        Category books = category("Books", "0.0500");
        Category apparel = category("Apparel", "0.1200");

        List<Object[]> catalog = List.of(
                new Object[] {electronics, "ELEC-LAPTOP-14", "UltraBook 14", "74999.00", 5, 3},
                new Object[] {electronics, "ELEC-PHONE-X", "Phone X", "29999.00", 10, 0},
                new Object[] {electronics, "ELEC-EARBUDS", "Wireless Earbuds", "2499.00", 40, 25},
                new Object[] {books, "BOOK-DDIA", "Designing Data-Intensive Applications", "899.00", 15, 10},
                new Object[] {books, "BOOK-CLEAN", "Clean Architecture", "599.00", 0, 8},
                new Object[] {apparel, "APP-TEE-M", "Cotton T-Shirt (M)", "499.00", 100, 60});
        for (Object[] row : catalog) {
            Product product = product((Category) row[0], (String) row[1], (String) row[2], (String) row[3]);
            stock(product, blr, (Integer) row[4]);
            stock(product, del, (Integer) row[5]);
        }

        discount("WELCOME10", DiscountType.PERCENTAGE, "10", null, "500.00");
        discount("FLAT200", DiscountType.FIXED_AMOUNT, "200", "1000.00", null);
        log.info("Seeded demo data: admin@oms.local / Admin@123, alice@example.com / Customer@123");
    }

    private Warehouse warehouse(String code, String name, String city) {
        Warehouse warehouse = new Warehouse();
        warehouse.setCode(code);
        warehouse.setName(name);
        warehouse.setCity(city);
        return warehouseRepository.save(warehouse);
    }

    private void user(String email, String password, String name, Role role, Long warehouseId) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFullName(name);
        user.setRole(role);
        user.setWarehouseId(warehouseId);
        userRepository.save(user);
    }

    private Category category(String name, String taxRate) {
        Category category = new Category();
        category.setName(name);
        category.setTaxRate(new BigDecimal(taxRate));
        return categoryRepository.save(category);
    }

    private Product product(Category category, String sku, String name, String price) {
        Product product = new Product();
        product.setCategory(category);
        product.setSku(sku);
        product.setName(name);
        product.setDescription(name);
        product.setPrice(new BigDecimal(price));
        return productRepository.save(product);
    }

    private void stock(Product product, Warehouse warehouse, int onHand) {
        InventoryItem item = new InventoryItem();
        item.setProduct(product);
        item.setWarehouse(warehouse);
        item.setOnHand(onHand);
        inventoryItemRepository.save(item);
    }

    private void discount(String code, DiscountType type, String value, String minOrder, String maxDiscount) {
        Discount discount = new Discount();
        discount.setCode(code);
        discount.setType(type);
        discount.setValue(new BigDecimal(value));
        discount.setMinOrderAmount(minOrder == null ? null : new BigDecimal(minOrder));
        discount.setMaxDiscountAmount(maxDiscount == null ? null : new BigDecimal(maxDiscount));
        discountRepository.save(discount);
    }
}
