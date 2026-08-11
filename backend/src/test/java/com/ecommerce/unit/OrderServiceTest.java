package com.ecommerce.unit;

import com.ecommerce.service.OrderService;
import java.util.*;
import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.dto.response.PageResponse;
import com.ecommerce.entity.*;
import com.ecommerce.enums.OrderStatus;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.mapper.OrderMapper;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.UserRepository;
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
    private CartRepository cartRepository;

    @Mock
    private ProductRepository productRepository;

    @Spy
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderService orderService;

    private User user;
    private Product product;
    private Cart cart;
    private CartItem cartItem;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("user@gmail.com");

        product = new Product();
        product.setId(1L);
        product.setName("Laptop");
        product.setPrice(BigDecimal.valueOf(1000));
        product.setStock(10);

        cart = new Cart();
        cart.setId(1L);
        cart.setUser(user);
        cart.setItems(new ArrayList<>());

        cartItem = new CartItem();
        cartItem.setId(10L);
        cartItem.setCart(cart);
        cartItem.setProduct(product);
        cartItem.setQuantity(2);

        cart.getItems().add(cartItem);
    }



    // ==========================================
    // TEST GET USER ORDERS (Lịch sử đơn hàng)
    // ==========================================

    @Test
    void getUserOrders_WhenUserExists_ShouldReturnPageResponse() {
        Order order = new Order();
        order.setId(100L);
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalPrice(BigDecimal.valueOf(2000));

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

    // ==========================================
    // TEST UPDATE ORDER STATUS (Admin duyệt đơn)
    // ==========================================

    @Test
    void updateOrderStatus_WhenValidTransition_ShouldUpdateStatusSuccessfully() {
        Order order = new Order();
        order.setId(100L);
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalPrice(BigDecimal.valueOf(1000));

        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse result = orderService.updateOrderStatus(100L, OrderStatus.CONFIRMED);

        assertNotNull(result);
        assertEquals(OrderStatus.CONFIRMED, result.getStatus());
        verify(orderRepository, times(1)).save(order);
    }

    @Test
    void updateOrderStatus_WhenOrderAlreadyDone_ShouldThrowException() {
        Order order = new Order();
        order.setId(100L);
        order.setStatus(OrderStatus.DONE); // Đơn đã hoàn tất

        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        assertThrows(IllegalArgumentException.class, () -> orderService.updateOrderStatus(100L, OrderStatus.CANCELLED));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void updateOrderStatus_WhenCancelled_ShouldRestockProducts() {
        Order order = new Order();
        order.setId(100L);
        order.setStatus(OrderStatus.PENDING);
        order.setUser(user); // OrderMapper.tOrderResponse() cần user để gọi getUser().getId()


        OrderItem item = new OrderItem();
        item.setProduct(product); // product có stock = 10
        item.setQuantity(2);
        item.setPrice(product.getPrice()); // cần set price để OrderMapper không NPE
        order.getItems().add(item);


        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse result = orderService.updateOrderStatus(100L, OrderStatus.CANCELLED);

        assertNotNull(result);
        assertEquals(OrderStatus.CANCELLED, result.getStatus());
        // Kiểm tra đã hoàn lại 2 sản phẩm vào kho: 10 + 2 = 12
        assertEquals(12, product.getStock());
        verify(productRepository, times(1)).save(product);
    }

}
