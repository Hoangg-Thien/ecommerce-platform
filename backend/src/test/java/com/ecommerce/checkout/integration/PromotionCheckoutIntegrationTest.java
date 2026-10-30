package com.ecommerce.checkout.integration;

import com.ecommerce.payment.service.MomoService;
import com.ecommerce.promotion.service.VoucherUsageService;
import com.ecommerce.promotion.entity.Voucher;
import com.ecommerce.promotion.repository.VoucherRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:tc:postgresql:17.9:///promotion_checkout","rate-limit.enabled=false"})
@AutoConfigureMockMvc
class PromotionCheckoutIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean MomoService momo;
    @MockitoSpyBean VoucherUsageService usageService;
    @Autowired VoucherRepository vouchers;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @BeforeEach void seed() {
        reset((VoucherUsageService) org.springframework.test.util.AopTestUtils.getUltimateTargetObject(usageService));
        jdbc.execute("TRUNCATE idempotency_keys,voucher_usages,vouchers,product_discounts,order_discounts,promotion_programs,order_items,payments,orders,cart_items,carts,product_variants,products,refresh_tokens,users RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO users(id,email,password,role) VALUES (1,'buyer@test.local','test','USER'),(2,'other@test.local','test','USER')");
        jdbc.update("INSERT INTO products(id,name,price,version) VALUES (1,'Product',200000,0)");
        jdbc.update("INSERT INTO product_variants(id,product_id,size_value,stock,version) VALUES (1,1,'M',10,0),(2,1,'L',10,0)");
        jdbc.update("INSERT INTO carts(id,user_id) VALUES (1,1),(2,2)");
        jdbc.update("INSERT INTO cart_items(id,cart_id,product_variant_id,quantity) VALUES (1,1,1,2),(2,1,2,1),(3,2,1,1)");
        jdbc.update("INSERT INTO promotion_programs(id,name,start_date,end_date) VALUES (1,'Sale',now()-interval '1 day',now()+interval '1 day')");
        jdbc.update("INSERT INTO vouchers(id,promotion_program_id,code,discount_percent,minimum_amount,maximum_discount,quantity) VALUES (1,1,'SALE10',10,300000,30000,5)");
        jdbc.update("INSERT INTO product_discounts(promotion_program_id,product_id,discount_percent) VALUES (1,1,20)");
        jdbc.update("INSERT INTO order_discounts(promotion_program_id,minimum_amount,discount_percent) VALUES (1,300000,5)");
    }
    Map<String,Object> request(String method, int total) {
        var r=new HashMap<String,Object>();
        r.put("firstName","Test"); r.put("lastName","Buyer"); r.put("address","Street");
        r.put("city","City"); r.put("ward","Ward"); r.put("phone","0123456789");
        r.put("paymentMethod",method); r.put("cartItemIds",List.of(1)); r.put("voucherCode","sale10"); r.put("expectedTotal",total);
        return r;
    }
    org.springframework.test.web.servlet.ResultActions checkout(Map<String,Object> r,String key) throws Exception {
        return mvc.perform(post("/api/v1/checkout").with(user("buyer@test.local")).header("Idempotency-Key",key)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(r)));
    }
    int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class); }
    void noCheckoutWrites() {
        assertThat(count("orders")).isZero(); assertThat(count("payments")).isZero();
        assertThat(count("voucher_usages")).isZero(); assertThat(count("idempotency_keys")).isZero();
        assertThat(jdbc.queryForObject("SELECT stock FROM product_variants WHERE id=1",Integer.class)).isEqualTo(10);
        assertThat(count("cart_items")).isEqualTo(3);
    }
    @Test void previewIsAuthenticatedSideEffectFreeAndExplainsInvalidVoucher() throws Exception {
        mvc.perform(post("/api/v1/checkout/preview").with(user("buyer@test.local")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"cartItemIds\":[1],\"voucherCode\":\"sale10\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subtotal").value(320000))
                .andExpect(jsonPath("$.total").value(320000)).andExpect(jsonPath("$.voucher.applied").value(true));
        mvc.perform(post("/api/v1/checkout/preview").with(user("buyer@test.local")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"cartItemIds\":[1],\"voucherCode\":\"BAD\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.voucher.reason").value("NOT_FOUND"))
                .andExpect(jsonPath("$.voucher.message").isString());
        mvc.perform(post("/api/v1/checkout/preview").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is4xxClientError());
        noCheckoutWrites();
    }
    @ParameterizedTest @ValueSource(strings={"COD","MOMO"})
    void checkoutPersistsSnapshotsAndReservesAfterOrderId(String method) throws Exception {
        checkout(request(method,320000),"success").andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalPrice").value(320000))
                .andExpect(jsonPath("$.paymentResponse.amount").value(320000));
        assertThat(count("orders")).isEqualTo(1); assertThat(count("voucher_usages")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT used_quantity FROM vouchers WHERE id=1",Integer.class)).isEqualTo(1);
        var order=jdbc.queryForMap("SELECT * FROM orders");
        assertThat((BigDecimal)order.get("subtotal_price")).isEqualByComparingTo("320000");
        assertThat((BigDecimal)order.get("discount_amount")).isEqualByComparingTo("30000");
        assertThat(order.get("voucher_code")).isEqualTo("SALE10");
        assertThat(jdbc.queryForObject("SELECT original_price FROM order_items",BigDecimal.class)).isEqualByComparingTo("200000");
        assertThat(jdbc.queryForObject("SELECT price FROM order_items",BigDecimal.class)).isEqualByComparingTo("160000");
        assertThat(count("cart_items")).isEqualTo(method.equals("COD")?2:3);
        assertThat(jdbc.queryForObject("SELECT stock FROM product_variants WHERE id=1",Integer.class)).isEqualTo(method.equals("COD")?8:10);
        verifyNoInteractions(momo);
    }
    @Test void priceChangedRollsBackKeyAndAllowsSameKeyRetry() throws Exception {
        checkout(request("COD",1),"retry").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRICE_CHANGED"))
                .andExpect(jsonPath("$.breakdown.total").value(320000));
        noCheckoutWrites();
        checkout(request("COD",320000),"retry").andExpect(status().isCreated());
    }
    @Test void adminEditBetweenPreviewAndCheckoutRequiresConfirmation() throws Exception {
        jdbc.update("UPDATE product_discounts SET discount_percent=15");
        checkout(request("COD",320000),"edited").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRICE_CHANGED"));
        noCheckoutWrites();
    }
    @ParameterizedTest @ValueSource(strings={"COD","MOMO"})
    void soldOutAfterStrategyRollsBackOrderPaymentStockCartAndKey(String method) throws Exception {
        doAnswer(invocation -> {
            entityManager.flush();
            assertThat(count("orders")).isEqualTo(1);
            assertThat(count("payments")).isEqualTo(1);
            assertThat(count("cart_items")).isEqualTo(method.equals("COD") ? 2 : 3);
            assertThat(jdbc.queryForObject("SELECT stock FROM product_variants WHERE id=1",Integer.class))
                    .isEqualTo(method.equals("COD") ? 8 : 10);
            jdbc.update("UPDATE vouchers SET used_quantity=quantity WHERE id=1");
            return invocation.callRealMethod();
        }).when((VoucherUsageService)org.springframework.test.util.AopTestUtils.getUltimateTargetObject(usageService))
                .reserve(any(Voucher.class),anyLong(),anyLong(),any(BigDecimal.class));
        checkout(request(method,320000),"sold-out").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VOUCHER_SOLD_OUT"));
        noCheckoutWrites();
        assertThat(jdbc.queryForObject("SELECT used_quantity FROM vouchers WHERE id=1",Integer.class)).isZero();
        mvc.perform(get("/api/v1/checkout/result").with(user("buyer@test.local"))
                .header("Idempotency-Key","sold-out")).andExpect(status().isNotFound());
    }
    @Test void lostResponseLookupIsOwnerOnlyAndDuplicateDoesNotCreateAnotherOrder() throws Exception {
        checkout(request("MOMO",320000),"lost").andExpect(status().isCreated());
        mvc.perform(get("/api/v1/checkout/result").with(user("buyer@test.local")).header("Idempotency-Key","lost"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalPrice").value(320000)).andExpect(jsonPath("$.id").isNumber());
        mvc.perform(get("/api/v1/checkout/result").with(user("other@test.local")).header("Idempotency-Key","lost")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/checkout/result").with(user("buyer@test.local")).header("Idempotency-Key","missing")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/checkout/result").with(user("buyer@test.local"))).andExpect(status().isBadRequest());
        jdbc.update("INSERT INTO idempotency_keys(id,created_at) VALUES ('legacy-unlinked',now())");
        mvc.perform(get("/api/v1/checkout/result").with(user("buyer@test.local")).header("Idempotency-Key","legacy-unlinked"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/checkout/result").header("Idempotency-Key","lost")).andExpect(status().is4xxClientError());
        checkout(request("MOMO",320000),"lost").andExpect(status().isBadRequest());
        assertThat(count("orders")).isEqualTo(1); assertThat(count("voucher_usages")).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"null","[]"})
    void nullAndEmptyIdsCheckoutEntireCart(String ids) throws Exception {
        var r=request("COD",486000); r.remove("voucherCode");
        r.put("cartItemIds",json.readValue(ids,Object.class));
        checkout(r,"all").andExpect(status().isCreated()).andExpect(jsonPath("$.totalPrice").value(486000));
        assertThat(count("order_items")).isEqualTo(2);
    }
    @ParameterizedTest @ValueSource(strings={"[999]","[1,999]","[3]","[null]"})
    void rejectsInvalidOrForeignCartIds(String ids) throws Exception {
        var r=request("COD",320000); r.put("cartItemIds",json.readValue(ids,Object.class));
        checkout(r,"invalid").andExpect(status().isBadRequest()); noCheckoutWrites();
    }
    @ParameterizedTest @ValueSource(strings={"null","\"320000\"","320000.5","true","{}"})
    void rejectsMissingOrWrongExpectedTotal(String value) throws Exception {
        var r=request("COD",320000); r.put("expectedTotal",json.readValue(value,Object.class));
        checkout(r,"invalid").andExpect(status().isBadRequest()); noCheckoutWrites();
    }
    @Test void invalidVoucherCannotSilentlyFallBackToAuto() throws Exception {
        var r=request("COD",334000); r.put("voucherCode","BAD");
        checkout(r,"bad").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VOUCHER_NOT_FOUND"));
        noCheckoutWrites();
    }

    @Test void emptyCartAndMissingExpectedTotalAreRejected() throws Exception {
        var r = request("COD",320000);
        r.remove("expectedTotal");
        checkout(r,"missing-total").andExpect(status().isBadRequest());
        noCheckoutWrites();
        jdbc.update("DELETE FROM cart_items WHERE cart_id=1");
        r = request("COD",320000);
        r.remove("cartItemIds");
        checkout(r,"empty").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMPTY_CHECKOUT"));
        assertThat(count("orders")).isZero();
        assertThat(count("idempotency_keys")).isZero();
    }

    @Test void betterAutomaticDiscountDoesNotReserveVoucher() throws Exception {
        jdbc.update("UPDATE order_discounts SET discount_percent=20");
        checkout(request("COD",286000),"auto").andExpect(status().isCreated());
        assertThat(count("voucher_usages")).isZero();
        assertThat(jdbc.queryForObject("SELECT order_discount_source FROM orders",String.class)).isEqualTo("ORDER_DISCOUNT");
        assertThat(jdbc.queryForObject("SELECT voucher_code FROM orders",String.class)).isNull();
    }

    @Test void equalTotalAcceptsCurrentBreakdownAndSnapshotSurvivesAdminEdits() throws Exception {
        jdbc.update("UPDATE product_discounts SET discount_percent=15");
        jdbc.update("UPDATE vouchers SET discount_percent=20,maximum_discount=50000");
        checkout(request("MOMO",320000),"snapshot").andExpect(status().isCreated());
        jdbc.update("UPDATE product_discounts SET discount_percent=50");
        jdbc.update("UPDATE vouchers SET discount_percent=90,code='NEWCODE'");
        mvc.perform(get("/api/v1/checkout/result").with(user("buyer@test.local")).header("Idempotency-Key","snapshot"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subtotalPrice").value(340000))
                .andExpect(jsonPath("$.discountAmount").value(50000))
                .andExpect(jsonPath("$.voucherCode").value("SALE10"))
                .andExpect(jsonPath("$.items[0].price").value(170000));
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void concurrentReservationsRollbackTheLosingCheckout(boolean sameUser) throws Exception {
        jdbc.update("UPDATE cart_items SET quantity=2 WHERE id=3");
        jdbc.update("UPDATE vouchers SET quantity=? WHERE id=1",sameUser ? 5 : 1);
        var bothStrategiesSaved = new CountDownLatch(2);
        doAnswer(invocation -> {
            bothStrategiesSaved.countDown();
            assertThat(bothStrategiesSaved.await(15,TimeUnit.SECONDS)).isTrue();
            return invocation.callRealMethod();
        }).when((VoucherUsageService) org.springframework.test.util.AopTestUtils.getUltimateTargetObject(usageService))
                .reserve(any(Voucher.class),anyLong(),anyLong(),any(BigDecimal.class));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> checkout(request("MOMO",320000),"race-a").andReturn().getResponse());
            var second = executor.submit(() -> {
                var r = request("MOMO",320000);
                r.put("cartItemIds",List.of(sameUser ? 1 : 3));
                return mvc.perform(post("/api/v1/checkout").with(user(sameUser ? "buyer@test.local" : "other@test.local"))
                        .header("Idempotency-Key","race-b").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(r))).andReturn().getResponse();
            });
            var responses = List.of(first.get(25,TimeUnit.SECONDS),second.get(25,TimeUnit.SECONDS));
            assertThat(responses).extracting(r -> r.getStatus()).containsExactlyInAnyOrder(201,409);
            var rejected = responses.stream().filter(r -> r.getStatus()==409).findFirst().orElseThrow();
            assertThat(json.readTree(rejected.getContentAsString()).get("code").asText())
                    .isEqualTo(sameUser ? "VOUCHER_ALREADY_USED" : "VOUCHER_SOLD_OUT");
        }
        assertThat(count("orders")).isEqualTo(1);
        assertThat(count("payments")).isEqualTo(1);
        assertThat(count("idempotency_keys")).isEqualTo(1);
        assertThat(count("voucher_usages")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT used_quantity FROM vouchers WHERE id=1",Integer.class)).isEqualTo(1);
        assertThat(count("cart_items")).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT stock FROM product_variants WHERE id=1",Integer.class)).isEqualTo(10);
    }
}
