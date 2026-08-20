package com.ecommerce.integration;

import com.ecommerce.config.JwtAuthenticationFilter;
import com.ecommerce.ratelimit.RateLimitFilter;
import com.ecommerce.controller.CheckoutController;
import com.ecommerce.dto.request.CheckoutRequest;
import com.ecommerce.dto.response.CheckoutResponse;
import com.ecommerce.dto.response.PaymentResponse;
import com.ecommerce.enums.OrderStatus;
import com.ecommerce.enums.PaymentMethod;
import com.ecommerce.enums.PaymentStatus;
import com.ecommerce.repository.IdempotencyKeyRepository;
import com.ecommerce.service.CheckoutService;
import com.ecommerce.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CheckoutController.class)
@AutoConfigureMockMvc(addFilters = false)
class CheckoutControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private CheckoutService checkoutService;
    @MockBean private JwtService jwtService;
    @MockBean private UserDetailsService userDetailsService;
    @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean private RateLimitFilter rateLimitFilter;
    @MockBean private IdempotencyKeyRepository idempotencyKeyRepository;

    @Test
    @WithMockUser(username = "user@gmail.com")
    void checkout_WithCod_ShouldReturn201WithPayment() throws Exception {
        CheckoutRequest request = new CheckoutRequest();
        request.setPaymentMethod(PaymentMethod.COD);
        request.setFirstName("Test");
        request.setLastName("User");
        request.setAddress("123 Street");
        request.setCity("HCM");
        request.setWard("Ward 1");
        request.setPhone("0123456789");

        PaymentResponse paymentResponse = PaymentResponse.builder()
                .id(1L).orderId(1L)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.PENDING)
                .amount(BigDecimal.valueOf(500000))
                .build();

        CheckoutResponse checkoutResponse = CheckoutResponse.builder()
                .orderId(1L)
                .orderStatus(OrderStatus.PENDING)
                .totalPrice(BigDecimal.valueOf(500000))
                .paymentResponse(paymentResponse)
                .paymentUrl(null)
                .build();

        when(checkoutService.checkout(any(), any(CheckoutRequest.class), anyString()))
                .thenReturn(checkoutResponse);

        mockMvc.perform(post("/api/v1/checkout").header("Idempotency-Key", "test-uuid-1234")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.orderStatus").value("PENDING"))
                .andExpect(jsonPath("$.paymentResponse.paymentMethod").value("COD"))
                .andExpect(jsonPath("$.paymentUrl").doesNotExist());
    }

    @Test
    @WithMockUser(username = "user@gmail.com")
    void checkout_WithMomo_ShouldReturn201WithPaymentUrl() throws Exception {
        CheckoutRequest request = new CheckoutRequest();
        request.setPaymentMethod(PaymentMethod.MOMO);
        request.setFirstName("Test");
        request.setLastName("User");
        request.setAddress("123 Street");
        request.setCity("HCM");
        request.setWard("Ward 1");
        request.setPhone("0123456789");

        CheckoutResponse checkoutResponse = CheckoutResponse.builder()
                .orderId(2L)
                .orderStatus(OrderStatus.AWAITING_PAYMENT)
                .totalPrice(BigDecimal.valueOf(200000))
                .paymentUrl("https://test-payment.momo.vn/pay/abc123")
                .build();

        when(checkoutService.checkout(any(), any(CheckoutRequest.class), anyString()))
                .thenReturn(checkoutResponse);

        mockMvc.perform(post("/api/v1/checkout").header("Idempotency-Key", "test-uuid-1234")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(2))
                .andExpect(jsonPath("$.orderStatus").value("AWAITING_PAYMENT"))
                .andExpect(jsonPath("$.paymentUrl").value("https://test-payment.momo.vn/pay/abc123"));
    }

    @Test
    void checkout_WithNullPaymentMethod_ShouldReturn400() throws Exception {
        CheckoutRequest request = new CheckoutRequest();
        // paymentMethod = null (không set gì cả)
        request.setPaymentMethod(null);
        request.setFirstName("Test");
        request.setLastName("User");
        request.setAddress("123 Street");
        request.setCity("HCM");
        request.setWard("Ward 1");
        request.setPhone("0123456789");

        mockMvc.perform(post("/api/v1/checkout").header("Idempotency-Key", "test-uuid-1234")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }
}
