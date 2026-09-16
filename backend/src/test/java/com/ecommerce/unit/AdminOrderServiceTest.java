package com.ecommerce.unit;

import com.ecommerce.dto.response.AdminOrderResponse;
import com.ecommerce.dto.response.PageResponse;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderItem;
import com.ecommerce.entity.Payment;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.ProductVariant;
import com.ecommerce.entity.User;
import com.ecommerce.enums.OrderStatus;
import com.ecommerce.enums.PaymentMethod;
import com.ecommerce.enums.PaymentStatus;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.mapper.OrderMapper;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.service.AdminOrderService;
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
class AdminOrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Spy
    private OrderMapper orderMapper;

    @InjectMocks
    private AdminOrderService adminOrderService;

    private User user;
    private Product product;
    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("customer@gmail.com");

        product = new Product();
        product.setId(10L);
        product.setName("Sneakers");
        product.setPrice(BigDecimal.valueOf(500));

        variant = new ProductVariant();
        variant.setId(100L);
        variant.setProduct(product);
        variant.setSize("42");
        variant.setStock(10);
        product.setVariants(List.of(variant));
    }

    @Test
    void getAllOrders_WhenBothStatusAndEmailProvided_ShouldCallCombinedQuery() {
        Pageable pageable = PageRequest.of(0, 10);
        Order order = createOrder(1L, OrderStatus.CONFIRMED);
        Page<Order> page = new PageImpl<>(List.of(order), pageable, 1);

        when(orderRepository.findByStatusAndUserEmailContainingIgnoreCase(OrderStatus.CONFIRMED, "customer", pageable))
                .thenReturn(page);

        PageResponse<AdminOrderResponse> result = adminOrderService.getAllOrders(OrderStatus.CONFIRMED, " customer ", pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(orderRepository, times(1))
                .findByStatusAndUserEmailContainingIgnoreCase(OrderStatus.CONFIRMED, "customer", pageable);
    }

    @Test
    void getAllOrders_WhenOnlyStatusProvided_ShouldCallFindByStatus() {
        Pageable pageable = PageRequest.of(0, 10);
        Order order = createOrder(1L, OrderStatus.PENDING);
        Page<Order> page = new PageImpl<>(List.of(order), pageable, 1);

        when(orderRepository.findByStatus(OrderStatus.PENDING, pageable)).thenReturn(page);

        PageResponse<AdminOrderResponse> result = adminOrderService.getAllOrders(OrderStatus.PENDING, null, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(orderRepository, times(1)).findByStatus(OrderStatus.PENDING, pageable);
    }

    @Test
    void getAllOrders_WhenOnlyEmailProvided_ShouldCallFindByUserEmail() {
        Pageable pageable = PageRequest.of(0, 10);
        Order order = createOrder(1L, OrderStatus.PENDING);
        Page<Order> page = new PageImpl<>(List.of(order), pageable, 1);

        when(orderRepository.findByUserEmailContainingIgnoreCase("customer", pageable)).thenReturn(page);

        PageResponse<AdminOrderResponse> result = adminOrderService.getAllOrders(null, "customer", pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(orderRepository, times(1)).findByUserEmailContainingIgnoreCase("customer", pageable);
    }

    @Test
    void getAllOrders_WhenNoFiltersProvided_ShouldCallFindAll() {
        Pageable pageable = PageRequest.of(0, 10);
        Order order = createOrder(1L, OrderStatus.PENDING);
        Page<Order> page = new PageImpl<>(List.of(order), pageable, 1);

        when(orderRepository.findAll(pageable)).thenReturn(page);

        PageResponse<AdminOrderResponse> result = adminOrderService.getAllOrders(null, null, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(orderRepository, times(1)).findAll(pageable);
    }

    @Test
    void getOrderById_WhenOrderExists_ShouldReturnAdminOrderResponse() {
        Order order = createOrder(100L, OrderStatus.PENDING);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        AdminOrderResponse result = adminOrderService.getOrderById(100L);

        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals(OrderStatus.PENDING, result.getStatus());
        assertEquals("customer@gmail.com", result.getUserEmail());
    }

    @Test
    void getOrderById_WhenNotFound_ShouldThrowResourceNotFoundException() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> adminOrderService.getOrderById(999L));
    }

    @Test
    void updateOrderStatus_WhenValidTransition_ShouldUpdateStatusSuccessfully() {
        Order order = createOrder(100L, OrderStatus.PENDING);

        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminOrderResponse result = adminOrderService.updateOrderStatus(100L, OrderStatus.CONFIRMED);

        assertNotNull(result);
        assertEquals(OrderStatus.CONFIRMED, result.getStatus());
        verify(orderRepository, times(1)).save(order);
    }

    @Test
    void updateOrderStatus_WhenOrderAlreadyCancelled_ShouldThrowException() {
        Order order = createOrder(100L, OrderStatus.CANCELLED);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        assertThrows(IllegalArgumentException.class, () -> adminOrderService.updateOrderStatus(100L, OrderStatus.CONFIRMED));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void updateOrderStatus_WhenOrderAlreadyDone_ShouldThrowException() {
        Order order = createOrder(100L, OrderStatus.DONE);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        assertThrows(IllegalArgumentException.class, () -> adminOrderService.updateOrderStatus(100L, OrderStatus.CANCELLED));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void updateOrderStatus_WhenCancelledAndStockWasDeducted_ShouldRestockProducts() {
        Order order = createOrder(100L, OrderStatus.PENDING);

        OrderItem item = new OrderItem();
        item.setId(1L);
        item.setOrder(order);
        item.setProduct(product);
        item.setSize("42");
        item.setQuantity(3);
        item.setPrice(product.getPrice());
        order.getItems().add(item);

        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminOrderResponse result = adminOrderService.updateOrderStatus(100L, OrderStatus.CANCELLED);

        assertNotNull(result);
        assertEquals(OrderStatus.CANCELLED, result.getStatus());
        assertEquals(13, variant.getStock());
        verify(productRepository, times(1)).save(product);
    }

    @Test
    void updateOrderStatus_WhenCancelledAndAwaitingPayment_ShouldNotRestock() {
        Order order = createOrder(100L, OrderStatus.AWAITING_PAYMENT);

        OrderItem item = new OrderItem();
        item.setId(1L);
        item.setOrder(order);
        item.setProduct(product);
        item.setSize("42");
        item.setQuantity(3);
        item.setPrice(product.getPrice());
        order.getItems().add(item);

        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminOrderResponse result = adminOrderService.updateOrderStatus(100L, OrderStatus.CANCELLED);

        assertNotNull(result);
        assertEquals(OrderStatus.CANCELLED, result.getStatus());
        assertEquals(10, variant.getStock());
        verify(productRepository, never()).save(any());
    }

    @Test
    void updateOrderStatus_WhenDoneAndCodPayment_ShouldMarkPaymentPaid() {
        Order order = createOrder(100L, OrderStatus.SHIPPING);
        order.setPaymentMethod(PaymentMethod.COD);
        Payment payment = new Payment();
        payment.setPaymentStatus(PaymentStatus.PENDING);
        order.setPayment(payment);

        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminOrderResponse result = adminOrderService.updateOrderStatus(100L, OrderStatus.DONE);

        assertNotNull(result);
        assertEquals(OrderStatus.DONE, result.getStatus());
        assertEquals(PaymentStatus.PAID, payment.getPaymentStatus());
    }

    private Order createOrder(Long id, OrderStatus status) {
        Order order = new Order();
        order.setId(id);
        order.setUser(user);
        order.setStatus(status);
        order.setTotalPrice(BigDecimal.valueOf(1000));
        order.setItems(new ArrayList<>());
        return order;
    }
}
