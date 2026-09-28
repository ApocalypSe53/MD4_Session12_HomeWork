package com.rikkei.orderservice.event;

import com.rikkei.orderservice.entity.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Event gửi lên topic order-events (dạng JSON).
 * Đây là "hợp đồng" giữa các service: consumer tự khai báo DTO chứa các field nó cần,
 * không phụ thuộc vào class Java này.
 */
public record OrderCreatedEvent(
        String eventId,
        Long orderId,
        String customerName,
        String customerEmail,
        List<Item> items,
        BigDecimal totalAmount,
        Instant createdAt
) {
    public record Item(Long productId, String productName, int quantity, BigDecimal unitPrice) {
    }

    public static OrderCreatedEvent from(Order o) {
        List<Item> items = o.getItems().stream()
                .map(i -> new Item(i.getProductId(), i.getProductName(), i.getQuantity(), i.getUnitPrice()))
                .toList();
        return new OrderCreatedEvent(UUID.randomUUID().toString(), o.getId(), o.getCustomerName(),
                o.getCustomerEmail(), items, o.getTotalAmount(), o.getCreatedAt());
    }
}
