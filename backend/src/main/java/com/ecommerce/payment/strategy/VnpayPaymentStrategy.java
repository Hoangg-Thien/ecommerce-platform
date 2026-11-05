package com.ecommerce.payment.strategy;

import com.ecommerce.cart.entity.Cart;
import com.ecommerce.checkout.dto.response.CheckoutResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.enums.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.enums.*;
import com.ecommerce.payment.mapper.PaymentMapper;
import com.ecommerce.payment.repository.PaymentRepository;
import com.ecommerce.payment.service.VnpayService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VnpayPaymentStrategy implements PaymentStrategy {
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final PaymentMapper mapper;
    private final VnpayService vnpay;
    private final HttpServletRequest request;

    @Override public CheckoutResponse processPayment(Order order, Cart cart) {
        VnpayService.minorAmount(order.getTotalPrice());
        order.setStatus(OrderStatus.AWAITING_PAYMENT);
        orders.save(order);
        Payment payment = new Payment();
        payment.setOrder(order); payment.setPaymentMethod(PaymentMethod.VNPAY);
        payment.setPaymentStatus(PaymentStatus.PENDING); payment.setAmount(order.getTotalPrice());
        // Use the server request address; forwarded headers require separately verified ingress configuration.
        vnpay.createUrl(payment, request.getRemoteAddr());
        payments.save(payment);
        order.setPayment(payment);
        return CheckoutResponse.builder().orderId(order.getId()).orderStatus(order.getStatus())
                .totalPrice(order.getTotalPrice()).paymentResponse(mapper.toPaymentResponse(payment))
                .paymentUrl(payment.getPaymentUrl()).build();
    }
}
