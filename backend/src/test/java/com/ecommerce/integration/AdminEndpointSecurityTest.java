package com.ecommerce.integration;

import com.ecommerce.dto.request.ProductRequest;
import com.ecommerce.entity.User;
import com.ecommerce.enums.OrderStatus;
import com.ecommerce.enums.Role;
import com.ecommerce.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminEndpointSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    // =========================================================================
    // /api/v1/admin/orders/**
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
                    assert status == 401 || status == 403 : "Expected 401 or 403 but got " + status;
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
    void patchOrderStatus_WhenUserIsNonAdmin_ShouldReturn403() throws Exception {
        String body = "{\"orderStatus\":\"" + OrderStatus.CONFIRMED.name() + "\"}";
        mockMvc.perform(patch("/api/v1/admin/orders/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "user@gmail.com", authorities = "USER")
    void getAdminOrderById_AsUser_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders/1"))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // /api/v1/admin/users/**
    // =========================================================================

    @Test
    @WithMockUser(username = "user@gmail.com", authorities = "USER")
    void getAdminUsers_AsUser_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAdminUsers_Unauthenticated_ShouldReturn401Or403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assert status == 401 || status == 403 : "Expected 401 or 403 but got " + status;
                });
    }

    @Test
    @WithMockUser(username = "user@gmail.com", authorities = "USER")
    void getAdminUserById_AsUser_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/1"))
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
    @WithMockUser(username = "self-admin@test.com", authorities = "ADMIN")
    void patchAdminUserRole_WhenAdminChangesOwnRole_ShouldReturn400BadRequest() throws Exception {
        User selfAdmin = new User();
        selfAdmin.setEmail("self-admin@test.com");
        selfAdmin.setPassword("password123");
        selfAdmin.setRole(Role.ADMIN);
        selfAdmin = userRepository.save(selfAdmin);

        mockMvc.perform(patch("/api/v1/admin/users/" + selfAdmin.getId() + "/role")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"USER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(result -> {
                    String response = result.getResponse().getContentAsString();
                    assert response.contains("chính mình") || response.contains("own")
                            : "Response should indicate admin cannot change own role: " + response;
                });
    }

    @Test
    @WithMockUser(username = "caller-admin@test.com", authorities = "ADMIN")
    void patchAdminUserRole_WhenAdminChangesOtherUserRole_ShouldReturn200Ok() throws Exception {
        User targetUser = new User();
        targetUser.setEmail("target-member@test.com");
        targetUser.setPassword("password123");
        targetUser.setRole(Role.USER);
        targetUser = userRepository.save(targetUser);

        mockMvc.perform(patch("/api/v1/admin/users/" + targetUser.getId() + "/role")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
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
    void getDashboard_Unauthenticated_ShouldReturn401Or403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assert status == 401 || status == 403 : "Expected 401 or 403 but got " + status;
                });
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

    // =========================================================================
    // /api/v1/products (Admin write protection)
    // =========================================================================

    @Test
    @WithMockUser(username = "user@gmail.com", authorities = "USER")
    void createProduct_WhenUserIsNonAdmin_ShouldReturn403() throws Exception {
        ProductRequest request = new ProductRequest();
        request.setName("Test Product");
        request.setPrice(BigDecimal.valueOf(100));
        request.setCategoryId(1L);

        mockMvc.perform(post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
