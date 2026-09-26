package com.ecommerce.oms.auth;

import com.ecommerce.oms.auth.AuthDtos.RegisterRequest;
import com.ecommerce.oms.auth.AuthDtos.UserResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.registerCustomer(request);
        return ResponseEntity.created(URI.create("/api/auth/me")).body(UserResponse.from(user));
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AppUserDetails me) {
        return UserResponse.from(userService.getById(me.id()));
    }
}
