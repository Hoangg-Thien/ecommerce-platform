package com.ecommerce.order.service;

import com.ecommerce.order.dto.response.AdminOrderResponse;
import com.ecommerce.common.dto.PageResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.entity.ProductVariant;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.order.enums.OrderStatus;
import com.ecommerce.payment.enums.PaymentMethod;
import com.ecommerce.payment.enums.PaymentStatus;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.order.mapper.OrderMapper;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminOrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final OrderMapper orderMapper;

    /**
     * Lấy tất cả đơn hàng — hỗ trợ filter theo status và email khách hàng.
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminOrderResponse> getAllOrders(OrderStatus status, String email, Pageable pageable) {
        Page<Order> orders;
        boolean hasEmail = StringUtils.hasText(email);

        if (status != null && hasEmail) {
            orders = orderRepository.findByStatusAndUserEmailContainingIgnoreCase(status, email.trim(), pageable);
        } else if (status != null) {
            orders = orderRepository.findByStatus(status, pageable);
        } else if (hasEmail) {
            orders = orderRepository.findByUserEmailContainingIgnoreCase(email.trim(), pageable);
        } else {
            orders = orderRepository.findAll(pageable);
        }

        return PageResponse.of(orders.map(orderMapper::toAdminOrderResponse));
    }

    /**
     * Lấy chi tiết 1 đơn — ADMIN bypass owner check.
     */
    @Transactional(readOnly = true)
    public AdminOrderResponse getOrderById(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
        return orderMapper.toAdminOrderResponse(order);
    }

    /**
     * Đổi trạng thái đơn — bao gồm logic restock khi CANCELLED và auto-PAID khi DONE.
     */
    @Transactional
    public AdminOrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus) {
        log.info("Admin updating order {} -> {}", orderId, newStatus);

        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        OrderStatus currentStatus = order.getStatus();

        // Kiểm tra state machine: chỉ cho phép các transition được định nghĩa trong OrderStatus
        if (!currentStatus.canTransitionTo(newStatus)) {
            throw new IllegalArgumentException(
                "Không thể chuyển trạng thái từ " + currentStatus + " sang " + newStatus
            );
        }

        // Guard: đơn MoMo phải được thanh toán trước khi có thể DONE
        // (tránh bug doanh thu: đánh dấu DONE trong khi payment vẫn PENDING)
        if (newStatus == OrderStatus.DONE
                && order.getPaymentMethod() == PaymentMethod.MOMO) {
            Payment payment = order.getPayment();
            if (payment == null || payment.getPaymentStatus() != PaymentStatus.PAID) {
                throw new IllegalArgumentException(
                    "Không thể hoàn thành đơn MoMo khi chưa thanh toán (payment status: "
                    + (payment == null ? "null" : payment.getPaymentStatus()) + ")"
                );
            }
        }

        // Restock khi huỷ (chỉ nếu stock đã bị trừ)
        if (newStatus == OrderStatus.CANCELLED) {
            boolean stockWasDeducted = currentStatus != OrderStatus.AWAITING_PAYMENT;
            if (stockWasDeducted) {
                for (OrderItem item : order.getItems()) {
                    Product product = item.getProduct();
                    ProductVariant variant = product.getVariantBySize(item.getSize())
                        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy size: " + item.getSize()));
                    variant.setStock(variant.getStock() + item.getQuantity());
                    productRepository.save(product);
                    log.info("Restocked {} units for product '{}' (size {}) due to cancellation",
                        item.getQuantity(), product.getName(), item.getSize());
                }
            }
        }

        order.setStatus(newStatus);

        // Auto-mark COD payment as PAID when order is DONE
        if (newStatus == OrderStatus.DONE) {
            Payment payment = order.getPayment();
            if (payment != null && order.getPaymentMethod() == PaymentMethod.COD) {
                payment.setPaymentStatus(PaymentStatus.PAID);
                log.info("Auto-marked COD payment as PAID for order {}", orderId);
            }
        }

        Order saved = orderRepository.save(order);
        log.info("Order {} status updated to {}", orderId, newStatus);
        return orderMapper.toAdminOrderResponse(saved);
    }
}
