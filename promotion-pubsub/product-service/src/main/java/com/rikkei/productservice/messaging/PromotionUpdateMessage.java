package com.rikkei.productservice.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/** Bản DTO riêng của product-service cho thông điệp trên channel promotion-updates. */
public record PromotionUpdateMessage(
        @JsonProperty("product_id") Long productId,
        String action,
        @JsonProperty("published_at") Instant publishedAt
) {
}
