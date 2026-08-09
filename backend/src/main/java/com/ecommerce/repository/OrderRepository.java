package com.ecommerce.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ecommerce.entity.Order;
import com.ecommerce.enums.OrderStatus;
import com.ecommerce.enums.PaymentMethod;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    Page<Order> findByUserId(Long userId, Pageable pageable);
    
    @EntityGraph(attributePaths = {"payment"})
    List<Order> findByStatusAndPaymentMethodAndCreateAtBefore(
        OrderStatus status,
        PaymentMethod paymentMethod,
        LocalDateTime createAt);
}
