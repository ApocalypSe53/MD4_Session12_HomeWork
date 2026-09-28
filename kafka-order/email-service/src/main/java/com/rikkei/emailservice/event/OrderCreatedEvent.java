package com.rikkei.emailservice.event;

import java.math.BigDecimal;
import java.util.List;

/**
 * Bản DTO riêng của email-service — chỉ khai báo các field cần để soạn mail.
 * Field thừa trong JSON bị bỏ qua, nên order-service thêm field mới không làm vỡ service này.
 */
public record OrderCreatedEvent(
        String eventId,
        Long orderId,
        String customerName,
        String customerEmail,
        List<Item> items,
        BigDecimal totalAmount
) {
    public record Item(String productName, int quantity, BigDecimal unitPrice) {
    }
}
