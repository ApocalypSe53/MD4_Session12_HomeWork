package com.rikkei.orderservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rikkei.orderservice.dto.CreateOrderRequest;
import com.rikkei.orderservice.dto.OrderResponse;
import com.rikkei.orderservice.repository.OrderRepository;
import com.rikkei.orderservice.service.OrderService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 3, topics = "order-events")
class OrderServiceKafkaTest {

    @Autowired
    OrderService orderService;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    EmbeddedKafkaBroker broker;

    @Autowired
    ObjectMapper objectMapper;

    Consumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        Map<String, Object> props = KafkaTestUtils.consumerProps("test-group", "false", broker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        consumer = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer())
                .createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumer, "order-events");
    }

    @AfterEach
    void tearDown() {
        consumer.close();
    }

    @Test
    void placeOrderSavesToDbThenPublishesOrderCreatedEvent() throws Exception {
        OrderResponse order = orderService.placeOrder(new CreateOrderRequest("Nguyễn Văn A", "a@example.com", List.of(
                new CreateOrderRequest.Item(1L, "iPhone 16 Pro", 2, new BigDecimal("28990000")),
                new CreateOrderRequest.Item(4L, "Sony WH-1000XM5", 1, new BigDecimal("7490000")))));

        assertThat(orderRepository.existsById(order.id())).isTrue();
        assertThat(order.totalAmount()).isEqualByComparingTo("65470000");

        ConsumerRecord<String, String> record =
                KafkaTestUtils.getSingleRecord(consumer, "order-events", Duration.ofSeconds(10));

        assertThat(record.key()).isEqualTo(String.valueOf(order.id()));
        assertThat(record.headers().lastHeader("__TypeId__"))
                .as("no Java class name leaks into the message")
                .isNull();

        JsonNode json = objectMapper.readTree(record.value());
        assertThat(json.get("eventId").asText()).isNotBlank();
        assertThat(json.get("orderId").asLong()).isEqualTo(order.id());
        assertThat(json.get("customerEmail").asText()).isEqualTo("a@example.com");
        assertThat(json.get("items")).hasSize(2);
        assertThat(json.get("items").get(0).get("productId").asLong()).isEqualTo(1L);
        assertThat(json.get("items").get(0).get("quantity").asInt()).isEqualTo(2);
    }
}
