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
import com.ecommerce.service.MomoService;
import com.ecommerce.service.payment.MomoPaymentStrategy;

@ExtendWith(MockitoExtension.class)
class MomoPaymentStrategyTest {

    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private MomoService momoService;
    @Spy  private PaymentMapper paymentMapper;

    @InjectMocks
    private MomoPaymentStrategy momoPaymentStrategy;

    private Product product;
    private Order order;
    private Cart cart;
    private static final String FAKE_PAY_URL = "https://test-payment.momo.vn/pay/abc123";

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(1L);

        product = new Product();
        product.setId(1L);
        product.setName("iPhone");
        product.setPrice(BigDecimal.valueOf(5000));
        product.setStock(5);

        order = new Order();
        order.setUser(user);
        order.setTotalPrice(BigDecimal.valueOf(5000));
        order.setPaymentMethod(PaymentMethod.MOMO);
        order.setItems(new ArrayList<>());

        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(1);
        item.setPrice(BigDecimal.valueOf(5000));
        item.setOrder(order);
        order.getItems().add(item);

        cart = new Cart();
        cart.setUser(user);
        cart.setItems(new ArrayList<>());
        CartItem cartItem = new CartItem();
        cartItem.setProduct(product);
        cartItem.setQuantity(1);
        cartItem.setCart(cart);
        cart.getItems().add(cartItem);

        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        when(momoService.createPaymentUrl(any(Payment.class))).thenReturn(FAKE_PAY_URL);
    }

    @Test
    void processPayment_ShouldSetOrderStatusToAwaitingPayment() {
        CheckoutResponse response = momoPaymentStrategy.processPayment(order, cart);

        assertEquals(OrderStatus.AWAITING_PAYMENT, response.getOrderStatus());
    }

    @Test
    void processPayment_ShouldReturnPaymentUrl() {
        CheckoutResponse response = momoPaymentStrategy.processPayment(order, cart);

        assertNotNull(response.getPaymentUrl());
        assertEquals(FAKE_PAY_URL, response.getPaymentUrl());
    }

    @Test
    void processPayment_ShouldNotDeductStock() {
        momoPaymentStrategy.processPayment(order, cart);

        // stock phải giữ nguyên = 5
        assertEquals(5, product.getStock());
    }

    @Test
    void processPayment_ShouldNotClearCart() {
        momoPaymentStrategy.processPayment(order, cart);

        // cart vẫn còn items
        assertFalse(cart.getItems().isEmpty());
        assertEquals(1, cart.getItems().size());
    }
}
