package com.rikkei.flashsale.controller;

import com.rikkei.flashsale.dto.ProductStatus;
import com.rikkei.flashsale.dto.PurchaseRequest;
import com.rikkei.flashsale.dto.PurchaseResponse;
import com.rikkei.flashsale.service.FlashSaleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/flash-sale/products/{id}")
@Validated
public class FlashSaleController {

    private final FlashSaleService flashSaleService;

    public FlashSaleController(FlashSaleService flashSaleService) {
        this.flashSaleService = flashSaleService;
    }

    @PostMapping("/purchase")
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseResponse purchase(@PathVariable Long id, @Valid @RequestBody PurchaseRequest request) {
        return flashSaleService.purchase(id, request.userId(), request.quantity());
    }

    /** Endpoint so sánh, không dùng khóa — để thấy hiện tượng bán lố. */
    @PostMapping("/purchase-unsafe")
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseResponse purchaseUnsafe(@PathVariable Long id, @Valid @RequestBody PurchaseRequest request) {
        return flashSaleService.purchaseWithoutLock(id, request.userId(), request.quantity());
    }

    @GetMapping
    public ProductStatus status(@PathVariable Long id) {
        return flashSaleService.status(id);
    }

    @PostMapping("/reset")
    public ProductStatus reset(@PathVariable Long id, @RequestParam @Min(0) int stock) {
        return flashSaleService.reset(id, stock);
    }
}
