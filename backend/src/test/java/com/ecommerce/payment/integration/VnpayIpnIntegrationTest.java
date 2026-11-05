package com.ecommerce.payment.integration;

import com.ecommerce.payment.service.VnpayService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:tc:postgresql:17.9:///vnpay_ipn",
        "payment.vnpay.enabled=true", "payment.momo.mock-enabled=false", "payment.vnpay.tmn-code=TESTCODE",
        "payment.vnpay.hash-secret=test-only-key", "payment.vnpay.return-url=https://merchant.test/api/v1/payments/vnpay/return",
        "rate-limit.enabled=true"})
@AutoConfigureMockMvc
class VnpayIpnIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired VnpayService gateway;
    @Autowired com.ecommerce.payment.config.VnpayConfig config;
    @BeforeEach void seed() {
        jdbc.execute("TRUNCATE idempotency_keys,voucher_usages,vouchers,product_discounts,order_discounts,promotion_programs,order_items,payments,orders,cart_items,carts,product_variants,products,refresh_tokens,users RESTART IDENTITY CASCADE");
        jdbc.execute("INSERT INTO users(id,email,password,role) VALUES (1,'ipn@test.local','test','USER')");
        jdbc.execute("INSERT INTO products(id,name,price,version) VALUES (1,'Product',10000,0)");
        jdbc.execute("INSERT INTO product_variants(id,product_id,size_value,stock,version) VALUES (1,1,'M',5,0)");
        jdbc.execute("INSERT INTO orders(id,user_id,total_price,subtotal_price,discount_amount,status,payment_method,version) VALUES (1,1,9000,10000,1000,'AWAITING_PAYMENT','VNPAY',0)");
        jdbc.execute("INSERT INTO order_items(order_id,product_id,size,quantity,price,original_price) VALUES (1,1,'M',1,10000,10000)");
        jdbc.execute("INSERT INTO payments(order_id,amount,payment_method,payment_status,provider_txn_ref,provider_environment,refund_status,version) VALUES (1,9000,'VNPAY','PENDING','TESTREF1','SANDBOX','NONE',0)");
        jdbc.execute("INSERT INTO carts(id,user_id) VALUES (1,1)");
        jdbc.execute("INSERT INTO cart_items(cart_id,product_variant_id,quantity) VALUES (1,1,1)");
        jdbc.execute("INSERT INTO promotion_programs(id,name,start_date,end_date) VALUES (1,'Test',now()-interval '1 day',now()+interval '1 day')");
        jdbc.execute("INSERT INTO vouchers(id,promotion_program_id,code,discount_percent,quantity,used_quantity) VALUES (1,1,'IPN10',10,2,1)");
        jdbc.execute("INSERT INTO voucher_usages(voucher_id,user_id,order_id,discount_amount) VALUES (1,1,1,1000)");
    }
    Map<String,String> fields(String response, String status) {
        var p = new HashMap<String,String>();
        p.put("vnp_TmnCode","TESTCODE"); p.put("vnp_TxnRef","TESTREF1"); p.put("vnp_Amount","900000");
        p.put("vnp_TransactionNo","123456"); p.put("vnp_BankCode","NCB"); p.put("vnp_OrderInfo","Thanh toan don hang 1");
        p.put("vnp_ResponseCode",response); p.put("vnp_TransactionStatus",status);
        return p;
    }
    MockHttpServletRequestBuilder signed(String path,Map<String,String> p) {
        var request = get(path); p.forEach(request::param); request.param("vnp_SecureHash",gateway.sign(p)); return request;
    }
    String text(String sql) { return jdbc.queryForObject(sql,String.class); }
    @Test void disablingNewCheckoutStillAcceptsSignedIpnAndReadOnlyReturn() throws Exception {
        org.springframework.test.util.ReflectionTestUtils.setField(config,"enabled",false);
        try {
            mvc.perform(signed("/api/v1/payments/vnpay/return",fields("00","00")))
                    .andExpect(status().isSeeOther());
            assertThat(text("SELECT payment_status FROM payments")).isEqualTo("PENDING");
            mvc.perform(signed("/api/v1/payments/vnpay/ipn",fields("00","00")))
                    .andExpect(jsonPath("$.RspCode").value("00"));
            mvc.perform(signed("/api/v1/payments/vnpay/ipn",fields("00","00")))
                    .andExpect(jsonPath("$.RspCode").value("02"));
            assertThat(text("SELECT payment_status FROM payments")).isEqualTo("PAID");
        } finally { org.springframework.test.util.ReflectionTestUtils.setField(config,"enabled",true); }
    }
    @Test void verifiedSuccessAndDuplicateAffectStockAndVoucherOnce() throws Exception {
        mvc.perform(signed("/api/v1/payments/vnpay/ipn",fields("00","00"))).andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value("00"));
        mvc.perform(signed("/api/v1/payments/vnpay/ipn",fields("00","00"))).andExpect(jsonPath("$.RspCode").value("02"));
        assertThat(text("SELECT payment_status FROM payments")).isEqualTo("PAID");
        assertThat(text("SELECT status FROM orders")).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT stock FROM product_variants",Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cart_items",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT used_quantity FROM vouchers",Integer.class)).isEqualTo(1);
    }
    @ParameterizedTest @CsvSource({"24,02","11,02","51,02","07,00","00,01","00,04","00,05","00,06","00,07","00,09","00,02"})
    void unknownNeverFailsOrFulfills(String response,String state) throws Exception {
        mvc.perform(signed("/api/v1/payments/vnpay/ipn",fields(response,state))).andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(text("SELECT payment_status FROM payments")).isEqualTo("PENDING");
        assertThat(text("SELECT status FROM orders")).isEqualTo("AWAITING_PAYMENT");
        assertThat(text("SELECT refund_status FROM payments")).isEqualTo("RECONCILIATION_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT stock FROM product_variants",Integer.class)).isEqualTo(5);
    }
    @Test void rejectsTamperingDuplicateParamsWrongMerchantReferenceAmountAndEnvironment() throws Exception {
        var p = fields("00","00");
        mvc.perform(get("/api/v1/payments/vnpay/ipn").param("vnp_SecureHash","bad")).andExpect(jsonPath("$.RspCode").value("97"));
        mvc.perform(signed("/api/v1/payments/vnpay/ipn",p).param("vnp_Amount","1")).andExpect(jsonPath("$.RspCode").value("97"));
        p.put("vnp_TmnCode","OTHERONE"); mvc.perform(signed("/api/v1/payments/vnpay/ipn",p)).andExpect(jsonPath("$.RspCode").value("97"));
        p = fields("00","00"); p.put("vnp_TxnRef","UNKNOWNREF"); mvc.perform(signed("/api/v1/payments/vnpay/ipn",p)).andExpect(jsonPath("$.RspCode").value("01"));
        p = fields("00","00"); p.put("vnp_Amount","900100"); mvc.perform(signed("/api/v1/payments/vnpay/ipn",p)).andExpect(jsonPath("$.RspCode").value("04"));
        jdbc.execute("UPDATE payments SET provider_environment=NULL");
        mvc.perform(signed("/api/v1/payments/vnpay/ipn",fields("00","00"))).andExpect(jsonPath("$.RspCode").value("04"));
        assertThat(text("SELECT payment_status FROM payments")).isEqualTo("PENDING");
    }
    @Test void returnHasFixedRedirectAndNeverMutates() throws Exception {
        mvc.perform(signed("/api/v1/payments/vnpay/return",fields("00","00")))
                .andExpect(status().isSeeOther()).andExpect(redirectedUrl("http://localhost:5173/payment-result?orderId=1"));
        assertThat(text("SELECT payment_status FROM payments")).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT stock FROM product_variants",Integer.class)).isEqualTo(5);
    }
    @Test void successWithoutStockCommitsPaidManualInsteadOfFakeRefund() throws Exception {
        jdbc.execute("UPDATE product_variants SET stock=0");
        mvc.perform(signed("/api/v1/payments/vnpay/ipn",fields("00","00"))).andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(text("SELECT payment_status FROM payments")).isEqualTo("PAID");
        assertThat(text("SELECT status FROM orders")).isEqualTo("CANCELLED");
        assertThat(text("SELECT refund_status FROM payments")).isEqualTo("REQUIRED_MANUAL");
        assertThat(text("SELECT status FROM voucher_usages")).isEqualTo("RELEASED");
    }
    @Test void lateSuccessRecordsPaidWithoutResurrectingCanceledOrder() throws Exception {
        jdbc.execute("UPDATE orders SET status='CANCELLED'");
        mvc.perform(signed("/api/v1/payments/vnpay/ipn",fields("00","00"))).andExpect(jsonPath("$.RspCode").value("00"));
        assertThat(text("SELECT payment_status FROM payments")).isEqualTo("PAID");
        assertThat(text("SELECT status FROM orders")).isEqualTo("CANCELLED");
        assertThat(text("SELECT refund_status FROM payments")).isEqualTo("REQUIRED_MANUAL");
        assertThat(jdbc.queryForObject("SELECT stock FROM product_variants",Integer.class)).isEqualTo(5);
    }
    @Test void deferredCommitFailureNeverAcknowledgesSuccessAndRetryCanRecover() throws Exception {
        jdbc.execute("CREATE FUNCTION vnpay_test_commit_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test commit failure'; END $$");
        jdbc.execute("CREATE CONSTRAINT TRIGGER vnpay_test_fail AFTER UPDATE ON payments DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION vnpay_test_commit_failure()");
        try {
            mvc.perform(signed("/api/v1/payments/vnpay/ipn",fields("00","00"))).andExpect(jsonPath("$.RspCode").value("99"));
            assertThat(text("SELECT payment_status FROM payments")).isEqualTo("PENDING");
            assertThat(text("SELECT status FROM orders")).isEqualTo("AWAITING_PAYMENT");
            assertThat(jdbc.queryForObject("SELECT stock FROM product_variants",Integer.class)).isEqualTo(5);
        } finally {
            jdbc.execute("DROP TRIGGER vnpay_test_fail ON payments"); jdbc.execute("DROP FUNCTION vnpay_test_commit_failure()");
        }
        mvc.perform(signed("/api/v1/payments/vnpay/ipn",fields("00","00"))).andExpect(jsonPath("$.RspCode").value("00"));
    }
}
