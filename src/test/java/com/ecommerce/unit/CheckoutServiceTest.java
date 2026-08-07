package com.ecommerce.unit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ecommerce.dto.request.CheckoutRequest;
import com.ecommerce.entity.*;
import com.ecommerce.enums.PaymentMethod;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.*;
import com.ecommerce.service.CheckoutService;
import com.ecommerce.service.payment.*;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private CartRepository cartRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private PaymentStrategyFactory strategyFactory;
    @Mock private PaymentStrategy mockStrategy;

    // CheckoutService có field momoPaymentStrategy (do bạn inject trực tiếp)
    @Mock private MomoPaymentStrategy momoPaymentStrategy;

    @InjectMocks
    private CheckoutService checkoutService;

    private User user;
    private Product product;
    private Cart cart;
    private CheckoutRequest codRequest;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("user@gmail.com");

        product = new Product();
        product.setId(1L);
        product.setName("Laptop");
        product.setPrice(BigDecimal.valueOf(1000));
        product.setStock(5);

        cart = new Cart();
        cart.setUser(user);
        cart.setItems(new ArrayList<>());

        CartItem cartItem = new CartItem();
        cartItem.setProduct(product);
        cartItem.setQuantity(2);
        cartItem.setCart(cart);
        cart.getItems().add(cartItem);

        codRequest = new CheckoutRequest();
        codRequest.setPaymentMethod(PaymentMethod.COD);
    }

    @Test
    void checkout_WhenUserNotFound_ShouldThrowResourceNotFoundException() {
        when(userRepository.findByEmail("ghost@gmail.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> checkoutService.checkout("ghost@gmail.com", codRequest));
    }

    @Test
    void checkout_WhenCartNotFound_ShouldThrowResourceNotFoundException() {
        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> checkoutService.checkout("user@gmail.com", codRequest));
    }

    @Test
    void checkout_WhenCartIsEmpty_ShouldThrowIllegalArgumentException() {
        cart.getItems().clear(); // cart rỗng

        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> checkoutService.checkout("user@gmail.com", codRequest));

        assertTrue(ex.getMessage().contains("empty cart"));
    }

    @Test
    void checkout_WhenStockNotEnough_ShouldThrowIllegalArgumentException() {
        product.setStock(1); // chỉ còn 1 nhưng quantity = 2

        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> checkoutService.checkout("user@gmail.com", codRequest));

        assertTrue(ex.getMessage().contains("Not enough stock"));
        assertTrue(ex.getMessage().contains(product.getName()));
    }

    @Test
    void checkout_WhenValid_ShouldCallStrategy() {
        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(strategyFactory.getPaymentStrategy(PaymentMethod.COD)).thenReturn(mockStrategy);
        when(mockStrategy.processPayment(any(Order.class), any(Cart.class))).thenReturn(null);

        checkoutService.checkout("user@gmail.com", codRequest);

        verify(mockStrategy, times(1)).processPayment(any(Order.class), eq(cart));
    }
}
