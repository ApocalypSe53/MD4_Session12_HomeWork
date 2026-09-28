package com.rikkei.inventoryservice;

import com.rikkei.inventoryservice.entity.ProcessedOrder;
import com.rikkei.inventoryservice.repository.ProcessedOrderRepository;
import com.rikkei.inventoryservice.repository.ProductStockRepository;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "app.inventory.simulated-latency=0s"
})
@EmbeddedKafka(partitions = 3, topics = "order-events")
class InventoryServiceKafkaTest {

    @Autowired
    EmbeddedKafkaBroker broker;

    @Autowired
    ProductStockRepository stockRepository;

    @Autowired
    ProcessedOrderRepository processedOrderRepository;

    @Test
    void deductsStockOnceEvenIfEventIsDeliveredTwice() {
        int before = stock(2L);
        String event = orderEvent(101, 2, 3);

        send("101", event);
        send("101", event);   // Kafka at-least-once: cùng event đến lần 2
        // Cùng key -> cùng partition, xếp sau duplicate -> khi 102 xong thì duplicate chắc chắn đã xử lý
        send("101", orderEvent(102, 2, 1));

        awaitProcessed(102L);
        assertThat(stock(2L)).isEqualTo(before - 3 - 1);
        assertThat(processedOrderRepository.findById(101L)).get()
                .extracting(ProcessedOrder::getResult).isEqualTo(ProcessedOrder.Result.RESERVED);
    }

    @Test
    void rejectsWholeOrderWhenAnyItemIsOutOfStock() {
        int iphoneBefore = stock(1L);
        int headphoneBefore = stock(4L);

        // Món 1 đủ hàng, món 2 không đủ -> không được trừ món nào
        send("201", """
                {"orderId":201,"items":[{"productId":1,"quantity":1},{"productId":4,"quantity":999}]}""");

        awaitProcessed(201L);
        assertThat(processedOrderRepository.findById(201L)).get()
                .extracting(ProcessedOrder::getResult).isEqualTo(ProcessedOrder.Result.REJECTED);
        assertThat(stock(1L)).isEqualTo(iphoneBefore);
        assertThat(stock(4L)).isEqualTo(headphoneBefore);
    }

    private static String orderEvent(long orderId, long productId, int qty) {
        return """
                {"eventId":"e-%d","orderId":%d,"customerEmail":"a@example.com",
                 "items":[{"productId":%d,"productName":"x","quantity":%d,"unitPrice":1}]}"""
                .formatted(orderId, orderId, productId, qty);
    }

    private int stock(Long productId) {
        return stockRepository.findById(productId).orElseThrow().getQuantity();
    }

    private void awaitProcessed(Long orderId) {
        await().atMost(Duration.ofSeconds(15)).until(() -> processedOrderRepository.existsById(orderId));
    }

    private void send(String key, String json) {
        var pf = new DefaultKafkaProducerFactory<>(KafkaTestUtils.producerProps(broker),
                new StringSerializer(), new StringSerializer());
        new KafkaTemplate<>(pf).send("order-events", key, json).join();
        pf.destroy();
    }
}
