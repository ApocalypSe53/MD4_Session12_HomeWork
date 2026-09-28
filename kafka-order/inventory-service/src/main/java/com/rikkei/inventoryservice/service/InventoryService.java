package com.rikkei.inventoryservice.service;

import com.rikkei.inventoryservice.entity.ProcessedOrder;
import com.rikkei.inventoryservice.entity.ProcessedOrder.Result;
import com.rikkei.inventoryservice.event.OrderCreatedEvent;
import com.rikkei.inventoryservice.exception.InsufficientStockException;
import com.rikkei.inventoryservice.repository.ProcessedOrderRepository;
import com.rikkei.inventoryservice.repository.ProductStockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final ProductStockRepository stockRepository;
    private final ProcessedOrderRepository processedOrderRepository;
    private final Duration simulatedLatency;

    public InventoryService(ProductStockRepository stockRepository,
                            ProcessedOrderRepository processedOrderRepository,
                            @Value("${app.inventory.simulated-latency:0s}") Duration simulatedLatency) {
        this.stockRepository = stockRepository;
        this.processedOrderRepository = processedOrderRepository;
        this.simulatedLatency = simulatedLatency;
    }

    /**
     * Trừ kho cho toàn bộ đơn trong một transaction: thiếu một món -> rollback cả đơn.
     * Ghi processed_orders cùng transaction nên "đã trừ kho" và "đã đánh dấu xử lý" luôn đi cùng nhau.
     *
     * @return false nếu đơn này đã được xử lý trước đó (event bị gửi lại)
     */
    @Transactional
    public boolean reserveStock(OrderCreatedEvent event) {
        if (processedOrderRepository.existsById(event.orderId())) {
            log.warn("Order {} already processed, skipping duplicate event", event.orderId());
            return false;
        }
        sleep(simulatedLatency);
        for (OrderCreatedEvent.Item item : event.items()) {
            if (stockRepository.deduct(item.productId(), item.quantity()) == 0) {
                throw new InsufficientStockException(item.productId(), item.quantity());
            }
        }
        processedOrderRepository.save(new ProcessedOrder(event.orderId(), Result.RESERVED, null));
        log.info("Stock reserved for order {}: {}", event.orderId(), event.items());
        return true;
    }

    @Transactional
    public void recordRejected(Long orderId, String reason) {
        if (!processedOrderRepository.existsById(orderId)) {
            processedOrderRepository.save(new ProcessedOrder(orderId, Result.REJECTED, reason));
        }
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
