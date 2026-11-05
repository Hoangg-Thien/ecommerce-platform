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
import com.ecommerce.payment.enums.RefundStatus;
import com.ecommerce.payment.repository.PaymentRepository;
import com.ecommerce.product.repository.ProductVariantRepository;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.order.mapper.OrderMapper;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.promotion.service.VoucherUsageService;
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
    private final VoucherUsageService voucherUsageService;
    private final PaymentRepository payments;
    private final ProductVariantRepository variants;

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

    @Transactional(readOnly = true)
    public PageResponse<AdminOrderResponse> getAllOrders(OrderStatus status, String email, PaymentMethod method,
            PaymentStatus paymentStatus, RefundStatus refundStatus, Pageable pageable) {
        if (method == null && paymentStatus == null && refundStatus == null) return getAllOrders(status,email,pageable);
        return PageResponse.of(orderRepository.findAdminPaymentReview(status,
                StringUtils.hasText(email) ? email.trim() : "", method, paymentStatus, refundStatus, pageable)
                .map(orderMapper::toAdminOrderResponse));
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

        boolean vnpay = orderRepository.findPaymentMethodById(orderId).orElse(null) == PaymentMethod.VNPAY;
        Order order = (vnpay ? orderRepository.lockRootById(orderId) : orderRepository.findById(orderId))
            .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        OrderStatus currentStatus = order.getStatus();

        // Kiểm tra state machine: chỉ cho phép các transition được định nghĩa trong OrderStatus
        if (!currentStatus.canTransitionTo(newStatus)
                || (vnpay && currentStatus == OrderStatus.AWAITING_PAYMENT && newStatus != OrderStatus.CANCELLED)) {
            throw new IllegalArgumentException(
                "Không thể chuyển trạng thái từ " + currentStatus + " sang " + newStatus
            );
        }

        Payment lockedPayment = vnpay ? payments.lockByOrderId(orderId).orElseThrow(
                () -> new IllegalStateException("VNPay payment is missing; reconciliation required")) : order.getPayment();
        if (vnpay) {
            order.setPayment(lockedPayment);
            boolean paid = lockedPayment.getPaymentStatus() == PaymentStatus.PAID && lockedPayment.getPaidAt() != null;
            if (currentStatus == OrderStatus.PENDING
                    || (currentStatus == OrderStatus.AWAITING_PAYMENT && (lockedPayment.getPaidAt() != null || lockedPayment.getPaymentStatus() != PaymentStatus.PENDING))
                    || (currentStatus != OrderStatus.AWAITING_PAYMENT && !paid))
                throw new IllegalStateException("Unproven VNPay order/stock state; reconciliation required");
            if (newStatus != OrderStatus.CANCELLED && lockedPayment.getRefundStatus() != RefundStatus.NONE)
                throw new IllegalStateException("Resolve VNPay reconciliation before shipping or completion");
        }

        // Guard: đơn MoMo phải được thanh toán trước khi có thể DONE
        // (tránh bug doanh thu: đánh dấu DONE trong khi payment vẫn PENDING)
        if (newStatus == OrderStatus.DONE
                && (order.getPaymentMethod() == PaymentMethod.MOMO || vnpay)) {
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
            Payment payment = lockedPayment;
            if (vnpay) {
                payment.setRefundStatus(payment.getPaymentStatus() == PaymentStatus.PAID
                        ? RefundStatus.REQUIRED_MANUAL : RefundStatus.RECONCILIATION_REQUIRED);
                payment.setReason(payment.getPaymentStatus() == PaymentStatus.PAID ? "ADMIN_CANCEL_PAID" : "ADMIN_CANCEL_UNVERIFIED");
            } else if (payment != null && payment.getPaymentStatus() == PaymentStatus.PENDING) {
                payment.setPaymentStatus(PaymentStatus.FAILED);
            }
            boolean stockWasDeducted = vnpay
                    ? currentStatus == OrderStatus.CONFIRMED || currentStatus == OrderStatus.SHIPPING
                    : currentStatus != OrderStatus.AWAITING_PAYMENT;
            if (vnpay && stockWasDeducted) {
                var lockedVariants = variants.lockForOrder(orderId);
                for (OrderItem item : order.getItems()) {
                    ProductVariant variant = lockedVariants.stream().filter(v -> v.getProduct().getId().equals(item.getProduct().getId())
                            && v.getSize().equals(item.getSize())).findFirst()
                            .orElseThrow(() -> new IllegalStateException("Cannot restore missing VNPay stock; reconciliation required"));
                    variant.setStock(variant.getStock() + item.getQuantity());
                }
            } else if (stockWasDeducted) {
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
        if (newStatus == OrderStatus.CANCELLED) voucherUsageService.releaseByOrderId(orderId);
        log.info("Order {} status updated to {}", orderId, newStatus);
        return orderMapper.toAdminOrderResponse(saved);
    }
}
