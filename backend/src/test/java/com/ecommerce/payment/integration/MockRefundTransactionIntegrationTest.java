package com.ecommerce.payment.integration;

import com.ecommerce.payment.repository.PaymentRepository;
import com.ecommerce.payment.service.PaymentFulfillmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.ecommerce.order.service.AdminOrderService;
import com.ecommerce.order.service.OrderCancellationService;
import com.ecommerce.order.service.OrderService;
import com.ecommerce.order.enums.OrderStatus;
import com.ecommerce.payment.service.MockPaymentService;
import com.ecommerce.payment.service.MomoIpnService;
import com.ecommerce.payment.service.MomoService;
import com.ecommerce.payment.dto.request.MockPaymentRequest;
import com.ecommerce.payment.dto.request.MomoIpnRequest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:17.9:///mock_refund_transaction",
        "payment.momo.mock-enabled=true", "rate-limit.enabled=false"
})
@AutoConfigureMockMvc
class MockRefundTransactionIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PaymentRepository payments;
    @MockitoSpyBean PaymentFulfillmentService fulfillment;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean RestTemplate http;
    @Autowired AdminOrderService admin;
    @Autowired OrderCancellationService cancellation;
    @Autowired OrderService orders;
    @Autowired MockPaymentService mockPayment;
    @Autowired MomoIpnService ipn;
    @MockitoSpyBean MomoService momo;

    @BeforeEach void seed() {
        // Only the isolated Testcontainers database is modified.
        jdbc.execute("TRUNCATE idempotency_keys,voucher_usages,vouchers,product_discounts,order_discounts,promotion_programs,order_items,payments,orders,cart_items,carts,product_variants,products,refresh_tokens,users RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO users(id,email,password,role) VALUES (1,'refund@test.local','test','USER')");
        jdbc.update("INSERT INTO products(id,name,price,version) VALUES (1,'Sold out product',100000,0)");
        jdbc.update("INSERT INTO product_variants(id,product_id,size_value,stock,version) VALUES (1,1,'M',10,0)");
        jdbc.update("""
                INSERT INTO orders(id,user_id,status,payment_method,total_price,subtotal_price,shipping_fee,version)
                VALUES (1,1,'AWAITING_PAYMENT','MOMO',130000,100000,30000,0)
                """);
        jdbc.update("INSERT INTO order_items(order_id,product_id,size,quantity,price,original_price) VALUES (1,1,'M',1,100000,100000)");
        jdbc.update("""
                INSERT INTO payments(id,order_id,payment_method,payment_status,amount,version,momo_order_id)
                VALUES (1,1,'MOMO','PENDING',130000,0,'LEGACY_1')
                """);
        jdbc.update("INSERT INTO carts(id,user_id) VALUES (1,1)");
        jdbc.update("INSERT INTO cart_items(cart_id,product_variant_id,quantity) VALUES (1,1,1)");
        jdbc.update("INSERT INTO promotion_programs(id,name,start_date,end_date) VALUES (1,'Sale',now()-interval '1 day',now()+interval '1 day')");
        jdbc.update("INSERT INTO vouchers(id,promotion_program_id,code,discount_percent,quantity,used_quantity) VALUES (1,1,'TEST',10,5,1)");
        jdbc.update("INSERT INTO voucher_usages(voucher_id,user_id,order_id,discount_amount) VALUES (1,1,1,10000)");
    }

    @Test
    void outOfStockMockSuccessMustCommitCancellationAndRefundTogether() {
        jdbc.update("UPDATE product_variants SET stock=0");
        Throwable failure = catchThrowable(() -> new TransactionTemplate(transactionManager).executeWithoutResult(
                ignored -> fulfillment.processSuccess(payments.findByOrderId(1L).orElseThrow(), "MOCK_OUT_OF_STOCK")));

        var state = jdbc.queryForMap("""
                SELECT o.status AS order_status,p.payment_status,p.transaction_id
                FROM orders o JOIN payments p ON p.order_id=o.id WHERE o.id=1
                """);
        verifyNoInteractions(http);
        assertThat(failure).as("Mock out-of-stock transaction must commit atomically; persisted state: %s", state).isNull();
        assertThat(state).containsEntry("order_status", "CANCELLED")
                .containsEntry("payment_status", "REFUNDED")
                .containsEntry("transaction_id", "MOCK_OUT_OF_STOCK");
        assertReleased();
    }

    void simulate(String scenario) {
        var request = new MockPaymentRequest();
        request.setScenario(scenario);
        mockPayment.simulatePayment(1L, request, "refund@test.local");
    }

    void assertReleased() {
        assertThat(jdbc.queryForObject("SELECT status FROM voucher_usages",String.class)).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("SELECT used_quantity FROM vouchers",Integer.class)).isZero();
    }

    void assertState(String orderStatus, String paymentStatus, String usage, int stock) {
        assertThat(jdbc.queryForObject("SELECT status FROM orders",String.class)).isEqualTo(orderStatus);
        assertThat(jdbc.queryForObject("SELECT payment_status FROM payments",String.class)).isEqualTo(paymentStatus);
        assertThat(jdbc.queryForObject("SELECT status FROM voucher_usages",String.class)).isEqualTo(usage);
        assertThat(jdbc.queryForObject("SELECT stock FROM product_variants",Integer.class)).isEqualTo(stock);
        assertThat(jdbc.queryForObject("SELECT used_quantity FROM vouchers",Integer.class)).isEqualTo(usage.equals("APPLIED") ? 1 : 0);
        verifyNoInteractions(http);
    }

    @Test void adminCancelThenLateSuccessDoesNotChangePaymentOrRefund() {
        admin.updateOrderStatus(1L,OrderStatus.CANCELLED);
        // Even a legacy inconsistent CANCELLED/PENDING pair must be ignored before stock checks.
        jdbc.update("UPDATE product_variants SET stock=0");
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
            var payment = payments.findById(1L).orElseThrow();
            fulfillment.processSuccess(payment,"LATE");
        });
        assertState("CANCELLED","FAILED","RELEASED",0);
        jdbc.update("UPDATE payments SET payment_status='PENDING'");
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored ->
                fulfillment.processSuccess(payments.findById(1L).orElseThrow(),"LATE"));
        assertState("CANCELLED","PENDING","RELEASED",0);
        assertThat(jdbc.queryForObject("SELECT transaction_id FROM payments",String.class)).isNull();
    }

    @Test void mockFailureAndTimeoutReleaseOnce() {
        simulate("FAIL");
        cancellation.cancelExpiredOrder(1L);
        assertThatThrownBy(() -> simulate("SUCCESS")).isInstanceOf(IllegalArgumentException.class);
        assertState("CANCELLED","FAILED","RELEASED",10);
    }

    @Test void timeoutThenRepeatedCallbacksLeaveCancellationUntouched() {
        cancellation.cancelExpiredOrder(1L);
        cancellation.cancelExpiredOrder(1L);
        for (String scenario : new String[]{"SUCCESS","FAIL","PENDING"}) {
            assertThatThrownBy(() -> simulate(scenario)).isInstanceOf(IllegalArgumentException.class);
        }
        assertState("CANCELLED","FAILED","RELEASED",10);
    }

    @ParameterizedTest @ValueSource(strings={"CONFIRMED","SHIPPING"})
    void adminCancelPaidMockRestocksAndReleasesButPreservesPaidHistory(String status) {
        simulate("SUCCESS");
        if (status.equals("SHIPPING")) admin.updateOrderStatus(1L,OrderStatus.SHIPPING);
        admin.updateOrderStatus(1L,OrderStatus.CANCELLED);
        assertThatThrownBy(() -> admin.updateOrderStatus(1L,OrderStatus.CANCELLED)).isInstanceOf(IllegalArgumentException.class);
        assertState("CANCELLED","PAID","RELEASED",10);
    }

    @Test void pendingRetrySuccessAndDoneNeverReserveAgainOrRelease() {
        simulate("PENDING");
        orders.retryPayment(1L,"refund@test.local");
        jdbc.update("UPDATE vouchers SET discount_percent=90,status='INACTIVE'");
        simulate("SUCCESS");
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
            var p = payments.findById(1L).orElseThrow();
            fulfillment.processSuccess(p,"DUPLICATE");
            fulfillment.processFailure(p,"LATE FAIL");
        });
        admin.updateOrderStatus(1L,OrderStatus.SHIPPING);
        admin.updateOrderStatus(1L,OrderStatus.DONE);
        assertThatThrownBy(() -> admin.updateOrderStatus(1L,OrderStatus.CANCELLED)).isInstanceOf(IllegalArgumentException.class);
        assertState("DONE","PAID","APPLIED",9);
        assertThat(jdbc.queryForObject("SELECT amount FROM payments",java.math.BigDecimal.class)).isEqualByComparingTo("130000");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM voucher_usages",Integer.class)).isEqualTo(1);
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
            var p = payments.findById(1L).orElseThrow();
            fulfillment.processSuccess(p,"DONE_DUPLICATE");
            fulfillment.processFailure(p,"DONE_FAIL");
        });
        assertState("DONE","PAID","APPLIED",9);
    }

    MomoIpnRequest lateIpn() {
        doReturn(true).when(momo).verifySignature(any());
        var request = new MomoIpnRequest();
        request.setOrderId("LEGACY_1"); request.setTransId("LEGACY_TX"); request.setResultCode(0);
        return request;
    }

    @Test void legacyLateIpnRefundCommitsTransactionIdAndIsIdempotent() {
        cancellation.cancelExpiredOrder(1L);
        var request = lateIpn();
        ipn.handleIpn(request);
        ipn.handleIpn(request);
        assertState("CANCELLED","REFUNDED","RELEASED",10);
        assertThat(jdbc.queryForObject("SELECT transaction_id FROM payments",String.class)).isEqualTo("LEGACY_TX");
    }

    @Test void legacyLateRefundRollsBackWithCaller() {
        cancellation.cancelExpiredOrder(1L);
        var request = lateIpn();
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
            ipn.handleIpn(request);
            throw new IllegalStateException("Force outer rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertState("CANCELLED","FAILED","RELEASED",10);
        assertThat(jdbc.queryForObject("SELECT transaction_id FROM payments",String.class)).isNull();
    }

    @Test void refundStubFailureRollsBackPreviouslyFlushedCancellationAndRelease() {
        jdbc.update("UPDATE product_variants SET stock=0");
        doThrow(new IllegalStateException("Simulated stub failure")).when(momo).refundPayment(any());
        assertThatThrownBy(() -> simulate("SUCCESS")).isInstanceOf(IllegalStateException.class);
        assertState("AWAITING_PAYMENT","PENDING","APPLIED",0);
    }

    @Test void legacyIpnStillHandlesPendingOrderState() {
        admin.updateOrderStatus(1L,OrderStatus.PENDING);
        ipn.handleIpn(lateIpn());
        assertState("CONFIRMED","PAID","APPLIED",9);
    }

    @Test void codCancellationRestocksAndReleases() {
        jdbc.update("UPDATE orders SET status='PENDING',payment_method='COD'");
        jdbc.update("UPDATE payments SET payment_method='COD'");
        jdbc.update("UPDATE product_variants SET stock=9");
        admin.updateOrderStatus(1L,OrderStatus.CANCELLED);
        assertState("CANCELLED","FAILED","RELEASED",10);
    }

    @Test void timeoutReleaseFailureRollsBackOrderAndPayment() {
        jdbc.update("UPDATE vouchers SET used_quantity=0");
        assertThatThrownBy(() -> cancellation.cancelExpiredOrder(1L)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM orders",String.class)).isEqualTo("AWAITING_PAYMENT");
        assertThat(jdbc.queryForObject("SELECT payment_status FROM payments",String.class)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT status FROM voucher_usages",String.class)).isEqualTo("APPLIED");
        assertThat(jdbc.queryForObject("SELECT used_quantity FROM vouchers",Integer.class)).isZero();
    }

    @Test void concurrentTimeoutsReleaseOnlyOnce() throws Exception {
        var ready = new CountDownLatch(2);
        Callable<Throwable> cancel = () -> catchThrowable(() -> {
            await(ready);
            cancellation.cancelExpiredOrder(1L);
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(cancel);
            var second = executor.submit(cancel);
            Throwable a = first.get(25,TimeUnit.SECONDS), b = second.get(25,TimeUnit.SECONDS);
            assertThat(a == null || b == null).isTrue();
            for (Throwable error : new Throwable[]{a,b}) {
                if (error != null) assertThat(error).isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
            }
        }
        assertState("CANCELLED","FAILED","RELEASED",10);
    }

    @ParameterizedTest @ValueSource(strings={"SUCCESS","FAIL","ADMIN"})
    void cancellationAndRefundAndReleaseRollBackTogether(String scenario) {
        int stock = scenario.equals("SUCCESS") ? 0 : 10;
        jdbc.update("UPDATE product_variants SET stock=?", stock);
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
            if (scenario.equals("ADMIN")) admin.updateOrderStatus(1L,OrderStatus.CANCELLED);
            else simulate(scenario);
            throw new IllegalStateException("Force outer rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertState("AWAITING_PAYMENT","PENDING","APPLIED",stock);
    }

    @ParameterizedTest @ValueSource(strings={"admin","timeout","success"})
    void concurrentSuccessAndCompetingTransitionRemainConsistent(String competitor) throws Exception {
        var ready = new CountDownLatch(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Throwable> success = () -> catchThrowable(() -> new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
                var p = payments.findById(1L).orElseThrow();
                p.getOrder().getStatus();
                await(ready);
                simulate("SUCCESS");
            }));
            var a = executor.submit(success);
            var b = executor.submit(() -> catchThrowable(() -> new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
                var p = payments.findById(1L).orElseThrow();
                p.getOrder().getStatus();
                await(ready);
                if (competitor.equals("admin")) admin.updateOrderStatus(1L,OrderStatus.CANCELLED);
                else if (competitor.equals("timeout")) cancellation.cancelExpiredOrder(1L);
                else simulate("SUCCESS");
            })));
            Throwable first = a.get(30,TimeUnit.SECONDS), second = b.get(30,TimeUnit.SECONDS);
            assertThat(first == null || second == null).isTrue();
            for (Throwable error : new Throwable[]{first,second}) {
                if (error != null) assertThat(error).isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
            }
        }
        String status = jdbc.queryForObject("SELECT status FROM orders",String.class);
        if (status.equals("CANCELLED")) assertState("CANCELLED","FAILED","RELEASED",10);
        else assertState("CONFIRMED","PAID","APPLIED",9);
    }

    private static void await(CountDownLatch ready) {
        ready.countDown();
        try { assertThat(ready.await(15,TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }

    @ParameterizedTest @ValueSource(ints={0,1006})
    void legacyIpnNormalResultPreservesPaymentAndVoucherLifecycle(int result) {
        var request = lateIpn();
        request.setResultCode(result);
        ipn.handleIpn(request);
        ipn.handleIpn(request);
        if (result == 0) assertState("CONFIRMED","PAID","APPLIED",9);
        else assertState("CANCELLED","FAILED","RELEASED",10);
    }

    @Test void optimisticConflictReturns409AndRollsBackPaymentStockCart() throws Exception {
        var entered = new CountDownLatch(1);
        var proceed = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            assertThat(proceed.await(15,TimeUnit.SECONDS)).isTrue();
            return invocation.callRealMethod();
        }).when((PaymentFulfillmentService) org.springframework.test.util.AopTestUtils.getUltimateTargetObject(fulfillment))
                .processSuccess(any(),anyString());
        try (var executor = Executors.newSingleThreadExecutor()) {
            var response = executor.submit(() -> mvc.perform(post("/api/v1/mock-payments/1/simulate")
                    .with(user("refund@test.local")).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"scenario\":\"SUCCESS\"}")).andReturn().getResponse());
            try {
                assertThat(entered.await(15,TimeUnit.SECONDS)).isTrue();
                jdbc.update("UPDATE orders SET version=version+1 WHERE id=1");
            } finally { proceed.countDown(); }
            assertThat(response.get(25,TimeUnit.SECONDS).getStatus()).isEqualTo(409);
        }
        assertState("AWAITING_PAYMENT","PENDING","APPLIED",10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cart_items",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT transaction_id FROM payments",String.class)).isNull();
    }
}
