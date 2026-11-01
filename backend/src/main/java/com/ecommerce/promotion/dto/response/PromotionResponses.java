package com.ecommerce.promotion.dto.response;

import com.ecommerce.promotion.entity.*;
import com.ecommerce.promotion.enums.PromotionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class PromotionResponses {
    private PromotionResponses() {}
    public record Program(Long id,String name,String description,Instant startDate,Instant endDate,
                          PromotionStatus status,String phase) {
        public static Program from(PromotionProgram p, Instant now) {
            String phase = now.isBefore(p.getStartDate()) ? "SCHEDULED" : now.isBefore(p.getEndDate()) ? "RUNNING" : "ENDED";
            return new Program(p.getId(),p.getName(),p.getDescription(),p.getStartDate(),p.getEndDate(),p.getStatus(),phase);
        }
    }
    public record Detail(Long id,String name,String description,Instant startDate,Instant endDate,
                         PromotionStatus status,String phase,List<VoucherView> vouchers,
                         List<OrderDiscountView> orderDiscounts,List<ProductDiscountView> productDiscounts) {}
    public record VoucherView(Long id,Long programId,String code,BigDecimal discountPercent,
                              BigDecimal minimumAmount,BigDecimal maximumDiscount,Integer quantity,Integer usedQuantity,PromotionStatus status) {
        public static VoucherView from(Voucher v) { return new VoucherView(v.getId(),v.getProgram().getId(),v.getCode(),v.getDiscountPercent(),v.getMinimumAmount(),v.getMaximumDiscount(),v.getQuantity(),v.getUsedQuantity(),v.getStatus()); }
    }
    public record OrderDiscountView(Long id,Long programId,BigDecimal minimumAmount,BigDecimal discountPercent,BigDecimal maximumDiscount) {
        public static OrderDiscountView from(OrderDiscount d) { return new OrderDiscountView(d.getId(),d.getProgram().getId(),d.getMinimumAmount(),d.getDiscountPercent(),d.getMaximumDiscount()); }
    }
    public record ProductDiscountView(Long id,Long programId,Long productId,BigDecimal discountPercent) {
        public static ProductDiscountView from(ProductDiscount d) { return new ProductDiscountView(d.getId(),d.getProgram().getId(),d.getProduct().getId(),d.getDiscountPercent()); }
    }
}
