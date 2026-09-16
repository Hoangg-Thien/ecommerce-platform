package com.ecommerce.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.dto.response.PageResponse;

import com.ecommerce.exception.UnauthorizedAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.ecommerce.dto.response.CheckoutResponse;
import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.Payment;
import com.ecommerce.entity.User;
import com.ecommerce.enums.OrderStatus;
import com.ecommerce.enums.PaymentStatus;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.mapper.OrderMapper;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.service.payment.MomoPaymentStrategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderMapper orderMapper;
    private final MomoPaymentStrategy momoPaymentStrategy;

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getUserOrders(String userEmail, Pageable pageable) {
        User user = userRepository.findByEmail(userEmail)
        .orElseThrow(() -> new ResourceNotFoundException("User", "email", userEmail));

        Page<Order> orders = orderRepository.findByUserId(user.getId(), pageable);

        return PageResponse.of(orders.map(orderMapper::tOrderResponse));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId, String userEmail){

        Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if(!order.getUser().getEmail().equals(userEmail)){
            throw new UnauthorizedAccessException("Bạn không có quyền truy cập vào đơn hàng này!");
        }

        return orderMapper.tOrderResponse(order);
    }

    @Transactional
    public CheckoutResponse retryPayment(Long orderId, String userEmail){
        
        Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if(!order.getUser().getEmail().equals(userEmail)){
            throw new UnauthorizedAccessException("Không có quyền truy cập đơn hàng này");
        }

        if(order.getStatus() != OrderStatus.AWAITING_PAYMENT){
            throw new IllegalStateException("Đơn hàng không ở trạng thái chờ thanh toán");
        }

        Payment payment = order.getPayment();
        if(payment == null || payment.getPaymentStatus() == PaymentStatus.PAID){
            throw new IllegalStateException("Đơn hàng này đã được thanh toán hoặc không hợp lệ");
        }

        payment.setPaymentStatus(PaymentStatus.PENDING);

        String paymentUrl = momoPaymentStrategy.generatePaymentUrl(payment);

        return CheckoutResponse.builder()
        .orderId(order.getId())
        .paymentUrl(paymentUrl)
        .build();
    }
}
