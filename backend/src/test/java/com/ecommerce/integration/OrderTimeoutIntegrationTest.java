package com.ecommerce.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.entity.Order;
import com.ecommerce.entity.Payment;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.User;
import com.ecommerce.enums.OrderStatus;
import com.ecommerce.enums.PaymentMethod;
import com.ecommerce.enums.PaymentStatus;
import com.ecommerce.enums.Role;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.scheduler.PaymentTimeoutScheduler;

@SpringBootTest
@ActiveProfiles("test")
class OrderTimeoutIntegrationTest {

    @Autowired
    private PaymentTimeoutScheduler scheduler;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Integration Test: Expired MoMo order should be CANCELLED and stock remains unchanged")
    void testSchedulerCancelsExpiredOrderAndLeavesStockIntact() {
        // 1. Create a Product
        Product product = new Product();
        product.setName("Test Product");
        product.setPrice(BigDecimal.valueOf(100));
        product.setStock(100);
        product = productRepository.save(product);

        // 2. Create User
        User user = new User();
        user.setEmail("test_timeout@example.com");
        user.setPassword("password");
        user.setRole(Role.USER);
        user = userRepository.save(user);

        // 3. Create an Order that is AWAITING_PAYMENT
        Order order = new Order();
        order.setUser(user);
        order.setTotalPrice(BigDecimal.valueOf(100));
        order.setStatus(OrderStatus.AWAITING_PAYMENT);
        order.setPaymentMethod(PaymentMethod.MOMO);

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentMethod(PaymentMethod.MOMO);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setAmount(BigDecimal.valueOf(100));
        
        order.setPayment(payment);
        
        order = orderRepository.save(order);
        
        // Force update the create_at column to be 16 minutes in the past
        jdbcTemplate.update("UPDATE orders SET create_at = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(16), order.getId());

        // We flush or clear to ensure the next read comes from DB if needed, but 
        // since we are using jdbcTemplate to modify, let's just proceed.

        // 4. Act: run scheduler
        scheduler.cancelExpiredMomoPayments();

        // 5. Assert: check order status and product stock
        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.CANCELLED, updatedOrder.getStatus());
        assertEquals(PaymentStatus.FAILED, updatedOrder.getPayment().getPaymentStatus());

        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertEquals(100, updatedProduct.getStock(), "Stock should remain unchanged");
    }
}
