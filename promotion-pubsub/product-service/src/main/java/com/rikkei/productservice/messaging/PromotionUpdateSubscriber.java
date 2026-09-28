package com.rikkei.productservice.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rikkei.productservice.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

@Component
public class PromotionUpdateSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(PromotionUpdateSubscriber.class);

    private final ProductService productService;
    private final ObjectMapper objectMapper;
    private final TaskScheduler taskScheduler;
    private final Duration doubleDeleteDelay;
    private final String instanceId;

    public PromotionUpdateSubscriber(ProductService productService, ObjectMapper objectMapper,
                                     TaskScheduler taskScheduler,
                                     @Value("${app.cache.double-delete-delay:0ms}") Duration doubleDeleteDelay,
                                     @Value("${app.instance-id:product}") String instanceId) {
        this.productService = productService;
        this.objectMapper = objectMapper;
        this.taskScheduler = taskScheduler;
        this.doubleDeleteDelay = doubleDeleteDelay;
        this.instanceId = instanceId;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        PromotionUpdateMessage update;
        try {
            update = objectMapper.readValue(message.getBody(), PromotionUpdateMessage.class);
        } catch (IOException e) {
            // Tin nhắn hỏng không được làm chết listener
            log.warn("Ignoring malformed message on {}: {}", new String(message.getChannel()), e.getMessage());
            return;
        }
        if (update.productId() == null) {
            log.warn("Ignoring message without product_id: {}", new String(message.getBody()));
            return;
        }

        productService.evict(update.productId());
        long latencyMs = update.publishedAt() == null ? -1
                : Duration.between(update.publishedAt(), Instant.now()).toMillis();
        log.info("[{}] RECEIVED promotion {} product_id={} -> evicted products::{} ({} ms after publish)",
                instanceId, update.action(), update.productId(), update.productId(), latencyMs);

        if (!doubleDeleteDelay.isZero()) {
            taskScheduler.schedule(() -> productService.evict(update.productId()),
                    Instant.now().plus(doubleDeleteDelay));
        }
    }
}
