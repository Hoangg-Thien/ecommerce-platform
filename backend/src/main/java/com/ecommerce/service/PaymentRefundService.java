package com.ecommerce.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.entity.Payment;
import com.ecommerce.enums.PaymentStatus;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRefundService {

    private final PaymentRepository paymentRepository;
    private final MomoService momoService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processRefund(Long paymentId){

        // lay payment tu database
        Payment payment = paymentRepository.findById(paymentId)
        .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", paymentId));

        // idempotency guard: dang refund hoac refund roi thi ko goi api nua
        if(payment.getPaymentStatus() == PaymentStatus.REFUNDING || payment.getPaymentStatus() == PaymentStatus.REFUNDED){
            log.info("Refund already processed/processing for payment {}", paymentId);
            return;
        }

        // chuyen trang thai sang refunding
        payment.setPaymentStatus(PaymentStatus.REFUNDING);
        paymentRepository.save(payment);

        try{
            momoService.refundPayment(payment);

            // neu goi api momo thanh cong, doi sang refunded
            payment.setPaymentStatus(PaymentStatus.REFUNDED);
            paymentRepository.save(payment);
            log.info("Successfully refunded late payment for order {}", payment.getOrder().getId());
        }catch(Exception e){
            
            log.error("Failed to call MoMo Refund API for payment {}: {}", paymentId, e.getMessage());
            throw e;
        }
    }
}
