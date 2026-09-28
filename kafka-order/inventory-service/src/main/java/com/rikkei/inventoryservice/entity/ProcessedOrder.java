package com.rikkei.inventoryservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Ghi nhận đơn đã xử lý. Kafka đảm bảo at-least-once nên một event có thể đến nhiều lần;
 * bảng này giúp không trừ kho hai lần cho cùng một đơn.
 */
@Entity
@Table(name = "processed_orders")
public class ProcessedOrder {

    public enum Result { RESERVED, REJECTED }

    @Id
    @Column(name = "order_id")
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Result result;

    private String reason;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedOrder() {
    }

    public ProcessedOrder(Long orderId, Result result, String reason) {
        this.orderId = orderId;
        this.result = result;
        this.reason = reason;
        this.processedAt = Instant.now();
    }

    public Long getOrderId() { return orderId; }
    public Result getResult() { return result; }
    public String getReason() { return reason; }
    public Instant getProcessedAt() { return processedAt; }
}
