package com.ecommerce.user.service;

import com.ecommerce.user.dto.response.AdminUserResponse;
import com.ecommerce.user.entity.User;
import com.ecommerce.order.enums.OrderStatus;
import com.ecommerce.user.enums.Role;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.user.repository.UserRepository;
import com.ecommerce.user.service.AdminUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private AdminUserService adminUserService;

    private User adminUser;
    private User regularUser;

    @BeforeEach
    void setUp() {
        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setEmail("admin@gmail.com");
        adminUser.setRole(Role.ADMIN);

        regularUser = new User();
        regularUser.setId(2L);
        regularUser.setEmail("user@gmail.com");
        regularUser.setRole(Role.USER);
    }

    // =========================================================================
    // updateRole — business rule: không tự đổi role chính mình
    // =========================================================================

    @Test
    void updateRole_WhenAdminTriesToChangeOwnRole_ShouldThrowIllegalArgumentException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> adminUserService.updateRole(1L, Role.USER, "admin@gmail.com")
        );

        assertTrue(ex.getMessage().contains("chính mình") || ex.getMessage().toLowerCase().contains("own"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateRole_WhenAdminChangesAnotherUserRole_ShouldSucceed() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(regularUser));
        when(userRepository.save(regularUser)).thenReturn(regularUser);
        when(orderRepository.countByUserId(2L)).thenReturn(0L);
        when(orderRepository.countByUserIdAndStatus(2L, OrderStatus.DONE)).thenReturn(0L);

        AdminUserResponse result = adminUserService.updateRole(2L, Role.ADMIN, "admin@gmail.com");

        assertNotNull(result);
        assertEquals(Role.ADMIN, regularUser.getRole());
        verify(userRepository, times(1)).save(regularUser);
    }

    @Test
    void updateRole_WhenUserNotFound_ShouldThrowResourceNotFoundException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> adminUserService.updateRole(999L, Role.ADMIN, "admin@gmail.com")
        );
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateRole_WhenCallerEmailDiffersFromTargetEmail_ShouldNotThrow() {
        // Caller là admin@gmail.com, target là user@gmail.com — khác nhau → OK
        when(userRepository.findById(2L)).thenReturn(Optional.of(regularUser));
        when(userRepository.save(regularUser)).thenReturn(regularUser);
        when(orderRepository.countByUserId(2L)).thenReturn(5L);
        when(orderRepository.countByUserIdAndStatus(2L, OrderStatus.DONE)).thenReturn(3L);

        assertDoesNotThrow(() -> adminUserService.updateRole(2L, Role.ADMIN, "admin@gmail.com"));
    }

    // =========================================================================
    // getUserById
    // =========================================================================

    @Test
    void getUserById_WhenUserExists_ShouldReturnResponse() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(regularUser));
        when(orderRepository.countByUserId(2L)).thenReturn(3L);
        when(orderRepository.countByUserIdAndStatus(2L, OrderStatus.DONE)).thenReturn(1L);

        AdminUserResponse result = adminUserService.getUserById(2L);

        assertNotNull(result);
        assertEquals("user@gmail.com", result.getEmail());
        assertEquals(Role.USER, result.getRole());
        assertEquals(3L, result.getTotalOrders());
        assertEquals(1L, result.getCompletedOrders());
    }

    @Test
    void getUserById_WhenNotFound_ShouldThrowResourceNotFoundException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> adminUserService.getUserById(999L));
    }

    // =========================================================================
    // getAllUsers — batch stats query
    // =========================================================================

    @Test
    void getAllUsers_WhenNoFilters_ShouldCallFindAllAndBatchStats() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> page = new PageImpl<>(List.of(adminUser, regularUser), pageable, 2);

        when(userRepository.findAll(pageable)).thenReturn(page);
        // Batch stats trả về 1 row cho mỗi user có đơn hàng — regularUser có 2 đơn
        Object[] statsRow = new Object[]{ 2L, 2L, 1L };
        List<Object[]> statsList = new java.util.ArrayList<>();
        statsList.add(statsRow);
        when(orderRepository.countOrderStatsByUserIds(List.of(1L, 2L)))
                .thenReturn(statsList);

        var result = adminUserService.getAllUsers(null, null, pageable);

        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
        // adminUser không có trong stats → default 0, 0
        verify(orderRepository, times(1)).countOrderStatsByUserIds(List.of(1L, 2L));
        // Không gọi countByUserId riêng lẻ
        verify(orderRepository, never()).countByUserId(anyLong());
    }

    @Test
    void getAllUsers_WhenUserHasNoOrders_ShouldDefaultToZeroStats() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> page = new PageImpl<>(List.of(regularUser), pageable, 1);

        when(userRepository.findAll(pageable)).thenReturn(page);
        // Không có row nào trong stats (user chưa có đơn hàng)
        when(orderRepository.countOrderStatsByUserIds(List.of(2L))).thenReturn(List.of());

        var result = adminUserService.getAllUsers(null, null, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        // stats mặc định là 0, 0 — không throw exception
    }

    @Test
    void getAllUsers_WhenFilterByRole_ShouldCallFindByRole() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> page = new PageImpl<>(List.of(regularUser), pageable, 1);

        when(userRepository.findByRole(Role.USER, pageable)).thenReturn(page);
        when(orderRepository.countOrderStatsByUserIds(List.of(2L))).thenReturn(List.of());

        var result = adminUserService.getAllUsers(Role.USER, null, pageable);

        assertNotNull(result);
        verify(userRepository, times(1)).findByRole(Role.USER, pageable);
    }

    @Test
    void getAllUsers_WhenFilterByEmail_ShouldCallFindByEmail() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> page = new PageImpl<>(List.of(regularUser), pageable, 1);

        when(userRepository.findByEmailContainingIgnoreCase("user", pageable)).thenReturn(page);
        when(orderRepository.countOrderStatsByUserIds(List.of(2L))).thenReturn(List.of());

        var result = adminUserService.getAllUsers(null, " user ", pageable);

        assertNotNull(result);
        verify(userRepository, times(1)).findByEmailContainingIgnoreCase("user", pageable);
    }

    @Test
    void getAllUsers_WhenFilterByRoleAndEmail_ShouldCallFindByRoleAndEmail() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> page = new PageImpl<>(List.of(regularUser), pageable, 1);

        when(userRepository.findByRoleAndEmailContainingIgnoreCase(Role.USER, "user", pageable)).thenReturn(page);
        when(orderRepository.countOrderStatsByUserIds(List.of(2L))).thenReturn(List.of());

        var result = adminUserService.getAllUsers(Role.USER, "user", pageable);

        assertNotNull(result);
        verify(userRepository, times(1)).findByRoleAndEmailContainingIgnoreCase(Role.USER, "user", pageable);
    }
}
