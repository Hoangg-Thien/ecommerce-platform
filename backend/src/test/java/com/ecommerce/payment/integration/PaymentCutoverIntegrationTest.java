package com.ecommerce.payment.integration;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Legacy flags are intentionally supplied to prove they cannot reactivate a removed gateway. */
@SpringBootTest(properties={"spring.datasource.url=jdbc:tc:postgresql:17.9:///payment_cutover",
        "payment.vnpay.enabled=false", "payment.momo.mock-enabled=true", "rate-limit.enabled=false"})
@AutoConfigureMockMvc
class PaymentCutoverIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;
    @Autowired com.ecommerce.payment.scheduler.PaymentTimeoutScheduler scheduler;
    @Autowired com.ecommerce.order.service.OrderCancellationService cancellation;

    @BeforeEach void seed() {
        jdbc.execute("TRUNCATE idempotency_keys,voucher_usages,vouchers,product_discounts,order_discounts,promotion_programs,order_items,payments,orders,cart_items,carts,product_variants,products,refresh_tokens,users RESTART IDENTITY CASCADE");
        jdbc.execute("INSERT INTO users(id,email,password,role) VALUES (1,'cutover@test.local','test','USER')");
        jdbc.execute("INSERT INTO products(id,name,price,version) VALUES (1,'Product',10000,0)");
        jdbc.execute("INSERT INTO product_variants(id,product_id,size_value,stock,version) VALUES (1,1,'M',5,0)");
        jdbc.execute("INSERT INTO carts(id,user_id) VALUES (1,1)");
        jdbc.execute("INSERT INTO cart_items(cart_id,product_variant_id,quantity) VALUES (1,1,1)");
    }
    String body(String method) {
        return """
            {"firstName":"Test","lastName":"Buyer","address":"Street","city":"City","ward":"Ward",
             "phone":"0123456789","paymentMethod":"%s","expectedTotal":40000}
            """.formatted(method);
    }
    void legacy() {
        jdbc.execute("INSERT INTO orders(id,user_id,total_price,subtotal_price,status,payment_method,create_at,version) VALUES (1,1,10000,10000,'AWAITING_PAYMENT','MOMO',now()-interval '1 day',0)");
        jdbc.execute("INSERT INTO payments(order_id,amount,payment_method,payment_status,momo_order_id,momo_request_id,transaction_id,version) VALUES (1,10000,'MOMO','PENDING','OLDREF','OLDREQUEST','OLDTX',0)");
        jdbc.execute("INSERT INTO order_items(order_id,product_id,size,quantity,price,original_price) VALUES (1,1,'M',1,10000,10000)");
        jdbc.execute("INSERT INTO promotion_programs(id,name,start_date,end_date) VALUES (1,'Old',now()-interval '1 day',now()+interval '1 day')");
        jdbc.execute("INSERT INTO vouchers(id,promotion_program_id,code,discount_percent,quantity,used_quantity) VALUES (1,1,'OLD10',10,5,1)");
        jdbc.execute("INSERT INTO voucher_usages(voucher_id,user_id,order_id,discount_amount) VALUES (1,1,1,1000)");
    }
    @Test void disabledVnpayNeverFallsBackToMomoAndCodStillWorks() throws Exception {
        for (String method : new String[]{"MOMO","VNPAY"}) {
            mvc.perform(post("/api/v1/checkout").with(user("cutover@test.local")).header("Idempotency-Key",method)
                    .contentType(MediaType.APPLICATION_JSON).content(body(method))).andExpect(status().isBadRequest());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM orders",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_keys",Integer.class)).isZero();
        }
        mvc.perform(post("/api/v1/checkout").with(user("cutover@test.local")).header("Idempotency-Key","COD")
                .contentType(MediaType.APPLICATION_JSON).content(body("COD"))).andExpect(status().isCreated());
    }
    @Test void retiredEndpointsCannotMutateLegacyPaymentEvenWithOldFlagEnabled() throws Exception {
        legacy();
        var before=jdbc.queryForMap("SELECT * FROM payments WHERE order_id=1");
        mvc.perform(post("/api/v1/mock-payments/1/simulate").with(user("cutover@test.local"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"scenario\":\"SUCCESS\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/payments/momo/ipn").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/payments/momo/return").param("resultCode","0")).andExpect(status().isForbidden());
        assertThat(jdbc.queryForMap("SELECT * FROM payments WHERE order_id=1")).isEqualTo(before);
    }
    @Test void pendingLegacyIsReadOnlyToRetryAndAutomaticTimeout() throws Exception {
        legacy();
        var payment=jdbc.queryForMap("SELECT * FROM payments WHERE order_id=1");
        var order=jdbc.queryForMap("SELECT * FROM orders WHERE id=1");
        scheduler.cancelExpiredOnlinePayments(); cancellation.cancelExpiredOrder(1L);
        mvc.perform(post("/api/v1/orders/1/retry-payment").with(user("cutover@test.local")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/payments/order/1").with(user("cutover@test.local")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.transactionId").value("OLDTX"));
        mvc.perform(get("/api/v1/orders/1").with(user("cutover@test.local")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paymentMethod").value("MOMO"));
        assertThat(jdbc.queryForMap("SELECT * FROM payments WHERE order_id=1")).isEqualTo(payment);
        assertThat(jdbc.queryForMap("SELECT * FROM orders WHERE id=1")).isEqualTo(order);
        assertThat(jdbc.queryForObject("SELECT used_quantity FROM vouchers",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT stock FROM product_variants",Integer.class)).isEqualTo(5);
    }
    @Test void noRetiredGatewayBeansCanBeReactivated() {
        for (String bean : new String[]{"momoConfig","momoPaymentStrategy","momoServiceImpl","momoIpnService",
                "mockPaymentController","mockPaymentService","paymentRefundService"})
            assertThat(context.containsBean(bean)).as(bean).isFalse();
    }
    @Test void checkoutOpenApiOffersOnlyCodAndVnpay() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.CheckoutRequest.properties.paymentMethod.enum")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("COD","VNPAY")));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"PENDING","PAID","FAILED","REFUNDING","REFUNDED"})
    void allHistoricalStatusesAndIdsRemainReadable(String state) throws Exception {
        legacy(); jdbc.update("UPDATE payments SET payment_status=? WHERE order_id=1",state);
        mvc.perform(get("/api/v1/payments/order/1").with(user("cutover@test.local")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paymentStatus").value(state))
                .andExpect(jsonPath("$.transactionId").value("OLDTX"));
        assertThat(jdbc.queryForMap("SELECT momo_order_id,momo_request_id FROM payments WHERE order_id=1"))
                .containsEntry("momo_order_id","OLDREF").containsEntry("momo_request_id","OLDREQUEST");
    }
}
