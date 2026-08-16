package com.ecommerce.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.entity.Cart;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderItem;
import com.ecommerce.entity.Payment;
import com.ecommerce.entity.Product;
import com.ecommerce.enums.OrderStatus;
import com.ecommerce.enums.PaymentStatus;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.CartItemRepository;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.PaymentRepository;
import com.ecommerce.repository.ProductRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentFulfillmentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final PaymentRefundService paymentRefundService;

    @Transactional
    public void processSuccess(Payment payment, String transactionId) {
        Order order = payment.getOrder();

        // VALIDATE STOCK TRƯỚC TIÊN
        for (OrderItem orderItem : order.getItems()) {
            Product product = orderItem.getProduct();
            com.ecommerce.entity.ProductVariant variant = product.getVariants().stream()
                    .filter(v -> v.getSize().equals(orderItem.getSize()))
                    .findFirst()
                    .orElse(null);

            if (variant == null || variant.getStock() < orderItem.getQuantity()) {
                log.warn("Out of stock during IPN process for Order {}. Product: {}, Required: {}, Available: {}",
                        order.getId(), product.getName(), orderItem.getQuantity(), variant != null ? variant.getStock() : 0);

                // Lưu transactionId để biết mã mà refund
                payment.setTransactionId(transactionId);

                // Đánh rớt đơn hàng vì không có hàng để giao
                payment.setPaymentStatus(PaymentStatus.FAILED);
                order.setStatus(OrderStatus.CANCELLED);
                orderRepository.save(order);

                // Kích hoạt tự động hoàn tiền lại cho khách
                log.info("Triggering automatic refund for Order {} due to OUT OF STOCK", order.getId());
                paymentRefundService.processRefund(payment.getId());

                return;
            }
        }

        // Cập nhật Payment thành công
        payment.setPaymentStatus(PaymentStatus.PAID);
        payment.setTransactionId(transactionId);
        paymentRepository.save(payment);

        // Cập nhật Order thành công
        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.error("Fatal: Trying to set CONFIRMED on CANCELLED order {}.", order.getId());
            return; // cancel roi ko duoc confirmed
        }

        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);

        // Trừ stock
        for (OrderItem orderItem : order.getItems()) {
            Product product = orderItem.getProduct();
            com.ecommerce.entity.ProductVariant variant = product.getVariants().stream()
                    .filter(v -> v.getSize().equals(orderItem.getSize()))
                    .findFirst()
                    .orElse(null);
            if (variant != null) {
                variant.setStock(variant.getStock() - orderItem.getQuantity());
                productRepository.save(product);
            }
        }

        // Xóa cart (xóa các CartItem trong database)
        Cart cart = cartRepository.findByUserId(order.getUser().getId()).orElse(null);
        if (cart != null) {
            cartItemRepository.deleteAll(cart.getItems());
            cart.getItems().clear(); // Clear the collection in memory for consistency
        }

        log.info("Payment SUCCESS for order {}. TransactionId: {}", order.getId(), transactionId);
    }

    @Transactional
    public void processFailure(Payment payment, String reason) {
        Order order = payment.getOrder();

        // Set fail
        payment.setPaymentStatus(PaymentStatus.FAILED);
        paymentRepository.save(payment);

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        log.info("Payment FAILED for order {}. Reason: {}", order.getId(), reason);
    }
}
