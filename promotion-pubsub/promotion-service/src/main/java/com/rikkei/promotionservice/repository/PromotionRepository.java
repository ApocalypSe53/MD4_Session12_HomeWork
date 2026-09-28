package com.rikkei.promotionservice.repository;

import com.rikkei.promotionservice.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    Optional<Promotion> findByProductId(Long productId);
}
