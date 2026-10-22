package com.ecommerce.user.service;

import com.ecommerce.user.dto.response.AdminUserResponse;
import com.ecommerce.common.dto.PageResponse;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.enums.Role;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.user.repository.UserRepository;
import com.ecommerce.order.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

        // Lấy toàn bộ userIds trên trang hiện tại
        List<Long> userIds = users.stream().map(User::getId).toList();

        // 1 query GROUP BY duy nhất thay vì N×2 COUNT queries
        Map<Long, long[]> statsMap = orderRepository.countOrderStatsByUserIds(userIds)
            .stream()
            .collect(Collectors.toMap(
                row -> (Long) row[0],
                row -> new long[]{ ((Number) row[1]).longValue(), ((Number) row[2]).longValue() }
            ));

        return PageResponse.of(users.map(user -> {
            long[] stats = statsMap.getOrDefault(user.getId(), new long[]{0L, 0L});
            return buildAdminUserResponse(user, stats[0], stats[1]);
        }));
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
     * Đổi role của user (USER ↔ ADMIN).
     * Chú ý: không cho admin tự đổi role của chính mình.
     *
     * @param callerEmail email của admin đang gọi API (lấy từ token)
     */
    @Transactional
    public AdminUserResponse updateRole(Long id, Role newRole, String callerEmail) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        // Business rule: admin không được tự đổi role của chính mình
        if (user.getEmail().equals(callerEmail)) {
            throw new IllegalArgumentException("Không thể tự thay đổi vai trò của chính mình");
        }

        log.info("Admin {} changing role of user {} from {} to {}", callerEmail, id, user.getRole(), newRole);
        user.setRole(newRole);
        User saved = userRepository.save(user);
        return toAdminUserResponse(saved);
    }

    // ---- Mapper ----

    /** Dùng cho single-user paths (getUserById, updateRole) — chỉ 2 COUNT queries, chấp nhận được. */
    private AdminUserResponse toAdminUserResponse(User user) {
        long totalOrders = orderRepository.countByUserId(user.getId());
        long completedOrders = orderRepository.countByUserIdAndStatus(user.getId(), OrderStatus.DONE);
        return buildAdminUserResponse(user, totalOrders, completedOrders);
    }

    private AdminUserResponse buildAdminUserResponse(User user, long totalOrders, long completedOrders) {
        return AdminUserResponse.builder()
            .id(user.getId())
            .email(user.getEmail())
            .role(user.getRole())
            .totalOrders(totalOrders)
            .completedOrders(completedOrders)
            .build();
    }
}
