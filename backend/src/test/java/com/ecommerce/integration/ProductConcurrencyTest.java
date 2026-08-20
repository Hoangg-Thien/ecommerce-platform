package com.ecommerce.integration;

import com.ecommerce.entity.Category;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.ProductVariant;
import com.ecommerce.repository.CategoryRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.ProductVariantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.List;
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
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Long testVariantId;

    @BeforeEach
    void setUp() {
        productVariantRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();

        Category category = new Category();
        category.setName("Laptops");
        category.setDescription("Gaming laptops");
        category = categoryRepository.save(category);

        Product product = new Product();
        product.setName("MacBook Pro M3");
        product.setPrice(BigDecimal.valueOf(2000));
        product.setCategory(category);
        
        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSize("42");
        variant.setStock(1);
        product.setVariants(List.of(variant));
        
        product = productRepository.save(product);
        testVariantId = product.getVariants().get(0).getId();
    }

    @AfterEach
    void tearDown() {
        productVariantRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    @Test
    @DisplayName("Direct JPA Optimistic Locking: 2 concurrent transactions updating same variant version=0 -> 1 success, 1 OptimisticLockException, final stock=0")
    void concurrentDirectProductStockUpdate_ShouldThrowOptimisticLockException() throws InterruptedException {
        int threads = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(threads);

        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger optimisticLockExceptionCount = new AtomicInteger(0);

        ProductVariant variantThread1 = productVariantRepository.findById(testVariantId).orElseThrow();
        ProductVariant variantThread2 = productVariantRepository.findById(testVariantId).orElseThrow();

        assertEquals(0L, variantThread1.getVersion());
        assertEquals(0L, variantThread2.getVersion());

        executorService.submit(() -> {
            try {
                readyLatch.countDown();
                startLatch.await();

                variantThread1.setStock(variantThread1.getStock() - 1);
                productVariantRepository.save(variantThread1);
                successCount.incrementAndGet();
            } catch (ObjectOptimisticLockingFailureException | jakarta.persistence.OptimisticLockException e) {
                optimisticLockExceptionCount.incrementAndGet();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                doneLatch.countDown();
            }
        });

        executorService.submit(() -> {
            try {
                readyLatch.countDown();
                startLatch.await();

                Thread.sleep(20);
                variantThread2.setStock(variantThread2.getStock() - 1);
                productVariantRepository.save(variantThread2);
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

        assertEquals(1, successCount.get(), "Chỉ có 1 thread cập nhật thành công");
        assertEquals(1, optimisticLockExceptionCount.get(), "Thread còn lại phải ném OptimisticLockException");

        ProductVariant finalVariant = productVariantRepository.findById(testVariantId).orElseThrow();
        assertEquals(0, finalVariant.getStock(), "Stock cuối cùng trong DB phải là 0, không được âm");
        assertEquals(1L, finalVariant.getVersion(), "Version của ProductVariant phải tăng lên 1 sau khi update thành công");
    }
}
