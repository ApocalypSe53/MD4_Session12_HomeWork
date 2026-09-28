package com.rikkei.flashsale.dto;

public record ProductStatus(Long productId, String name, int stock, long ordersPlaced) {
}
