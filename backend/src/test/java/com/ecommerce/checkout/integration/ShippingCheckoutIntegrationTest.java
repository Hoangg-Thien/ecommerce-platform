package com.ecommerce.checkout.integration;

import com.ecommerce.cart.entity.Cart;
import com.ecommerce.cart.entity.CartItem;
import com.ecommerce.cart.repository.CartRepository;
import com.ecommerce.checkout.dto.request.CheckoutRequest;
import com.ecommerce.checkout.service.CheckoutService;
import com.ecommerce.order.enums.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.payment.enums.PaymentMethod;
import com.ecommerce.payment.enums.PaymentStatus;
import com.ecommerce.payment.repository.PaymentRepository;
import com.ecommerce.product.entity.Product;
import com.ecommerce.product.entity.ProductVariant;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.user.entity.User;
import com.ecommerce.user.enums.Role;
import com.ecommerce.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(properties={"payment.vnpay.enabled=true", "payment.vnpay.tmn-code=TESTCODE", "payment.vnpay.hash-secret=test-only-key", "payment.vnpay.return-url=https://merchant.test/api/v1/payments/vnpay/return"})
@Transactional
class ShippingCheckoutIntegrationTest {
    @Autowired CheckoutService checkoutService;
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired CartRepository carts;
    @Autowired OrderRepository orders;
    @Autowired PaymentRepository payments;
    @Autowired EntityManager entityManager;

    @org.junit.jupiter.api.BeforeEach void bindRequest() {
        var request = new org.springframework.mock.web.MockHttpServletRequest(); request.setRemoteAddr("127.0.0.1");
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
    }
    @org.junit.jupiter.api.AfterEach void clearRequest() { org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes(); }

    @ParameterizedTest
    @CsvSource({"COD,-1", "COD,0", "COD,999999", "VNPAY,-1", "VNPAY,0", "VNPAY,999999"})
    void usesServerShippingAndPreservesStockCartBehavior(PaymentMethod method, BigDecimal clientFee) {
        User user = new User();
        user.setEmail(UUID.randomUUID() + "@test.local");
        user.setPassword("test");
        user.setRole(Role.USER);
        users.save(user);
        Product product = new Product();
        product.setName("Shipping regression");
        product.setPrice(new BigDecimal("100000"));
        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSize("M");
        variant.setStock(5);
        product.getVariants().add(variant);
        products.save(product);
        Cart cart = new Cart();
        cart.setUser(user);
        CartItem item = new CartItem();
        item.setCart(cart);
        item.setProductVariant(variant);
        item.setQuantity(2);
        cart.getItems().add(item);
        carts.save(cart);

        CheckoutRequest request = new CheckoutRequest();
        request.setPaymentMethod(method);
        request.setExpectedTotal(new BigDecimal("230000"));
        request.setShippingFee(clientFee);
        // Exercise both legacy representations of whole-cart checkout.
        request.setCartItemIds(method == PaymentMethod.COD ? null : List.of());
        var response = checkoutService.checkout(user.getEmail(), request, UUID.randomUUID().toString());
        entityManager.flush();
        entityManager.clear();

        var order = orders.findById(response.getOrderId()).orElseThrow();
        var payment = payments.findByOrderId(order.getId()).orElseThrow();
        assertThat(order.getShippingFee()).isEqualByComparingTo("30000");
        assertThat(order.getTotalPrice()).isEqualByComparingTo("230000");
        assertThat(order.getSubtotalPrice()).isEqualByComparingTo("200000");
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("0");
        assertThat(order.getItems().getFirst().getOriginalPrice()).isEqualByComparingTo("100000");
        assertThat(payment.getAmount()).isEqualByComparingTo(order.getTotalPrice());
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getStatus()).isEqualTo(method == PaymentMethod.COD
                ? OrderStatus.PENDING : OrderStatus.AWAITING_PAYMENT);
        assertThat(products.findById(product.getId()).orElseThrow().getVariantBySize("M").orElseThrow().getStock())
                .isEqualTo(method == PaymentMethod.COD ? 3 : 5);
        assertThat(carts.findByUserId(user.getId()).orElseThrow().getItems())
                .hasSize(method == PaymentMethod.COD ? 0 : 1);
        if (method == PaymentMethod.COD) assertThat(response.getPaymentUrl()).isNull();
        else assertThat(response.getPaymentUrl()).startsWith(com.ecommerce.payment.config.VnpayConfig.SANDBOX_PAY_URL + "?");
    }
}
