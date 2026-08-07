package com.ecommerce.unit.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.ArrayList;

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
    @Spy  private PaymentMapper paymentMapper;

    @InjectMocks
    private CodPaymentStrategy codPaymentStrategy;

    private Product product;
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
        product.setStock(10);

        order = new Order();
        order.setUser(user);
        order.setTotalPrice(BigDecimal.valueOf(2000));
        order.setPaymentMethod(PaymentMethod.COD);
        order.setItems(new ArrayList<>());

        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(2);
        item.setPrice(BigDecimal.valueOf(1000));
        item.setOrder(order);
        order.getItems().add(item);

        cart = new Cart();
        cart.setUser(user);
        cart.setItems(new ArrayList<>());
        CartItem cartItem = new CartItem();
        cartItem.setProduct(product);
        cartItem.setQuantity(2);
        cartItem.setCart(cart);
        cart.getItems().add(cartItem);

        // save() trả về chính object được truyền vào
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        when(cartRepository.save(any(Cart.class))).thenAnswer(i -> i.getArgument(0));
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

        // stock ban đầu = 10, quantity = 2 → còn 8
        assertEquals(8, product.getStock());
        verify(productRepository, times(1)).save(product);
    }

    @Test
    void processPayment_ShouldClearCartItems() {
        codPaymentStrategy.processPayment(order, cart);

        assertTrue(cart.getItems().isEmpty());
        verify(cartRepository, times(1)).save(cart);
    }
}
