package com.rikkei.orderservice.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;
    private final String topic;

    public OrderEventPublisher(KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate,
                               @Value("${app.kafka.topic.order-events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    /**
     * Chỉ gửi SAU KHI transaction lưu đơn đã commit: không bao giờ phát event
     * cho một đơn hàng bị rollback.
     *
     * send() là bất đồng bộ — trả về ngay, không chờ broker ack, nên request
     * đặt hàng kết thúc ngay. Key = orderId để mọi event của cùng một đơn
     * vào cùng partition (giữ thứ tự).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(OrderCreatedEvent event) {
        kafkaTemplate.send(topic, String.valueOf(event.orderId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish OrderCreatedEvent for order {}: {}",
                                event.orderId(), ex.getMessage());
                        return;
                    }
                    var meta = result.getRecordMetadata();
                    log.info("Published OrderCreatedEvent order={} -> {}-{}@{}",
                            event.orderId(), meta.topic(), meta.partition(), meta.offset());
                });
    }
}
