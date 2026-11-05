package com.ecommerce.payment.integration;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:tc:postgresql:17.9:///vnpay_checkout",
        "payment.vnpay.enabled=true", "payment.momo.mock-enabled=false", "payment.vnpay.tmn-code=TESTCODE",
        "payment.vnpay.hash-secret=test-only-key", "payment.vnpay.return-url=https://merchant.test/api/v1/payments/vnpay/return",
        "app.shipping.flat-fee=0", "rate-limit.enabled=false"})
@AutoConfigureMockMvc
class VnpayCheckoutIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void seed() {
        jdbc.execute("TRUNCATE idempotency_keys,voucher_usages,vouchers,product_discounts,order_discounts,promotion_programs,order_items,payments,orders,cart_items,carts,product_variants,products,refresh_tokens,users RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO users(id,email,password,role) VALUES (1,'vnp@test.local','test','USER')");
        jdbc.update("INSERT INTO products(id,name,price,version) VALUES (1,'Product',5000,0)");
        jdbc.update("INSERT INTO product_variants(id,product_id,size_value,stock,version) VALUES (1,1,'M',5,0)");
        jdbc.update("INSERT INTO carts(id,user_id) VALUES (1,1)");
        jdbc.update("INSERT INTO cart_items(id,cart_id,product_variant_id,quantity) VALUES (1,1,1,1)");
    }
    String request(String method, int expected, String extra) {
        return """
                {"firstName":"Test","lastName":"Buyer","address":"Street","city":"City","ward":"Ward",
                 "phone":"0123456789","paymentMethod":"%s","expectedTotal":%d%s}
                """.formatted(method, expected, extra);
    }
    @ParameterizedTest @ValueSource(ints={0,4999,5000})
    void minimumIsAuthoritativeAndRollbackLeavesCodUsable(int total) throws Exception {
        jdbc.update("UPDATE products SET price=?",total);
        mvc.perform(post("/api/v1/checkout/preview").with(user("vnp@test.local"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.vnpay.enabled").value(true))
                .andExpect(jsonPath("$.vnpay.available").value(total >= 5000));
        var result = mvc.perform(post("/api/v1/checkout").with(user("vnp@test.local"))
                .header("Idempotency-Key","minimum").contentType(MediaType.APPLICATION_JSON).content(request("VNPAY",total,"")));
        if (total < 5000) {
            result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VNPAY_AMOUNT_BELOW_MINIMUM"));
            for (String table : new String[]{"orders","payments","idempotency_keys"})
                assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table,Integer.class)).isZero();
            mvc.perform(post("/api/v1/checkout").with(user("vnp@test.local")).header("Idempotency-Key","minimum")
                    .contentType(MediaType.APPLICATION_JSON).content(request("COD",total,""))).andExpect(status().isCreated());
        } else {
            result.andExpect(status().isCreated()).andExpect(jsonPath("$.orderStatus").value("AWAITING_PAYMENT"));
            assertThat(jdbc.queryForObject("SELECT stock FROM product_variants",Integer.class)).isEqualTo(5);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM cart_items",Integer.class)).isEqualTo(1);
        }
    }
    @Test void discountedSnapshotAndRetryNeverCreateAnotherSessionOrUsage() throws Exception {
        jdbc.update("UPDATE products SET price=10000");
        jdbc.execute("INSERT INTO promotion_programs(id,name,start_date,end_date) VALUES (1,'Test',now()-interval '1 day',now()+interval '1 day')");
        jdbc.execute("INSERT INTO vouchers(id,promotion_program_id,code,discount_percent,quantity) VALUES (1,1,'VNP10',10,2)");
        String body = request("VNPAY",9000,",\"voucherCode\":\"VNP10\"");
        mvc.perform(post("/api/v1/checkout").with(user("vnp@test.local")).header("Idempotency-Key","once")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentResponse.amount").value(9000));
        var before = jdbc.queryForMap("SELECT provider_txn_ref,payment_url,expires_at FROM payments");
        mvc.perform(post("/api/v1/checkout").with(user("vnp@test.local")).header("Idempotency-Key","once")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/checkout/result").with(user("vnp@test.local")).header("Idempotency-Key","once"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));
        jdbc.execute("UPDATE products SET price=500000");
        mvc.perform(post("/api/v1/orders/1/retry-payment").with(user("vnp@test.local")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paymentUrl").value(before.get("payment_url")));
        assertThat(jdbc.queryForMap("SELECT provider_txn_ref,payment_url,expires_at FROM payments")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT used_quantity FROM vouchers",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM voucher_usages",Integer.class)).isEqualTo(1);
    }
}
