package com.ecommerce.promotion.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;
import java.util.concurrent.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.ecommerce.promotion.repository.VoucherRepository;
import com.ecommerce.promotion.service.VoucherUsageService;

@SpringBootTest(properties={"spring.datasource.url=jdbc:tc:postgresql:17.9:///admin_promotions","rate-limit.enabled=false", "payment.vnpay.enabled=true", "payment.vnpay.tmn-code=TESTCODE", "payment.vnpay.hash-secret=test-only-key", "payment.vnpay.return-url=https://merchant.test/api/v1/payments/vnpay/return"})
@AutoConfigureMockMvc
class AdminPromotionIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired VoucherRepository vouchers;
    @Autowired VoucherUsageService usages;
    final String root="/api/v1/admin/promotions";
    final String program="""
            {"name":"Sale","description":"Demo","startDate":"2020-01-01T00:00:00Z","endDate":"2099-01-01T00:00:00Z"}
            """;
    final String voucher="""
            {"code":" sale10 ","discountPercent":10,"minimumAmount":0,"maximumDiscount":30000,"quantity":5}
            """;
    @BeforeEach void seed() {
        jdbc.execute("TRUNCATE idempotency_keys,voucher_usages,vouchers,product_discounts,order_discounts,promotion_programs,order_items,payments,orders,cart_items,carts,product_variants,products,refresh_tokens,users RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO users(id,email,password,role) VALUES (1,'buyer@test.local','test','USER')");
        jdbc.update("INSERT INTO products(id,name,price,version) VALUES (1,'First',200000,0),(2,'Second',100000,0)");
    }
    ResultActions admin(MockHttpServletRequestBuilder request, String body) throws Exception {
        request.with(user("admin@test.local").authorities(() -> "ADMIN"));
        if(body!=null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request);
    }
    void createProgram() throws Exception { admin(post(root),program).andExpect(status().isCreated()); }
    void createVoucher() throws Exception { admin(post(root+"/1/vouchers"),voucher).andExpect(status().isCreated()); }

    @Test void programCrudAndDetailExposeChildrenAndDerivedPhase() throws Exception {
        createProgram();
        admin(get(root),null).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].phase").value("RUNNING"));
        createVoucher();
        admin(post(root+"/1/order-discounts"),"{\"minimumAmount\":0,\"discountPercent\":5}").andExpect(status().isCreated());
        admin(post(root+"/1/product-discounts"),"{\"productIds\":[1,2],\"discountPercent\":20}").andExpect(status().isCreated());
        admin(get(root+"/1"),null).andExpect(status().isOk()).andExpect(jsonPath("$.vouchers[0].code").value("SALE10"))
                .andExpect(jsonPath("$.productDiscounts.length()").value(2));
        admin(put(root+"/1"),program.replace("Sale","Renamed")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Renamed"));
        admin(patch(root+"/1/status"),"{\"status\":\"INACTIVE\"}").andExpect(status().isOk());
        admin(delete(root+"/1"),null).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vouchers",Integer.class)).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"USER","ANONYMOUS"})
    void allRoutesRequireAdmin(String role) throws Exception {
        for(var request : new MockHttpServletRequestBuilder[]{get(root),post(root),get(root+"/1"),put(root+"/1"),delete(root+"/1"),
                patch(root+"/1/status"),post(root+"/1/vouchers"),put(root+"/1/vouchers/1"),patch(root+"/1/vouchers/1/status"),delete(root+"/1/vouchers/1"),
                post(root+"/1/order-discounts"),put(root+"/1/order-discounts/1"),delete(root+"/1/order-discounts/1"),
                post(root+"/1/product-discounts"),put(root+"/1/product-discounts/1"),delete(root+"/1/product-discounts/1")}) {
            if(role.equals("USER")) request.with(user("buyer@test.local").authorities(() -> "USER"));
            mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isForbidden());
        }
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","100.01","0.001","null"})
    void invalidPercentRejected(String percent) throws Exception {
        createProgram();
        admin(post(root+"/1/vouchers"),voucher.replace("\"discountPercent\":10","\"discountPercent\":"+percent)).andExpect(status().isBadRequest());
        admin(post(root+"/1/order-discounts"),"{\"minimumAmount\":0,\"discountPercent\":"+percent+"}").andExpect(status().isBadRequest());
        admin(post(root+"/1/product-discounts"),"{\"productIds\":[1],\"discountPercent\":"+percent+"}").andExpect(status().isBadRequest());
    }
    @Test void datesCodesAndDuplicateBulkAreValidatedAtomically() throws Exception {
        admin(post(root),program.replace("2099","2019")).andExpect(status().isBadRequest());
        createProgram(); createVoucher();
        admin(post(root+"/1/vouchers"),voucher.replace(" sale10 ","SALE10")).andExpect(status().isConflict());
        admin(post(root+"/1/vouchers"),voucher.replace(" sale10 ","BAD CODE")).andExpect(status().isBadRequest());
        admin(post(root+"/1/product-discounts"),"{\"productIds\":[1,1],\"discountPercent\":10}").andExpect(status().isBadRequest());
        admin(post(root+"/1/product-discounts"),"{\"productIds\":[1,999],\"discountPercent\":10}").andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_discounts",Integer.class)).isZero();
    }
    @Test void usedVoucherCannotBeDeletedAndQuotaCannotDropBelowUsage() throws Exception {
        createProgram(); createVoucher();
        jdbc.update("INSERT INTO orders(id,user_id,status,total_price,subtotal_price) VALUES (1,1,'DONE',100000,100000)");
        jdbc.update("INSERT INTO voucher_usages(voucher_id,user_id,order_id,discount_amount) VALUES (1,1,1,10000)");
        jdbc.update("UPDATE vouchers SET used_quantity=2");
        admin(put(root+"/1/vouchers/1"),voucher.replace("\"quantity\":5","\"quantity\":1")).andExpect(status().isBadRequest());
        admin(put(root+"/1/vouchers/1"),voucher.replace("\"quantity\":5","\"quantity\":2,\"usedQuantity\":0")).andExpect(status().isOk()).andExpect(jsonPath("$.usedQuantity").value(2));
        admin(delete(root+"/1/vouchers/1"),null).andExpect(status().isConflict());
        jdbc.update("UPDATE voucher_usages SET status='RELEASED',released_at=now()");
        admin(delete(root+"/1/vouchers/1"),null).andExpect(status().isConflict());
        admin(delete(root+"/1"),null).andExpect(status().isConflict());
        admin(patch(root+"/1/vouchers/1/status"),"{\"status\":\"INACTIVE\"}").andExpect(status().isOk());
    }

    @Test void childIdsCannotCrossProgramsAndUnusedRulesCanBeUpdatedAndDeleted() throws Exception {
        createProgram(); createVoucher(); createProgram();
        admin(put(root+"/2/vouchers/1"),voucher).andExpect(status().isNotFound());
        admin(patch(root+"/2/vouchers/1/status"),"{\"status\":\"INACTIVE\"}").andExpect(status().isNotFound());
        admin(delete(root+"/2/vouchers/1"),null).andExpect(status().isNotFound());
        String tier="{\"minimumAmount\":100000,\"discountPercent\":10}";
        admin(post(root+"/1/order-discounts"),tier).andExpect(status().isCreated());
        admin(post(root+"/1/order-discounts"),tier).andExpect(status().isConflict());
        admin(put(root+"/2/order-discounts/1"),tier).andExpect(status().isNotFound());
        admin(delete(root+"/2/order-discounts/1"),null).andExpect(status().isNotFound());
        admin(put(root+"/1/order-discounts/1"),tier.replace("100000","200000")).andExpect(status().isOk());
        admin(post(root+"/1/product-discounts"),"{\"productIds\":[1],\"discountPercent\":10}").andExpect(status().isCreated());
        admin(post(root+"/1/product-discounts"),"{\"productIds\":[1,2],\"discountPercent\":10}").andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_discounts",Integer.class)).isEqualTo(1);
        admin(put(root+"/2/product-discounts/1"),"{\"discountPercent\":20}").andExpect(status().isNotFound());
        admin(delete(root+"/2/product-discounts/1"),null).andExpect(status().isNotFound());
        admin(put(root+"/1/product-discounts/1"),"{\"discountPercent\":20}").andExpect(status().isOk());
        admin(delete(root+"/1/product-discounts/1"),null).andExpect(status().isNoContent());
        admin(delete(root+"/1/order-discounts/1"),null).andExpect(status().isNoContent());
        admin(delete(root+"/1/vouchers/1"),null).andExpect(status().isNoContent());
    }

    @ParameterizedTest @ValueSource(strings={"-1","0"})
    void invalidMaximumAndQuantityRejected(String value) throws Exception {
        createProgram();
        admin(post(root+"/1/vouchers"),voucher.replace("30000",value)).andExpect(status().isBadRequest());
        admin(post(root+"/1/vouchers"),voucher.replace("\"quantity\":5","\"quantity\":"+value)).andExpect(status().isBadRequest());
        admin(post(root+"/1/order-discounts"),"{\"minimumAmount\":0,\"discountPercent\":10,\"maximumDiscount\":"+value+"}").andExpect(status().isBadRequest());
    }

    @Test void derivedPhaseBoundariesUseClock() throws Exception {
        createProgram();
        admin(put(root+"/1"),program.replace("2020","2090")).andExpect(status().isOk()).andExpect(jsonPath("$.phase").value("SCHEDULED"));
        admin(put(root+"/1"),program.replace("2099","2021")).andExpect(status().isOk()).andExpect(jsonPath("$.phase").value("ENDED"));
    }

    @Test void runningEditChangesNewCheckoutButNeverHistoricalSnapshots() throws Exception {
        createProgram();
        admin(post(root+"/1/product-discounts"),"{\"productIds\":[1],\"discountPercent\":20}").andExpect(status().isCreated());
        admin(post(root+"/1/order-discounts"),"{\"minimumAmount\":0,\"discountPercent\":5}").andExpect(status().isCreated());
        jdbc.update("INSERT INTO product_variants(id,product_id,size_value,stock,version) VALUES (1,1,'M',10,0)");
        jdbc.update("INSERT INTO carts(id,user_id) VALUES (1,1)");
        jdbc.update("INSERT INTO cart_items(cart_id,product_variant_id,quantity) VALUES (1,1,1)");
        String checkout="""
                {"firstName":"Test","lastName":"Buyer","address":"Street","city":"City","ward":"Ward","phone":"0123456789",
                "paymentMethod":"VNPAY","expectedTotal":182000}
                """;
        mvc.perform(post("/api/v1/checkout").with(user("buyer@test.local")).header("Idempotency-Key","old")
                .contentType(MediaType.APPLICATION_JSON).content(checkout)).andExpect(status().isCreated());
        admin(put(root+"/1/product-discounts/1"),"{\"discountPercent\":15}").andExpect(status().isOk());
        admin(put(root+"/1/order-discounts/1"),"{\"minimumAmount\":0,\"discountPercent\":10}").andExpect(status().isOk());
        mvc.perform(post("/api/v1/checkout/preview").with(user("buyer@test.local")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(183000));
        mvc.perform(post("/api/v1/checkout").with(user("buyer@test.local")).header("Idempotency-Key","new")
                .contentType(MediaType.APPLICATION_JSON).content(checkout.replace("182000","183000"))).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT total_price FROM orders WHERE id=1",java.math.BigDecimal.class)).isEqualByComparingTo("182000");
        assertThat(jdbc.queryForObject("SELECT price FROM order_items WHERE order_id=1",java.math.BigDecimal.class)).isEqualByComparingTo("160000");
        admin(delete(root+"/1/order-discounts/1"),null).andExpect(status().isConflict());
        admin(delete(root+"/1/product-discounts/1"),null).andExpect(status().isConflict());
        admin(delete(root+"/1"),null).andExpect(status().isConflict());
    }

    @Test void editingUsedVoucherAffectsNewOrdersButPreservesOldCodeAndAmounts() throws Exception {
        createProgram(); createVoucher();
        jdbc.update("INSERT INTO users(id,email,password,role) VALUES (2,'next@test.local','test','USER')");
        jdbc.update("INSERT INTO product_variants(id,product_id,size_value,stock,version) VALUES (1,1,'M',10,0)");
        jdbc.update("INSERT INTO carts(id,user_id) VALUES (1,1),(2,2)");
        jdbc.update("INSERT INTO cart_items(cart_id,product_variant_id,quantity) VALUES (1,1,1),(2,1,1)");
        String checkout = """
                {"firstName":"Test","lastName":"Buyer","address":"Street","city":"City","ward":"Ward","phone":"0123456789",
                "paymentMethod":"VNPAY","voucherCode":"SALE10","expectedTotal":210000}
                """;
        mvc.perform(post("/api/v1/checkout").with(user("buyer@test.local")).header("Idempotency-Key","voucher-old")
                .contentType(MediaType.APPLICATION_JSON).content(checkout)).andExpect(status().isCreated());
        admin(put(root+"/1/vouchers/1"),voucher.replace("sale10","sale12").replace("\"discountPercent\":10","\"discountPercent\":12"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.usedQuantity").value(1));
        mvc.perform(post("/api/v1/checkout/preview").with(user("next@test.local"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"voucherCode\":\"SALE12\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(206000));
        mvc.perform(post("/api/v1/checkout").with(user("next@test.local")).header("Idempotency-Key","voucher-new")
                .contentType(MediaType.APPLICATION_JSON).content(checkout.replace("SALE10","SALE12").replace("210000","206000")))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT voucher_code FROM orders WHERE id=1",String.class)).isEqualTo("SALE10");
        assertThat(jdbc.queryForObject("SELECT discount_amount FROM orders WHERE id=1",java.math.BigDecimal.class)).isEqualByComparingTo("20000");
        assertThat(jdbc.queryForObject("SELECT total_price FROM orders WHERE id=1",java.math.BigDecimal.class)).isEqualByComparingTo("210000");
        assertThat(jdbc.queryForObject("SELECT voucher_code FROM orders WHERE id=2",String.class)).isEqualTo("SALE12");
    }

    @Test void quotaEditWaitsForReservationAndCannotOverwriteCommittedCounter() throws Exception {
        createProgram(); createVoucher();
        jdbc.update("UPDATE vouchers SET used_quantity=1");
        jdbc.update("INSERT INTO orders(id,user_id,status,total_price,subtotal_price) VALUES (1,1,'AWAITING_PAYMENT',100000,100000)");
        var reserved=new CountDownLatch(1); var release=new CountDownLatch(1); var editStarted=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var reserve=executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(ignored -> {
                usages.reserve(vouchers.getReferenceById(1L),1L,1L,new java.math.BigDecimal("10000"));
                reserved.countDown();
                try { assertThat(release.await(15,TimeUnit.SECONDS)).isTrue(); }
                catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }));
            assertThat(reserved.await(15,TimeUnit.SECONDS)).isTrue();
            var edit=executor.submit(() -> {
                editStarted.countDown();
                return admin(put(root+"/1/vouchers/1"),voucher.replace("\"quantity\":5","\"quantity\":1")).andReturn().getResponse().getStatus();
            });
            try {
                assertThat(editStarted.await(5,TimeUnit.SECONDS)).isTrue();
                // Observe PostgreSQL actually blocking the edit, before allowing reserve to commit.
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                boolean waiting = false;
                while (System.nanoTime() < deadline) {
                    waiting = Boolean.TRUE.equals(jdbc.queryForObject("""
                            SELECT EXISTS (SELECT 1 FROM pg_stat_activity
                            WHERE datname=current_database() AND wait_event_type='Lock' AND query ILIKE '%vouchers%')
                            """, Boolean.class));
                    if (waiting) break;
                    Thread.sleep(25);
                }
                assertThat(waiting).as("admin edit waits for the voucher reservation lock").isTrue();
            }
            finally { release.countDown(); }
            reserve.get(20,TimeUnit.SECONDS);
            assertThat(edit.get(20,TimeUnit.SECONDS)).isEqualTo(400);
        }
        assertThat(jdbc.queryForObject("SELECT used_quantity FROM vouchers",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT quantity FROM vouchers",Integer.class)).isEqualTo(5);
    }
}
