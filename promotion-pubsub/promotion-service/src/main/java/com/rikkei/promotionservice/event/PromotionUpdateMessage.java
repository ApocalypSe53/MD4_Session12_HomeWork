package com.rikkei.promotionservice.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Thông điệp gửi lên channel promotion-updates, ví dụ:
 * {"product_id":1,"action":"UPDATED","published_at":"2026-09-28T06:40:00.123Z"}
 */
public record PromotionUpdateMessage(
        @JsonProperty("product_id") Long productId,
        Action action,
        @JsonProperty("published_at") Instant publishedAt
) {
    public enum Action { UPDATED, ENDED }
}
