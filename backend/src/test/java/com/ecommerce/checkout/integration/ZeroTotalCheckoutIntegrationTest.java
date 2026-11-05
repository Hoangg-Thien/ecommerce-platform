package com.ecommerce.checkout.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:17.9:///zero_checkout",
        "app.shipping.flat-fee=0","payment.vnpay.enabled=true", "payment.vnpay.tmn-code=TESTCODE", "payment.vnpay.hash-secret=test-only-key", "payment.vnpay.return-url=https://merchant.test/api/v1/payments/vnpay/return", "rate-limit.enabled=false"
})
@AutoConfigureMockMvc
class ZeroTotalCheckoutIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void seed() {
        jdbc.execute("TRUNCATE idempotency_keys,voucher_usages,vouchers,product_discounts,order_discounts,promotion_programs,order_items,payments,orders,cart_items,carts,product_variants,products,refresh_tokens,users RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO users(id,email,password,role) VALUES (1,'zero@test.local','test','USER')");
        jdbc.update("INSERT INTO products(id,name,price,version) VALUES (1,'Free gift',100000,0)");
        jdbc.update("INSERT INTO product_variants(id,product_id,size_value,stock,version) VALUES (1,1,'M',1,0)");
        jdbc.update("INSERT INTO carts(id,user_id) VALUES (1,1)");
        jdbc.update("INSERT INTO cart_items(id,cart_id,product_variant_id,quantity) VALUES (1,1,1,1)");
        jdbc.update("INSERT INTO promotion_programs(id,name,start_date,end_date) VALUES (1,'Gift',now()-interval '1 day',now()+interval '1 day')");
        jdbc.update("INSERT INTO product_discounts(promotion_program_id,product_id,discount_percent) VALUES (1,1,100)");
    }

    @Test void zeroTotalCodSucceedsWhileVnpayRollsBackAndCanRetryWithSameKey() throws Exception {
        String request = """
                {"firstName":"Test","lastName":"Buyer","address":"Street","city":"City",
                 "ward":"Ward","phone":"0123456789","expectedTotal":0,"paymentMethod":"%s"}
                """;
        mvc.perform(post("/api/v1/checkout").with(user("zero@test.local")).header("Idempotency-Key","zero")
                        .contentType(MediaType.APPLICATION_JSON).content(request.formatted("VNPAY")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VNPAY_AMOUNT_BELOW_MINIMUM"));
        for (String table : new String[]{"orders","payments","idempotency_keys"}) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table,Integer.class)).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT stock FROM product_variants WHERE id=1",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cart_items",Integer.class)).isEqualTo(1);
        mvc.perform(post("/api/v1/checkout").with(user("zero@test.local")).header("Idempotency-Key","zero")
                        .contentType(MediaType.APPLICATION_JSON).content(request.formatted("COD")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.totalPrice").value(0))
                .andExpect(jsonPath("$.paymentResponse.amount").value(0));
        assertThat(jdbc.queryForObject("SELECT stock FROM product_variants WHERE id=1",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cart_items",Integer.class)).isZero();
    }
}
