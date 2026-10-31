package com.ecommerce.promotion.service;

import com.ecommerce.promotion.pricing.PricingCalculator;
import com.ecommerce.promotion.repository.ProductDiscountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import static com.ecommerce.promotion.pricing.PricingCalculator.*;

/** Product discounts only: no order tiers, vouchers or shipping in catalog/cart prices. */
@Service
@RequiredArgsConstructor
public class ProductSalePricingService {
    public record Sale(BigDecimal unitPrice, BigDecimal percent, Instant endsAt) {}
    private final ProductDiscountRepository discounts;
    private final Clock clock;
    private final PricingCalculator calculator = new PricingCalculator();

    @Transactional(readOnly = true)
    public Map<Long, Sale> price(Map<Long, BigDecimal> basePrices) {
        if (basePrices.isEmpty()) return Map.of();
        var active = discounts.findActiveForProducts(basePrices.keySet(), clock.instant());
        var rules = active.stream().map(d -> new ProductRule(d.getId(),d.getProduct().getId(),
                d.getProgram().getId(),d.getDiscountPercent())).toList();
        Map<Long, Instant> ends = new HashMap<>();
        active.forEach(d -> ends.put(d.getProgram().getId(),d.getProgram().getEndDate()));
        var items = basePrices.entrySet().stream().map(e -> new Item(null,e.getKey(),e.getValue(),1)).toList();
        var priced = calculator.calculate(items,rules,List.of(),null,BigDecimal.ZERO);
        Map<Long, Sale> result = new HashMap<>();
        priced.items().forEach(item -> result.put(item.productId(),new Sale(item.unitPrice(),
                item.productDiscountPercent(),ends.get(item.productDiscountProgramId()))));
        return result;
    }
}
