package com.ecommerce.unit;

import com.ecommerce.checkout.dto.response.CheckoutResponse;
import com.ecommerce.order.dto.response.OrderResponse;
import com.ecommerce.common.dto.PageResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.payment.entity.Payment;
import com.ecommerce.user.entity.User;
import com.ecommerce.order.enums.OrderStatus;
import com.ecommerce.payment.enums.PaymentStatus;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.exception.UnauthorizedAccessException;
import com.ecommerce.order.mapper.OrderMapper;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.user.repository.UserRepository;
import com.ecommerce.order.service.OrderService;
import com.ecommerce.payment.strategy.MomoPaymentStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MomoPaymentStrategy momoPaymentStrategy;

    @Spy
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderService orderService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("user@gmail.com");
    }

    @Test
    void getUserOrders_WhenUserExists_ShouldReturnPageResponse() {
        Order order = new Order();
        order.setId(100L);
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalPrice(BigDecimal.valueOf(2000));
        order.setItems(new ArrayList<>());

        Pageable pageable = PageRequest.of(0, 10);
        Page<Order> orderPage = new PageImpl<>(List.of(order), pageable, 1);

        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.of(user));
        when(orderRepository.findByUserId(1L, pageable)).thenReturn(orderPage);

        PageResponse<OrderResponse> results = orderService.getUserOrders("user@gmail.com", pageable);

        assertNotNull(results);
        assertEquals(1, results.getTotalElements());
        assertEquals(1, results.getTotalPages());
        assertEquals(0, results.getPageNo());
        assertEquals(10, results.getPageSize());
        assertEquals(1, results.getContent().size());
        assertEquals(100L, results.getContent().get(0).getId());
        verify(orderRepository, times(1)).findByUserId(1L, pageable);
    }

    @Test
    void getUserOrders_WhenUserNotFound_ShouldThrowException() {
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.findByEmail("notfound@gmail.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> orderService.getUserOrders("notfound@gmail.com", pageable));
        verify(orderRepository, never()).findByUserId(any(), any());
    }

    @Test
    void getOrderById_WhenUserOwnsOrder_ShouldReturnOrderResponse() {
        Order order = new Order();
        order.setId(100L);
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalPrice(BigDecimal.valueOf(1000));
        order.setItems(new ArrayList<>());

        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        OrderResponse result = orderService.getOrderById(100L, "user@gmail.com");

        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals(OrderStatus.PENDING, result.getStatus());
    }

    @Test
    void getOrderById_WhenOrderNotFound_ShouldThrowResourceNotFoundException() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> orderService.getOrderById(999L, "user@gmail.com"));
    }

    @Test
    void getOrderById_WhenUserDoesNotOwnOrder_ShouldThrowUnauthorizedAccessException() {
        Order order = new Order();
        order.setId(100L);
        User owner = new User();
        owner.setId(2L);
        owner.setEmail("owner@gmail.com");
        order.setUser(owner);

        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        assertThrows(UnauthorizedAccessException.class, 
                () -> orderService.getOrderById(100L, "hacker@gmail.com"));
    }

    @Test
    void retryPayment_WhenSuccess_ShouldReturnCheckoutResponse() {
        Order order = new Order();
        order.setId(10L);
        order.setStatus(OrderStatus.AWAITING_PAYMENT);
        order.setUser(user);
        Payment payment = new Payment();
        payment.setPaymentStatus(PaymentStatus.PENDING);
        order.setPayment(payment);

        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(momoPaymentStrategy.generatePaymentUrl(payment)).thenReturn("http://momo.vn/pay");

        CheckoutResponse response = orderService.retryPayment(10L, "user@gmail.com");

        assertNotNull(response);
        assertEquals("http://momo.vn/pay", response.getPaymentUrl());
    }

    @Test
    void retryPayment_WhenUserDoesNotOwnOrder_ShouldThrowUnauthorizedAccessException() {
        Order order = new Order();
        order.setId(10L);
        User owner = new User();
        owner.setEmail("owner@gmail.com");
        order.setUser(owner);

        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        assertThrows(UnauthorizedAccessException.class, () -> orderService.retryPayment(10L, "hacker@gmail.com"));
    }

    @Test
    void retryPayment_WhenStatusNotAwaitingPayment_ShouldThrowException() {
        Order order = new Order();
        order.setId(10L);
        order.setStatus(OrderStatus.PENDING);
        order.setUser(user);

        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        assertThrows(IllegalStateException.class, () -> orderService.retryPayment(10L, "user@gmail.com"));
    }

    @Test
    void retryPayment_WhenPaymentPaid_ShouldThrowException() {
        Order order = new Order();
        order.setId(10L);
        order.setStatus(OrderStatus.AWAITING_PAYMENT);
        order.setUser(user);
        Payment payment = new Payment();
        payment.setPaymentStatus(PaymentStatus.PAID);
        order.setPayment(payment);

        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        assertThrows(IllegalStateException.class, () -> orderService.retryPayment(10L, "user@gmail.com"));
    }
}
