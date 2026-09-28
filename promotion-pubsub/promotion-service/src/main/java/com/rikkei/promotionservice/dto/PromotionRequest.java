package com.rikkei.promotionservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PromotionRequest(@NotNull @DecimalMin("0") BigDecimal salePrice) {
}
