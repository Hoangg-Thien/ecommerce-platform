package com.ecommerce.integration;

import com.ecommerce.entity.Category;
import com.ecommerce.entity.Product;
import com.ecommerce.repository.CategoryRepository;
import com.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ProductConcurrencyTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Long testProductId;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        categoryRepository.deleteAll();

        Category category = new Category();
        category.setName("Laptops");
        category.setDescription("Gaming laptops");
        category = categoryRepository.save(category);

        Product product = new Product();
        product.setName("MacBook Pro M3");
        product.setPrice(BigDecimal.valueOf(2000));
        product.setStock(1); // Chỉ còn 1 sản phẩm
        product.setCategory(category);
        product = productRepository.save(product);
        testProductId = product.getId();
    }

    @AfterEach
    void tearDown() {
        productRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    @Test
    @DisplayName("Direct JPA Optimistic Locking: 2 concurrent transactions updating same product version=0 -> 1 success, 1 OptimisticLockException, final stock=0")
    void concurrentDirectProductStockUpdate_ShouldThrowOptimisticLockException() throws InterruptedException {
        int threads = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(threads);

        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger optimisticLockExceptionCount = new AtomicInteger(0);

        // Cả 2 thread cùng đọc snapshot product từ DB (khi version = 0, stock = 1)
        Product productThread1 = productRepository.findById(testProductId).orElseThrow();
        Product productThread2 = productRepository.findById(testProductId).orElseThrow();

        assertEquals(0L, productThread1.getVersion());
        assertEquals(0L, productThread2.getVersion());

        // Thread 1 trừ kho và lưu
        executorService.submit(() -> {
            try {
                readyLatch.countDown();
                startLatch.await();

                productThread1.setStock(productThread1.getStock() - 1);
                productRepository.save(productThread1);
                successCount.incrementAndGet();
            } catch (ObjectOptimisticLockingFailureException | jakarta.persistence.OptimisticLockException e) {
                optimisticLockExceptionCount.incrementAndGet();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2 cũng trừ kho và lưu (trên entity snapshot version = 0 cũ)
        executorService.submit(() -> {
            try {
                readyLatch.countDown();
                startLatch.await();

                // Chờ một chút để Thread 1 commit trước, làm version trong DB nhảy lên 1
                Thread.sleep(20);
                productThread2.setStock(productThread2.getStock() - 1);
                productRepository.save(productThread2);
                successCount.incrementAndGet();
            } catch (ObjectOptimisticLockingFailureException | jakarta.persistence.OptimisticLockException e) {
                optimisticLockExceptionCount.incrementAndGet();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                doneLatch.countDown();
            }
        });

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        assertTrue(completed, "Product Concurrency test timed out");
        executorService.shutdown();

        // Kiểm tra kết quả:
        // Đúng 1 thread save thành công
        assertEquals(1, successCount.get(), "Chỉ có 1 thread cập nhật thành công");
        // Đúng 1 thread bị OptimisticLockException
        assertEquals(1, optimisticLockExceptionCount.get(), "Thread còn lại phải ném OptimisticLockException / ObjectOptimisticLockingFailureException");

        // Kiểm tra trong DB: stock = 0, version = 1, tuyệt đối không bị âm
        Product finalProduct = productRepository.findById(testProductId).orElseThrow();
        assertEquals(0, finalProduct.getStock(), "Stock cuối cùng trong DB phải là 0, không được âm");
        assertEquals(1L, finalProduct.getVersion(), "Version của Product phải tăng lên 1 sau khi 1 thread update thành công");
    }
}
