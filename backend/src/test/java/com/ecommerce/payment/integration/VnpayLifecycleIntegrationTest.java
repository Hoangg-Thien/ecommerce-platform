package com.ecommerce.payment.integration;

import com.ecommerce.order.service.OrderCancellationService;
import com.ecommerce.payment.scheduler.PaymentTimeoutScheduler;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:tc:postgresql:17.9:///vnpay_lifecycle",
        "payment.vnpay.enabled=true", "payment.momo.mock-enabled=false", "payment.vnpay.tmn-code=TESTCODE",
        "payment.vnpay.hash-secret=test-only-key", "payment.vnpay.return-url=https://merchant.test/api/v1/payments/vnpay/return",
        "rate-limit.enabled=false"})
class VnpayLifecycleIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PaymentTimeoutScheduler scheduler;
    @Autowired OrderCancellationService cancellation;
    @Autowired com.ecommerce.order.service.AdminOrderService admin;
    @Autowired com.ecommerce.payment.service.VnpayIpnService ipn;
    @Autowired com.ecommerce.checkout.service.CheckoutService checkout;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.ecommerce.payment.service.VnpayService gateway;

    java.util.Map<String,String> callback(String response,String state,String transaction) {
        var p = new java.util.HashMap<String,String>();
        p.put("vnp_TmnCode","TESTCODE"); p.put("vnp_TxnRef","LIFECYCLE1"); p.put("vnp_Amount","900000");
        p.put("vnp_TransactionNo",transaction); p.put("vnp_BankCode","NCB"); p.put("vnp_OrderInfo","Thanh toan don hang 1");
        p.put("vnp_ResponseCode",response); p.put("vnp_TransactionStatus",state);
        p.put("vnp_SecureHash",gateway.sign(p)); return p;
    }
    String success() { return ipn.handle(callback("00","00","123456")); }

    @BeforeEach void seed() {
        org.mockito.Mockito.reset(gateway);
        jdbc.execute("TRUNCATE idempotency_keys,voucher_usages,vouchers,product_discounts,order_discounts,promotion_programs,order_items,payments,orders,cart_items,carts,product_variants,products,refresh_tokens,users RESTART IDENTITY CASCADE");
        jdbc.execute("INSERT INTO users(id,email,password,role) VALUES (1,'lifecycle@test.local','test','USER')");
        jdbc.execute("INSERT INTO products(id,name,price,version) VALUES (1,'Product',10000,0)");
        jdbc.execute("INSERT INTO product_variants(id,product_id,size_value,stock,version) VALUES (1,1,'M',5,0)");
        jdbc.execute("INSERT INTO orders(id,user_id,total_price,subtotal_price,discount_amount,status,payment_method,version,create_at) VALUES (1,1,9000,10000,1000,'AWAITING_PAYMENT','VNPAY',0,now()-interval '1 hour')");
        jdbc.execute("INSERT INTO order_items(order_id,product_id,size,quantity,price,original_price) VALUES (1,1,'M',1,10000,10000)");
        jdbc.execute("INSERT INTO payments(order_id,amount,payment_method,payment_status,provider_txn_ref,provider_environment,refund_status,version,expires_at) VALUES (1,9000,'VNPAY','PENDING','LIFECYCLE1','SANDBOX','NONE',0,now()-interval '1 minute')");
        jdbc.execute("INSERT INTO carts(id,user_id) VALUES (1,1)");
        jdbc.execute("INSERT INTO cart_items(cart_id,product_variant_id,quantity) VALUES (1,1,1)");
        jdbc.execute("INSERT INTO promotion_programs(id,name,start_date,end_date) VALUES (1,'Test',now()-interval '1 day',now()+interval '1 day')");
        jdbc.execute("INSERT INTO vouchers(id,promotion_program_id,code,discount_percent,quantity,used_quantity) VALUES (1,1,'LIFE10',10,5,1)");
        jdbc.execute("INSERT INTO voucher_usages(voucher_id,user_id,order_id,discount_amount) VALUES (1,1,1,1000)");
    }
    String text(String sql) { return jdbc.queryForObject(sql,String.class); }
    int number(String sql) { return jdbc.queryForObject(sql,Integer.class); }
    void assertState(String order,String payment,String refund,int stock,int used) {
        assertThat(text("SELECT status FROM orders WHERE id=1")).isEqualTo(order);
        assertThat(text("SELECT payment_status FROM payments WHERE order_id=1")).isEqualTo(payment);
        assertThat(text("SELECT refund_status FROM payments WHERE order_id=1")).isEqualTo(refund);
        assertThat(number("SELECT stock FROM product_variants WHERE id=1")).isEqualTo(stock);
        assertThat(number("SELECT used_quantity FROM vouchers WHERE id=1")).isEqualTo(used);
    }
    @Test void expiredVnpayKeepsUnknownAndReleasesOnce() {
        scheduler.cancelExpiredOnlinePayments();
        cancellation.cancelExpiredOrder(1L);
        assertState("CANCELLED","PENDING","RECONCILIATION_REQUIRED",5,0);
        assertThat(text("SELECT reason FROM payments")).isEqualTo("PAYMENT_TIMEOUT_UNVERIFIED");
        assertThat(text("SELECT status FROM voucher_usages")).isEqualTo("RELEASED");
    }
    @Test void deadlineMustBeRecheckedInsideCancellationTransaction() {
        jdbc.execute("UPDATE payments SET expires_at=now()+interval '1 hour'");
        cancellation.cancelExpiredOrder(1L);
        assertState("AWAITING_PAYMENT","PENDING","NONE",5,1);
    }
    @Test void timeoutNeverCancelsVerifiedPaidEvenWithInconsistentAwaitingState() {
        jdbc.execute("UPDATE payments SET payment_status='PAID',paid_at=now()");
        cancellation.cancelExpiredOrder(1L);
        assertState("AWAITING_PAYMENT","PAID","NONE",5,1);
    }
    @Test void legacyMomoPendingRetainsHistoryWithoutAutomaticCancellation() {
        jdbc.execute("UPDATE orders SET payment_method='MOMO'");
        jdbc.execute("UPDATE payments SET payment_method='MOMO',provider_txn_ref=NULL,provider_environment=NULL,refund_status=NULL,expires_at=NULL,momo_order_id='LEGACYREF'");
        scheduler.cancelExpiredOnlinePayments();
        assertState("AWAITING_PAYMENT","PENDING",null,5,1);
        assertThat(text("SELECT momo_order_id FROM payments")).isEqualTo("LEGACYREF");
    }
    @Test void codIsNeverCanceledByOnlineTimeoutService() {
        jdbc.execute("UPDATE orders SET payment_method='COD'");
        jdbc.execute("UPDATE payments SET payment_method='COD'");
        cancellation.cancelExpiredOrder(1L);
        assertState("AWAITING_PAYMENT","PENDING","NONE",5,1);
    }
    @Test void releaseFailureRollsBackTimeoutPaymentAndOrder() {
        jdbc.execute("UPDATE vouchers SET used_quantity=0");
        assertThatThrownBy(() -> cancellation.cancelExpiredOrder(1L)).isInstanceOf(IllegalStateException.class);
        assertState("AWAITING_PAYMENT","PENDING","NONE",5,0);
        assertThat(text("SELECT status FROM voucher_usages")).isEqualTo("APPLIED");
    }

    @Test void paidAdminCancellationReleaseFailureRollsBackRestockAndManualFlag() {
        success(); jdbc.execute("UPDATE vouchers SET used_quantity=0");
        assertThatThrownBy(() -> admin.updateOrderStatus(1L,com.ecommerce.order.enums.OrderStatus.CANCELLED))
                .isInstanceOf(IllegalStateException.class);
        assertState("CONFIRMED","PAID","NONE",4,0);
        assertThat(text("SELECT status FROM voucher_usages")).isEqualTo("APPLIED");
    }
    @Test void outOfStockReleaseFailureRollsBackWholeIpnIncludingPaidEvidence() {
        jdbc.execute("UPDATE product_variants SET stock=0"); jdbc.execute("UPDATE vouchers SET used_quantity=0");
        assertThatThrownBy(this::success).isInstanceOf(IllegalStateException.class);
        assertState("AWAITING_PAYMENT","PENDING","NONE",0,0);
        assertThat(text("SELECT provider_transaction_no FROM payments")).isNull();
        assertThat(text("SELECT status FROM voucher_usages")).isEqualTo("APPLIED");
    }
    @Test void adminCancelUnverifiedAndLateSuccessNeverRestoreOrReserveStockOrVoucher() {
        admin.updateOrderStatus(1L,com.ecommerce.order.enums.OrderStatus.CANCELLED);
        assertState("CANCELLED","PENDING","RECONCILIATION_REQUIRED",5,0);
        assertThat(text("SELECT reason FROM payments")).isEqualTo("ADMIN_CANCEL_UNVERIFIED");
        assertThat(success()).isEqualTo("00");
        assertState("CANCELLED","PAID","REQUIRED_MANUAL",5,0);
        assertThat(success()).isEqualTo("02");
        assertThat(number("SELECT count(*) FROM voucher_usages")).isEqualTo(1);
    }
    @Test void adminCannotBypassAwaitingOrMutateUnprovenPendingOrUnpaidConfirmed() {
        for (var state : new com.ecommerce.order.enums.OrderStatus[]{com.ecommerce.order.enums.OrderStatus.PENDING,
                com.ecommerce.order.enums.OrderStatus.CONFIRMED, com.ecommerce.order.enums.OrderStatus.SHIPPING, com.ecommerce.order.enums.OrderStatus.DONE})
            assertThatThrownBy(() -> admin.updateOrderStatus(1L,state)).isInstanceOf(IllegalArgumentException.class);
        assertState("AWAITING_PAYMENT","PENDING","NONE",5,1);
        for (String state : new String[]{"PENDING","CONFIRMED","SHIPPING"}) {
            jdbc.update("UPDATE orders SET status=? WHERE id=1",state);
            assertThatThrownBy(() -> admin.updateOrderStatus(1L,com.ecommerce.order.enums.OrderStatus.CANCELLED)).isInstanceOf(IllegalStateException.class);
            assertThat(number("SELECT stock FROM product_variants")).isEqualTo(5);
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void paidCancelRestoresExactlyOnceFromConfirmedOrShipping(boolean shipping) {
        assertThat(success()).isEqualTo("00");
        assertState("CONFIRMED","PAID","NONE",4,1);
        if (shipping) admin.updateOrderStatus(1L,com.ecommerce.order.enums.OrderStatus.SHIPPING);
        admin.updateOrderStatus(1L,com.ecommerce.order.enums.OrderStatus.CANCELLED);
        assertState("CANCELLED","PAID","REQUIRED_MANUAL",5,0);
        assertThatThrownBy(() -> admin.updateOrderStatus(1L,com.ecommerce.order.enums.OrderStatus.CANCELLED)).isInstanceOf(IllegalArgumentException.class);
        cancellation.cancelExpiredOrder(1L);
        assertThat(success()).isEqualTo("02");
        assertState("CANCELLED","PAID","REQUIRED_MANUAL",5,0);
    }
    @Test void doneIsTerminalAndNeverRestoresOrReleases() {
        success();
        admin.updateOrderStatus(1L,com.ecommerce.order.enums.OrderStatus.SHIPPING);
        admin.updateOrderStatus(1L,com.ecommerce.order.enums.OrderStatus.DONE);
        assertThatThrownBy(() -> admin.updateOrderStatus(1L,com.ecommerce.order.enums.OrderStatus.CANCELLED)).isInstanceOf(IllegalArgumentException.class);
        cancellation.cancelExpiredOrder(1L);
        assertThat(success()).isEqualTo("02");
        assertState("DONE","PAID","NONE",4,1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void syntheticDefinitiveFailureClearsResolvedReconciliationWithoutRepeatingEffects(boolean timeout) {
        if (timeout) cancellation.cancelExpiredOrder(1L);
        else admin.updateOrderStatus(1L,com.ecommerce.order.enums.OrderStatus.CANCELLED);
        // Synthetic classifier outcome only; no real VNPay failure pair is approved.
        org.mockito.Mockito.doReturn(com.ecommerce.payment.service.VnpayResult.FAILURE).when(gateway).classify("98","98");
        assertThat(ipn.handle(callback("98","98","123456"))).isEqualTo("00");
        assertState("CANCELLED","FAILED","NONE",5,0);
        assertThat(text("SELECT reason FROM payments")).isNull();
        assertThat(ipn.handle(callback("98","98","123456"))).isEqualTo("02");
        assertState("CANCELLED","FAILED","NONE",5,0);
    }
    @Test void syntheticDefinitiveFailureCancelsAwaitingAndReleasesOnce() {
        org.mockito.Mockito.doReturn(com.ecommerce.payment.service.VnpayResult.FAILURE).when(gateway).classify("98","98");
        assertThat(ipn.handle(callback("98","98","123456"))).isEqualTo("00");
        assertState("CANCELLED","FAILED","NONE",5,0);
    }
    @Test void failedResultNeverDowngradesPaidAndUnresolvedConflictSurvivesDuplicateSuccess() {
        success();
        var paid = jdbc.queryForMap("SELECT paid_at,provider_transaction_no FROM payments");
        org.mockito.Mockito.doReturn(com.ecommerce.payment.service.VnpayResult.FAILURE).when(gateway).classify("98","98");
        ipn.handle(callback("98","98","123456"));
        assertState("CONFIRMED","PAID","RECONCILIATION_REQUIRED",4,1);
        assertThat(text("SELECT reason FROM payments")).isEqualTo("CALLBACK_CONFLICT");
        assertThat(jdbc.queryForMap("SELECT paid_at,provider_transaction_no FROM payments")).isEqualTo(paid);
        assertThat(success()).isEqualTo("02");
        assertThatThrownBy(() -> admin.updateOrderStatus(1L,com.ecommerce.order.enums.OrderStatus.SHIPPING)).isInstanceOf(IllegalStateException.class);
    }

    java.util.List<Throwable> race(Runnable... actions) throws Exception {
        var ready = new java.util.concurrent.CountDownLatch(actions.length);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(actions.length)) {
            var jobs = new java.util.ArrayList<java.util.concurrent.Future<Throwable>>();
            for (Runnable action : actions) jobs.add(pool.submit(() -> {
                ready.countDown();
                if (!start.await(10,java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("race start timeout");
                return catchThrowable(action::run);
            }));
            assertThat(ready.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var failures = new java.util.ArrayList<Throwable>();
            for (var job : jobs) failures.add(job.get(25,java.util.concurrent.TimeUnit.SECONDS));
            return failures;
        } finally { start.countDown(); }
    }
    @RepeatedTest(3) void concurrentDuplicateSuccessHasOneEffect() throws Exception {
        var acks = new java.util.concurrent.ConcurrentLinkedQueue<String>();
        assertThat(race(() -> acks.add(success()), () -> acks.add(success()))).allMatch(java.util.Objects::isNull);
        assertThat(acks).containsExactlyInAnyOrder("00","02");
        assertState("CONFIRMED","PAID","NONE",4,1);
    }
    @RepeatedTest(3) void timeoutAndIpnSerializeToOneOfTwoValidOutcomes() throws Exception {
        assertThat(race(() -> cancellation.cancelExpiredOrder(1L), this::success)).allMatch(java.util.Objects::isNull);
        if (text("SELECT status FROM orders").equals("CANCELLED")) assertState("CANCELLED","PAID","REQUIRED_MANUAL",5,0);
        else assertState("CONFIRMED","PAID","NONE",4,1);
    }
    @RepeatedTest(3) void adminCancelAndIpnAlwaysEndCanceledPaidWithFullStock() throws Exception {
        assertThat(race(() -> admin.updateOrderStatus(1L,com.ecommerce.order.enums.OrderStatus.CANCELLED), this::success))
                .allMatch(java.util.Objects::isNull);
        assertState("CANCELLED","PAID","REQUIRED_MANUAL",5,0);
    }
    @Test void concurrentDoubleAdminCancelCannotDoubleRestock() throws Exception {
        success();
        var errors = race(() -> admin.updateOrderStatus(1L,com.ecommerce.order.enums.OrderStatus.CANCELLED),
                () -> admin.updateOrderStatus(1L,com.ecommerce.order.enums.OrderStatus.CANCELLED));
        assertThat(errors.stream().filter(java.util.Objects::nonNull).toList()).hasSize(1)
                .allMatch(e -> e instanceof IllegalArgumentException);
        assertState("CANCELLED","PAID","REQUIRED_MANUAL",5,0);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void multiItemOutOfStockOrMissingVariantNeverPartiallyDeducts(boolean missing) {
        jdbc.execute("INSERT INTO products(id,name,price,version) VALUES (2,'Second',10000,0)");
        if (!missing) jdbc.execute("INSERT INTO product_variants(id,product_id,size_value,stock,version) VALUES (2,2,'M',0,0)");
        jdbc.execute("INSERT INTO order_items(order_id,product_id,size,quantity,price,original_price) VALUES (1,2,'M',1,10000,10000)");
        jdbc.execute("UPDATE orders SET subtotal_price=20000,total_price=19000");
        jdbc.execute("UPDATE payments SET amount=19000");
        var p = callback("00","00","123456"); p.remove("vnp_SecureHash"); p.put("vnp_Amount","1900000"); p.put("vnp_SecureHash",gateway.sign(p));
        assertThat(ipn.handle(p)).isEqualTo("00");
        assertState("CANCELLED","PAID","REQUIRED_MANUAL",5,0);
        if (!missing) assertThat(number("SELECT stock FROM product_variants WHERE id=2")).isZero();
    }
    @Test void cartQuantityChangedDuringPaymentRetainsAcceptedD9LineRemovalSemantics() {
        jdbc.execute("UPDATE cart_items SET quantity=3 WHERE cart_id=1");
        assertThat(success()).isEqualTo("00");
        assertState("CONFIRMED","PAID","NONE",4,1);
        assertThat(number("SELECT quantity FROM order_items WHERE order_id=1")).isEqualTo(1);
        // MVP D9: remove purchased product+size line, even when its current quantity changed.
        assertThat(number("SELECT count(*) FROM cart_items WHERE cart_id=1")).isZero();
    }
    void secondOnlineOrder() {
        jdbc.execute("INSERT INTO users(id,email,password,role) VALUES (2,'second@test.local','test','USER')");
        jdbc.execute("INSERT INTO orders(id,user_id,total_price,subtotal_price,status,payment_method,version) VALUES (2,2,10000,10000,'AWAITING_PAYMENT','VNPAY',0)");
        jdbc.execute("INSERT INTO order_items(order_id,product_id,size,quantity,price,original_price) VALUES (2,1,'M',1,10000,10000)");
        jdbc.execute("INSERT INTO payments(order_id,amount,payment_method,payment_status,provider_txn_ref,provider_environment,refund_status,version) VALUES (2,10000,'VNPAY','PENDING','LIFECYCLE2','SANDBOX','NONE',0)");
    }
    String secondSuccess() {
        var p = callback("00","00","123457"); p.remove("vnp_SecureHash");
        p.put("vnp_TxnRef","LIFECYCLE2"); p.put("vnp_Amount","1000000"); p.put("vnp_SecureHash",gateway.sign(p));
        return ipn.handle(p);
    }
    @RepeatedTest(3) void twoOnlinePaymentsAndCodCompeteForLastStockWithoutLosingPaidEvidence() throws Exception {
        secondOnlineOrder();
        jdbc.execute("UPDATE product_variants SET stock=1");
        jdbc.execute("INSERT INTO users(id,email,password,role) VALUES (3,'cod@test.local','test','USER')");
        jdbc.execute("INSERT INTO carts(id,user_id) VALUES (3,3)");
        jdbc.execute("INSERT INTO cart_items(cart_id,product_variant_id,quantity) VALUES (3,1,1)");
        jdbc.execute("SELECT setval(pg_get_serial_sequence('orders','id'),2,true)");
        var request = new com.ecommerce.checkout.dto.request.CheckoutRequest();
        request.setPaymentMethod(com.ecommerce.payment.enums.PaymentMethod.COD);
        request.setExpectedTotal(new java.math.BigDecimal("40000"));
        request.setFirstName("Test"); request.setLastName("COD"); request.setAddress("Street"); request.setCity("City"); request.setWard("Ward"); request.setPhone("0123456789");
        var errors = race(this::success, this::secondSuccess, () -> checkout.checkout("cod@test.local",request,"stock-race-cod"));
        assertThat(errors.get(0)).isNull(); assertThat(errors.get(1)).isNull();
        if (errors.get(2) != null) assertThat(errors.get(2)).isInstanceOfAny(IllegalArgumentException.class,
                org.springframework.orm.ObjectOptimisticLockingFailureException.class);
        assertThat(number("SELECT stock FROM product_variants")).isZero();
        assertThat(number("SELECT count(*) FROM orders WHERE status IN ('PENDING','CONFIRMED')")).isEqualTo(1);
        assertThat(number("SELECT count(*) FROM payments WHERE payment_method='VNPAY' AND payment_status='PAID'")).isEqualTo(2);
        assertThat(number("SELECT used_quantity FROM vouchers")).isEqualTo(number("SELECT count(*) FROM voucher_usages WHERE status='APPLIED'"));
        assertThat(number("SELECT count(*) FROM orders o JOIN payments p ON p.order_id=o.id WHERE o.total_price<>p.amount")).isZero();
    }
}
