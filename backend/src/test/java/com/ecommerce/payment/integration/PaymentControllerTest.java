package com.ecommerce.payment.integration;

import com.ecommerce.order.entity.Order;
import com.ecommerce.payment.entity.Payment;

import com.ecommerce.auth.config.JwtAuthenticationFilter;
import com.ecommerce.common.ratelimit.RateLimitFilter;
import com.ecommerce.payment.controller.PaymentController;
import com.ecommerce.payment.dto.response.PaymentResponse;
import com.ecommerce.payment.enums.PaymentMethod;
import com.ecommerce.payment.enums.PaymentStatus;
import com.ecommerce.payment.mapper.PaymentMapper;
import com.ecommerce.common.idempotency.IdempotencyKeyRepository;
import com.ecommerce.payment.repository.PaymentRepository;
import com.ecommerce.auth.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private PaymentRepository paymentRepository;
    @MockBean private PaymentMapper paymentMapper;
    @MockBean private JwtService jwtService;
    @MockBean private UserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        UserDetails userDetails = User.withUsername("test@example.com").password("").roles("USER").build();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );
    }

    @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean private RateLimitFilter rateLimitFilter;
    @MockBean private IdempotencyKeyRepository idempotencyKeyRepository;

    // ===== GET payment status =====

    @Test
    void getPaymentByOrderId_WhenExists_ShouldReturn200WithPaymentInfo() throws Exception {
        PaymentResponse paymentResponse = PaymentResponse.builder()
                .id(1L).orderId(5L)
                .paymentMethod(PaymentMethod.MOMO)
                .paymentStatus(PaymentStatus.PAID)
                .amount(BigDecimal.valueOf(300000))
                .transactionId("MOMO_TXN_12345")
                .build();

        com.ecommerce.user.entity.User user = new com.ecommerce.user.entity.User();
        user.setEmail("test@example.com");
        com.ecommerce.order.entity.Order order = new com.ecommerce.order.entity.Order();
        order.setUser(user);
        com.ecommerce.payment.entity.Payment payment = new com.ecommerce.payment.entity.Payment();
        payment.setOrder(order);
        
        when(paymentRepository.findByOrderId(5L)).thenReturn(Optional.of(payment));
        when(paymentMapper.toPaymentResponse(payment)).thenReturn(paymentResponse);

        mockMvc.perform(get("/api/v1/payments/order/5")
                .principal(() -> "test@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(5))
                .andExpect(jsonPath("$.paymentMethod").value("MOMO"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.transactionId").value("MOMO_TXN_12345"));
    }
    @Test
    void getPaymentByOrderId_WhenUserIsNotOwner_ShouldReturn403() throws Exception {
        com.ecommerce.user.entity.User userB = new com.ecommerce.user.entity.User();
        userB.setEmail("userB@example.com"); // Owner of the order is userB
        
        com.ecommerce.order.entity.Order order = new com.ecommerce.order.entity.Order();
        order.setUser(userB);
        
        com.ecommerce.payment.entity.Payment payment = new com.ecommerce.payment.entity.Payment();
        payment.setOrder(order);
        
        when(paymentRepository.findByOrderId(5L)).thenReturn(Optional.of(payment));

        // The requester is userA (different from userB)
        mockMvc.perform(get("/api/v1/payments/order/5")
                .principal(() -> "userA@example.com"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPaymentByOrderId_WhenNotExists_ShouldReturn404() throws Exception {
        when(paymentRepository.findByOrderId(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/payments/order/999")
                .principal(() -> "test@example.com"))
                .andExpect(status().isNotFound());
    }

}
