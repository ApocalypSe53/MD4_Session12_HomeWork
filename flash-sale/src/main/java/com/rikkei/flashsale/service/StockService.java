package com.rikkei.flashsale.service;

import com.rikkei.flashsale.entity.FlashSaleOrder;
import com.rikkei.flashsale.entity.Product;
import com.rikkei.flashsale.exception.OutOfStockException;
import com.rikkei.flashsale.exception.ProductNotFoundException;
import com.rikkei.flashsale.repository.FlashSaleOrderRepository;
import com.rikkei.flashsale.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * Nghiệp vụ "kiểm tra tồn kho -> trừ kho -> tạo đơn" kiểu read-check-write.
 * Bản thân nó KHÔNG an toàn khi chạy song song; FlashSaleService bọc nó trong distributed lock.
 */
@Service
public class StockService {

    private final ProductRepository productRepository;
    private final FlashSaleOrderRepository orderRepository;
    private final Duration processingDelay;

    public StockService(ProductRepository productRepository, FlashSaleOrderRepository orderRepository,
                        @Value("${app.flash-sale.processing-delay:0ms}") Duration processingDelay) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.processingDelay = processingDelay;
    }

    @Transactional
    public FlashSaleOrder checkAndDeduct(Long productId, String userId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        if (product.getStock() < quantity) {
            throw new OutOfStockException();
        }

        sleep(processingDelay);

        product.setStock(product.getStock() - quantity);
        return orderRepository.save(new FlashSaleOrder(productId, userId, quantity));
    }

    private static void sleep(Duration d) {
        if (d.isZero()) {
            return;
        }
        try {
            Thread.sleep(d);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
