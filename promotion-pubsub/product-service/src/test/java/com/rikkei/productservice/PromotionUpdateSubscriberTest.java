package com.rikkei.productservice;

import com.rikkei.productservice.dto.ProductView;
import com.rikkei.productservice.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shop;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=file:../db/init.sql",
        "app.cache.double-delete-delay=500ms"
})
class PromotionUpdateSubscriberTest {

    static final String CHANNEL = "promotion-updates";
    static final String KEY = "products::1";

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

    @Autowired
    ProductService productService;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void setUp() {
        jdbc.sql("DELETE FROM promotions").update();
        redisTemplate.delete(KEY);
    }

    @Test
    void messageEvictsCacheWithinOneSecondAndNextReadSeesNewPrice() {
        ProductView before = productService.getById(1L);
        assertThat(before.onSale()).isFalse();
        assertThat(redisTemplate.hasKey(KEY)).isTrue();

        // promotion-service: ghi giá mới vào DB rồi publish product_id
        setPromotion(1L, "24990000");
        publish("{\"product_id\":1,\"action\":\"UPDATED\",\"published_at\":\"" + Instant.now() + "\"}");

        await().atMost(Duration.ofSeconds(1)).until(() -> !redisTemplate.hasKey(KEY));

        ProductView after = productService.getById(1L);
        assertThat(after.onSale()).isTrue();
        assertThat(after.finalPrice()).isEqualByComparingTo("24990000");
    }

    @Test
    void malformedMessageDoesNotKillTheSubscriber() {
        productService.getById(1L);

        publish("not-json");
        publish("{\"action\":\"UPDATED\"}");   // thiếu product_id
        publish("{\"product_id\":1,\"action\":\"UPDATED\"}");

        await().atMost(Duration.ofSeconds(1)).until(() -> !redisTemplate.hasKey(KEY));
    }

    /**
     * Race: một request đọc giá cũ từ DB trước khi admin commit, nhưng ghi vào cache SAU lần xóa đầu tiên.
     * Lần xóa thứ 2 (sau 500ms) phải dọn được giá cũ này.
     */
    @Test
    void delayedSecondDeleteRemovesStaleValueWrittenAfterFirstEviction() {
        productService.getById(1L);
        String staleJson = redisTemplate.opsForValue().get(KEY);

        setPromotion(1L, "24990000");
        publish("{\"product_id\":1,\"action\":\"UPDATED\"}");
        await().atMost(Duration.ofSeconds(1)).until(() -> !redisTemplate.hasKey(KEY));

        redisTemplate.opsForValue().set(KEY, staleJson);   // request chậm ghi giá cũ vào cache

        await().atMost(Duration.ofSeconds(2)).until(() -> !redisTemplate.hasKey(KEY));
        assertThat(productService.getById(1L).finalPrice()).isEqualByComparingTo("24990000");
    }

    private void setPromotion(Long productId, String salePrice) {
        jdbc.sql("""
                        INSERT INTO promotions (product_id, sale_price, active, updated_at)
                        VALUES (:pid, :price, TRUE, CURRENT_TIMESTAMP)""")
                .param("pid", productId)
                .param("price", new BigDecimal(salePrice))
                .update();
    }

    private void publish(String payload) {
        redisTemplate.convertAndSend(CHANNEL, payload);
    }
}
