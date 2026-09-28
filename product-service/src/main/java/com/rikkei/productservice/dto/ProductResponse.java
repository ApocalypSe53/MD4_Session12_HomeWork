package com.rikkei.productservice.dto;

import com.rikkei.productservice.entity.Product;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Đối tượng được lưu vào Redis. Cache DTO thay vì Entity để tránh
 * serialize proxy/lazy field của Hibernate và giữ JSON trong Redis gọn.
 */
public record ProductResponse(
        Long id,
        String name,
        String description,
        String imageUrl,
        BigDecimal price,
        Instant updatedAt
) {
    public static ProductResponse from(Product p) {
        return new ProductResponse(p.getId(), p.getName(), p.getDescription(),
                p.getImageUrl(), p.getPrice(), p.getUpdatedAt());
    }
}
