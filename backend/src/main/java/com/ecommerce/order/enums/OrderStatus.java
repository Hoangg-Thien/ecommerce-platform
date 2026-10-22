package com.ecommerce.order.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum OrderStatus {
    AWAITING_PAYMENT, // [momo] chờ thanh toán
    PENDING,          // [cod] chờ xác nhận
    CONFIRMED,        // đã xác nhận
    SHIPPING,         // đang giao
    DONE,             // hoàn thành (terminal)
    CANCELLED;        // đã huỷ (terminal)

    /**
     * Các chuyển trạng thái hợp lệ.
     * CANCELLED có thể nhận từ mọi trạng thái chưa kết thúc — được xử lý trong map này.
     *
     * Flow COD  : PENDING → CONFIRMED → SHIPPING → DONE
     * Flow MoMo : AWAITING_PAYMENT → PENDING → CONFIRMED → SHIPPING → DONE
     */
    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS = Map.of(
        AWAITING_PAYMENT, EnumSet.of(PENDING, CANCELLED),
        PENDING,          EnumSet.of(CONFIRMED, CANCELLED),
        CONFIRMED,        EnumSet.of(SHIPPING, CANCELLED),
        SHIPPING,         EnumSet.of(DONE, CANCELLED),
        DONE,             EnumSet.noneOf(OrderStatus.class),
        CANCELLED,        EnumSet.noneOf(OrderStatus.class)
    );

    /**
     * Kiểm tra xem có thể chuyển từ trạng thái hiện tại sang {@code next} không.
     *
     * @param next trạng thái muốn chuyển sang
     * @return true nếu transition hợp lệ
     */
    public boolean canTransitionTo(OrderStatus next) {
        Set<OrderStatus> allowed = VALID_TRANSITIONS.getOrDefault(this, EnumSet.noneOf(OrderStatus.class));
        return allowed.contains(next);
    }
}
