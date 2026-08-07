package com.ecommerce.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.dto.request.CheckoutRequest;
import com.ecommerce.dto.response.CheckoutResponse;
import com.ecommerce.entity.Cart;
import com.ecommerce.entity.CartItem;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderItem;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.User;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.service.payment.MomoPaymentStrategy;
import com.ecommerce.service.payment.PaymentStrategy;
import com.ecommerce.service.payment.PaymentStrategyFactory;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CheckoutService {
    private final MomoPaymentStrategy momoPaymentStrategy;
    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final PaymentStrategyFactory strategyFactory;
    
    @Transactional
    public CheckoutResponse checkout(String userEmail, CheckoutRequest request){
        // tim user
        User user = userRepository.findByEmail(userEmail)
        .orElseThrow(() -> new ResourceNotFoundException("User", "email", userEmail));

        // tim cart
        Cart cart = cartRepository.findByUserId(user.getId())
        .orElseThrow(() -> new ResourceNotFoundException("Cart not found for user: " + user.getEmail()));

        // validate cart co rong ko
        if(cart.getItems().isEmpty()){
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
                throw new IllegalArgumentException("Not enough stock for product: " + product.getName());
            }

            // tao orderItem
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setPrice(product.getPrice());
            order.getItems().add(orderItem);
        }

        order.setTotalPrice(totalPrice);

        //  Delegate sang Strategy — strategy sẽ quyết định:
        //    - Set trạng thái order (PENDING hay AWAITING_PAYMENT)
        //    - Tạo Payment
        //    - Trừ stock hay không
        //    - Xóa cart hay không
        //    - Gọi API ngoài (MoMo) hay không
        PaymentStrategy strategy = strategyFactory.getPaymentStrategy(request.getPaymentMethod());
        return strategy.processPayment(order, cart);
    }
}
