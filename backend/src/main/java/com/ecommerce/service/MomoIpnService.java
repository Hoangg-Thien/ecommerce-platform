package com.ecommerce.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.dto.request.MomoIpnRequest;
import com.ecommerce.entity.Payment;
import com.ecommerce.enums.PaymentStatus;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MomoIpnService {

    private final PaymentRepository paymentRepository;
    private final MomoService momoService;
    private final PaymentRefundService paymentRefundService;
    private final PaymentFulfillmentService paymentFulfillmentService;

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

        // IDEMPOTENCY CHECK VÀ XỬ LÝ TRỄ (LATE IPN)
        if(payment.getPaymentStatus() != PaymentStatus.PENDING){

            // 1. Trường hợp IPN về trễ, user ĐÃ THANH TOÁN THÀNH CÔNG (resultCode == 0)
            // nhưng Order của ta đã bị timeout đánh rớt thành FAILED/CANCELLED trước đó rồi.
            if(payment.getPaymentStatus() == PaymentStatus.FAILED && request.getResultCode() == 0){
                log.warn("Late IPN received: Payment was successful but order {} was cancelled. Triggering refund...", request.getOrderId());

                // Cần lưu transId từ request vào payment trước khi refund
                payment.setTransactionId(String.valueOf(request.getTransId()));
                paymentRepository.save(payment);
                paymentRefundService.processRefund(payment.getId());
                
                return;
            }

            // 2. Trường hợp IPN về trễ nhiều lần và ta ĐANG/ĐÃ REFUND rồi thì bỏ qua
            if(payment.getPaymentStatus() == PaymentStatus.REFUNDING ||  (payment.getPaymentStatus() == PaymentStatus.REFUNDED)){
                log.info("Duplicate late IPN received. Payment {} is already refunded.", request.getOrderId());
                return;
            }

            // 3. Các trường hợp duplicate IPN thông thường (VD: đã PAID rồi)
            log.info("IPN already processed for momoOrderId: {} (status: {})",
            request.getOrderId(), payment.getPaymentStatus());
            return;
        }

        if(request.getResultCode() == 0) {
            paymentFulfillmentService.processSuccess(payment, String.valueOf(request.getTransId()));
        } else {
            paymentFulfillmentService.processFailure(payment, "MoMo IPN ResultCode: " + request.getResultCode() + ", Message: " + request.getMessage());
        }
    }
}
