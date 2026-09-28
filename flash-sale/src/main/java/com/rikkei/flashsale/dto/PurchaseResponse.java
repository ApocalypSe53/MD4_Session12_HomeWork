package com.rikkei.flashsale.dto;

public record PurchaseResponse(
        Long orderId,
        Long productId,
        String userId,
        int quantity,
        String message,
        String servedBy
) {
}
