package com.rikkei.inventoryservice.event;

import java.util.List;

/** Bản DTO riêng của inventory-service — chỉ cần mã đơn và danh sách (sản phẩm, số lượng). */
public record OrderCreatedEvent(Long orderId, List<Item> items) {

    public record Item(Long productId, int quantity) {
    }
}
