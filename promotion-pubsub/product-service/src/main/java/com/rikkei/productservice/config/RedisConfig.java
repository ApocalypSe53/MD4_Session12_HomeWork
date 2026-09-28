package com.rikkei.productservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rikkei.productservice.dto.ProductView;
import com.rikkei.productservice.messaging.PromotionUpdateSubscriber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper,
                                          @Value("${app.cache.product-ttl:30m}") Duration productTtl) {
        RedisCacheConfiguration products = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(productTtl)
                .disableCachingNullValues()
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new Jackson2JsonRedisSerializer<>(objectMapper, ProductView.class)));
        return RedisCacheManager.builder(connectionFactory)
                .withCacheConfiguration(CacheNames.PRODUCTS, products)
                .build();
    }

    /** Đăng ký subscriber vào channel promotion-updates (dùng một kết nối Redis riêng cho SUBSCRIBE). */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory, PromotionUpdateSubscriber subscriber,
            @Value("${app.pubsub.channel}") String channel) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(channel));
        return container;
    }
}
