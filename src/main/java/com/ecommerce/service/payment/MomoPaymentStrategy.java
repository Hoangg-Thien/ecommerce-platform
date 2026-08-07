package com.ecommerce.service.payment;

import org.springframework.stereotype.Component;

import com.ecommerce.dto.response.CheckoutResponse;
import com.ecommerce.entity.Cart;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.Payment;
import com.ecommerce.enums.OrderStatus;
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

    @Override
    public CheckoutResponse processPayment(Order order, Cart cart){
        
        // Set trạng thái order: MoMo → AWAITING_PAYMENT (chờ user trả tiền)
        order.setStatus(OrderStatus.AWAITING_PAYMENT);
        Order savedOrder = orderRepository.save(order);

        // Tạo Payment record (PENDING — chưa có tiền)
        Payment payment = new Payment();
        payment.setOrder(savedOrder);
        payment.setPaymentMethod(payment.getPaymentMethod());
        payment.setPaymentStatus(payment.getPaymentStatus());
        payment.setAmount(payment.getAmount());
        Payment savedPayment = paymentRepository.save(payment);

        // Gọi MoMo API để lấy URL thanh toán
        // createPaymentUrl() sẽ SET momoOrderId và momoRequestId vào savedPayment
        String paymentUrl = momoService.createPaymentUrl(savedPayment);

        // Lưu lại momoOrderId và momoRequestId vào DB
        paymentRepository.save(savedPayment);

        // Ko trừ stock, KHÔNG xóa cart — chờ IPN xác nhận
        log.info("Created MoMo payment for order {}, waiting for IPN callback", savedOrder.getId());

        return CheckoutResponse.builder()
        .orderId(savedOrder.getId())
        .orderStatus(savedOrder.getStatus())
        .totalPrice(savedOrder.getTotalPrice())
        .paymentUrl(paymentUrl) // frontend redict sang url nay de thanh toan
        .paymentResponse(paymentMapper.toPaymentResponse(savedPayment))
        .build();
    }
}
