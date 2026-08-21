package com.ecommerce.service.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ecommerce.dto.response.CheckoutResponse;
import com.ecommerce.entity.Cart;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.Payment;
import com.ecommerce.enums.OrderStatus;
import com.ecommerce.enums.PaymentMethod;
import com.ecommerce.enums.PaymentStatus;
import com.ecommerce.mapper.PaymentMapper;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.PaymentRepository;
import com.ecommerce.service.MomoService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MomoPaymentStrategy implements PaymentStrategy{

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final MomoService momoService;
    private final PaymentMapper paymentMapper;

    @Value("${payment.momo.mock-enabled:false}")
    private boolean mockEnabled;

    @Override
    public CheckoutResponse processPayment(Order order, Cart cart){
        
        // Set trạng thái order: MoMo → AWAITING_PAYMENT (chờ user trả tiền)
        order.setStatus(OrderStatus.AWAITING_PAYMENT);
        Order savedOrder = orderRepository.save(order);

        // Tạo Payment record (PENDING — chưa có tiền)
        Payment payment = new Payment();
        payment.setOrder(savedOrder);
        payment.setPaymentMethod(PaymentMethod.MOMO);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setAmount(savedOrder.getTotalPrice());
        Payment savedPayment = paymentRepository.save(payment);

        String paymentUrl;
        
        if (mockEnabled) {
            paymentUrl = "/mock-payment?orderId=" + savedOrder.getId();
            log.info("Mock payment enabled. Redirecting order {} to mock gateway.", savedOrder.getId());
        } else {
            // Gọi MoMo API để lấy URL thanh toán thật
            paymentUrl = momoService.createPaymentUrl(savedPayment);
            // Lưu lại momoOrderId và momoRequestId vào DB
            paymentRepository.save(savedPayment);
        }

        // Ko trừ stock, ko xóa cart — chờ IPN xác nhận
        log.info("Created MoMo payment for order {}, waiting for IPN callback", savedOrder.getId());

        return CheckoutResponse.builder()
        .orderId(savedOrder.getId())
        .orderStatus(savedOrder.getStatus())
        .totalPrice(savedOrder.getTotalPrice())
        .paymentUrl(paymentUrl) // frontend redict sang url nay de thanh toan
        .paymentResponse(paymentMapper.toPaymentResponse(savedPayment))
        .build();
    }

    public String generatePaymentUrl(Payment payment){
        if(mockEnabled){
            log.info("Mock payment enabled. Redirecting order {} to mock gateway.", payment.getOrder().getId());
            return "/mock-payment?orderId=" + payment.getOrder().getId();
        }else {
            String paymentUrl = momoService.createPaymentUrl(payment);
            paymentRepository.save(payment);
            return paymentUrl;
        }
    }
}
