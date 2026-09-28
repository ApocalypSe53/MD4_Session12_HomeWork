package com.rikkei.emailservice.config;

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

    /** Lỗi tạm thời (SMTP timeout...) -> thử lại 3 lần, cách nhau 1s; hết lượt thì log và bỏ qua. */
    @Bean
    public CommonErrorHandler errorHandler() {
        return new DefaultErrorHandler(
                (record, ex) -> log.error("Giving up on record {}-{}@{} after retries: {}",
                        record.topic(), record.partition(), record.offset(), ex.getMessage()),
                new FixedBackOff(1000L, 3));
    }
}
