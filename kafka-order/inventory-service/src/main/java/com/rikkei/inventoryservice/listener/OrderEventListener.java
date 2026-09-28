package com.rikkei.inventoryservice.listener;

import com.rikkei.inventoryservice.event.OrderCreatedEvent;
import com.rikkei.inventoryservice.exception.InsufficientStockException;
import com.rikkei.inventoryservice.service.InventoryService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final InventoryService inventoryService;

    public OrderEventListener(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @KafkaListener(topics = "${app.kafka.topic.order-events}")
    public void onOrderCreated(ConsumerRecord<String, OrderCreatedEvent> record) {
        OrderCreatedEvent event = record.value();
        log.info("Received OrderCreatedEvent order={} from {}-{}@{}",
                event.orderId(), record.topic(), record.partition(), record.offset());
        try {
            inventoryService.reserveStock(event);
        } catch (InsufficientStockException e) {
            // Thực tế: phát InventoryRejectedEvent để order-service huỷ đơn (Saga)
            log.warn("Order {} rejected: {}", event.orderId(), e.getMessage());
            inventoryService.recordRejected(event.orderId(), e.getMessage());
        }
    }
}
