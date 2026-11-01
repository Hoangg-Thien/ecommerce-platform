package com.ecommerce.promotion.dto.request;

import com.ecommerce.promotion.enums.PromotionStatus;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

public final class PromotionRequests {
    private PromotionRequests() {}
    public record Program(@NotBlank @Size(max=200) String name, String description,
                          @NotNull Instant startDate, @NotNull Instant endDate) {}
    public record Status(@NotNull PromotionStatus status) {}
    public record Voucher(
            @NotBlank @Size(max=50) @Pattern(regexp="[A-Z0-9_-]+") String code,
            @NotNull @DecimalMin(value="0",inclusive=false) @DecimalMax("100") @Digits(integer=3,fraction=2) BigDecimal discountPercent,
            @NotNull @DecimalMin("0") @Digits(integer=13,fraction=2) BigDecimal minimumAmount,
            @DecimalMin(value="0",inclusive=false) @Digits(integer=13,fraction=2) BigDecimal maximumDiscount,
            @NotNull @Positive Integer quantity) {
        public Voucher { code = code == null ? null : code.trim().toUpperCase(Locale.ROOT); }
    }
    public record OrderDiscount(
            @NotNull @DecimalMin("0") @Digits(integer=13,fraction=2) BigDecimal minimumAmount,
            @NotNull @DecimalMin(value="0",inclusive=false) @DecimalMax("100") @Digits(integer=3,fraction=2) BigDecimal discountPercent,
            @DecimalMin(value="0",inclusive=false) @Digits(integer=13,fraction=2) BigDecimal maximumDiscount) {}
    public record ProductDiscounts(@NotEmpty List<@NotNull @Positive Long> productIds,
            @NotNull @DecimalMin(value="0",inclusive=false) @DecimalMax("100") @Digits(integer=3,fraction=2) BigDecimal discountPercent) {}
    public record ProductDiscount(
            @NotNull @DecimalMin(value="0",inclusive=false) @DecimalMax("100") @Digits(integer=3,fraction=2) BigDecimal discountPercent) {}
}
