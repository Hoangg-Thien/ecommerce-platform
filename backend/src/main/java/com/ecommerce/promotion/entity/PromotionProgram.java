package com.ecommerce.promotion.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import com.ecommerce.promotion.enums.PromotionStatus;

@Entity
@Table(name = "promotion_programs")
@Getter
@Setter
@NoArgsConstructor
public class PromotionProgram {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;
    @Column(columnDefinition = "text")
    private String description;
    @Column(name = "start_date", nullable = false)
    private Instant startDate;
    @Column(name = "end_date", nullable = false)
    private Instant endDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PromotionStatus status = PromotionStatus.ACTIVE;
}
