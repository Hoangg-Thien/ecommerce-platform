package com.ecommerce.integration;

import com.ecommerce.dto.request.CheckoutRequest;
import com.ecommerce.entity.*;
import com.ecommerce.enums.OrderStatus;
import com.ecommerce.enums.PaymentMethod;
import com.ecommerce.enums.Role;
import com.ecommerce.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class OrderConcurrencyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long testProductId;
    private Long testVariantId;
    private static final String USER1_EMAIL = "buyer1@example.com";
    private static final String USER2_EMAIL = "buyer2@example.com";

    @BeforeEach
    void setUp() {
        // Xóa dữ liệu cũ trước khi test
        cleanupDatabase();

        // 1. Tạo Category & Product với stock = 1
        Category category = new Category();
        category.setName("Electronics");
        category.setDescription("Electronic devices");
        category = categoryRepository.save(category);

        Product product = new Product();
        product.setName("Limited Edition Smartphone");
        product.setPrice(BigDecimal.valueOf(1000));
        product.setDescription("Rare item");
        product.setCategory(category);
        product = productRepository.save(product);
        testProductId = product.getId();

        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSize("42");
        variant.setStock(1);
        variant = productVariantRepository.save(variant);
        testVariantId = variant.getId();

        // 2. Tạo User 1 & Cart chứa 1 sản phẩm
        User user1 = new User();
        user1.setEmail(USER1_EMAIL);
        user1.setPassword(passwordEncoder.encode("password123"));
        user1.setRole(Role.USER);
        user1 = userRepository.save(user1);

        Cart cart1 = new Cart();
        cart1.setUser(user1);
        CartItem cartItem1 = new CartItem();
        cartItem1.setCart(cart1);
        cartItem1.setProductVariant(variant);
        cartItem1.setQuantity(1);
        cart1.getItems().add(cartItem1);
        cartRepository.save(cart1);

        // 3. Tạo User 2 & Cart chứa 1 sản phẩm
        User user2 = new User();
        user2.setEmail(USER2_EMAIL);
        user2.setPassword(passwordEncoder.encode("password123"));
        user2.setRole(Role.USER);
        user2 = userRepository.save(user2);

        Cart cart2 = new Cart();
        cart2.setUser(user2);
        CartItem cartItem2 = new CartItem();
        cartItem2.setCart(cart2);
        cartItem2.setProductVariant(variant);
        cartItem2.setQuantity(1);
        cart2.getItems().add(cartItem2);
        cartRepository.save(cart2);
    }

    @AfterEach
    void tearDown() {
        cleanupDatabase();
    }

    private void cleanupDatabase() {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        cartRepository.deleteAll();
        productVariantRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("2 concurrent orders for product with stock = 1 -> 1 Success (201), 1 Conflict/Bad Request, Final stock = 0 (never negative)")
    void concurrentOrderCreation_WithStockOne_ShouldPreventOverselling() throws InterruptedException {
        int numberOfThreads = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);

        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

        List<Integer> responseStatuses = Collections.synchronizedList(new ArrayList<>());
        List<String> responseBodies = Collections.synchronizedList(new ArrayList<>());

        String[] users = {USER1_EMAIL, USER2_EMAIL};

        for (String userEmail : users) {
            executorService.submit(() -> {
                try {
                    readyLatch.countDown();
                    // Cả 2 thread đợi lệnh xuất phát đồng thời
                    startLatch.await();

                    MvcResult result = mockMvc.perform(post("/api/v1/checkout")
                                    .with(user(userEmail))
                                    .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"paymentMethod\": \"COD\", \"firstName\": \"Test\", \"lastName\": \"User\", \"address\": \"123 Street\", \"city\": \"HCM\", \"ward\": \"Ward 1\", \"phone\": \"0123456789\"}"))
                            .andReturn();

                    responseStatuses.add(result.getResponse().getStatus());
                    responseBodies.add(result.getResponse().getContentAsString());
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Đợi cả 2 thread sẵn sàng
        readyLatch.await(5, TimeUnit.SECONDS);
        // Phát tín hiệu bắn đồng thời
        startLatch.countDown();
        // Đợi cả 2 hoàn tất
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        assertTrue(completed, "Concurrency test timed out");
        executorService.shutdown();

        // 1. Kiểm chứng HTTP Status codes:
        assertEquals(2, responseStatuses.size(), "Phải có đúng 2 response");
        
        long successCount = responseStatuses.stream().filter(s -> s == 201).count();
        long conflictCount = responseStatuses.stream().filter(s -> s == 409).count();
        long badRequestCount = responseStatuses.stream().filter(s -> s == 400).count();

        // Phải có đúng 1 request thành công và 1 request thất bại (409 Conflict do Optimistic Lock hoặc 400 Bad Request do hết tồn kho)
        assertEquals(1, successCount, "Chỉ được duy nhất 1 request đặt hàng thành công (HTTP 201)");
        assertTrue(conflictCount + badRequestCount == 1, 
                "Request thứ hai phải bị từ chối với 409 Conflict hoặc 400 Bad Request, nhưng nhận statuses: " + responseStatuses);

        // 2. Kiểm chứng Database Integrity (Không bao giờ bị âm kho):
        ProductVariant updatedVariant = productVariantRepository.findById(testVariantId).orElseThrow();
        assertEquals(0, updatedVariant.getStock(), "Tồn kho sau khi tranh chấp phải là 0, tuyệt đối không bị âm!");
        assertTrue(updatedVariant.getStock() >= 0, "Tồn kho không được âm!");

        // 3. Kiểm chứng số lượng đơn hàng được tạo trong database:
        long totalOrders = orderRepository.count();
        assertEquals(1, totalOrders, "Chỉ có duy nhất 1 Order được lưu trong Database!");
    }

    @Test
    @DisplayName("2 concurrent checkout (COD) for product with stock = 1 -> 1 Success (201), 1 Failure (409/400), Final stock = 0")
    void concurrentCheckout_WithStockOne_ShouldPreventOverselling() throws InterruptedException {
        int numberOfThreads = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);

        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

        List<Integer> responseStatuses = Collections.synchronizedList(new ArrayList<>());

        String[] users = {USER1_EMAIL, USER2_EMAIL};

        CheckoutRequest checkoutRequest = new CheckoutRequest();
        checkoutRequest.setPaymentMethod(PaymentMethod.COD);
        checkoutRequest.setFirstName("Test");
        checkoutRequest.setLastName("User");
        checkoutRequest.setAddress("123 Street");
        checkoutRequest.setCity("HCM");
        checkoutRequest.setWard("Ward 1");
        checkoutRequest.setPhone("0123456789");

        for (String userEmail : users) {
            executorService.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();

                    MvcResult result = mockMvc.perform(post("/api/v1/checkout")
                                    .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                                    .with(user(userEmail))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(checkoutRequest)))
                            .andReturn();

                    responseStatuses.add(result.getResponse().getStatus());
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        assertTrue(completed, "Checkout Concurrency test timed out");
        executorService.shutdown();

        assertEquals(2, responseStatuses.size(), "Phải có đúng 2 response");

        long successCount = responseStatuses.stream().filter(s -> s == 201).count();
        long failureCount = responseStatuses.stream().filter(s -> s == 409 || s == 400).count();

        assertEquals(1, successCount, "Chỉ được duy nhất 1 checkout thành công (HTTP 201)");
        assertEquals(1, failureCount, "Checkout thứ 2 phải thất bại (409 Conflict hoặc 400 Bad Request)");

        ProductVariant updatedVariant = productVariantRepository.findById(testVariantId).orElseThrow();
        assertEquals(0, updatedVariant.getStock(), "Tồn kho phải là 0!");
        assertEquals(1, orderRepository.count(), "Chỉ có duy nhất 1 đơn hàng được tạo!");
    }

    @Test
    @DisplayName("Idempotency Key Rollback Test")
    void checkout_WhenFails_ShouldRollbackIdempotencyKey() throws Exception {
        String idempotencyKey = java.util.UUID.randomUUID().toString();

        // 1. Force a failure by setting the product stock to 0
        ProductVariant variant = productVariantRepository.findById(testVariantId).orElseThrow();
        variant.setStock(0);
        productVariantRepository.save(variant);

        // 2. User 1 calls checkout with the key -> should fail with 400 Bad Request (Insufficient stock)
        mockMvc.perform(post("/api/v1/checkout")
                .with(user(USER1_EMAIL))
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentMethod\": \"COD\", \"firstName\": \"Test\", \"lastName\": \"User\", \"address\": \"123 Street\", \"city\": \"HCM\", \"ward\": \"Ward 1\", \"phone\": \"0123456789\"}"))
                .andExpect(status().isBadRequest()); 
        
        // 3. Fix the product stock back to 1
        ProductVariant latestVariant = productVariantRepository.findById(testVariantId).orElseThrow();
        latestVariant.setStock(1);
        productVariantRepository.save(latestVariant);

        // 4. Call checkout again with the EXACT SAME KEY -> must SUCCEED (201 Created)
        // If the key wasn't rolled back, this would fail (409 Conflict or 400 Bad Request due to Duplicate Key)
        mockMvc.perform(post("/api/v1/checkout")
                .with(user(USER1_EMAIL))
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentMethod\": \"COD\", \"firstName\": \"Test\", \"lastName\": \"User\", \"address\": \"123 Street\", \"city\": \"HCM\", \"ward\": \"Ward 1\", \"phone\": \"0123456789\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("2 threads calling checkout with same Idempotency-Key -> only 1 creates order, other receives 400/409")
    void concurrentCheckout_WithSameIdempotencyKey_ShouldProcessOneAndRejectOther() throws InterruptedException {
        int numberOfThreads = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        
        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

        List<Integer> responseStatuses = Collections.synchronizedList(new ArrayList<>());
        List<String> responseBodies = Collections.synchronizedList(new ArrayList<>());
        
        String idempotencyKey = java.util.UUID.randomUUID().toString();

        for (int i = 0; i < numberOfThreads; i++) {
            executorService.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await(); // Đợi cả 2 thread sẵn sàng

                    MvcResult result = mockMvc.perform(post("/api/v1/checkout")
                                    .with(user(USER1_EMAIL))
                                    .header("Idempotency-Key", idempotencyKey) // Dùng CHUNG 1 key
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"paymentMethod\": \"COD\", \"firstName\": \"Test\", \"lastName\": \"User\", \"address\": \"123 Street\", \"city\": \"HCM\", \"ward\": \"Ward 1\", \"phone\": \"0123456789\"}"))
                            .andReturn();

                    responseStatuses.add(result.getResponse().getStatus());
                    responseBodies.add(result.getResponse().getContentAsString());
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Bắn cờ phát lệnh chạy đồng thời

        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        assertTrue(completed, "Concurrency test timed out");
        executorService.shutdown();

        assertEquals(2, responseStatuses.size(), "Phải có đúng 2 response");

        long successCount = responseStatuses.stream().filter(s -> s == 201).count();
        long failureCount = responseStatuses.stream().filter(s -> s == 409 || s == 400).count();
        
        // Tuyệt đối không được phép có mã 500 (Internal Server Error)
        assertFalse(responseStatuses.contains(500), "Hệ thống không được phép bị crash (HTTP 500)");

        assertEquals(1, successCount, "Chỉ được duy nhất 1 request thành công (HTTP 201)");
        assertEquals(1, failureCount, "Request thứ 2 phải bị chặn (HTTP 400 hoặc 409)");
        
        // Đảm bảo phải có chứa mã lỗi mong đợi (400 hoặc 409)
        assertTrue(responseStatuses.contains(400) || responseStatuses.contains(409), 
                "Phải có 1 request bị từ chối với mã 400 hoặc 409");
                
        assertEquals(1, orderRepository.count(), "Chỉ có duy nhất 1 đơn hàng được tạo trong DB!");
    }
}
