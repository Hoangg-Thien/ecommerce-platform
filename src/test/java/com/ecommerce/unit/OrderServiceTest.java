package com.ecommerce.unit;

import com.ecommerce.service.OrderService;
import java.util.*;
import com.ecommerce.dto.response.OrderResponse;
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

    // 1. Luồng thành công: Đặt hàng thành công 1 sản phẩm
    @Test
    void createOrder_WhenCartHasItems_ShouldCreateOrderSuccessfully() {
        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse result = orderService.createOrder("user@gmail.com");

        assertNotNull(result);
        assertEquals(1L, result.getUserId());
        assertEquals(OrderStatus.PENDING, result.getStatus());
        assertEquals(BigDecimal.valueOf(2000), result.getTotalPrice()); // 1000 * 2
        assertEquals(1, result.getItems().size());
        assertEquals(2, result.getItems().get(0).getQuantity());
        assertEquals(BigDecimal.valueOf(1000), result.getItems().get(0).getPrice());

        // Kiểm tra trừ kho: 10 - 2 = 8
        assertEquals(8, product.getStock());
        verify(productRepository, times(1)).save(product);

        // Kiểm tra giỏ hàng đã được xóa sạch
        assertTrue(cart.getItems().isEmpty());
        verify(cartRepository, times(1)).save(cart);

        // Kiểm tra đã lưu đơn hàng
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    // 2. Luồng thành công: Đặt hàng nhiều sản phẩm và tính đúng tổng tiền
    @Test
    void createOrder_WhenCartHasMultipleItems_ShouldCalculateTotalPriceCorrectly() {
        Product product2 = new Product();
        product2.setId(2L);
        product2.setName("Mouse");
        product2.setPrice(BigDecimal.valueOf(200));
        product2.setStock(5);

        CartItem cartItem2 = new CartItem();
        cartItem2.setId(20L);
        cartItem2.setCart(cart);
        cartItem2.setProduct(product2);
        cartItem2.setQuantity(3);

        cart.getItems().add(cartItem2);

        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse result = orderService.createOrder("user@gmail.com");

        assertNotNull(result);
        assertEquals(2, result.getItems().size());
        // Tổng tiền: (1000 * 2) + (200 * 3) = 2000 + 600 = 2600
        assertEquals(BigDecimal.valueOf(2600), result.getTotalPrice());
        assertEquals(8, product.getStock()); // 10 - 2
        assertEquals(2, product2.getStock()); // 5 - 3
    }

    // 3. Luồng lỗi: Giỏ hàng rỗng
    @Test
    void createOrder_WhenCartIsEmpty_ShouldThrowException() {
        cart.getItems().clear(); // Làm trống giỏ hàng

        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

        assertThrows(IllegalArgumentException.class, () -> orderService.createOrder("user@gmail.com"));

        // Đảm bảo không lưu Order và không trừ stock
        verify(orderRepository, never()).save(any());
        verify(productRepository, never()).save(any());
    }

    // 4. Luồng lỗi: Sản phẩm trong giỏ không đủ tồn kho (vượt quá stock)
    @Test
    void createOrder_WhenProductOutOfStock_ShouldThrowException() {
        product.setStock(1); // Tồn kho chỉ còn 1 nhưng giỏ hàng có 2

        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

        assertThrows(IllegalArgumentException.class, () -> orderService.createOrder("user@gmail.com"));

        // Đảm bảo không lưu Order
        verify(orderRepository, never()).save(any());
    }

    // 5. Luồng lỗi: User không tồn tại
    @Test
    void createOrder_WhenUserNotFound_ShouldThrowException() {
        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> orderService.createOrder("user@gmail.com"));
        verify(orderRepository, never()).save(any());
    }

    // 6. Luồng lỗi: Cart của User không tìm thấy
    @Test
    void createOrder_WhenCartNotFound_ShouldThrowException() {
        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> orderService.createOrder("user@gmail.com"));
        verify(orderRepository, never()).save(any());
    }

    // ==========================================
    // TEST GET USER ORDERS (Lịch sử đơn hàng)
    // ==========================================

    @Test
    void getUserOrders_WhenUserExists_ShouldReturnListOfOrderResponses() {
        Order order = new Order();
        order.setId(100L);
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalPrice(BigDecimal.valueOf(2000));

        when(userRepository.findByEmail("user@gmail.com")).thenReturn(Optional.of(user));
        when(orderRepository.findByUserId(1L)).thenReturn(List.of(order));

        List<OrderResponse> results = orderService.getUserOrders("user@gmail.com");

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(100L, results.get(0).getId());
        verify(orderRepository, times(1)).findByUserId(1L);
    }

    @Test
    void getUserOrders_WhenUserNotFound_ShouldThrowException() {
        when(userRepository.findByEmail("notfound@gmail.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> orderService.getUserOrders("notfound@gmail.com"));
        verify(orderRepository, never()).findByUserId(any());
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

        OrderItem item = new OrderItem();
        item.setProduct(product); // product có stock = 10
        item.setQuantity(2);
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
