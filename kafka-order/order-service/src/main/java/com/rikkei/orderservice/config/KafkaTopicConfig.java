package com.rikkei.orderservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    /** Producer sở hữu topic nên khai báo topic ở đây; KafkaAdmin tạo khi app khởi động nếu chưa có. */
    @Bean
    public NewTopic orderEventsTopic(@Value("${app.kafka.topic.order-events}") String name) {
        return TopicBuilder.name(name)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
