package com.ecommerce.repository;

import java.time.LocalDateTime;
import java.util.*;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ecommerce.entity.Payment;
import com.ecommerce.enums.PaymentStatus;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long>{
    
    Optional<Payment> findByOrderId(Long orderId);

    // Tìm payment theo momoOrderId mà MoMo gửi về trong IPN callback
    Optional<Payment> findByMomoOrderId(String momoOrderId); 

    // Tìm các payment PENDING đã tạo trước thời điểm cutoff (dùng cho timeout scheduler)
    List<Payment> findByPaymentStatusAndCreatedAtBefore(PaymentStatus paymentStatus, LocalDateTime cutoff); 
}
