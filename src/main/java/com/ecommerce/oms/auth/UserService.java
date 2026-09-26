package com.ecommerce.oms.auth;

import com.ecommerce.oms.auth.AuthDtos.RegisterRequest;
import com.ecommerce.oms.common.ConflictException;
import com.ecommerce.oms.common.NotFoundException;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User registerCustomer(RegisterRequest request) {
        return create(request.email(), request.password(), request.fullName(), Role.CUSTOMER, null);
    }

    @Transactional
    public User create(String email, String rawPassword, String fullName, Role role, Long warehouseId) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(normalized)) {
            throw new ConflictException("Email already registered");
        }
        User user = new User();
        user.setEmail(normalized);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setFullName(fullName.trim());
        user.setRole(role);
        user.setWarehouseId(warehouseId);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
    }
}
