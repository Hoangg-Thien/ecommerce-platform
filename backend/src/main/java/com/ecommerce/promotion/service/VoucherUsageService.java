package com.ecommerce.promotion.service;

import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.user.repository.UserRepository;
import com.ecommerce.promotion.entity.Voucher;
import com.ecommerce.promotion.entity.VoucherUsage;
import com.ecommerce.promotion.repository.VoucherRepository;
import com.ecommerce.promotion.repository.VoucherUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Clock;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class VoucherUsageService {
    private final VoucherRepository vouchers;
    private final VoucherUsageRepository usages;
    private final UserRepository users;
    private final OrderRepository orders;
    private final Clock clock;

    /** Called only for the winning voucher, after checkout has persisted the order. */
    public void reserve(Voucher voucher, Long userId, Long orderId, BigDecimal amount) {
        if (voucher == null || voucher.getId() == null || userId == null || orderId == null
                || amount == null || amount.signum() < 0 || amount.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("Reservation requires persisted IDs and non-negative integer VND");
        }
        if (vouchers.incrementUsageIfAvailable(voucher.getId()) != 1) {
            throw new VoucherSoldOutException();
        }
        var usage = new VoucherUsage();
        usage.setVoucher(vouchers.getReferenceById(voucher.getId()));
        usage.setUser(users.getReferenceById(userId));
        usage.setOrder(orders.getReferenceById(orderId));
        usage.setDiscountAmount(amount);
        usage.setCreatedAt(clock.instant());
        // Let constraint errors escape. The caller must translate them only after rollback.
        usages.saveAndFlush(usage);
    }

    /** No usage, a non-cancelled order or an already released usage is a no-op. */
    public void releaseByOrderId(Long orderId) {
        if (orderId == null) throw new IllegalArgumentException("Order ID is required");
        var voucherId = usages.findVoucherIdByOrderId(orderId);
        if (voucherId.isEmpty()) return;
        // Reserve locks voucher before inserting usage. Release must use the same order.
        vouchers.lockId(voucherId.get()).orElseThrow(() -> new IllegalStateException("Usage voucher is missing"));
        if (usages.releaseIfOrderCancelled(orderId, clock.instant()) == 0) return;
        if (vouchers.decrementUsageIfPositive(voucherId.get()) != 1) {
            throw new IllegalStateException("Voucher usage counter is inconsistent");
        }
    }
}
