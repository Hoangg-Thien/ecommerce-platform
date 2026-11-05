package com.ecommerce.payment.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.cart.entity.Cart;
import com.ecommerce.cart.entity.CartItem;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.product.entity.ProductVariant;
import com.ecommerce.order.enums.OrderStatus;
import com.ecommerce.payment.enums.PaymentStatus;
import com.ecommerce.cart.repository.CartRepository;
import com.ecommerce.cart.repository.CartItemRepository;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.payment.repository.PaymentRepository;
import com.ecommerce.promotion.service.VoucherUsageService;

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
    private final VoucherUsageService voucherUsageService;
    private final com.ecommerce.product.repository.ProductVariantRepository variants;

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void processVnpaySuccess(Payment payment) {
        Order order = payment.getOrder();
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            payment.setRefundStatus(com.ecommerce.payment.enums.RefundStatus.REQUIRED_MANUAL);
            payment.setReason(order.getStatus() == OrderStatus.CANCELLED ? "LATE_PAID_AFTER_CANCEL" : "ORDER_STATE_REQUIRES_REVIEW");
            return;
        }
        var lockedVariants = variants.lockForOrder(order.getId());
        var locked = new java.util.LinkedHashMap<OrderItem,ProductVariant>();
        for (OrderItem item : order.getItems())
            locked.put(item, lockedVariants.stream().filter(v -> v.getProduct().getId().equals(item.getProduct().getId())
                    && v.getSize().equals(item.getSize())).findFirst().orElse(null));
        if (locked.entrySet().stream().anyMatch(e -> e.getValue() == null || e.getValue().getStock() < e.getKey().getQuantity())) {
            order.setStatus(OrderStatus.CANCELLED);
            payment.setRefundStatus(com.ecommerce.payment.enums.RefundStatus.REQUIRED_MANUAL);
            payment.setReason("OUT_OF_STOCK");
            orderRepository.save(order);
            voucherUsageService.releaseByOrderId(order.getId());
            return;
        }
        locked.forEach((item, variant) -> variant.setStock(variant.getStock() - item.getQuantity()));
        order.setStatus(OrderStatus.CONFIRMED);
        payment.setRefundStatus(com.ecommerce.payment.enums.RefundStatus.NONE);
        payment.setReason(null);
        clearPurchasedCart(order);
    }

    private void clearPurchasedCart(Order order) {
        // shortcut: product+size removes the line even if quantity changed during payment; preserve MVP cart semantics.
        Cart cart = cartRepository.findByUserId(order.getUser().getId()).orElse(null);
        if (cart != null) {
           List <CartItem> itemsToRemove = cart.getItems().stream()
           .filter(cartItem -> order.getItems().stream().anyMatch(
            orderItem -> orderItem.getProduct().getId().equals(cartItem.getProductVariant().getProduct().getId())
            && orderItem.getSize().equals(cartItem.getProductVariant().getSize())
           )
        ).collect(Collectors.toList());

        cartItemRepository.deleteAll(itemsToRemove);
        cart.getItems().removeAll(itemsToRemove);
        }

    }

    @Transactional
    public void processFailure(Payment payment, String reason) {
        Order order = payment.getOrder();
        if (order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.DONE
                || payment.getPaymentStatus() != PaymentStatus.PENDING) return;

        // Set fail
        payment.setPaymentStatus(PaymentStatus.FAILED);
        paymentRepository.save(payment);

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        voucherUsageService.releaseByOrderId(order.getId());

        log.info("Payment FAILED for order {}. Reason: {}", order.getId(), reason);
    }
}
