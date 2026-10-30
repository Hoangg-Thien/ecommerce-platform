package com.ecommerce.promotion.repository;

import com.ecommerce.promotion.entity.PromotionProgram;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromotionProgramRepository extends JpaRepository<PromotionProgram, Long> {
}
