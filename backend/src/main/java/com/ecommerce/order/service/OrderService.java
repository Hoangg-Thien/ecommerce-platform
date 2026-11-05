package com.ecommerce.order.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.common.dto.PageResponse;

import com.ecommerce.common.exception.UnauthorizedAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.ecommerce.checkout.dto.response.CheckoutResponse;
import com.ecommerce.order.dto.response.OrderResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.user.entity.User;
import com.ecommerce.order.enums.OrderStatus;
import com.ecommerce.payment.enums.PaymentStatus;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.order.mapper.OrderMapper;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderMapper orderMapper;
    private final com.ecommerce.payment.service.VnpayService vnpayService;
    private final com.ecommerce.payment.repository.PaymentRepository payments;

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
        
        boolean vnpay = orderRepository.findPaymentMethodById(orderId).orElse(null) == com.ecommerce.payment.enums.PaymentMethod.VNPAY;
        Order order = (vnpay ? orderRepository.lockRootById(orderId) : orderRepository.findById(orderId))
        .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if(!order.getUser().getEmail().equals(userEmail)){
            throw new UnauthorizedAccessException("Không có quyền truy cập đơn hàng này");
        }

        if (!vnpay) throw new IllegalStateException("Only VNPay orders support online payment retry");

        if(order.getStatus() != OrderStatus.AWAITING_PAYMENT){
            throw new IllegalStateException("Đơn hàng không ở trạng thái chờ thanh toán");
        }

        Payment payment = vnpay ? payments.lockByOrderId(orderId).orElse(null) : order.getPayment();
        if(payment == null || payment.getPaymentStatus() == PaymentStatus.PAID){
            throw new IllegalStateException("Đơn hàng này đã được thanh toán hoặc không hợp lệ");
        }

        if (order.getPaymentMethod() == com.ecommerce.payment.enums.PaymentMethod.VNPAY) {
            return CheckoutResponse.builder().orderId(order.getId()).orderStatus(order.getStatus())
                    .totalPrice(order.getTotalPrice()).paymentUrl(vnpayService.retryUrl(payment)).build();
        }

        throw new IllegalStateException("Only VNPay orders support online payment retry");
    }
}
