package com.ecommerce.integration;

import com.ecommerce.controller.AuthController;
import com.ecommerce.config.JwtAuthenticationFilter;
import com.ecommerce.dto.request.LoginRequest;
import com.ecommerce.dto.request.RefreshTokenRequest;
import com.ecommerce.dto.request.RegisterRequest;
import com.ecommerce.dto.response.UserResponse;
import com.ecommerce.entity.User;
import com.ecommerce.enums.Role;
import com.ecommerce.exception.EmailAlreadyExistsException;
import com.ecommerce.repository.IdempotencyKeyRepository;
import com.ecommerce.service.JwtService;
import com.ecommerce.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false) // tat filter khi test rieng controller
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AuthenticationManager authenticationManager;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean 
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @MockBean
    private com.ecommerce.service.RefreshTokenService refreshTokenService;

    // ==========================================
    // REGISTER TESTS
    // ==========================================

    @Test
    void register_WithValidData_ShouldReturn201CreatedAndToken() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@example.com");
        request.setPassword("password123");

        UserResponse userResponse = new UserResponse(1L, "user@example.com", Role.USER);
        org.springframework.security.core.userdetails.User springUser =
                new org.springframework.security.core.userdetails.User("user@example.com", "pass", java.util.List.of());

        when(userService.register(any(RegisterRequest.class))).thenReturn(userResponse);
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(springUser);
        when(jwtService.generateToken(any())).thenReturn("dummy-jwt-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("dummy-refresh-token");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("dummy-jwt-token"))
                .andExpect(cookie().value("refreshToken", "dummy-refresh-token"))
                .andExpect(jsonPath("$.email").value("user@example.com"));
    }

    @Test
    void register_WhenEmailInvalidFormat_ShouldReturn400BadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("invalid-email-format");
        request.setPassword("password123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.messages.email").value("Invalid email format"));
    }

    @Test
    void register_WhenEmailIsBlank_ShouldReturn400BadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("");
        request.setPassword("password123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    void register_WhenPasswordTooShort_ShouldReturn400BadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@example.com");
        request.setPassword("12345"); // < 8 characters

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.messages.password").value("Password must be at least 8 characters long"));
    }

    @Test
    void register_WhenEmailAlreadyExists_ShouldReturn409Conflict() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("existing@example.com");
        request.setPassword("password123");

        when(userService.register(any(RegisterRequest.class)))
                .thenThrow(new EmailAlreadyExistsException("Email is already registered"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Email is already registered"));
    }

    // ==========================================
    // LOGIN TESTS
    // ==========================================

    @Test
    void login_WithValidCredentials_ShouldReturn200AndToken() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("password123");

        User user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setRole(Role.USER);

        org.springframework.security.core.userdetails.User springUser =
                new org.springframework.security.core.userdetails.User("user@example.com", "pass", java.util.List.of());

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(null);
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(springUser);
        when(jwtService.generateToken(any())).thenReturn("dummy-jwt-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("dummy-refresh-token");
        when(userService.findByEmail("user@example.com")).thenReturn(user);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("dummy-jwt-token"))
                .andExpect(cookie().value("refreshToken", "dummy-refresh-token"))
                .andExpect(jsonPath("$.email").value("user@example.com"));
    }

    @Test
    void login_WithWrongCredentials_ShouldReturn401Unauthorized() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("wrong-password");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void login_WhenEmailInvalidFormat_ShouldReturn400BadRequest() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("invalid-email");
        request.setPassword("password123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    void login_WhenPasswordIsBlank_ShouldReturn400BadRequest() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    // ==========================================
    // REFRESH TOKEN TESTS
    // ==========================================

    @Test
    void refreshToken_WithValidToken_ShouldReturn200AndNewAccessToken() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setRole(Role.USER);

        org.springframework.security.core.userdetails.User springUser =
                new org.springframework.security.core.userdetails.User("user@example.com", "pass", java.util.List.of());

        when(jwtService.extractUsername("valid-refresh-token")).thenReturn("user@example.com");
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(springUser);
        when(jwtService.isTokenValid("valid-refresh-token", springUser)).thenReturn(true);
        when(jwtService.generateToken(springUser)).thenReturn("new-access-token");
        when(userService.findByEmail("user@example.com")).thenReturn(user);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", "valid-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.email").value("user@example.com"));
    }

    @Test
    void refreshToken_WhenTokenExpiredOrInvalid_ShouldReturn401Unauthorized() throws Exception {
        org.springframework.security.core.userdetails.User springUser =
                new org.springframework.security.core.userdetails.User("user@example.com", "pass", java.util.List.of());

        when(jwtService.extractUsername("expired-refresh-token")).thenReturn("user@example.com");
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(springUser);
        when(jwtService.isTokenValid("expired-refresh-token", springUser)).thenReturn(false);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", "expired-refresh-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Refresh token is expired or invalid"));
    }

    @Test
    void refreshToken_WhenTokenMalformed_ShouldReturn401Unauthorized() throws Exception {
        when(jwtService.extractUsername("malformed-token")).thenThrow(new RuntimeException("Malformed token"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", "malformed-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid refresh token format or signature"));
    }

    @Test
    void refreshToken_WhenTokenMissing_ShouldReturn401Unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    // ==========================================
    // LOGOUT TESTS
    // ==========================================

    @Test
    void logout_WithRefreshToken_ShouldRevokeTokenAndClearCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", "valid-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("refreshToken", 0));
                
        verify(refreshTokenService, times(1)).revokeToken("valid-refresh-token");
    }

    @Test
    void logout_WithoutRefreshToken_ShouldClearCookieAndNotCallRevoke() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("refreshToken", 0));
                
        verify(refreshTokenService, never()).revokeToken(anyString());
    }
}
