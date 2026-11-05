package com.ecommerce.payment.entity;

import com.ecommerce.order.entity.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Instant;
import com.ecommerce.payment.enums.RefundStatus;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.ecommerce.payment.enums.PaymentMethod;
import com.ecommerce.payment.enums.PaymentStatus;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod; 

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status")
    private PaymentStatus paymentStatus;  

    @Version
    private Long version;

    @Column(name = "amount")
    private BigDecimal amount; // order.totalPrice

    private String transactionId;  // MoMo return (null with COD)
    private String momoRequestId;  // requestId send to MoMo (null with COD)
    private String momoOrderId;    // orderId MoMo (null with COD)

    @Column(unique = true, length = 100)
    private String providerTxnRef;
    @Column(length = 15)
    private String providerTransactionNo;
    @Column(length = 2)
    private String responseCode;
    @Column(length = 2)
    private String transactionStatus;
    private Instant paidAt;
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private RefundStatus refundStatus;
    @Column(length = 100)
    private String reason;
    private Instant expiresAt;
    @Column(columnDefinition = "TEXT")
    private String paymentUrl;
    @Column(length = 20)
    private String providerEnvironment;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
