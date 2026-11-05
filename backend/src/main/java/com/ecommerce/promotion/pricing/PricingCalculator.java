package com.ecommerce.promotion.pricing;

import com.ecommerce.promotion.enums.OrderDiscountSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Pure pricing over selected lines and already time-filtered rules. No persistence or reservations. */
public class PricingCalculator {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    public enum VoucherReason {
        NOT_FOUND, PROGRAM_INACTIVE, NOT_STARTED, EXPIRED, VOUCHER_INACTIVE,
        SOLD_OUT, MIN_AMOUNT_NOT_MET, ALREADY_USED, NOT_BETTER_THAN_ORDER_DISCOUNT
    }
    public record Item(Long cartItemId, Long productId, BigDecimal basePrice, int quantity) {}
    public record ProductRule(long id, long productId, long programId, BigDecimal percent) {}
    public record OrderRule(long id, BigDecimal minimum, BigDecimal percent, BigDecimal maximum) {}
    public record VoucherCandidate(Long id, String code, BigDecimal minimum, BigDecimal percent,
                                   BigDecimal maximum, VoucherReason reason) {}
    public record PricedItem(Long cartItemId, Long productId, int quantity, BigDecimal originalUnitPrice,
                             BigDecimal unitPrice, BigDecimal productDiscountPercent,
                             Long productDiscountProgramId, BigDecimal lineTotal) {}
    public record Discount(OrderDiscountSource source, Long id, String code, BigDecimal percent, BigDecimal amount) {}
    public record VoucherResult(String code, boolean applied, VoucherReason reason) {}
    public record Result(List<PricedItem> items, BigDecimal originalSubtotal, BigDecimal productDiscountTotal,
                         BigDecimal subtotal, Discount orderDiscount, BigDecimal shippingFee,
                         BigDecimal total, VoucherResult voucher) {
        public Result { items = List.copyOf(items); }
    }

    public Result calculate(List<Item> selectedItems, List<ProductRule> productRules, List<OrderRule> tiers,
                            VoucherCandidate voucher, BigDecimal shipping) {
        nonNegative(shipping);
        if (shipping.stripTrailingZeros().scale() > 0) throw new IllegalArgumentException("Shipping must be integer VND");
        Map<Long, ProductRule> bestProducts = new HashMap<>();
        for (var rule : productRules) {
            percent(rule.percent());
            bestProducts.merge(rule.productId(), rule, (a,b) ->
                    a.percent().compareTo(b.percent()) > 0 || (a.percent().compareTo(b.percent()) == 0 && a.id() < b.id()) ? a : b);
        }
        List<PricedItem> items = new ArrayList<>();
        BigDecimal original = ZERO;
        BigDecimal subtotal = ZERO;
        for (var item : selectedItems) {
            nonNegative(item.basePrice());
            if (item.quantity() <= 0) throw new IllegalArgumentException("Quantity must be positive");
            var rule = bestProducts.get(item.productId());
            BigDecimal base = money(item.basePrice());
            BigDecimal pct = rule == null ? ZERO : rule.percent();
            BigDecimal unit = money(item.basePrice().subtract(discount(item.basePrice(), pct, null)));
            BigDecimal line = unit.multiply(BigDecimal.valueOf(item.quantity()));
            original = original.add(base.multiply(BigDecimal.valueOf(item.quantity())));
            subtotal = subtotal.add(line);
            items.add(new PricedItem(item.cartItemId(), item.productId(), item.quantity(), base, unit, pct,
                    rule == null ? null : rule.programId(), line));
        }
        Discount best = new Discount(null, null, null, ZERO, ZERO);
        for (var tier : tiers) {
            validateRule(tier.minimum(), tier.percent(), tier.maximum());
            if (subtotal.compareTo(tier.minimum()) < 0) continue;
            BigDecimal amount = discount(subtotal, tier.percent(), tier.maximum());
            if (amount.compareTo(best.amount()) > 0 ||
                    (amount.compareTo(best.amount()) == 0 && best.id() != null && tier.id() < best.id())) {
                best = new Discount(OrderDiscountSource.ORDER_DISCOUNT, tier.id(), null, tier.percent(), amount);
            }
        }
        VoucherResult voucherResult = null;
        if (voucher != null) {
            VoucherReason reason = voucher.reason();
            if (reason == null) {
                validateRule(voucher.minimum(), voucher.percent(), voucher.maximum());
                if (subtotal.compareTo(voucher.minimum()) < 0) reason = VoucherReason.MIN_AMOUNT_NOT_MET;
                else {
                    BigDecimal amount = discount(subtotal, voucher.percent(), voucher.maximum());
                    if (amount.compareTo(best.amount()) >= 0) {
                        best = new Discount(OrderDiscountSource.VOUCHER, voucher.id(), voucher.code(), voucher.percent(), amount);
                    } else reason = VoucherReason.NOT_BETTER_THAN_ORDER_DISCOUNT;
                }
            }
            voucherResult = new VoucherResult(voucher.code(), reason == null, reason);
        }
        return new Result(items, original, original.subtract(subtotal), subtotal, best, money(shipping),
                subtotal.subtract(best.amount()).add(money(shipping)), voucherResult);
    }
    private static BigDecimal money(BigDecimal value) { return value.setScale(0, RoundingMode.HALF_UP); }
    private static BigDecimal discount(BigDecimal base, BigDecimal percent, BigDecimal maximum) {
        BigDecimal amount = money(base.multiply(percent).movePointLeft(2));
        return (maximum == null ? amount : amount.min(money(maximum))).min(base);
    }
    private static void nonNegative(BigDecimal value) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException("Money must be non-negative");
    }
    private static void percent(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.compareTo(BigDecimal.valueOf(100)) > 0)
            throw new IllegalArgumentException("Percent must be in (0,100]");
    }
    private static void validateRule(BigDecimal minimum, BigDecimal pct, BigDecimal maximum) {
        nonNegative(minimum);
        percent(pct);
        if (maximum != null && maximum.signum() <= 0) throw new IllegalArgumentException("Maximum must be positive");
    }
}
