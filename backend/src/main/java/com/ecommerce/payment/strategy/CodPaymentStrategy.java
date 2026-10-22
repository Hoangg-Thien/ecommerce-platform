package com.ecommerce.payment.strategy;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ecommerce.checkout.dto.response.CheckoutResponse;
import com.ecommerce.cart.entity.Cart;
import com.ecommerce.cart.entity.CartItem;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.entity.ProductVariant;
import com.ecommerce.order.enums.OrderStatus;
import com.ecommerce.payment.enums.PaymentMethod;
import com.ecommerce.payment.enums.PaymentStatus;
import com.ecommerce.payment.mapper.PaymentMapper;
import com.ecommerce.cart.repository.CartItemRepository;
import com.ecommerce.cart.repository.CartRepository;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.payment.repository.PaymentRepository;
import com.ecommerce.product.repository.ProductRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CodPaymentStrategy implements PaymentStrategy{

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final PaymentMapper paymentMapper;

    @Override
    public CheckoutResponse processPayment(Order order, Cart cart){
        log.info("Processing COD checkout for user id={}, totalAmount={}",
        order.getUser().getId(), order.getTotalPrice());

        // set trang thai
        order.setStatus(OrderStatus.PENDING);
        Order savedOrder = orderRepository.save(order);

        // tao payment moi
        Payment payment = new Payment();
        payment.setOrder(savedOrder);
        payment.setPaymentMethod(PaymentMethod.COD);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setAmount(savedOrder.getTotalPrice());
        Payment savedPayment = paymentRepository.save(payment);

        // tru stock
        for(OrderItem item : savedOrder.getItems()){
            Product product = item.getProduct();
            ProductVariant variant = product.getVariantBySize(item.getSize())
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy size: " + item.getSize()));
            if (variant != null) {
                variant.setStock(variant.getStock() - item.getQuantity());
                productRepository.save(product);
            }
        }

        // xoa cart sau khi dat hang thanh cong
        List <CartItem> itemsToRemove = cart.getItems().stream()
        .filter(cartItem -> savedOrder.getItems().stream().anyMatch(
            orderItem -> orderItem.getProduct().getId().equals(cartItem.getProductVariant().getProduct().getId())
            && orderItem.getSize().equals(cartItem.getProductVariant().getSize())
            )
        ).collect(Collectors.toList());

        cartItemRepository.deleteAll(itemsToRemove);
        cart.getItems().removeAll(itemsToRemove);
        
        log.info("COD order processed successfully: orderId={}, paymentId={}, status={}",
        savedOrder.getId(), savedPayment.getId(), savedOrder.getStatus());

        // tra ve response ko co url
        return CheckoutResponse.builder()
        .orderId(savedOrder.getId())
        .orderStatus(savedOrder.getStatus())
        .totalPrice(savedOrder.getTotalPrice())
        .paymentResponse(paymentMapper.toPaymentResponse(savedPayment))
        .paymentUrl(null)
        .build();
    }
}
