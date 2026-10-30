package com.ecommerce.promotion.pricing;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static com.ecommerce.promotion.pricing.PricingCalculator.*;

class PricingCalculatorTest {
    private final PricingCalculator calculator = new PricingCalculator();
    static BigDecimal n(String value) { return new BigDecimal(value); }
    static Item item(String price, int qty) { return new Item(1L, 10L, n(price), qty); }
    static ProductRule product(long id, String percent) { return new ProductRule(id, 10L, id, n(percent)); }
    static OrderRule tier(long id, String min, String percent, String cap) {
        return new OrderRule(id, n(min), n(percent), cap == null ? null : n(cap));
    }
    static VoucherCandidate voucher(String min, String percent, String cap) {
        return new VoucherCandidate(1L, "SALE10", n(min), n(percent), cap == null ? null : n(cap), null);
    }
    Result price(Item item, List<ProductRule> products, List<OrderRule> tiers, VoucherCandidate voucher, String ship) {
        return calculator.calculate(List.of(item), products, tiers, voucher, n(ship));
    }
    @Test void example1ProductThenCappedVoucher() {
        var r = price(item("200000",2), List.of(product(1,"20")), List.of(tier(1,"300000","5",null)), voucher("300000","10","30000"),"30000");
        assertThat(r.items().getFirst().unitPrice()).isEqualByComparingTo("160000");
        assertThat(r.subtotal()).isEqualByComparingTo("320000");
        assertThat(r.orderDiscount().amount()).isEqualByComparingTo("30000");
        assertThat(r.total()).isEqualByComparingTo("320000");
        assertThat(r.voucher().applied()).isTrue();
    }
    @Test void example2RoundsDiscountPerUnit() {
        assertThat(price(item("99999",1),List.of(product(1,"15")),List.of(),null,"0").items().getFirst().unitPrice()).isEqualByComparingTo("84999");
    }
    @Test void example3MinimumNotMet() {
        var r = price(item("320000",1),List.of(),List.of(),voucher("500000","10",null),"0");
        assertThat(r.voucher().reason()).isEqualTo(VoucherReason.MIN_AMOUNT_NOT_MET);
        assertThat(r.voucher().applied()).isFalse();
    }
    @Test void example4HundredPercentZeroShipping() {
        assertThat(price(item("200000",1),List.of(),List.of(),voucher("0","100",null),"0").total()).isEqualByComparingTo("0");
    }
    @Test void example5ShippingNotDiscounted() {
        assertThat(price(item("200000",1),List.of(),List.of(),voucher("0","100",null),"30000").total()).isEqualByComparingTo("30000");
    }
    @Test void example6HighestProductPercentWins() {
        assertThat(price(item("200000",1),List.of(product(1,"10"),product(2,"25")),List.of(),null,"0").subtotal()).isEqualByComparingTo("150000");
    }
    @Test void example7VoucherWinsTie() {
        assertThat(price(item("320000",1),List.of(),List.of(tier(1,"0","5",null)),voucher("0","5",null),"0").voucher().applied()).isTrue();
    }
    @Test void example8AutoWinsWithoutVoucherUsage() {
        var r = price(item("400000",1),List.of(),List.of(tier(1,"0","10",null)),voucher("0","10","30000"),"0");
        assertThat(r.orderDiscount().amount()).isEqualByComparingTo("40000");
        assertThat(r.voucher().reason()).isEqualTo(VoucherReason.NOT_BETTER_THAN_ORDER_DISCOUNT);
        assertThat(r.voucher().applied()).isFalse();
    }
    @Test void example9OnlySelectedItemsContribute() {
        var cart = List.of(item("100000",1),new Item(2L,20L,n("500000"),1),new Item(3L,30L,n("500000"),1));
        var r = calculator.calculate(cart.subList(0,1),List.of(),List.of(tier(1,"300000","10",null)),null,n("30000"));
        assertThat(r.total()).isEqualByComparingTo("130000");
        assertThat(r.orderDiscount().amount()).isEqualByComparingTo("0");
    }
    @Test void example10BestTier() {
        assertThat(price(item("520000",1),List.of(),List.of(tier(1,"300000","5",null),tier(2,"500000","10",null)),null,"0").orderDiscount().amount()).isEqualByComparingTo("52000");
    }
    @Test void example11NoPromotion() {
        var r=price(item("100000",2),List.of(),List.of(),null,"30000");
        assertThat(r.total()).isEqualByComparingTo("230000");
        assertThat(r.productDiscountTotal()).isEqualByComparingTo("0");
        assertThat(r.voucher()).isNull();
    }
    @Test void deterministicTiesAndBestAmountRatherThanPercent() {
        var r=price(item("100000",1),List.of(product(8,"20"),product(2,"20")),List.of(tier(8,"0","10",null),tier(2,"0","10",null),tier(1,"0","90","100")),null,"0");
        assertThat(r.items().getFirst().productDiscountProgramId()).isEqualTo(2L);
        assertThat(r.orderDiscount().id()).isEqualTo(2L);
    }
    @Test void roundsBeforeMultiplyingQuantityAndCapsAtSubtotal() {
        var r=price(item("5",3),List.of(product(1,"10")),List.of(),voucher("0","100","999"),"0");
        assertThat(r.subtotal()).isEqualByComparingTo("12");
        assertThat(r.total()).isEqualByComparingTo("0");
        assertThat(r.total().scale()).isZero();
    }
    @Test void invalidInputsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> price(item("10",0),List.of(),List.of(),null,"0"));
        assertThatIllegalArgumentException().isThrownBy(() -> price(item("-1",1),List.of(),List.of(),null,"0"));
        assertThatIllegalArgumentException().isThrownBy(() -> price(item("10",1),List.of(),List.of(),null,"-1"));
        assertThatIllegalArgumentException().isThrownBy(() -> price(item("10",1),List.of(product(1,"101")),List.of(),null,"0"));
    }
    @Test void fractionalBaseCalculatesDiscountBeforeFinalVndRounding() {
        // Existing product storage allows decimals. Apply the specified formula before rounding the payable unit.
        assertThat(price(item("4.50",1),List.of(product(1,"10")),List.of(),null,"0").subtotal()).isEqualByComparingTo("5");
    }
    @Test void invalidVoucherStillShowsAutomaticDiscountInPreview() {
        var invalid = new VoucherCandidate(null,"MISSING",null,null,null,VoucherReason.NOT_FOUND);
        var r=price(item("100000",1),List.of(),List.of(tier(1,"0","10",null)),invalid,"30000");
        assertThat(r.total()).isEqualByComparingTo("120000");
        assertThat(r.voucher().reason()).isEqualTo(VoucherReason.NOT_FOUND);
    }
    @Test void hundredPercentProductAndImmutableResult() {
        var r=price(item("100000",1),List.of(product(1,"100")),List.of(),null,"0");
        assertThat(r.total()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> r.items().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
