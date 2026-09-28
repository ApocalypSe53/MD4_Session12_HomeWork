package com.rikkei.productservice.config;

import com.rikkei.productservice.dto.ProductResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RedisCacheConfigTest {

    @Test
    void productsCacheHas30MinuteTtl() {
        AppProperties props = new AppProperties(
                new AppProperties.Cache(Duration.ofMinutes(30)), new AppProperties.Db(Duration.ZERO));
        RedisCacheManager manager = new RedisCacheConfig().cacheManager(mock(RedisConnectionFactory.class), props);
        manager.afterPropertiesSet();

        RedisCache cache = (RedisCache) manager.getCache(CacheNames.PRODUCTS);

        Duration ttl = cache.getCacheConfiguration().getTtlFunction().getTimeToLive(1L, null);
        assertThat(ttl).isEqualTo(Duration.ofMinutes(30));
        assertThat(cache.getCacheConfiguration().getAllowCacheNullValues()).isFalse();
    }

    @Test
    void productSerializerRoundTripsAsReadableJson() {
        RedisSerializer<ProductResponse> serializer = RedisCacheConfig.productSerializer();
        ProductResponse product = new ProductResponse(1L, "iPhone 16 Pro", "Chip A18 Pro",
                "https://cdn.example.com/iphone16pro.jpg", new BigDecimal("28990000.00"),
                Instant.parse("2026-09-28T10:15:30Z"));

        byte[] bytes = serializer.serialize(product);
        String json = new String(bytes, StandardCharsets.UTF_8);

        assertThat(json).contains("\"name\":\"iPhone 16 Pro\"")
                .contains("\"updatedAt\":\"2026-09-28T10:15:30Z\"");
        assertThat(serializer.deserialize(bytes)).isEqualTo(product);
    }
}
