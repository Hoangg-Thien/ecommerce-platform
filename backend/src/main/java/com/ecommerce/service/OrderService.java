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
import com.ecommerce.entity.OrderItem;
import com.ecommerce.entity.Payment;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.ProductVariant;
import com.ecommerce.entity.User;
import com.ecommerce.enums.OrderStatus;
import com.ecommerce.enums.PaymentMethod;
import com.ecommerce.enums.PaymentStatus;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.mapper.OrderMapper;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ProductRepository;
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
    private final ProductRepository productRepository;
    private final OrderMapper orderMapper;
    private final MomoPaymentStrategy momoPaymentStrategy;

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getUserOrders(String userEmail, Pageable pageable) {
        User user = userRepository.findByEmail(userEmail)
        .orElseThrow(() -> new ResourceNotFoundException("User", "email", userEmail));

        Page<Order> orders = orderRepository.findByUserId(user.getId(), pageable);

        return PageResponse.of(orders.map(orderMapper::tOrderResponse));
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus){
        log.info("Admin requesting status update for orderId={}, newStatus={}", orderId, newStatus);
        
        // tim order theo id
        Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        // ko cho doi trang thai neu DONE va CANCELLED
        if(order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.DONE){
            log.warn("Cannot change status for orderId={} because it is already {}", 
            orderId, order.getStatus());
            throw new IllegalArgumentException("Cannot change status of an order that is already " + order.getStatus());
        }

        // Chỉ restock khi stock đã thực sự bị trừ trước đó
        // Stock bị trừ khi: COD (khi PENDING, CONFIRMED...) hoặc MoMo (khi CONFIRMED trở đi)
        // Stock CHƯA bị trừ khi: MoMo đang AWAITING_PAYMENT
        if (newStatus == OrderStatus.CANCELLED) {
        boolean stockWasDeducted = order.getStatus() != OrderStatus.AWAITING_PAYMENT;
        if (stockWasDeducted) {
            for (OrderItem orderItem : order.getItems()) {
                    Product product = orderItem.getProduct();
                    ProductVariant variant = product.getVariantBySize(orderItem.getSize())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy size: " + orderItem.getSize()));
                    if (variant != null) {
                        variant.setStock(variant.getStock() + orderItem.getQuantity());
                        productRepository.save(product);
                        log.info("Restocked {} units for product '{}' (size={}, id={}) due to order cancellation",
                        orderItem.getQuantity(), product.getName(), variant.getSize(), product.getId());
                    }
                }
            }
        }
        // cap nhat va luu
        order.setStatus(newStatus);
        
        // tu dong cap nhat payment status khi giao hang thanh cong
        if(newStatus == OrderStatus.DONE){
            if(order.getPayment() != null && order.getPaymentMethod() == PaymentMethod.COD){
                order.getPayment().setPaymentStatus(PaymentStatus.PAID);
                log.info("Automatically marked COD Payment as PAID for orderId={}", orderId);
            }
        }

        Order updatedOrder = orderRepository.save(order);
        log.info("Order status updated successfully: orderId={}, status={}", updatedOrder.getId(), updatedOrder.getStatus());
        return orderMapper.tOrderResponse(updatedOrder);
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
        if(payment.getPaymentStatus() == PaymentStatus.PAID){
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
