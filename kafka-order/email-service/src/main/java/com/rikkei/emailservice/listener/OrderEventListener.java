package com.rikkei.emailservice.listener;

import com.rikkei.emailservice.event.OrderCreatedEvent;
import com.rikkei.emailservice.service.EmailService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final EmailService emailService;

    public OrderEventListener(EmailService emailService) {
        this.emailService = emailService;
    }

    @KafkaListener(topics = "${app.kafka.topic.order-events}")
    public void onOrderCreated(ConsumerRecord<String, OrderCreatedEvent> record) {
        OrderCreatedEvent event = record.value();
        log.info("Received OrderCreatedEvent order={} from {}-{}@{}",
                event.orderId(), record.topic(), record.partition(), record.offset());
        emailService.sendOrderConfirmation(event);
    }
}
