package com.ecommerce.unit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ecommerce.dto.request.MomoIpnRequest;
import com.ecommerce.entity.*;
import com.ecommerce.enums.*;
import com.ecommerce.repository.*;
import com.ecommerce.service.MomoIpnService;
import com.ecommerce.service.MomoService;
import com.ecommerce.service.PaymentRefundService;

import com.ecommerce.service.PaymentFulfillmentService;

@ExtendWith(MockitoExtension.class)
class MomoIpnServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private MomoService momoService;
    @Mock private PaymentRefundService paymentRefundService;

    private PaymentFulfillmentService paymentFulfillmentService;
    private MomoIpnService momoIpnService;

    private User user;
    private Product product;
    private ProductVariant variant;
    private Order order;
    private Payment payment;
    private MomoIpnRequest ipnSuccess;
    private MomoIpnRequest ipnFailed;

    private static final String MOMO_ORDER_ID = "ORDER_1_1234567890";

    @BeforeEach
    void setUp() {
        paymentFulfillmentService = new PaymentFulfillmentService(
            paymentRepository, orderRepository, cartRepository, cartItemRepository, productRepository, paymentRefundService
        );
        momoIpnService = new MomoIpnService(
            paymentRepository, momoService, paymentRefundService, paymentFulfillmentService
        );
        user = new User();
        user.setId(1L);

        product = new Product();
        product.setId(1L);
        product.setName("Test Product");
        
        variant = new ProductVariant();
        variant.setId(10L);
        variant.setProduct(product);
        variant.setSize("42");
        variant.setStock(10);
        product.setVariants(List.of(variant));

        order = new Order();
        order.setId(1L);
        order.setUser(user);
        order.setStatus(OrderStatus.AWAITING_PAYMENT);
        order.setTotalPrice(BigDecimal.valueOf(1000));
        order.setItems(new ArrayList<>());

        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setSize("42");
        item.setQuantity(3);
        item.setOrder(order);
        order.getItems().add(item);

        payment = new Payment();
        payment.setId(1L);
        payment.setOrder(order);
        payment.setPaymentMethod(PaymentMethod.MOMO);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setAmount(BigDecimal.valueOf(1000));
        payment.setMomoOrderId(MOMO_ORDER_ID);

        ipnSuccess = buildIpn(0, "Successful.");
        ipnFailed  = buildIpn(1006, "Payment was cancelled by the user.");
    }

    @Test
    void handleIpn_WhenSuccessful_ShouldSetPaymentToPaid() {
        when(momoService.verifySignature(ipnSuccess)).thenReturn(true);
        when(paymentRepository.findByMomoOrderId(MOMO_ORDER_ID)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());

        momoIpnService.handleIpn(ipnSuccess);

        assertEquals(PaymentStatus.PAID, payment.getPaymentStatus());
    }

    @Test
    void handleIpn_WhenSuccessful_ShouldSetOrderToConfirmed() {
        when(momoService.verifySignature(ipnSuccess)).thenReturn(true);
        when(paymentRepository.findByMomoOrderId(MOMO_ORDER_ID)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());

        momoIpnService.handleIpn(ipnSuccess);

        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
    }

    @Test
    void handleIpn_WhenSuccessful_ShouldDeductStock() {
        when(momoService.verifySignature(ipnSuccess)).thenReturn(true);
        when(paymentRepository.findByMomoOrderId(MOMO_ORDER_ID)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());

        momoIpnService.handleIpn(ipnSuccess);

        assertEquals(7, product.getVariants().get(0).getStock());
        verify(productRepository, times(1)).save(product);
    }

    @Test
    void handleIpn_WhenSuccessful_ShouldClearCart() {
        Cart cart = new Cart();
        cart.setUser(user);
        cart.setItems(new ArrayList<>());
        CartItem cartItem = new CartItem();
        cartItem.setCart(cart);
        cart.getItems().add(cartItem);

        when(momoService.verifySignature(ipnSuccess)).thenReturn(true);
        when(paymentRepository.findByMomoOrderId(MOMO_ORDER_ID)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

        momoIpnService.handleIpn(ipnSuccess);

        assertTrue(cart.getItems().isEmpty());
        verify(cartItemRepository, times(1)).deleteAll(any());
    }

    @Test
    void handleIpn_WhenSuccessful_ShouldSaveTransactionId() {
        when(momoService.verifySignature(ipnSuccess)).thenReturn(true);
        when(paymentRepository.findByMomoOrderId(MOMO_ORDER_ID)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());

        momoIpnService.handleIpn(ipnSuccess);

        assertEquals("987654321", payment.getTransactionId());
    }

    @Test
    void handleIpn_WhenFailed_ShouldSetPaymentToFailed() {
        when(momoService.verifySignature(ipnFailed)).thenReturn(true);
        when(paymentRepository.findByMomoOrderId(MOMO_ORDER_ID)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        momoIpnService.handleIpn(ipnFailed);

        assertEquals(PaymentStatus.FAILED, payment.getPaymentStatus());
    }

    @Test
    void handleIpn_WhenFailed_ShouldSetOrderToCancelled() {
        when(momoService.verifySignature(ipnFailed)).thenReturn(true);
        when(paymentRepository.findByMomoOrderId(MOMO_ORDER_ID)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        momoIpnService.handleIpn(ipnFailed);

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    @Test
    void handleIpn_WhenFailed_ShouldNotDeductStock() {
        when(momoService.verifySignature(ipnFailed)).thenReturn(true);
        when(paymentRepository.findByMomoOrderId(MOMO_ORDER_ID)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        momoIpnService.handleIpn(ipnFailed);

        assertEquals(10, product.getVariants().get(0).getStock());
        verify(productRepository, never()).save(any());
    }

    @Test
    void handleIpn_WhenSignatureInvalid_ShouldThrowSecurityException() {
        when(momoService.verifySignature(ipnSuccess)).thenReturn(false);

        assertThrows(SecurityException.class, () -> momoIpnService.handleIpn(ipnSuccess));
    }

    @Test
    void handleIpn_WhenSignatureInvalid_ShouldNotQueryDatabase() {
        when(momoService.verifySignature(ipnSuccess)).thenReturn(false);

        assertThrows(SecurityException.class, () -> momoIpnService.handleIpn(ipnSuccess));

        verify(paymentRepository, never()).findByMomoOrderId(any());
    }

    @Test
    void handleIpn_WhenAlreadyProcessed_ShouldNotSaveAnything() {
        payment.setPaymentStatus(PaymentStatus.PAID);

        when(momoService.verifySignature(ipnSuccess)).thenReturn(true);
        when(paymentRepository.findByMomoOrderId(MOMO_ORDER_ID)).thenReturn(Optional.of(payment));

        momoIpnService.handleIpn(ipnSuccess);

        verify(paymentRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
        verify(productRepository, never()).save(any());
    }

    @Test
    void handleIpn_WhenAlreadyFailed_ShouldNotProcessAgain() {
        payment.setPaymentStatus(PaymentStatus.FAILED);

        when(momoService.verifySignature(ipnFailed)).thenReturn(true);
        when(paymentRepository.findByMomoOrderId(MOMO_ORDER_ID)).thenReturn(Optional.of(payment));

        momoIpnService.handleIpn(ipnFailed);

        verify(orderRepository, never()).save(any());
    }

    private MomoIpnRequest buildIpn(int resultCode, String message) {
        MomoIpnRequest req = new MomoIpnRequest();
        req.setOrderId(MOMO_ORDER_ID);
        req.setResultCode(resultCode);
        req.setMessage(message);
        req.setTransId("987654321");
        req.setAmount(BigDecimal.valueOf(1000));
        req.setExtraData("");
        req.setOrderInfo("Thanh toan don hang #1");
        req.setOrderType("momo_wallet");
        req.setPartnerCode("MOMO_TEST");
        req.setPayType("qr");
        req.setRequestId("req-001");
        req.setResponseTime(1722955200000L);
        req.setSignature("any-signature");
        return req;
    }

    @Test
    void handleIpn_PaymentFailedAndResultCode0_ShouldTriggerRefundAndNotConfirmOrder() {
        payment.setPaymentStatus(PaymentStatus.FAILED);
        order.setStatus(OrderStatus.CANCELLED);
        
        when(momoService.verifySignature(ipnSuccess)).thenReturn(true);
        when(paymentRepository.findByMomoOrderId(MOMO_ORDER_ID)).thenReturn(Optional.of(payment));

        momoIpnService.handleIpn(ipnSuccess);

        verify(paymentRefundService).processRefund(payment.getId());
        verify(orderRepository, never()).save(any(Order.class)); 
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }
    
    @Test
    void handleIpn_WhenStockIsZeroAndPaymentSuccessful_ShouldFailOrderAndNotDeductStock() {
        variant.setStock(0); 
        
        when(momoService.verifySignature(ipnSuccess)).thenReturn(true);
        when(paymentRepository.findByMomoOrderId(MOMO_ORDER_ID)).thenReturn(Optional.of(payment));

        momoIpnService.handleIpn(ipnSuccess);

        assertEquals(0, product.getVariants().get(0).getStock(), "Stock MUST NOT be negative!");
        verify(productRepository, never()).save(any(Product.class)); 
        
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(PaymentStatus.FAILED, payment.getPaymentStatus());
        
        verify(orderRepository).save(order);
        
        verify(paymentRefundService).processRefund(payment.getId());
    }
}
