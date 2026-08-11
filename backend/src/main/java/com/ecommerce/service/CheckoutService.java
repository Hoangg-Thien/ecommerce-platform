package com.ecommerce.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.config.IdempotencyInterceptor;
import com.ecommerce.dto.request.CheckoutRequest;
import com.ecommerce.dto.response.CheckoutResponse;
import com.ecommerce.entity.Cart;
import com.ecommerce.entity.CartItem;
import com.ecommerce.entity.IdempotencyKey;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderItem;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.User;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.IdempotencyKeyRepository;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.service.payment.PaymentStrategy;
import com.ecommerce.service.payment.PaymentStrategyFactory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutService {
    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final PaymentStrategyFactory strategyFactory;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    
    @Transactional
    public CheckoutResponse checkout(String userEmail, CheckoutRequest request, String idempotencyKey){
        log.info("User '{}' initiated checkout with paymentMethod: {}", userEmail, request.getPaymentMethod());

        try {
            idempotencyKeyRepository.saveAndFlush(new IdempotencyKey(idempotencyKey, java.time.LocalDateTime.now()));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.warn("Idempotency-Key {} already exists. Blocking duplicate request", idempotencyKey);

            // quang loi neu trung key (2 request ban vao cung 1 luc)
            throw new IllegalStateException("Request is already processing or completed");
        }

        // tim user
        User user = userRepository.findByEmail(userEmail)
        .orElseThrow(() -> new ResourceNotFoundException("User", "email", userEmail));

        // tim cart
        Cart cart = cartRepository.findByUserId(user.getId())
        .orElseThrow(() -> new ResourceNotFoundException("Cart not found for user: " + user.getEmail()));

        // validate cart co rong ko
        if(cart.getItems().isEmpty()){
            log.warn("Checkout rejected: Cart is empty for user '{}'", userEmail);
            throw new IllegalArgumentException("Cannot checkout from an empty cart");
        }

        // build order, chua save tru stock
        Order order = new Order();
        order.setUser(user);
        order.setPaymentMethod(request.getPaymentMethod());

        BigDecimal totalPrice = BigDecimal.ZERO;

        for(CartItem cartItem : cart.getItems()){
            Product product = cartItem.getProduct();

            // validate stock
            if(product.getStock() < cartItem.getQuantity()){
                log.warn("Checkout rejected: Product '{}' out of stock (requested: {}, available: {})",
                product.getName(), cartItem.getQuantity(), product.getStock());
                throw new IllegalArgumentException("Not enough stock for product: " + product.getName());
            }

            // tao orderItem
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setPrice(product.getPrice());
            order.getItems().add(orderItem);

            BigDecimal subTotal = product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            totalPrice = totalPrice.add(subTotal);
        }

        order.setTotalPrice(totalPrice);

        //  Delegate sang Strategy — strategy sẽ quyết định:
        //    - Set trạng thái order (PENDING hay AWAITING_PAYMENT)
        //    - Tạo Payment
        //    - Trừ stock hay không
        //    - Xóa cart hay không
        //    - Gọi API ngoài (MoMo) hay không

        log.info("Order prepared for checkout: user='{}', itemsCount={}, estimatedTotal={}",
        userEmail, order.getItems().size(), order.getTotalPrice());
        
        PaymentStrategy strategy = strategyFactory.getPaymentStrategy(request.getPaymentMethod());
        return strategy.processPayment(order, cart);
    }
}
