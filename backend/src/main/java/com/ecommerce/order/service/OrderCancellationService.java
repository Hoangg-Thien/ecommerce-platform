package com.ecommerce.order.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.order.entity.Order;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.order.enums.OrderStatus;
import com.ecommerce.payment.enums.PaymentStatus;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class OrderCancellationService {

    private final OrderRepository orderRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelExpiredOrder(Long orderId) {

        // tim order theo orderId
        Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        // Idempotency guard
        // chi huy neu trang thai order la AWAITING_PAYMENT
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            return;
        }

        // tim payment
        Payment payment = order.getPayment();
        if(payment == null){
            log.error("Abnormal: Order {} has no associated payment.", orderId);
            return;
        }

        // set trang thai
        payment.setPaymentStatus(PaymentStatus.FAILED);
        order.setStatus(OrderStatus.CANCELLED);

        orderRepository.save(order);

        log.info("Expired MoMo order {} cancelled.", orderId);
    }
}
