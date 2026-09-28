package com.rikkei.promotionservice.event;

/** Event nội bộ trong JVM; PromotionPublisher nhận sau khi transaction commit. */
public record PromotionChangedEvent(Long productId, PromotionUpdateMessage.Action action) {
}
