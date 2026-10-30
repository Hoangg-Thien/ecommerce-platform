package com.ecommerce.promotion.service;

import com.ecommerce.promotion.entity.Voucher;
import com.ecommerce.promotion.enums.PromotionStatus;
import com.ecommerce.promotion.enums.VoucherUsageStatus;
import com.ecommerce.promotion.pricing.PricingCalculator;
import com.ecommerce.promotion.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import static com.ecommerce.promotion.pricing.PricingCalculator.*;

@Service
@RequiredArgsConstructor
public class PromotionPricingService {
    private final ProductDiscountRepository productDiscounts;
    private final OrderDiscountRepository orderDiscounts;
    private final VoucherRepository vouchers;
    private final VoucherUsageRepository usages;
    private final Clock clock;
    private final PricingCalculator calculator = new PricingCalculator();

    /** selectedItems and shippingFee must come from trusted backend data, never request prices. */
    @Transactional(readOnly = true)
    public Result price(List<Item> selectedItems, Long userId, String voucherCode, BigDecimal shippingFee) {
        Instant now = clock.instant();
        Set<Long> productIds = selectedItems.stream().map(Item::productId).collect(Collectors.toSet());
        List<ProductRule> products = productIds.isEmpty() ? List.of() :
                productDiscounts.findActiveForProducts(productIds, now).stream()
                .map(d -> new ProductRule(d.getId(), d.getProduct().getId(), d.getProgram().getId(), d.getDiscountPercent()))
                .toList();
        List<OrderRule> tiers = orderDiscounts.findActiveAt(now).stream()
                .map(d -> new OrderRule(d.getId(), d.getMinimumAmount(), d.getDiscountPercent(), d.getMaximumDiscount()))
                .toList();
        VoucherCandidate candidate = null;
        if (voucherCode != null && !voucherCode.isBlank()) {
            if (userId == null) throw new IllegalArgumentException("Voucher pricing requires user ID");
            String code = voucherCode.trim().toUpperCase(Locale.ROOT);
            Voucher voucher = vouchers.findByCode(code).orElse(null);
            candidate = voucher == null
                    ? new VoucherCandidate(null, code, null, null, null, VoucherReason.NOT_FOUND)
                    : new VoucherCandidate(voucher.getId(), code, voucher.getMinimumAmount(), voucher.getDiscountPercent(),
                            voucher.getMaximumDiscount(), rejection(voucher, userId, now));
        }
        return calculator.calculate(selectedItems, products, tiers, candidate, shippingFee);
    }

    private VoucherReason rejection(Voucher voucher, Long userId, Instant now) {
        var program = voucher.getProgram();
        if (program.getStatus() != PromotionStatus.ACTIVE) return VoucherReason.PROGRAM_INACTIVE;
        if (now.isBefore(program.getStartDate())) return VoucherReason.NOT_STARTED;
        if (!now.isBefore(program.getEndDate())) return VoucherReason.EXPIRED;
        if (voucher.getStatus() != PromotionStatus.ACTIVE) return VoucherReason.VOUCHER_INACTIVE;
        if (voucher.getUsedQuantity() >= voucher.getQuantity()) return VoucherReason.SOLD_OUT;
        if (usages.existsByVoucherIdAndUserIdAndStatus(voucher.getId(), userId, VoucherUsageStatus.APPLIED))
            return VoucherReason.ALREADY_USED;
        return null;
    }
}
