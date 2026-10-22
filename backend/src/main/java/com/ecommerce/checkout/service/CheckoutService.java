package com.ecommerce.checkout.service;

import com.ecommerce.payment.entity.Payment;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.common.idempotency.IdempotencyInterceptor;
import com.ecommerce.checkout.dto.request.CheckoutRequest;
import com.ecommerce.checkout.dto.response.CheckoutResponse;
import com.ecommerce.cart.entity.Cart;
import com.ecommerce.cart.entity.CartItem;
import com.ecommerce.common.idempotency.IdempotencyKey;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.entity.OrderItem;
import com.ecommerce.product.entity.Product;
import com.ecommerce.user.entity.User;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.cart.repository.CartRepository;
import com.ecommerce.common.idempotency.IdempotencyKeyRepository;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.user.repository.UserRepository;
import com.ecommerce.payment.strategy.PaymentStrategy;
import com.ecommerce.payment.strategy.PaymentStrategyFactory;

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
        
        // map thong tin giao hang
        String fullName = request.getLastName() + " " + request.getFirstName();
        order.setCustomerName(fullName);
        order.setCustomerPhone(request.getPhone());
        order.setShippingAddress(request.getAddress());
        order.setShippingCity(request.getCity());
        order.setShippingWard(request.getWard());
        order.setShippingMethod(request.getShippingMethod());
        order.setShippingFee(request.getShippingFee());

        BigDecimal totalPrice = BigDecimal.ZERO;

        for(CartItem cartItem : cart.getItems()){
            if(request.getCartItemIds() != null 
            && !request.getCartItemIds().isEmpty() 
            && !request.getCartItemIds().contains(cartItem.getId())) {{
                continue;
            }}

            Product product = cartItem.getProductVariant().getProduct();

            // validate stock
            if(cartItem.getProductVariant().getStock() < cartItem.getQuantity()){
                log.warn("Checkout rejected: Product '{}' out of stock (requested: {}, available: {})",
                product.getName(), cartItem.getQuantity(), cartItem.getProductVariant().getStock());
                throw new IllegalArgumentException("Not enough stock for product: " + product.getName() + " Size: " + cartItem.getProductVariant().getSize());
            }

            // tao orderItem
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setSize(cartItem.getProductVariant().getSize()); // set snapshot size
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setPrice(product.getPrice());
            order.getItems().add(orderItem);

            BigDecimal subTotal = product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            totalPrice = totalPrice.add(subTotal);
        }

        if (request.getShippingFee() != null) {
            totalPrice = totalPrice.add(request.getShippingFee());
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
