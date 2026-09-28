package com.rikkei.inventoryservice.controller;

import com.rikkei.inventoryservice.entity.ProcessedOrder;
import com.rikkei.inventoryservice.entity.ProductStock;
import com.rikkei.inventoryservice.repository.ProcessedOrderRepository;
import com.rikkei.inventoryservice.repository.ProductStockRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Endpoint chỉ-đọc để quan sát kết quả trừ kho. */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final ProductStockRepository stockRepository;
    private final ProcessedOrderRepository processedOrderRepository;

    public InventoryController(ProductStockRepository stockRepository,
                               ProcessedOrderRepository processedOrderRepository) {
        this.stockRepository = stockRepository;
        this.processedOrderRepository = processedOrderRepository;
    }

    @GetMapping
    public List<ProductStock> stock() {
        return stockRepository.findAll();
    }

    @GetMapping("/processed-orders")
    public List<ProcessedOrder> processedOrders() {
        return processedOrderRepository.findAll();
    }
}
