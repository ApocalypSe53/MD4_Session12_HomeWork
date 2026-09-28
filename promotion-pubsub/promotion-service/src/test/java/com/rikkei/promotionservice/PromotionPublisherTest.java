package com.rikkei.promotionservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rikkei.promotionservice.service.PromotionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.GenericContainer;

import java.math.BigDecimal;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shop;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=file:../db/init.sql"
})
class PromotionPublisherTest {

    /**
     * Singleton container: sống tới khi JVM tắt (Ryuk dọn sau). Nếu để JUnit dừng container sau mỗi class,
     * Spring context (còn cache giữa các test) sẽ kẹt khi shutdown vì listener Pub/Sub cố reconnect.
     */
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        redis.start();
    }

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    /** Ghi lại thông điệp nhận được + giá mà DB đang có ĐÚNG LÚC nhận (để chứng minh đã commit). */
    record Received(String payload, BigDecimal salePriceInDbAtReceipt) {
    }

    static final BlockingQueue<Received> inbox = new LinkedBlockingQueue<>();

    @TestConfiguration
    static class TestSubscriber {
        @Bean
        RedisMessageListenerContainer testListener(RedisConnectionFactory cf, JdbcClient jdbc) {
            RedisMessageListenerContainer c = new RedisMessageListenerContainer();
            c.setConnectionFactory(cf);
            c.addMessageListener((message, pattern) -> {
                BigDecimal price = jdbc.sql("SELECT sale_price FROM promotions WHERE product_id = 1")
                        .query(BigDecimal.class).optional().orElse(null);
                inbox.add(new Received(new String(message.getBody()), price));
            }, new ChannelTopic("promotion-updates"));
            return c;
        }
    }

    @Autowired
    PromotionService promotionService;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jdbc.sql("DELETE FROM promotions").update();
        inbox.clear();
    }

    @Test
    void upsertPublishesProductIdOnlyAfterCommit() throws Exception {
        promotionService.upsert(1L, new BigDecimal("24990000"));

        Received received = inbox.poll(2, TimeUnit.SECONDS);
        assertThat(received).isNotNull();

        JsonNode json = objectMapper.readTree(received.payload());
        assertThat(json.get("product_id").asLong()).isEqualTo(1L);
        assertThat(json.get("action").asText()).isEqualTo("UPDATED");
        assertThat(json.hasNonNull("published_at")).isTrue();
        // Subscriber đọc DB ngay khi nhận tin đã thấy giá mới -> tin được gửi sau commit
        assertThat(received.salePriceInDbAtReceipt()).isEqualByComparingTo("24990000");
    }

    @Test
    void endingPromotionPublishesEnded() throws Exception {
        promotionService.upsert(1L, new BigDecimal("24990000"));
        inbox.poll(2, TimeUnit.SECONDS);

        promotionService.end(1L);

        Received received = inbox.poll(2, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(objectMapper.readTree(received.payload()).get("action").asText()).isEqualTo("ENDED");
    }

    @Test
    void unknownProductIsRejectedAndNothingIsPublished() throws Exception {
        assertThatThrownBy(() -> promotionService.upsert(999L, new BigDecimal("1000")))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(inbox.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }
}
