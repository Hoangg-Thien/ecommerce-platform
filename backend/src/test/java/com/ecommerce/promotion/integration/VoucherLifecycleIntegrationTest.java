package com.ecommerce.promotion.integration;

import com.ecommerce.promotion.service.VoucherUsageService;
import com.ecommerce.promotion.service.VoucherSoldOutException;
import com.ecommerce.promotion.repository.VoucherRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntConsumer;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:tc:postgresql:17.9:///voucher_lifecycle",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.flyway.enabled=true"
})
class VoucherLifecycleIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired VoucherUsageService service;
    @Autowired VoucherRepository vouchers;
    @Autowired com.ecommerce.order.repository.OrderRepository orders;
    TransactionTemplate tx;

    @BeforeEach void setup() {
        tx = new TransactionTemplate(manager);
        jdbc.execute("TRUNCATE voucher_usages,vouchers,product_discounts,order_discounts,promotion_programs,order_items,payments,orders,cart_items,carts,product_variants,products,refresh_tokens,users RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO users(id,email,password) SELECT i,'user'||i||'@test.local','test' FROM generate_series(1,20) i");
        jdbc.update("INSERT INTO orders(id,user_id,total_price,subtotal_price,status,version) SELECT i,i,100000,100000,'AWAITING_PAYMENT',0 FROM generate_series(1,20) i");
        jdbc.update("INSERT INTO orders(id,user_id,total_price,subtotal_price,status,version) VALUES (21,1,100000,100000,'AWAITING_PAYMENT',0)");
        jdbc.update("INSERT INTO promotion_programs(id,name,start_date,end_date) VALUES (1,'Sale','2026-01-01Z','2027-01-01Z')");
        jdbc.update("INSERT INTO vouchers(id,promotion_program_id,code,discount_percent,quantity) VALUES (1,1,'SALE10',10,5)");
    }
    void reserve(long user, long order) {
        tx.executeWithoutResult(s -> service.reserve(vouchers.getReferenceById(1L),user,order,new BigDecimal("10000")));
    }
    int used() { return jdbc.queryForObject("SELECT used_quantity FROM vouchers WHERE id=1",Integer.class); }
    int applied() { return jdbc.queryForObject("SELECT count(*) FROM voucher_usages WHERE status='APPLIED'",Integer.class); }
    void cancel(long order) {
        tx.executeWithoutResult(s -> {
            jdbc.update("UPDATE orders SET status='CANCELLED' WHERE id=?",order);
            service.releaseByOrderId(order);
        });
    }
    List<Throwable> parallel(int count, IntConsumer action) throws Exception {
        var pool = Executors.newFixedThreadPool(count);
        var ready = new CountDownLatch(count);
        var start = new CountDownLatch(1);
        List<Future<Throwable>> jobs = new ArrayList<>();
        try {
            for (int i=0;i<count;i++) {
                final int index=i;
                jobs.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(20,TimeUnit.SECONDS)) return new AssertionError("Start timed out");
                    try { action.accept(index); return null; } catch (Throwable e) { return e; }
                }));
            }
            assertThat(ready.await(20,TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Throwable> failures = new ArrayList<>();
            for (var job:jobs) { var failure=job.get(40,TimeUnit.SECONDS); if(failure!=null) failures.add(failure); }
            return failures;
        } finally { start.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue(); }
    }
    @RepeatedTest(3) void twentyUsersCompeteForFiveUses() throws Exception {
        var failures=parallel(20,i -> reserve(i+1,i+1));
        assertThat(failures).hasSize(15).allMatch(e -> e instanceof VoucherSoldOutException);
        assertThat(used()).isEqualTo(5);
        assertThat(applied()).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT user_id) FROM voucher_usages",Integer.class)).isEqualTo(5);
    }
    @Test void sameUserConcurrentCheckoutOnlyOneWins() throws Exception {
        var failures=parallel(2,i -> reserve(1,i==0?1:21));
        assertThat(failures).hasSize(1).allMatch(e -> e instanceof DataIntegrityViolationException);
        assertThat(used()).isEqualTo(1);
        assertThat(applied()).isEqualTo(1);
    }
    @Test void concurrentReleaseDecrementsOnlyOnce() throws Exception {
        reserve(1,1);
        jdbc.update("UPDATE orders SET status='CANCELLED' WHERE id=1");
        assertThat(parallel(2,i -> tx.executeWithoutResult(s -> service.releaseByOrderId(1L)))).isEmpty();
        assertThat(used()).isZero();
        assertThat(applied()).isZero();
        var releasedAt=jdbc.queryForObject("SELECT released_at FROM voucher_usages WHERE order_id=1",java.time.OffsetDateTime.class);
        tx.executeWithoutResult(s -> service.releaseByOrderId(1L));
        assertThat(jdbc.queryForObject("SELECT released_at FROM voucher_usages WHERE order_id=1",java.time.OffsetDateTime.class)).isEqualTo(releasedAt);
    }
    @Test void releaseAllowsUserToUseVoucherAgainOnNewOrder() {
        reserve(1,1); cancel(1); reserve(1,21);
        assertThat(used()).isEqualTo(1); assertThat(applied()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM voucher_usages",Integer.class)).isEqualTo(2);
    }
    @Test void reserveRollsBackWithCallerTransaction() {
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            service.reserve(vouchers.getReferenceById(1L),1L,1L,BigDecimal.TEN);
            throw new IllegalStateException("Checkout failed");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(used()).isZero(); assertThat(applied()).isZero();
    }
    @Test void failedUsageInsertRollsBackIncrement() {
        assertThatThrownBy(() -> reserve(1,999)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(used()).isZero(); assertThat(applied()).isZero();
    }
    @Test void cancellationRollbackRestoresUsageCounterAndOrder() {
        reserve(1,1);
        tx.executeWithoutResult(s -> {
            jdbc.update("UPDATE orders SET status='CANCELLED' WHERE id=1");
            service.releaseByOrderId(1L);
            s.setRollbackOnly();
        });
        assertThat(used()).isEqualTo(1); assertThat(applied()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=1",String.class)).isEqualTo("AWAITING_PAYMENT");
    }
    @Test void doneAndUncancelledOrdersNeverRelease() {
        reserve(1,1);
        tx.executeWithoutResult(s -> service.releaseByOrderId(1L));
        jdbc.update("UPDATE orders SET status='DONE' WHERE id=1");
        tx.executeWithoutResult(s -> service.releaseByOrderId(1L));
        assertThat(used()).isEqualTo(1); assertThat(applied()).isEqualTo(1);
    }
    @Test void missingUsageIsNoOpAndTransactionsAreMandatory() {
        tx.executeWithoutResult(s -> service.releaseByOrderId(999L));
        assertThatThrownBy(() -> service.releaseByOrderId(1L)).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThatThrownBy(() -> service.reserve(vouchers.getReferenceById(1L),1L,1L,BigDecimal.TEN))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }
    @Test void counterInconsistencyFailsAndRollsBackRelease() {
        reserve(1,1);
        jdbc.update("UPDATE vouchers SET used_quantity=0");
        assertThatThrownBy(() -> cancel(1)).isInstanceOf(IllegalStateException.class);
        assertThat(applied()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=1",String.class)).isEqualTo("AWAITING_PAYMENT");
    }

    @RepeatedTest(3) void releaseRacesWithSameUserReuseWithoutDeadlock() throws Exception {
        reserve(1,1);
        jdbc.update("UPDATE orders SET status='CANCELLED' WHERE id=1");
        var failures=parallel(2,i -> {
            if (i==0) tx.executeWithoutResult(s -> service.releaseByOrderId(1L));
            else reserve(1,21);
        });
        // Reuse may reach the unique index before release commits; either ordering is valid.
        assertThat(failures).hasSizeLessThanOrEqualTo(1).allMatch(e -> e instanceof DataIntegrityViolationException);
        assertThat(jdbc.queryForObject("SELECT status FROM voucher_usages WHERE order_id=1",String.class)).isEqualTo("RELEASED");
        assertThat(used()).isEqualTo(applied()).isBetween(0,1);
    }

    @Test void duplicateReservationForSameOrderRollsBackIncrement() {
        reserve(1,1);
        assertThatThrownBy(() -> reserve(1,1)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(used()).isEqualTo(1); assertThat(applied()).isEqualTo(1);
    }

    @Test void failedReservationRollsBackNewOrderAndPaymentToo() {
        jdbc.update("UPDATE vouchers SET used_quantity=quantity");
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            jdbc.update("INSERT INTO orders(id,user_id,total_price,subtotal_price,status) VALUES (99,1,100000,100000,'AWAITING_PAYMENT')");
            jdbc.update("INSERT INTO payments(order_id,amount,payment_status,payment_method) VALUES (99,100000,'PENDING','VNPAY')");
            service.reserve(vouchers.getReferenceById(1L),1L,99L,BigDecimal.TEN);
        })).isInstanceOf(VoucherSoldOutException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders WHERE id=99",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE order_id=99",Integer.class)).isZero();
        assertThat(used()).isEqualTo(5); assertThat(applied()).isZero();
    }

    @Test void invalidReservationInputsDoNotIncrement() {
        assertThatThrownBy(() -> tx.executeWithoutResult(s ->
                service.reserve(vouchers.getReferenceById(1L),1L,null,BigDecimal.TEN)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tx.executeWithoutResult(s ->
                service.reserve(vouchers.getReferenceById(1L),1L,1L,new BigDecimal("-1"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tx.executeWithoutResult(s ->
                service.reserve(vouchers.getReferenceById(1L),1L,1L,new BigDecimal("0.5"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(used()).isZero(); assertThat(applied()).isZero();
    }

    @Test void managedOrderCancellationIsFlushedAndReleasedInSameTransaction() {
        reserve(1,1);
        tx.executeWithoutResult(s -> {
            var order=orders.findById(1L).orElseThrow();
            order.setStatus(com.ecommerce.order.enums.OrderStatus.CANCELLED);
            service.releaseByOrderId(1L);
        });
        assertThat(used()).isZero(); assertThat(applied()).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=1",String.class)).isEqualTo("CANCELLED");
    }
}
