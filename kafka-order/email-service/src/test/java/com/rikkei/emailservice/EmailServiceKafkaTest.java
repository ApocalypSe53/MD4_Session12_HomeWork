package com.rikkei.emailservice;

import com.rikkei.emailservice.event.OrderCreatedEvent;
import com.rikkei.emailservice.service.EmailService;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "app.email.simulated-latency=0s"
})
@EmbeddedKafka(partitions = 3, topics = "order-events")
class EmailServiceKafkaTest {

    @Autowired
    EmbeddedKafkaBroker broker;

    @SpyBean
    EmailService emailService;

    @Test
    void consumesOrderCreatedEventAndSendsEmail() {
        // JSON đúng như order-service gửi, có cả field email-service không khai báo (createdAt, productId)
        String json = """
                {"eventId":"e-1","orderId":42,"customerName":"Nguyễn Văn A","customerEmail":"a@example.com",
                 "items":[{"productId":1,"productName":"iPhone 16 Pro","quantity":2,"unitPrice":28990000}],
                 "totalAmount":57980000,"createdAt":1790000000.000000000}""";
        send("42", json);

        ArgumentCaptor<OrderCreatedEvent> captor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(emailService, timeout(15_000)).sendOrderConfirmation(captor.capture());

        OrderCreatedEvent event = captor.getValue();
        assertThat(event.orderId()).isEqualTo(42L);
        assertThat(event.customerEmail()).isEqualTo("a@example.com");
        assertThat(event.items()).singleElement()
                .satisfies(i -> assertThat(i.productName()).isEqualTo("iPhone 16 Pro"));
    }

    private void send(String key, String json) {
        var pf = new DefaultKafkaProducerFactory<>(KafkaTestUtils.producerProps(broker),
                new StringSerializer(), new StringSerializer());
        new KafkaTemplate<>(pf).send("order-events", key, json).join();
        pf.destroy();
    }
}
