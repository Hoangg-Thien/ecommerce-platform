package com.ecommerce.order.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.order.entity.Order;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.order.enums.OrderStatus;
import com.ecommerce.payment.enums.PaymentStatus;
import com.ecommerce.payment.enums.PaymentMethod;
import com.ecommerce.payment.enums.RefundStatus;
import com.ecommerce.payment.repository.PaymentRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.promotion.service.VoucherUsageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class OrderCancellationService {

    private final OrderRepository orderRepository;
    private final VoucherUsageService voucherUsageService;
    private final PaymentRepository paymentRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelExpiredOrder(Long orderId) {

        // tim order theo orderId
        boolean vnpay = orderRepository.findPaymentMethodById(orderId).orElse(null) == PaymentMethod.VNPAY;
        // Retired MoMo pending records require an explicit operator decision; never auto-cancel them.
        if (!vnpay) return;
        Order order = orderRepository.lockRootById(orderId)
        .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        // Idempotency guard
        // chi huy neu trang thai order la AWAITING_PAYMENT
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            return;
        }

        // tim payment
        Payment payment = vnpay ? paymentRepository.lockByOrderId(orderId).orElse(null) : order.getPayment();
        if(payment == null){
            log.error("Abnormal: Order {} has no associated payment.", orderId);
            return;
        }

        if (payment.getPaymentStatus() != PaymentStatus.PENDING || payment.getPaidAt() != null) return;
        if (order.getPaymentMethod() == PaymentMethod.VNPAY) {
            if (payment.getExpiresAt() == null || clock.instant().isBefore(payment.getExpiresAt())) return;
            // Local expiry closes the order, not the gateway's financial result.
            payment.setRefundStatus(RefundStatus.RECONCILIATION_REQUIRED);
            payment.setReason("PAYMENT_TIMEOUT_UNVERIFIED");
        } else return;
        order.setStatus(OrderStatus.CANCELLED);

        orderRepository.save(order);
        voucherUsageService.releaseByOrderId(order.getId());

        log.info("Expired online order {} cancelled.", orderId);
    }
}
