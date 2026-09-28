package com.rikkei.promotionservice.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

@Component
public class PromotionPublisher {

    private static final Logger log = LoggerFactory.getLogger(PromotionPublisher.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String channel;

    public PromotionPublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                              @Value("${app.pubsub.channel}") String channel) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.channel = channel;
    }

    /**
     * Chỉ publish SAU KHI giá mới đã commit. Nếu publish trước, subscriber có thể xóa cache
     * rồi nạp lại ngay giá cũ (do DB chưa commit) -> cache giữ giá cũ tới hết TTL.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(PromotionChangedEvent event) throws JsonProcessingException {
        var message = new PromotionUpdateMessage(event.productId(), event.action(), Instant.now());
        Long receivers = redisTemplate.convertAndSend(channel, objectMapper.writeValueAsString(message));
        log.info("PUBLISH {} product_id={} action={} -> {} subscriber(s)",
                channel, event.productId(), event.action(), receivers);
    }
}
