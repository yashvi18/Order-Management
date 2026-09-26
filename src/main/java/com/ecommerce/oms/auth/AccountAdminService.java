package com.ecommerce.oms.auth;

import com.ecommerce.oms.auth.AuthDtos.CreateUserRequest;
import com.ecommerce.oms.auth.AuthDtos.UserResponse;
import com.ecommerce.oms.common.BadRequestException;
import com.ecommerce.oms.common.NotFoundException;
import com.ecommerce.oms.inventory.WarehouseRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountAdminService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;

    public AccountAdminService(UserService userService, UserRepository userRepository,
            WarehouseRepository warehouseRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.warehouseRepository = warehouseRepository;
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (request.role() == Role.WAREHOUSE_STAFF) {
            if (request.warehouseId() == null) {
                throw new BadRequestException("warehouseId is required for warehouse staff");
            }
            if (!warehouseRepository.existsById(request.warehouseId())) {
                throw new NotFoundException("Warehouse not found");
            }
        } else if (request.warehouseId() != null) {
            throw new BadRequestException("warehouseId is only valid for warehouse staff");
        }
        return UserResponse.from(userService.create(request.email(), request.password(), request.fullName(),
                request.role(), request.warehouseId()));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return userRepository.findAll(Sort.by("id")).stream().map(UserResponse::from).toList();
    }
}
