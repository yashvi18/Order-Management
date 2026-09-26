package com.ecommerce.oms.cart;

import com.ecommerce.oms.auth.AppUserDetails;
import com.ecommerce.oms.cart.CartDtos.AddItemRequest;
import com.ecommerce.oms.cart.CartDtos.CartResponse;
import com.ecommerce.oms.cart.CartDtos.UpdateItemRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse get(@AuthenticationPrincipal AppUserDetails me) {
        return cartService.getCart(me.id());
    }

    @PostMapping("/items")
    public CartResponse add(@AuthenticationPrincipal AppUserDetails me, @Valid @RequestBody AddItemRequest request) {
        return cartService.addItem(me.id(), request.productId(), request.quantity());
    }

    @PutMapping("/items/{productId}")
    public CartResponse update(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long productId,
            @Valid @RequestBody UpdateItemRequest request) {
        return cartService.updateItem(me.id(), productId, request.quantity());
    }

    @DeleteMapping("/items/{productId}")
    public CartResponse remove(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long productId) {
        return cartService.removeItem(me.id(), productId);
    }

    @DeleteMapping
    public ResponseEntity<Void> clear(@AuthenticationPrincipal AppUserDetails me) {
        cartService.clear(me.id());
        return ResponseEntity.noContent().build();
    }
}
