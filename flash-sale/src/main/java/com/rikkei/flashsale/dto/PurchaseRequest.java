package com.rikkei.flashsale.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record PurchaseRequest(
        @NotBlank String userId,
        @Min(1) @Max(5) int quantity
) {
}
