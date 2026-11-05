package com.ecommerce.payment.integration;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:tc:postgresql:17.9:///vnpay_review","rate-limit.enabled=false"})
@AutoConfigureMockMvc
class VnpayReviewIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @BeforeEach void seed() {
        jdbc.execute("TRUNCATE idempotency_keys,voucher_usages,vouchers,product_discounts,order_discounts,promotion_programs,order_items,payments,orders,cart_items,carts,product_variants,products,refresh_tokens,users RESTART IDENTITY CASCADE");
        jdbc.execute("INSERT INTO users(id,email,password,role) VALUES (1,'first@test.local','test','USER'),(2,'second@test.local','test','USER')");
        jdbc.execute("INSERT INTO orders(id,user_id,total_price,subtotal_price,status,payment_method) VALUES (1,1,9000,9000,'CANCELLED','VNPAY'),(2,2,10000,10000,'CANCELLED','VNPAY'),(3,1,11000,11000,'DONE','MOMO')");
        jdbc.execute("INSERT INTO payments(order_id,amount,payment_method,payment_status,refund_status,reason,provider_txn_ref,provider_environment,expires_at) VALUES (1,9000,'VNPAY','PENDING','RECONCILIATION_REQUIRED','PAYMENT_TIMEOUT_UNVERIFIED','REVIEW1','SANDBOX',now()-interval '1 hour'),(2,10000,'VNPAY','PAID','REQUIRED_MANUAL','LATE_PAID_AFTER_CANCEL','REVIEW2','SANDBOX',now()-interval '1 hour'),(3,11000,'MOMO','PAID',NULL,NULL,NULL,NULL,NULL)");
        jdbc.execute("UPDATE payments SET paid_at=now(),provider_transaction_no='123456' WHERE order_id=2");
        jdbc.execute("UPDATE payments SET transaction_id='LEGACYTX',momo_order_id='OLDREF' WHERE order_id=3");
    }
    @Test void adminFiltersMethodMoneyStatusAndReviewStatusTogether() throws Exception {
        mvc.perform(get("/api/v1/admin/orders").with(user("admin@test.local").authorities(() -> "ADMIN"))
                .param("paymentMethod","VNPAY").param("paymentStatus","PENDING").param("refundStatus","RECONCILIATION_REQUIRED")
                .param("status","CANCELLED").param("email"," first "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].reason").value("PAYMENT_TIMEOUT_UNVERIFIED"));
        mvc.perform(get("/api/v1/admin/orders").with(user("admin@test.local").authorities(() -> "ADMIN"))
                .param("paymentMethod","VNPAY").param("paymentStatus","PAID").param("refundStatus","REQUIRED_MANUAL"))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].providerTransactionNo").value("123456"));
    }
    @Test void ownerSeesSeparateOrderAndPaymentStateButNotAnotherPersonsOrderOrAdminQueue() throws Exception {
        mvc.perform(get("/api/v1/orders/1").with(user("first@test.local"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED")).andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.refundStatus").value("RECONCILIATION_REQUIRED"))
                .andExpect(jsonPath("$.expiresAt").exists()).andExpect(jsonPath("$.paymentUrl").doesNotExist());
        mvc.perform(get("/api/v1/orders/2").with(user("first@test.local"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/orders").with(user("first@test.local"))).andExpect(status().isForbidden());
    }
    @Test void legacyMomoStillReadsWithoutInventingVnpayMetadata() throws Exception {
        mvc.perform(get("/api/v1/orders/3").with(user("first@test.local"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentMethod").value("MOMO")).andExpect(jsonPath("$.paymentStatus").value("PAID"));
        mvc.perform(get("/api/v1/payments/order/3").with(user("first@test.local")))
                .andExpect(jsonPath("$.transactionId").value("LEGACYTX"));
    }
}
