package com.ecommerce.controller;

import com.ecommerce.dto.request.UpdateUserRoleRequest;
import com.ecommerce.dto.response.AdminUserResponse;
import com.ecommerce.dto.response.PageResponse;
import com.ecommerce.enums.Role;
import com.ecommerce.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    /**
     * GET /api/v1/admin/users?page=0&size=10&role=USER&email=xxx
     */
    @GetMapping
    public ResponseEntity<PageResponse<AdminUserResponse>> getAllUsers(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) Role role,
        @RequestParam(required = false) String email
    ) {
        if (page < 0) page = 0;
        if (size <= 0) size = 10;
        if (size > 100) size = 100;

        Pageable pageable = PageRequest.of(page, size, Sort.by("id").ascending());
        return ResponseEntity.ok(adminUserService.getAllUsers(role, email, pageable));
    }

    /**
     * GET /api/v1/admin/users/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<AdminUserResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(adminUserService.getUserById(id));
    }

    /**
     * PATCH /api/v1/admin/users/{id}/role
     * Đổi role của user — Admin không được tự đổi role của chính mình.
     */
    @PatchMapping("/{id}/role")
    public ResponseEntity<AdminUserResponse> updateRole(
        @PathVariable Long id,
        @Valid @RequestBody UpdateUserRoleRequest request,
        @AuthenticationPrincipal UserDetails currentUser
    ) {
        return ResponseEntity.ok(adminUserService.updateRole(id, request.getRole(), currentUser.getUsername()));
    }
}
