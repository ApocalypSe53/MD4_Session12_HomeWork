package com.rikkei.promotionservice.service;

import com.rikkei.promotionservice.dto.PromotionResponse;
import com.rikkei.promotionservice.entity.Promotion;
import com.rikkei.promotionservice.event.PromotionChangedEvent;
import com.rikkei.promotionservice.event.PromotionUpdateMessage.Action;
import com.rikkei.promotionservice.repository.PromotionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
public class PromotionService {

    private final PromotionRepository promotionRepository;
    private final ApplicationEventPublisher eventPublisher;

    public PromotionService(PromotionRepository promotionRepository, ApplicationEventPublisher eventPublisher) {
        this.promotionRepository = promotionRepository;
        this.eventPublisher = eventPublisher;
    }

    /** Tạo mới hoặc đổi giá khuyến mãi của sản phẩm. */
    @Transactional
    public PromotionResponse upsert(Long productId, BigDecimal salePrice) {
        Promotion promotion = promotionRepository.findByProductId(productId)
                .orElseGet(() -> new Promotion(productId));
        promotion.setSalePrice(salePrice);
        promotion.setActive(true);
        try {
            promotion = promotionRepository.saveAndFlush(promotion);
        } catch (DataIntegrityViolationException e) {
            // FK promotions.product_id -> products.id
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: " + productId);
        }
        eventPublisher.publishEvent(new PromotionChangedEvent(productId, Action.UPDATED));
        return PromotionResponse.from(promotion);
    }

    /** Kết thúc khuyến mãi -> sản phẩm quay về giá gốc. */
    @Transactional
    public PromotionResponse end(Long productId) {
        Promotion promotion = promotionRepository.findByProductId(productId)
                .filter(Promotion::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No active promotion for product " + productId));
        promotion.setActive(false);
        promotion = promotionRepository.saveAndFlush(promotion);
        eventPublisher.publishEvent(new PromotionChangedEvent(productId, Action.ENDED));
        return PromotionResponse.from(promotion);
    }

    @Transactional(readOnly = true)
    public List<PromotionResponse> findAll() {
        return promotionRepository.findAll().stream().map(PromotionResponse::from).toList();
    }
}
