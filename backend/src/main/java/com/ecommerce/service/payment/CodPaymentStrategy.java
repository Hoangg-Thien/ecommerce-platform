package com.ecommerce.service.payment;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ecommerce.dto.response.CheckoutResponse;
import com.ecommerce.entity.Cart;
import com.ecommerce.entity.CartItem;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderItem;
import com.ecommerce.entity.Payment;
import com.ecommerce.entity.Product;
import com.ecommerce.enums.OrderStatus;
import com.ecommerce.enums.PaymentMethod;
import com.ecommerce.enums.PaymentStatus;
import com.ecommerce.mapper.PaymentMapper;
import com.ecommerce.repository.CartItemRepository;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.PaymentRepository;
import com.ecommerce.repository.ProductRepository;

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
            com.ecommerce.entity.ProductVariant variant = product.getVariants().stream()
                    .filter(v -> v.getSize().equals(item.getSize()))
                    .findFirst()
                    .orElse(null);
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
