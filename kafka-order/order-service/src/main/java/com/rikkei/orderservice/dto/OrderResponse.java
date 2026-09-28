package com.rikkei.orderservice.dto;

import com.rikkei.orderservice.entity.Order;
import com.rikkei.orderservice.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        String customerName,
        String customerEmail,
        List<Item> items,
        BigDecimal totalAmount,
        OrderStatus status,
        Instant createdAt
) {
    public record Item(Long productId, String productName, int quantity, BigDecimal unitPrice) {
    }

    public static OrderResponse from(Order o) {
        List<Item> items = o.getItems().stream()
                .map(i -> new Item(i.getProductId(), i.getProductName(), i.getQuantity(), i.getUnitPrice()))
                .toList();
        return new OrderResponse(o.getId(), o.getCustomerName(), o.getCustomerEmail(), items,
                o.getTotalAmount(), o.getStatus(), o.getCreatedAt());
    }
}
