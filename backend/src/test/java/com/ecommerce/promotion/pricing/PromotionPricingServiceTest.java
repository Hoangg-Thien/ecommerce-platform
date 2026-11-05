package com.ecommerce.promotion.pricing;

import com.ecommerce.promotion.service.PromotionPricingService;

import com.ecommerce.promotion.entity.*;
import com.ecommerce.promotion.enums.*;
import com.ecommerce.promotion.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
import static com.ecommerce.promotion.pricing.PricingCalculator.*;

class PromotionPricingServiceTest {
    final ProductDiscountRepository products = mock(ProductDiscountRepository.class);
    final OrderDiscountRepository tiers = mock(OrderDiscountRepository.class);
    final VoucherRepository vouchers = mock(VoucherRepository.class);
    final VoucherUsageRepository usages = mock(VoucherUsageRepository.class);
    final Instant now = Instant.parse("2026-10-30T00:00:00Z");
    final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    final PromotionPricingService service = new PromotionPricingService(products, tiers, vouchers, usages, clock);
    final List<Item> items = List.of(new Item(1L,10L,new BigDecimal("320000"),1));
    Voucher voucher;
    @BeforeEach void setup() {
        var program = new PromotionProgram();
        program.setId(1L); program.setStartDate(now); program.setEndDate(now.plusSeconds(60));
        voucher = new Voucher();
        voucher.setId(1L); voucher.setProgram(program); voucher.setCode("SALE10");
        voucher.setQuantity(5); voucher.setDiscountPercent(BigDecimal.TEN);
        when(vouchers.findByCode("SALE10")).thenReturn(Optional.of(voucher));
    }
    @Test void example12VoucherStartInclusiveEndExclusive() {
        assertThat(service.price(items,1L,"sale10",BigDecimal.ZERO).voucher().applied()).isTrue();
        voucher.getProgram().setEndDate(now);
        assertThat(service.price(items,1L,"sale10",BigDecimal.ZERO).voucher().reason()).isEqualTo(VoucherReason.EXPIRED);
    }
    @ParameterizedTest
    @EnumSource(value=VoucherReason.class, names={"NOT_FOUND","PROGRAM_INACTIVE","NOT_STARTED","EXPIRED","VOUCHER_INACTIVE","SOLD_OUT","ALREADY_USED","MIN_AMOUNT_NOT_MET"})
    void explainsVoucherRejectionWithoutMutation(VoucherReason reason) {
        switch(reason) {
            case NOT_FOUND -> when(vouchers.findByCode("SALE10")).thenReturn(Optional.empty());
            case PROGRAM_INACTIVE -> voucher.getProgram().setStatus(PromotionStatus.INACTIVE);
            case NOT_STARTED -> voucher.getProgram().setStartDate(now.plusSeconds(1));
            case EXPIRED -> voucher.getProgram().setEndDate(now);
            case VOUCHER_INACTIVE -> voucher.setStatus(PromotionStatus.INACTIVE);
            case SOLD_OUT -> voucher.setUsedQuantity(5);
            case ALREADY_USED -> when(usages.existsByVoucherIdAndUserIdAndStatus(1L,1L,VoucherUsageStatus.APPLIED)).thenReturn(true);
            case MIN_AMOUNT_NOT_MET -> voucher.setMinimumAmount(new BigDecimal("500000"));
            default -> throw new AssertionError(reason);
        }
        var result = service.price(items,1L," sale10 ",new BigDecimal("30000"));
        assertThat(result.voucher().reason()).isEqualTo(reason);
        assertThat(result.voucher().applied()).isFalse();
        assertThat(result.total()).isEqualByComparingTo("350000");
        verify(vouchers, never()).incrementUsageIfAvailable(anyLong());
        verify(usages, never()).save(any());
    }
    @Test void batchesProductRulesAndUsesOneClockInstant() {
        var p = new ProductDiscount();
        p.setId(2L); p.setProgram(voucher.getProgram()); p.setDiscountPercent(BigDecimal.TEN);
        var product = new com.ecommerce.product.entity.Product(); product.setId(10L); p.setProduct(product);
        when(products.findActiveForProducts(anyCollection(),eq(now))).thenReturn(List.of(p));
        var result = service.price(List.of(items.getFirst(),new Item(2L,10L,BigDecimal.TEN,2),new Item(3L,20L,BigDecimal.TEN,1)),1L,null,BigDecimal.ZERO);
        assertThat(result.items().getFirst().unitPrice()).isEqualByComparingTo("288000");
        verify(products).findActiveForProducts(Set.of(10L,20L),now);
        verify(tiers).findActiveAt(now);
        verifyNoInteractions(vouchers,usages);
    }
    @Test void validVoucherLosesToAutoWithoutReserving() {
        var tier = new OrderDiscount(); tier.setId(2L); tier.setMinimumAmount(BigDecimal.ZERO); tier.setDiscountPercent(new BigDecimal("20"));
        when(tiers.findActiveAt(now)).thenReturn(List.of(tier));
        var result=service.price(items,1L,"SALE10",BigDecimal.ZERO);
        assertThat(result.voucher().reason()).isEqualTo(VoucherReason.NOT_BETTER_THAN_ORDER_DISCOUNT);
        verify(vouchers,never()).incrementUsageIfAvailable(anyLong());
    }
    @Test void releasedUsageDoesNotRejectAndShippingComesFromCallerConfig() {
        assertThat(service.price(items,1L,"SALE10",new com.ecommerce.checkout.service.ShippingFeeService(BigDecimal.ZERO).getFlatFee()).shippingFee()).isEqualByComparingTo("0");
        verify(usages).existsByVoucherIdAndUserIdAndStatus(1L,1L,VoucherUsageStatus.APPLIED);
    }
}
