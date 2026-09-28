package com.rikkei.promotionservice.dto;

import com.rikkei.promotionservice.entity.Promotion;

import java.math.BigDecimal;
import java.time.Instant;

public record PromotionResponse(Long id, Long productId, BigDecimal salePrice, boolean active, Instant updatedAt) {

    public static PromotionResponse from(Promotion p) {
        return new PromotionResponse(p.getId(), p.getProductId(), p.getSalePrice(), p.isActive(), p.getUpdatedAt());
    }
}
