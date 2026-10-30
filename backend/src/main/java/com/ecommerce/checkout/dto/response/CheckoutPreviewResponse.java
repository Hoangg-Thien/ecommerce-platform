package com.ecommerce.checkout.dto.response;

import com.ecommerce.promotion.pricing.PricingCalculator.*;
import java.math.BigDecimal;
import java.util.List;

public record CheckoutPreviewResponse(List<PricedItem> items, BigDecimal originalSubtotal,
        BigDecimal productDiscountTotal, BigDecimal subtotal, Discount orderDiscount,
        BigDecimal shippingFee, BigDecimal total, VoucherInfo voucher) {
    public record VoucherInfo(String code, boolean applied, VoucherReason reason, String message) {}
    public static CheckoutPreviewResponse from(Result r) {
        var v = r.voucher();
        return new CheckoutPreviewResponse(r.items(), r.originalSubtotal(), r.productDiscountTotal(),
                r.subtotal(), r.orderDiscount(), r.shippingFee(), r.total(),
                v == null ? null : new VoucherInfo(v.code(), v.applied(), v.reason(), message(v.reason())));
    }
    private static String message(VoucherReason reason) {
        if (reason == null) return null;
        return switch (reason) {
            case NOT_FOUND -> "Mã giảm giá không tồn tại.";
            case PROGRAM_INACTIVE -> "Chương trình đã tắt.";
            case NOT_STARTED -> "Chương trình chưa bắt đầu.";
            case EXPIRED -> "Chương trình đã hết hạn.";
            case VOUCHER_INACTIVE -> "Mã giảm giá đã tắt.";
            case SOLD_OUT -> "Mã giảm giá đã hết lượt.";
            case MIN_AMOUNT_NOT_MET -> "Đơn hàng chưa đạt giá trị tối thiểu.";
            case ALREADY_USED -> "Bạn đã sử dụng mã giảm giá này.";
            case NOT_BETTER_THAN_ORDER_DISCOUNT -> "Ưu đãi tự động có lợi hơn mã giảm giá.";
        };
    }
}
