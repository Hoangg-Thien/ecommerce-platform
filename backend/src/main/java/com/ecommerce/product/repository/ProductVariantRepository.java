package com.ecommerce.product.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ecommerce.product.entity.ProductVariant;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    // Dashboard: sản phẩm sắp hết hàng (stock < threshold)
    @Query("SELECT pv FROM ProductVariant pv JOIN FETCH pv.product p " +
           "WHERE pv.stock > 0 AND pv.stock <= :threshold " +
           "ORDER BY pv.stock ASC")
    List<ProductVariant> findLowStockVariants(@Param("threshold") int threshold, Pageable pageable);

    // Dashboard: đếm số lượng variant hết hàng
    long countByStock(int stock);

    // Dashboard: đếm số variant còn ít hàng
    @Query("SELECT COUNT(pv) FROM ProductVariant pv WHERE pv.stock > 0 AND pv.stock <= :threshold")
    long countLowStockVariants(@Param("threshold") int threshold);
}
