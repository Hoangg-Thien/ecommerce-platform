package com.ecommerce.unit.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ecommerce.dto.response.CheckoutResponse;
import com.ecommerce.entity.*;
import com.ecommerce.enums.*;
import com.ecommerce.mapper.PaymentMapper;
import com.ecommerce.repository.*;
import com.ecommerce.service.payment.CodPaymentStrategy;

@ExtendWith(MockitoExtension.class)
class CodPaymentStrategyTest {

    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ProductRepository productRepository;
    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Spy  private PaymentMapper paymentMapper;

    @InjectMocks
    private CodPaymentStrategy codPaymentStrategy;

    private Product product;
    private ProductVariant variant;
    private Order order;
    private Cart cart;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(1L);

        product = new Product();
        product.setId(1L);
        product.setName("Laptop");
        product.setPrice(BigDecimal.valueOf(1000));
        
        variant = new ProductVariant();
        variant.setId(10L);
        variant.setProduct(product);
        variant.setSize("42");
        variant.setStock(10);
        product.setVariants(List.of(variant));

        order = new Order();
        order.setUser(user);
        order.setTotalPrice(BigDecimal.valueOf(2000));
        order.setPaymentMethod(PaymentMethod.COD);
        order.setItems(new ArrayList<>());

        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setSize("42");
        item.setQuantity(2);
        item.setPrice(BigDecimal.valueOf(1000));
        item.setOrder(order);
        order.getItems().add(item);

        cart = new Cart();
        cart.setUser(user);
        cart.setItems(new ArrayList<>());
        CartItem cartItem = new CartItem();
        cartItem.setProductVariant(variant);
        cartItem.setQuantity(2);
        cartItem.setCart(cart);
        cart.getItems().add(cartItem);

        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void processPayment_ShouldSetOrderStatusToPending() {
        CheckoutResponse response = codPaymentStrategy.processPayment(order, cart);

        assertEquals(OrderStatus.PENDING, response.getOrderStatus());
    }

    @Test
    void processPayment_ShouldReturnNullPaymentUrl() {
        CheckoutResponse response = codPaymentStrategy.processPayment(order, cart);

        assertNull(response.getPaymentUrl());
    }

    @Test
    void processPayment_ShouldCreatePaymentWithCodMethodAndPendingStatus() {
        CheckoutResponse response = codPaymentStrategy.processPayment(order, cart);

        assertNotNull(response.getPaymentResponse());
        assertEquals(PaymentMethod.COD, response.getPaymentResponse().getPaymentMethod());
        assertEquals(PaymentStatus.PENDING, response.getPaymentResponse().getPaymentStatus());
    }

    @Test
    void processPayment_ShouldDeductStockByQuantity() {
        codPaymentStrategy.processPayment(order, cart);

        assertEquals(8, product.getVariants().get(0).getStock());
        verify(productRepository, times(1)).save(product);
    }

    @Test
    void processPayment_ShouldClearCartItems() {
        codPaymentStrategy.processPayment(order, cart);

        assertTrue(cart.getItems().isEmpty());
        verify(cartItemRepository, times(1)).deleteAll(any());
    }
}
