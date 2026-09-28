package com.rikkei.promotionservice.controller;

import com.rikkei.promotionservice.dto.PromotionRequest;
import com.rikkei.promotionservice.dto.PromotionResponse;
import com.rikkei.promotionservice.service.PromotionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/promotions")
public class PromotionController {

    private final PromotionService promotionService;

    public PromotionController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @PutMapping("/{productId}")
    public PromotionResponse upsert(@PathVariable Long productId, @Valid @RequestBody PromotionRequest request) {
        return promotionService.upsert(productId, request.salePrice());
    }

    @DeleteMapping("/{productId}")
    public PromotionResponse end(@PathVariable Long productId) {
        return promotionService.end(productId);
    }

    @GetMapping
    public List<PromotionResponse> findAll() {
        return promotionService.findAll();
    }
}
