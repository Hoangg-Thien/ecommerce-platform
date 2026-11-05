package com.ecommerce.payment.scheduler;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ecommerce.order.entity.Order;
import com.ecommerce.order.enums.OrderStatus;
import com.ecommerce.payment.enums.PaymentMethod;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.service.OrderCancellationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentTimeoutScheduler {

    private final OrderRepository orderRepository;
    private final OrderCancellationService orderCancellationService;
    private final Clock clock;

    // A five-minute scan can cancel a fifteen-minute session around minute twenty.
    @Scheduled(fixedRate = 300_000)
    public void cancelExpiredOnlinePayments(){
        List<Long> expiredOrders = orderRepository.findExpiredOnlineOrderIds(clock.instant());

        int count = 0;
        for(Long orderId : expiredOrders){
            try{
                orderCancellationService.cancelExpiredOrder(orderId);
                count++;
            }catch(Exception e){
                log.error("Failed to cancel expired order {} ({})", orderId, e.getClass().getSimpleName());
                // ko rethrow, tiep tuc order tiep theo
            }
        }

        if(count > 0){
            log.info("Timeout scheduler processed {} expired online orders", count);
        }
    }
}
