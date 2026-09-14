package com.ecommerce.service;

import com.ecommerce.dto.response.AdminUserResponse;
import com.ecommerce.dto.response.PageResponse;
import com.ecommerce.entity.User;
import com.ecommerce.enums.Role;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    /**
     * Lấy danh sách tất cả users — hỗ trợ filter theo role và search theo email.
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> getAllUsers(Role role, String email, Pageable pageable) {
        boolean hasEmail = StringUtils.hasText(email);
        Page<User> users;

        if (role != null && hasEmail) {
            users = userRepository.findByRoleAndEmailContainingIgnoreCase(role, email.trim(), pageable);
        } else if (role != null) {
            users = userRepository.findByRole(role, pageable);
        } else if (hasEmail) {
            users = userRepository.findByEmailContainingIgnoreCase(email.trim(), pageable);
        } else {
            users = userRepository.findAll(pageable);
        }

        return PageResponse.of(users.map(this::toAdminUserResponse));
    }

    /**
     * Lấy chi tiết 1 user.
     */
    @Transactional(readOnly = true)
    public AdminUserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return toAdminUserResponse(user);
    }

    /**
     * Đổi role của user (USER <-> ADMIN).
     * Lưu ý: không cho đổi role chính mình trong controller.
     */
    @Transactional
    public AdminUserResponse updateRole(Long id, Role newRole) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        log.info("Admin changing role of user {} from {} to {}", id, user.getRole(), newRole);
        user.setRole(newRole);
        User saved = userRepository.save(user);
        return toAdminUserResponse(saved);
    }

    // ---- Mapper ----
    private AdminUserResponse toAdminUserResponse(User user) {
        long totalOrders = orderRepository.countByUserId(user.getId());
        long completedOrders = orderRepository.countByUserIdAndStatus(user.getId(), OrderStatus.DONE);

        return AdminUserResponse.builder()
            .id(user.getId())
            .email(user.getEmail())
            .role(user.getRole())
            .totalOrders(totalOrders)
            .completedOrders(completedOrders)
            .build();
    }
}
