package com.ecommerce.product.search;

import com.ecommerce.product.entity.Product;
import com.ecommerce.product.search.SearchTextUtils;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;

public final class ProductSpecifications {
    private ProductSpecifications() {}

    public static Specification<Product> hasCategory(Long categoryId) {
        return (root, query, cb) -> categoryId == null
                ? cb.conjunction()
                : cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Product> matchesKeyword(String keyword) {
        return (root, query, cb) -> {
            String q = SearchTextUtils.normalize(keyword);
            if (q.isEmpty()) return cb.conjunction();

            Predicate[] tokens = Arrays.stream(q.split(" "))
                    .limit(SearchTextUtils.MAX_TOKENS)
                    .map(t -> cb.like(root.<String>get("searchName"),
                            "%" + SearchTextUtils.escapeLike(t) + "%", '\\'))
                    .toArray(Predicate[]::new);

            // Không order trong câu COUNT
            boolean countQuery = Long.class.equals(query.getResultType())
                    || long.class.equals(query.getResultType());
            if (!countQuery) {
                Expression<Integer> rank = cb.<Integer>selectCase()
                        .when(cb.equal(root.get("searchName"), q), 3)
                        .when(cb.like(root.<String>get("searchName"),
                                SearchTextUtils.escapeLike(q) + "%", '\\'), 2)
                        .otherwise(1);
                query.orderBy(cb.desc(rank), cb.asc(root.get("id")));
            }
            return cb.and(tokens);
        };
    }
}
