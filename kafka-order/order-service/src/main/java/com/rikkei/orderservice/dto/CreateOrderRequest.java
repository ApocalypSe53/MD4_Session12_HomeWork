package com.rikkei.orderservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record CreateOrderRequest(
        @NotBlank String customerName,
        @NotBlank @Email String customerEmail,
        @NotEmpty List<@Valid Item> items
) {
    public record Item(
            @NotNull Long productId,
            @NotBlank String productName,
            @Min(1) int quantity,
            @NotNull @DecimalMin("0") BigDecimal unitPrice
    ) {
    }
}
