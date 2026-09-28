package com.rikkei.emailservice.service;

import com.rikkei.emailservice.event.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.stream.Collectors;

/** Giả lập gửi email xác nhận đơn hàng (thay bằng JavaMailSender khi có SMTP thật). */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final Duration simulatedLatency;

    public EmailService(@Value("${app.email.simulated-latency:0s}") Duration simulatedLatency) {
        this.simulatedLatency = simulatedLatency;
    }

    public void sendOrderConfirmation(OrderCreatedEvent event) {
        String lines = event.items().stream()
                .map(i -> "  - %s x%d @ %s".formatted(i.productName(), i.quantity(), i.unitPrice()))
                .collect(Collectors.joining("\n"));
        String body = """
                Xin chào %s,
                Đơn hàng #%d của bạn đã được tiếp nhận:
                %s
                Tổng cộng: %s VND""".formatted(event.customerName(), event.orderId(), lines, event.totalAmount());

        sleep(simulatedLatency);
        log.info("EMAIL SENT to={} subject=\"Xác nhận đơn hàng #{}\"\n{}",
                event.customerEmail(), event.orderId(), body);
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
