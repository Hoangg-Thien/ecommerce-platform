package com.ecommerce.scheduler;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ecommerce.entity.Order;
import com.ecommerce.enums.OrderStatus;
import com.ecommerce.enums.PaymentMethod;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.service.OrderCancellationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentTimeoutScheduler {

    private final OrderRepository orderRepository;
    private final OrderCancellationService orderCancellationService;
    private final Clock clock;

    // Chạy mỗi 5 phút, huỷ các MoMo payment PENDING quá 15 phút
    @Scheduled(fixedRate = 300_000) // 300,000ms = 5 phút
    public void cancelExpiredMomoPayments(){
        LocalDateTime cutoff = LocalDateTime.now(clock).minusMinutes(15);

        // Tìm tất cả payment PENDING được tạo trước 15 phút
        List<Order> expiredPayments = orderRepository
        .findByStatusAndPaymentMethodAndCreateAtBefore(
        OrderStatus.AWAITING_PAYMENT,
        PaymentMethod.MOMO,
        cutoff);

        int count = 0;
        for(Order order : expiredPayments){
            try{
                orderCancellationService.cancelExpiredOrder(order.getId());
                count++;
            }catch(Exception e){
                log.error("Failed to cancel order {}: {}", order.getId(), e.getMessage());
                // ko rethrow, tiep tuc order tiep theo
            }
        }

        if(count > 0){
            log.info("Timeout scheduler cancelled {} expired MoMo payments", count);
        }
    }
}
