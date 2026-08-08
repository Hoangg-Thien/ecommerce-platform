package com.ecommerce.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.dto.request.MomoIpnRequest;
import com.ecommerce.entity.Cart;
import com.ecommerce.entity.Order;
import com.ecommerce.entity.OrderItem;
import com.ecommerce.entity.Payment;
import com.ecommerce.entity.Product;
import com.ecommerce.enums.OrderStatus;
import com.ecommerce.enums.PaymentStatus;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.OrderRepository;
import com.ecommerce.repository.PaymentRepository;
import com.ecommerce.repository.ProductRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MomoIpnService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final MomoService momoService;

    @Transactional
    public void handleIpn(MomoIpnRequest request){

        // verify chu ky 
        if(!momoService.verifySignature(request)){
            log.warn("Invalid MoMo IPN signature! orderId: {}", request.getOrderId());
            throw new SecurityException("Invalid MoMo signature — possible fake callback");
        }

        // tim payment bang momoOrderId
        Payment payment = paymentRepository.findByMomoOrderId(request.getOrderId())
        .orElseThrow(() -> new ResourceNotFoundException("Payment not found for MoMo orderId: " + request.getOrderId()));

        // IDEMPOTENCY CHECK — tránh xử lý 2 lần
        // MoMo có thể gọi IPN nhiều lần nếu server trả về lỗi
        // Nếu đã xử lý rồi (không còn PENDING) → bỏ qua, vẫn trả 200 OK cho MoMo
        if(payment.getPaymentStatus() != PaymentStatus.PENDING){
            log.info("IPN already processed for momoOrderId: {} (status: {})",
                request.getOrderId(), payment.getPaymentStatus());
            return;
        }

        Order order = payment.getOrder();

        if(request.getResultCode() == 0) handleSuccessfulPayment(payment, order, request);
        else handleFailedPayment(payment, order, request);
    }

    private void handleSuccessfulPayment(Payment payment, Order order, MomoIpnRequest request){
        
        // Cập nhật Payment: PAID + lưu transaction ID từ MoMo
        payment.setPaymentStatus(PaymentStatus.PAID);
        payment.setTransactionId(String.valueOf(request.getTransId()));
        paymentRepository.save(payment);

        // Cập nhật Order: CONFIRMED 
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);

        // Trừ stock
        for(OrderItem orderItem : order.getItems()){
            Product product = orderItem.getProduct();
            product.setStock(product.getStock() - orderItem.getQuantity());
            productRepository.save(product);
        }

        // Xóa cart
        Cart cart = cartRepository.findByUserId(order.getUser().getId()).orElse(null);
        if(cart != null){
            cart.getItems().clear();
            cartRepository.save(cart);
        }

        log.info("MoMo payment SUCCESS for order {}. TransactionId: {}",order.getId(), request.getTransId());
    }

    private void handleFailedPayment (Payment payment, Order order, MomoIpnRequest request){
        // Set fail
        payment.setPaymentStatus(PaymentStatus.FAILED);
        paymentRepository.save(payment);

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        log.info("MoMo payment FAILED for order {}. ResultCode: {}, Message: {}",order.getId(), request.getResultCode(), request.getMessage());
    }
}
