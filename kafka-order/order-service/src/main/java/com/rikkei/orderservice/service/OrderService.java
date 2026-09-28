package com.rikkei.orderservice.service;

import com.rikkei.orderservice.dto.CreateOrderRequest;
import com.rikkei.orderservice.dto.OrderResponse;
import com.rikkei.orderservice.entity.Order;
import com.rikkei.orderservice.entity.OrderItem;
import com.rikkei.orderservice.event.OrderCreatedEvent;
import com.rikkei.orderservice.repository.OrderRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(OrderRepository orderRepository, ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Chỉ lưu đơn và phát event. Trừ kho, gửi mail... do các service khác
     * tự xử lý khi nhận event từ Kafka — khách không phải chờ.
     */
    @Transactional
    public OrderResponse placeOrder(CreateOrderRequest request) {
        List<OrderItem> items = request.items().stream()
                .map(i -> new OrderItem(i.productId(), i.productName(), i.quantity(), i.unitPrice()))
                .toList();
        Order order = orderRepository.save(new Order(request.customerName(), request.customerEmail(), items));

        // OrderEventPublisher nhận event này sau khi commit rồi mới gửi Kafka
        eventPublisher.publishEvent(OrderCreatedEvent.from(order));
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getById(Long id) {
        return orderRepository.findById(id)
                .map(OrderResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id));
    }
}
