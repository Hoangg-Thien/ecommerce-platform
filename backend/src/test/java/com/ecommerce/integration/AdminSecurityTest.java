package com.ecommerce.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests kiểm tra bảo mật toàn bộ /api/v1/admin/** endpoints.
 *
 * Mục tiêu:
 *  (1) User thường (ROLE_USER) bị chặn 403 trên mọi admin endpoint.
 *  (2) Request chưa xác thực bị chặn 401/403.
 *  (3) Admin (ROLE_ADMIN) được phép gọi admin endpoints (status 2xx hoặc 4xx từ logic, không phải 403).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    // =========================================================================
    // /api/v1/admin/orders
    // =========================================================================

    @Test
    @WithMockUser(username = "user@gmail.com", authorities = "USER")
    void getAdminOrders_AsUser_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAdminOrders_Unauthenticated_ShouldReturn401Or403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assert status == 401 || status == 403
                            : "Expected 401 or 403 but got " + status;
                });
    }

    @Test
    @WithMockUser(username = "admin@gmail.com", authorities = "ADMIN")
    void getAdminOrders_AsAdmin_ShouldNotReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assert status != 403 : "Admin should not receive 403";
                });
    }

    @Test
    @WithMockUser(username = "user@gmail.com", authorities = "USER")
    void patchAdminOrderStatus_AsUser_ShouldReturn403() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/orders/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderStatus\":\"CONFIRMED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "user@gmail.com", authorities = "USER")
    void getAdminOrderById_AsUser_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders/1"))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // /api/v1/admin/users
    // =========================================================================

    @Test
    @WithMockUser(username = "user@gmail.com", authorities = "USER")
    void getAdminUsers_AsUser_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "user@gmail.com", authorities = "USER")
    void patchAdminUserRole_AsUser_ShouldReturn403() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/1/role")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "user@gmail.com", authorities = "USER")
    void getAdminUserById_AsUser_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/1"))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // /api/v1/admin/dashboard
    // =========================================================================

    @Test
    @WithMockUser(username = "user@gmail.com", authorities = "USER")
    void getDashboard_AsUser_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@gmail.com", authorities = "ADMIN")
    void getDashboard_AsAdmin_ShouldNotReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assert status != 403 : "Admin should not receive 403 on dashboard";
                });
    }
}
