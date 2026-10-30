package com.ecommerce.promotion.integration;

import com.ecommerce.order.entity.Order;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.promotion.entity.*;
import com.ecommerce.promotion.enums.*;
import com.ecommerce.promotion.repository.*;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:tc:postgresql:17.9:///promotion_schema",
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate"
})
class PromotionRepositoryIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired PromotionProgramRepository programs;
    @Autowired VoucherRepository vouchers;
    @Autowired VoucherUsageRepository usages;
    @Autowired OrderDiscountRepository orderDiscounts;
    @Autowired ProductDiscountRepository productDiscounts;
    @Autowired OrderRepository orders;
    @Autowired ProductRepository products;
    @Autowired UserRepository users;
    @Autowired Clock clock;
    private TransactionTemplate tx;
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2027-01-01T00:00:00Z");

    @BeforeEach
    void seed() {
        tx = new TransactionTemplate(transactions);
        jdbc.execute("TRUNCATE voucher_usages, vouchers, product_discounts, order_discounts, promotion_programs, order_items, payments, orders, cart_items, carts, product_variants, products, refresh_tokens, users RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO users(id,email,password,role) VALUES (100,'one@test.local','test','USER'),(101,'two@test.local','test','USER')");
        jdbc.update("INSERT INTO products(id,name,price,version) VALUES (100,'Product',100000,0)");
        jdbc.update("INSERT INTO orders(id,user_id,total_price,subtotal_price,status) VALUES (100,100,100000,100000,'PENDING'),(101,100,100000,100000,'PENDING'),(102,101,100000,100000,'PENDING')");
        jdbc.update("INSERT INTO promotion_programs(id,name,start_date,end_date) VALUES (100,'Program','2026-01-01Z','2027-01-01Z')");
        jdbc.update("INSERT INTO vouchers(id,promotion_program_id,code,discount_percent,quantity) VALUES (100,100,'SALE10',10,2)");
        jdbc.update("INSERT INTO order_discounts(id,promotion_program_id,minimum_amount,discount_percent) VALUES (100,100,0,5)");
        jdbc.update("INSERT INTO product_discounts(id,promotion_program_id,product_id,discount_percent) VALUES (100,100,100,20)");
    }

    static Stream<String> invalidMutations() {
        return Stream.of(
            "UPDATE promotion_programs SET end_date=start_date",
            "UPDATE promotion_programs SET status='RUNNING'",
            "UPDATE vouchers SET code='sale10'",
            "UPDATE vouchers SET code=' BAD '",
            "UPDATE vouchers SET code=''",
            "UPDATE vouchers SET code=repeat('A',51)",
            "UPDATE vouchers SET discount_percent=0",
            "UPDATE vouchers SET discount_percent=100.01",
            "UPDATE vouchers SET minimum_amount=-1",
            "UPDATE vouchers SET maximum_discount=0",
            "UPDATE vouchers SET quantity=0",
            "UPDATE vouchers SET used_quantity=-1",
            "UPDATE vouchers SET used_quantity=3",
            "UPDATE vouchers SET status='EXPIRED'",
            "UPDATE order_discounts SET minimum_amount=-1",
            "UPDATE order_discounts SET discount_percent=0",
            "UPDATE order_discounts SET discount_percent=101",
            "UPDATE order_discounts SET maximum_discount=0",
            "UPDATE product_discounts SET discount_percent=0",
            "UPDATE product_discounts SET discount_percent=101",
            "INSERT INTO vouchers(promotion_program_id,code,discount_percent,quantity) VALUES (100,'SALE10',10,2)",
            "INSERT INTO order_discounts(promotion_program_id,minimum_amount,discount_percent) VALUES (100,0,10)",
            "INSERT INTO product_discounts(promotion_program_id,product_id,discount_percent) VALUES (100,100,10)",
            "UPDATE vouchers SET promotion_program_id=999",
            "UPDATE product_discounts SET product_id=999",
            "INSERT INTO voucher_usages(voucher_id,user_id,order_id,discount_amount) VALUES (999,100,100,10)",
            "INSERT INTO voucher_usages(voucher_id,user_id,order_id,discount_amount) VALUES (100,999,100,10)",
            "INSERT INTO voucher_usages(voucher_id,user_id,order_id,discount_amount) VALUES (100,100,999,10)",
            "INSERT INTO voucher_usages(voucher_id,user_id,order_id,discount_amount) VALUES (100,100,NULL,10)",
            "INSERT INTO voucher_usages(voucher_id,user_id,order_id,discount_amount) VALUES (100,100,100,-1)",
            "INSERT INTO voucher_usages(voucher_id,user_id,order_id,discount_amount,status) VALUES (100,100,100,10,'INVALID')"
        );
    }

    @ParameterizedTest
    @MethodSource("invalidMutations")
    void databaseRejectsInvalidRulesAndReferences(String sql) {
        assertThatThrownBy(() -> jdbc.update(sql)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void roundTripsAllPromotionEntitiesAndNormalizesVoucherCode() {
        tx.executeWithoutResult(status -> {
            var program = new PromotionProgram();
            program.setName("New program");
            program.setStartDate(START);
            program.setEndDate(END);
            programs.saveAndFlush(program);
            var voucher = new Voucher();
            voucher.setProgram(program);
            voucher.setCode("  new10  ");
            voucher.setQuantity(3);
            voucher.setDiscountPercent(new BigDecimal("10.50"));
            vouchers.saveAndFlush(voucher);
            assertThat(vouchers.findByCode("NEW10")).isPresent();

            var tier = new OrderDiscount();
            tier.setProgram(program);
            tier.setMinimumAmount(BigDecimal.ZERO);
            tier.setDiscountPercent(BigDecimal.TEN);
            orderDiscounts.saveAndFlush(tier);
            var rule = new ProductDiscount();
            rule.setProgram(program);
            rule.setProduct(products.getReferenceById(100L));
            rule.setDiscountPercent(new BigDecimal("25"));
            productDiscounts.saveAndFlush(rule);
            assertThat(orderDiscounts.findByProgramId(program.getId())).hasSize(1);
            assertThat(productDiscounts.findByProgramId(program.getId())).hasSize(1);
            assertThat(vouchers.findByProgramId(program.getId())).hasSize(1);
        });
        assertThat(programs.findAll()).anySatisfy(p -> {
            assertThat(p.getName()).isEqualTo("New program");
            assertThat(p.getStartDate()).isEqualTo(START);
            assertThat(p.getEndDate()).isEqualTo(END);
        });
    }

    @Test
    void activeQueriesRespectStartEndStatusAndProductSelection() {
        assertThat(productDiscounts.findActiveForProducts(List.of(100L), START)).hasSize(1);
        assertThat(orderDiscounts.findActiveAt(START)).hasSize(1);
        assertThat(productDiscounts.findActiveForProducts(List.of(101L), START)).isEmpty();
        assertThat(productDiscounts.findActiveForProducts(List.of(100L), START.minusSeconds(1))).isEmpty();
        assertThat(orderDiscounts.findActiveAt(START.minusSeconds(1))).isEmpty();
        assertThat(productDiscounts.findActiveForProducts(List.of(100L), END)).isEmpty();
        assertThat(orderDiscounts.findActiveAt(END)).isEmpty();
        jdbc.update("UPDATE promotion_programs SET status='INACTIVE'");
        assertThat(productDiscounts.findActiveForProducts(List.of(100L), START)).isEmpty();
        assertThat(orderDiscounts.findActiveAt(START)).isEmpty();
    }

    private void insertUsage(long orderId) {
        jdbc.update("INSERT INTO voucher_usages(voucher_id,user_id,order_id,discount_amount) VALUES (100,100,?,10000)", orderId);
    }

    @Test
    void partialUniqueAllowsReuseOnlyAfterReleaseAndNeverReusesOrder() {
        insertUsage(100);
        assertThatThrownBy(() -> insertUsage(101)).isInstanceOf(DataIntegrityViolationException.class);
        tx.executeWithoutResult(status -> {
            assertThat(usages.releaseIfApplied(100L, clock.instant())).isEqualTo(1);
            assertThat(usages.releaseIfApplied(100L, clock.instant())).isZero();
        });
        insertUsage(101);
        assertThat(usages.findByOrderId(100L).orElseThrow().getReleasedAt()).isNotNull();
        assertThat(usages.existsByVoucherIdAndUserIdAndStatus(100L, 100L, VoucherUsageStatus.APPLIED)).isTrue();
        tx.executeWithoutResult(status -> usages.releaseIfApplied(101L, clock.instant()));
        assertThatThrownBy(() -> insertUsage(100)).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_voucher_usages_order");
    }

    @Test
    void counterUpdatesAreBoundedAndEntitySavesCannotOverwriteCounter() {
        tx.executeWithoutResult(status -> {
            var stale = vouchers.findById(100L).orElseThrow();
            assertThat(vouchers.incrementUsageIfAvailable(100L)).isEqualTo(1);
            assertThat(vouchers.incrementUsageIfAvailable(100L)).isEqualTo(1);
            assertThat(vouchers.incrementUsageIfAvailable(100L)).isZero();
            stale.setDiscountPercent(new BigDecimal("15"));
            vouchers.saveAndFlush(stale);
        });
        assertThat(vouchers.findById(100L).orElseThrow().getUsedQuantity()).isEqualTo(2);
        assertThatThrownBy(() -> jdbc.update("UPDATE vouchers SET quantity=1")).isInstanceOf(DataIntegrityViolationException.class);
        tx.executeWithoutResult(status -> {
            assertThat(vouchers.decrementUsageIfPositive(100L)).isEqualTo(1);
            assertThat(vouchers.decrementUsageIfPositive(100L)).isEqualTo(1);
            assertThat(vouchers.decrementUsageIfPositive(100L)).isZero();
        });
        assertThat(vouchers.findById(100L).orElseThrow().getUsedQuantity()).isZero();
    }

    @Test
    void failedUsageInsertRollsBackCounterInSameTransaction() {
        insertUsage(100);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            assertThat(vouchers.incrementUsageIfAvailable(100L)).isEqualTo(1);
            var usage = new VoucherUsage();
            usage.setVoucher(vouchers.getReferenceById(100L));
            usage.setUser(users.getReferenceById(100L));
            usage.setOrder(orders.getReferenceById(101L));
            usage.setDiscountAmount(BigDecimal.TEN);
            usage.setCreatedAt(clock.instant());
            usages.saveAndFlush(usage);
        })).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(vouchers.findById(100L).orElseThrow().getUsedQuantity()).isZero();
        assertThat(usages.count()).isEqualTo(1);
    }

    @Test
    void usageRoundTripsClockTimestampAndReleaseRollsBackWithTransaction() {
        Instant createdAt = clock.instant();
        tx.executeWithoutResult(status -> {
            var usage = new VoucherUsage();
            usage.setVoucher(vouchers.getReferenceById(100L));
            usage.setUser(users.getReferenceById(100L));
            usage.setOrder(orders.getReferenceById(100L));
            usage.setDiscountAmount(BigDecimal.TEN);
            usage.setCreatedAt(createdAt);
            usages.saveAndFlush(usage);
        });
        assertThat(usages.findByOrderId(100L).orElseThrow().getCreatedAt())
                .isCloseTo(createdAt, org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MICROS));
        tx.executeWithoutResult(status -> {
            assertThat(usages.releaseIfApplied(100L, clock.instant())).isEqualTo(1);
            status.setRollbackOnly();
        });
        assertThat(usages.findByOrderId(100L).orElseThrow().getStatus()).isEqualTo(VoucherUsageStatus.APPLIED);
    }

    @Test
    void historyBlocksDeletionEvenAfterUsageReleased() {
        insertUsage(100);
        tx.executeWithoutResult(status -> usages.releaseIfApplied(100L, clock.instant()));
        assertThatThrownBy(() -> jdbc.update("DELETE FROM vouchers WHERE id=100")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM orders WHERE id=100")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM promotion_programs WHERE id=100")).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(usages.existsByVoucherId(100L)).isTrue();
    }

    @Test
    void snapshotReferencesBlockRuleAndProgramDeletion() {
        jdbc.update("UPDATE orders SET order_discount_id=100 WHERE id=100");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM order_discounts WHERE id=100")).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("INSERT INTO order_items(order_id,product_id,quantity,price,original_price,product_discount_program_id) VALUES (100,100,1,80000,100000,100)");
        jdbc.update("DELETE FROM product_discounts");
        jdbc.update("DELETE FROM vouchers");
        jdbc.update("UPDATE orders SET order_discount_id=NULL");
        jdbc.update("DELETE FROM order_discounts");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM promotion_programs WHERE id=100")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM products WHERE id=100")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unusedProductDeletionCleansRuleWithoutDeletingProgram() {
        assertThat(products.findById(100L).orElseThrow().getVersion()).isZero();
        tx.executeWithoutResult(status -> products.deleteById(100L));
        assertThat(products.existsById(100L)).isFalse();
        assertThat(productDiscounts.count()).isZero();
        assertThat(programs.existsById(100L)).isTrue();
    }

    @Test
    void unusedProgramCanBeDeletedAfterItsChildren() {
        tx.executeWithoutResult(status -> {
            productDiscounts.deleteById(100L);
            vouchers.deleteById(100L);
            orderDiscounts.deleteById(100L);
            programs.deleteById(100L);
        });
        assertThat(programs.existsById(100L)).isFalse();
        assertThat(products.existsById(100L)).isTrue();
    }

    @Test
    void counterAndReleaseRequireCallerTransaction() {
        assertThatThrownBy(() -> vouchers.incrementUsageIfAvailable(100L))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThatThrownBy(() -> vouchers.decrementUsageIfPositive(100L))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThatThrownBy(() -> usages.releaseIfApplied(100L, clock.instant()))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test
    void explicitOrderSnapshotSurvivesPromotionEdit() {
        Long id = tx.execute(status -> {
            Order order = new Order();
            order.setUser(users.getReferenceById(100L));
            order.setTotalPrice(new BigDecimal("90000"));
            order.setSubtotalPrice(new BigDecimal("100000"));
            order.setDiscountAmount(new BigDecimal("10000"));
            order.setOrderDiscountSource(OrderDiscountSource.ORDER_DISCOUNT);
            order.setOrderDiscountPercent(BigDecimal.TEN);
            order.setOrderDiscountId(100L);
            return orders.saveAndFlush(order).getId();
        });
        jdbc.update("UPDATE order_discounts SET discount_percent=25 WHERE id=100");
        var saved = orders.findById(id).orElseThrow();
        assertThat(saved.getTotalPrice()).isEqualByComparingTo("90000");
        assertThat(saved.getSubtotalPrice()).isEqualByComparingTo("100000");
        assertThat(saved.getDiscountAmount()).isEqualByComparingTo("10000");
        assertThat(saved.getOrderDiscountPercent()).isEqualByComparingTo("10");
        assertThat(saved.getOrderDiscountSource()).isEqualTo(OrderDiscountSource.ORDER_DISCOUNT);
    }
}
