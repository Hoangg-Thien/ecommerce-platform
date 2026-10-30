package com.ecommerce.checkout.service;

import com.ecommerce.cart.entity.Cart;
import com.ecommerce.cart.entity.CartItem;
import com.ecommerce.cart.repository.CartRepository;
import com.ecommerce.common.idempotency.IdempotencyKeyRepository;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.entity.ProductVariant;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.repository.UserRepository;

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

import com.ecommerce.checkout.dto.request.CheckoutRequest;
import com.ecommerce.payment.enums.PaymentMethod;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.checkout.service.CheckoutService;
import com.ecommerce.payment.strategy.*;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private CartRepository cartRepository;
    @Mock
    private OrderRepository orderRepository;

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Mock
    private PaymentStrategyFactory strategyFactory;
    @Mock private PaymentStrategy mockStrategy;

    @Mock private MomoPaymentStrategy momoPaymentStrategy;

    @InjectMocks
    private CheckoutService checkoutService;

    @org.mockito.Spy
    private ShippingFeeService shippingFeeService = new ShippingFeeService(new BigDecimal("30000"));

    @org.mockito.Spy private java.time.Clock clock = java.time.Clock.systemUTC();
    @Mock private com.ecommerce.promotion.service.PromotionPricingService pricing;
    @Mock private com.ecommerce.promotion.service.VoucherUsageService voucherUsage;
    @Mock private com.ecommerce.promotion.repository.VoucherRepository vouchers;
    @Mock private com.ecommerce.order.mapper.OrderMapper orderMapper;

    private User user;
    private Product product;
    private ProductVariant variant;
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
        
        variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSize("42");
        variant.setStock(5);
        product.setVariants(java.util.List.of(variant));

        cart = new Cart();
        cart.setUser(user);
        cart.setItems(new ArrayList<>());

        CartItem cartItem = new CartItem();
        cartItem.setProductVariant(variant);
        cartItem.setQuantity(2);
        cartItem.setCart(cart);
        cart.getItems().add(cartItem);

        codRequest = new CheckoutRequest();
        codRequest.setPaymentMethod(PaymentMethod.COD);
        codRequest.setExpectedTotal(new BigDecimal("32000"));
        lenient().when(idempotencyKeyRepository.insertIfAbsent(anyString(),any())).thenReturn(1);
        lenient().when(idempotencyKeyRepository.linkOrder(anyString(),anyLong())).thenReturn(1);
        lenient().when(pricing.price(anyList(),anyLong(),any(),any())).thenAnswer(invocation ->
                new com.ecommerce.promotion.pricing.PricingCalculator().calculate(invocation.getArgument(0),
                        java.util.List.of(),java.util.List.of(),null,invocation.getArgument(3)));
        lenient().when(mockStrategy.processPayment(any(),any())).thenReturn(
                com.ecommerce.checkout.dto.response.CheckoutResponse.builder().orderId(1L).build());
    }

    @Test
    void checkout_WhenUserNotFound_ShouldThrowResourceNotFoundException() {
        when(userRepository.findByEmail("ghost@gmail.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> checkoutService.checkout("ghost@gmail.com", codRequest, "test-key"));
    }

    @Test
    void checkout_WhenCartNotFound_ShouldThrowResourceNotFoundException() {
        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> checkoutService.checkout("user@gmail.com", codRequest, "test-key"));
    }

    @Test
    void checkout_WhenCartIsEmpty_ShouldThrowIllegalArgumentException() {
        cart.getItems().clear(); 

        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> checkoutService.checkout("user@gmail.com", codRequest, "test-key"));

        assertTrue(ex.getMessage().contains("empty cart"));
    }

    @Test
    void checkout_WhenStockNotEnough_ShouldThrowIllegalArgumentException() {
        variant.setStock(1); 

        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> checkoutService.checkout("user@gmail.com", codRequest, "test-key"));

        assertTrue(ex.getMessage().contains("Not enough stock"));
        assertTrue(ex.getMessage().contains(product.getName()));
    }

    @Test
    void checkout_WhenValid_ShouldCallStrategy() {
        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(strategyFactory.getPaymentStrategy(PaymentMethod.COD)).thenReturn(mockStrategy);

        checkoutService.checkout("user@gmail.com", codRequest, "test-key");

        verify(mockStrategy, times(1)).processPayment(any(Order.class), eq(cart));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"-1", "0", "999999"})
    void checkout_IgnoresClientShippingFee(String clientFee) {
        codRequest.setShippingFee(new BigDecimal(clientFee));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(strategyFactory.getPaymentStrategy(PaymentMethod.COD)).thenReturn(mockStrategy);

        checkoutService.checkout(user.getEmail(), codRequest, "shipping-test");

        var captured = org.mockito.ArgumentCaptor.forClass(Order.class);
        verify(mockStrategy).processPayment(captured.capture(), eq(cart));
        assertEquals(0, new BigDecimal("30000").compareTo(captured.getValue().getShippingFee()));
        assertEquals(0, new BigDecimal("32000").compareTo(captured.getValue().getTotalPrice()));
    }
}
