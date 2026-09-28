package com.rikkei.productservice.dto;

import java.math.BigDecimal;

/** Giá hiển thị của sản phẩm — đối tượng được cache trong Redis. */
public record ProductView(
        Long id,
        String name,
        BigDecimal basePrice,
        BigDecimal salePrice,     // null nếu không có khuyến mãi
        BigDecimal finalPrice,
        boolean onSale
) {
    public static ProductView of(Long id, String name, BigDecimal basePrice, BigDecimal salePrice) {
        boolean onSale = salePrice != null;
        return new ProductView(id, name, basePrice, salePrice, onSale ? salePrice : basePrice, onSale);
    }
}
