package com.ecommerce.oms.cart;

import com.ecommerce.oms.auth.UserRepository;
import com.ecommerce.oms.cart.CartDtos.CartLine;
import com.ecommerce.oms.cart.CartDtos.CartResponse;
import com.ecommerce.oms.catalog.CatalogService;
import com.ecommerce.oms.catalog.Product;
import com.ecommerce.oms.common.BadRequestException;
import com.ecommerce.oms.common.Money;
import com.ecommerce.oms.common.NotFoundException;
import com.ecommerce.oms.inventory.InsufficientStockException;
import com.ecommerce.oms.inventory.InventoryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {

    public static final int MAX_LINE_QUANTITY = 100;

    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final CatalogService catalogService;
    private final InventoryService inventoryService;

    public CartService(CartRepository cartRepository, UserRepository userRepository, CatalogService catalogService,
            InventoryService inventoryService) {
        this.cartRepository = cartRepository;
        this.userRepository = userRepository;
        this.catalogService = catalogService;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public CartResponse getCart(Long customerId) {
        return toResponse(getOrCreate(customerId));
    }

    @Transactional
    public CartResponse addItem(Long customerId, Long productId, int quantity) {
        Product product = catalogService.requireActiveProduct(productId);
        Cart cart = getOrCreate(customerId);
        Optional<CartItem> existing = cart.findItem(productId);
        int newQuantity = existing.map(CartItem::getQuantity).orElse(0) + quantity;
        if (newQuantity > MAX_LINE_QUANTITY) {
            throw new BadRequestException("A cart line cannot exceed " + MAX_LINE_QUANTITY + " units");
        }
        ensureAvailable(productId, newQuantity);
        if (existing.isPresent()) {
            existing.get().setQuantity(newQuantity);
        } else {
            CartItem item = new CartItem();
            item.setCart(cart);
            item.setProduct(product);
            item.setQuantity(newQuantity);
            cart.getItems().add(item);
        }
        cart.setUpdatedAt(Instant.now());
        return toResponse(cart);
    }

    @Transactional
    public CartResponse updateItem(Long customerId, Long productId, int quantity) {
        Cart cart = getOrCreate(customerId);
        CartItem item = cart.findItem(productId).orElseThrow(() -> new NotFoundException("Product is not in the cart"));
        if (quantity == 0) {
            cart.getItems().remove(item);
        } else {
            ensureAvailable(productId, quantity);
            item.setQuantity(quantity);
        }
        cart.setUpdatedAt(Instant.now());
        return toResponse(cart);
    }

    @Transactional
    public CartResponse removeItem(Long customerId, Long productId) {
        return updateItem(customerId, productId, 0);
    }

    @Transactional
    public void clear(Long customerId) {
        Cart cart = getOrCreate(customerId);
        cart.getItems().clear();
        cart.setUpdatedAt(Instant.now());
    }

    private void ensureAvailable(Long productId, int quantity) {
        long available = inventoryService.availableForProduct(productId);
        if (available < quantity) {
            throw new InsufficientStockException(productId, quantity, available);
        }
    }

    private Cart getOrCreate(Long customerId) {
        return cartRepository.findByCustomerId(customerId).orElseGet(() -> {
            Cart cart = new Cart();
            cart.setCustomer(userRepository.getReferenceById(customerId));
            return cartRepository.save(cart);
        });
    }

    private CartResponse toResponse(Cart cart) {
        List<CartLine> lines = cart.getItems().stream().map(item -> {
            Product p = item.getProduct();
            BigDecimal lineTotal = Money.of(p.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            return new CartLine(p.getId(), p.getSku(), p.getName(), p.getPrice(), item.getQuantity(), lineTotal,
                    p.isActive());
        }).toList();
        int totalQuantity = lines.stream().mapToInt(CartLine::quantity).sum();
        BigDecimal subtotal = lines.stream().map(CartLine::lineTotal).reduce(Money.ZERO, BigDecimal::add);
        return new CartResponse(lines, totalQuantity, subtotal);
    }
}
