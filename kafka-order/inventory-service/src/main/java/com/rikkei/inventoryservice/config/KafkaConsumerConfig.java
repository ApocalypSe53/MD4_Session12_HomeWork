package com.rikkei.inventoryservice.config;

import com.rikkei.inventoryservice.exception.InsufficientStockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    /** Lỗi tạm thời (DB timeout...) -> thử lại 3 lần, cách nhau 1s; hết lượt thì log và bỏ qua. */
    @Bean
    public CommonErrorHandler errorHandler() {
        DefaultErrorHandler handler = new DefaultErrorHandler(
                (record, ex) -> log.error("Giving up on record {}-{}@{} after retries: {}",
                        record.topic(), record.partition(), record.offset(), ex.getMessage()),
                new FixedBackOff(1000L, 3));
        // Hết hàng là lỗi nghiệp vụ, thử lại cũng không khác -> không retry
        handler.addNotRetryableExceptions(InsufficientStockException.class);
        return handler;
    }
}
