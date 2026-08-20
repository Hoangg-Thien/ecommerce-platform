package com.ecommerce.integration;

import com.ecommerce.config.JwtAuthenticationFilter;
import com.ecommerce.ratelimit.RateLimitFilter;
import com.ecommerce.controller.PaymentController;
import com.ecommerce.dto.request.MomoIpnRequest;
import com.ecommerce.dto.response.PaymentResponse;
import com.ecommerce.enums.PaymentMethod;
import com.ecommerce.enums.PaymentStatus;
import com.ecommerce.mapper.PaymentMapper;
import com.ecommerce.repository.IdempotencyKeyRepository;
import com.ecommerce.repository.PaymentRepository;
import com.ecommerce.service.JwtService;
import com.ecommerce.service.MomoIpnService;
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

    @MockBean private MomoIpnService momoIpnService;
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

    // ===== IPN endpoint =====

    @Test
    void handleMomoIpn_WhenSignatureValid_ShouldReturn200() throws Exception {
        MomoIpnRequest ipn = buildIpnRequest(0);
        doNothing().when(momoIpnService).handleIpn(any(MomoIpnRequest.class));

        mockMvc.perform(post("/api/v1/payments/momo/ipn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ipn)))
                .andExpect(status().isOk());
    }

    @Test
    void handleMomoIpn_WhenSignatureInvalid_ShouldReturn400() throws Exception {
        MomoIpnRequest ipn = buildIpnRequest(0);
        doThrow(new SecurityException("Invalid MoMo signature"))
                .when(momoIpnService).handleIpn(any(MomoIpnRequest.class));

        mockMvc.perform(post("/api/v1/payments/momo/ipn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ipn)))
                .andExpect(status().isBadRequest());
    }

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

        com.ecommerce.entity.User user = new com.ecommerce.entity.User();
        user.setEmail("test@example.com");
        com.ecommerce.entity.Order order = new com.ecommerce.entity.Order();
        order.setUser(user);
        com.ecommerce.entity.Payment payment = new com.ecommerce.entity.Payment();
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
        com.ecommerce.entity.User userB = new com.ecommerce.entity.User();
        userB.setEmail("userB@example.com"); // Owner of the order is userB
        
        com.ecommerce.entity.Order order = new com.ecommerce.entity.Order();
        order.setUser(userB);
        
        com.ecommerce.entity.Payment payment = new com.ecommerce.entity.Payment();
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

    // ===== Helper =====

    private MomoIpnRequest buildIpnRequest(int resultCode) {
        MomoIpnRequest req = new MomoIpnRequest();
        req.setOrderId("ORDER_1_123456");
        req.setResultCode(resultCode);
        req.setTransId("987654321");

        req.setAmount(BigDecimal.valueOf(100000));
        req.setMessage("Successful.");
        req.setExtraData("");
        req.setOrderInfo("Thanh toan don hang #1");
        req.setOrderType("momo_wallet");
        req.setPartnerCode("MOMO_TEST");
        req.setPayType("qr");
        req.setRequestId("req-001");
        req.setResponseTime(1722955200000L);
        req.setSignature("any-signature");
        return req;
    }
}
