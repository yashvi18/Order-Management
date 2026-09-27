package com.ecommerce.oms.order;

import com.ecommerce.oms.auth.AppUserDetails;
import com.ecommerce.oms.auth.UserRepository;
import com.ecommerce.oms.cart.Cart;
import com.ecommerce.oms.cart.CartItem;
import com.ecommerce.oms.cart.CartRepository;
import com.ecommerce.oms.catalog.Product;
import com.ecommerce.oms.common.BadRequestException;
import com.ecommerce.oms.common.ConflictException;
import com.ecommerce.oms.common.Money;
import com.ecommerce.oms.discount.AppliedDiscount;
import com.ecommerce.oms.discount.DiscountService;
import com.ecommerce.oms.events.OrderEventPublisher;
import com.ecommerce.oms.events.OrderEventType;
import com.ecommerce.oms.inventory.InventoryService;
import com.ecommerce.oms.inventory.StockAllocation;
import com.ecommerce.oms.inventory.WarehouseRepository;
import com.ecommerce.oms.order.OrderDtos.CheckoutRequest;
import com.ecommerce.oms.order.OrderDtos.OrderResponse;
import com.ecommerce.oms.payment.PaymentService;
import com.ecommerce.oms.pricing.PricedLine;
import com.ecommerce.oms.pricing.PricingLine;
import com.ecommerce.oms.pricing.PricingResult;
import com.ecommerce.oms.pricing.PricingService;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Places an order in ONE transaction: cart lock -> validate -> discount -> price -> reserve stock (row locks)
 * -> persist order -> capture payment -> clear cart -> outbox event. Any failure rolls everything back.
 */
@Service
public class CheckoutService {

    public record CheckoutResult(OrderResponse order, boolean replayed) {
    }

    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;
    private final DiscountService discountService;
    private final PricingService pricingService;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final OrderEventPublisher eventPublisher;
    private final OrderMapper orderMapper;
    private final Clock clock;

    public CheckoutService(CartRepository cartRepository, OrderRepository orderRepository,
            UserRepository userRepository, WarehouseRepository warehouseRepository, DiscountService discountService,
            PricingService pricingService, InventoryService inventoryService, PaymentService paymentService,
            OrderEventPublisher eventPublisher, OrderMapper orderMapper, Clock clock) {
        this.cartRepository = cartRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.warehouseRepository = warehouseRepository;
        this.discountService = discountService;
        this.pricingService = pricingService;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.eventPublisher = eventPublisher;
        this.orderMapper = orderMapper;
        this.clock = clock;
    }

    @Transactional
    public CheckoutResult checkout(AppUserDetails customer, CheckoutRequest request, String idempotencyKey) {
        Optional<CheckoutResult> replay = replay(customer.id(), idempotencyKey);
        if (replay.isPresent()) {
            return replay.get();
        }
        Cart cart = cartRepository.lockByCustomerId(customer.id())
                .orElseThrow(() -> new BadRequestException("Cart is empty"));
        // A concurrent request with the same key may have committed while we waited for the cart lock.
        replay = replay(customer.id(), idempotencyKey);
        if (replay.isPresent()) {
            return replay.get();
        }
        if (cart.getItems().isEmpty()) {
            throw new BadRequestException("Cart is empty");
        }

        Map<Long, Product> products = new LinkedHashMap<>();
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (CartItem item : cart.getItems()) {
            Product product = item.getProduct();
            if (!product.isActive()) {
                throw new ConflictException("Product " + product.getSku() + " is no longer available");
            }
            products.put(product.getId(), product);
            quantities.put(product.getId(), item.getQuantity());
        }
        List<PricingLine> lines = products.values().stream()
                .map(p -> new PricingLine(p.getId(), p.getPrice(), quantities.get(p.getId()), p.getCategory().getTaxRate()))
                .toList();

        AppliedDiscount discount = null;
        if (request.discountCode() != null && !request.discountCode().isBlank()) {
            BigDecimal subtotal = pricingService.price(lines, BigDecimal.ZERO).subtotal();
            discount = discountService.apply(request.discountCode(), subtotal, clock.instant());
            discountService.redeem(discount.discountId());
        }
        PricingResult pricing = pricingService.price(lines, discount == null ? Money.ZERO : discount.amount());

        List<StockAllocation> allocations = inventoryService.reserve(quantities);
        Map<Long, List<StockAllocation>> allocationsByProduct = allocations.stream()
                .collect(Collectors.groupingBy(StockAllocation::productId));

        CustomerOrder order = new CustomerOrder();
        order.setOrderNumber(OrderNumbers.next());
        order.setCustomer(userRepository.getReferenceById(customer.id()));
        order.setShippingAddress(request.shippingAddress().toAddress());
        order.setIdempotencyKey(idempotencyKey);
        order.setDiscountCode(discount == null ? null : discount.code());
        order.setSubtotal(pricing.subtotal());
        order.setDiscountTotal(pricing.discountTotal());
        order.setTaxTotal(pricing.taxTotal());
        order.setGrandTotal(pricing.grandTotal());
        for (PricedLine line : pricing.lines()) {
            Product product = products.get(line.productId());
            OrderItem item = new OrderItem();
            item.setProduct(product);
            item.setSku(product.getSku());
            item.setProductName(product.getName());
            item.setUnitPrice(line.unitPrice());
            item.setQuantity(line.quantity());
            item.setTaxRate(line.taxRate());
            item.setLineSubtotal(line.subtotal());
            item.setLineDiscount(line.discount());
            item.setLineTax(line.tax());
            item.setLineTotal(line.total());
            for (StockAllocation allocation : allocationsByProduct.get(product.getId())) {
                OrderAllocation orderAllocation = new OrderAllocation();
                orderAllocation.setWarehouse(warehouseRepository.getReferenceById(allocation.warehouseId()));
                orderAllocation.setQuantity(allocation.quantity());
                item.addAllocation(orderAllocation);
            }
            order.addItem(item);
        }
        order.markPlaced(customer.email(), clock.instant());
        orderRepository.save(order);

        paymentService.capture(order.getId(), order.getGrandTotal(), request.paymentToken());

        cart.getItems().clear();
        eventPublisher.publish(OrderEventType.ORDER_PLACED, order.getId(), customer.id(), customer.email(),
                "Order " + order.getOrderNumber() + " placed, total " + order.getGrandTotal());
        return new CheckoutResult(orderMapper.toResponse(order), false);
    }

    private Optional<CheckoutResult> replay(Long customerId, String idempotencyKey) {
        if (idempotencyKey == null) {
            return Optional.empty();
        }
        return orderRepository.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey)
                .map(existing -> new CheckoutResult(orderMapper.toResponse(existing), true));
    }
}
