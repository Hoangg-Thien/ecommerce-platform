package com.ecommerce.scheduler;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.entity.Order;
import com.ecommerce.entity.Payment;
import com.ecommerce.enums.OrderStatus;
import com.ecommerce.enums.PaymentMethod;
import com.ecommerce.enums.PaymentStatus;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentTimeoutScheduler {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    // Chạy mỗi 5 phút, huỷ các MoMo payment PENDING quá 15 phút
    @Scheduled(fixedRate = 300_000) // 300,000ms = 5 phút
    @Transactional
    public void cancelExpiredMomoPayments(){
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(15);

        // Tìm tất cả payment PENDING được tạo trước 15 phút
        List<Payment> expiredPayments = paymentRepository
        .findByPaymentStatusAndCreatedAtBefore(PaymentStatus.PENDING, cutoff);

        int count = 0;
        for(Payment payment : expiredPayments){

            // Chỉ cancel MoMo
            if(payment.getPaymentMethod() != PaymentMethod.MOMO){
                continue;
            }

            Order order = payment.getOrder();

            // Chỉ cancel nếu order vẫn còn ở AWAITING_PAYMENT
            if(order.getStatus() != OrderStatus.AWAITING_PAYMENT){
                continue;
            }

            payment.setPaymentStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);

            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);

            count++;
            log.info("Auto-cancelled expired MoMo payment for order {}", order.getId());
        }
            if (count > 0) {
            log.info("Timeout scheduler cancelled {} expired MoMo payments", count);
        }
    }
}
