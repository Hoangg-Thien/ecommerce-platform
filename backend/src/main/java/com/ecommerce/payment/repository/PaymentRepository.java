package com.ecommerce.payment.repository;

import java.time.LocalDateTime;
import java.util.*;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ecommerce.payment.entity.Payment;
import com.ecommerce.payment.enums.PaymentStatus;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long>{
    Optional<Payment> findByProviderTxnRef(String reference);

    @org.springframework.data.jpa.repository.Query("select p.order.id from Payment p where p.providerTxnRef = :reference")
    Optional<Long> findOrderIdByProviderTxnRef(@org.springframework.data.repository.query.Param("reference") String reference);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Payment p where p.order.id = :orderId")
    Optional<Payment> lockByOrderId(@org.springframework.data.repository.query.Param("orderId") Long orderId);
    
    Optional<Payment> findByOrderId(Long orderId);


    // Tìm các payment PENDING đã tạo trước thời điểm cutoff (dùng cho timeout scheduler)
    List<Payment> findByPaymentStatusAndCreatedAtBefore(PaymentStatus paymentStatus, LocalDateTime cutoff); 
}
