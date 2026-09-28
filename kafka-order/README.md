# kafka-order — Xử lý đơn hàng bất đồng bộ với Kafka

Tách nghiệp vụ "đặt hàng" thành 3 service giao tiếp qua Kafka topic `order-events`.

```
                    POST /api/orders
                          │
                 ┌────────▼────────┐
                 │  order-service  │  1. lưu đơn vào DB (transaction)
                 │   (Producer)    │  2. sau commit -> send OrderCreatedEvent (async)
                 └────────┬────────┘  3. trả 201 ngay (~5ms)
                          │ key = orderId
              ┌───────────▼───────────┐
              │ topic: order-events   │  3 partitions
              └─────┬───────────┬─────┘
    group-id =      │           │      group-id =
    email-service   │           │      inventory-service
          ┌─────────▼──┐     ┌──▼──────────────┐
          │email-service│    │inventory-service│
          │ gửi mail    │    │ trừ kho          │
          └─────────────┘    └──────────────────┘
```

Hai consumer **khác group-id** nên mỗi service nhận **đầy đủ mọi event** và xử lý độc lập:
email chậm hay lỗi không ảnh hưởng trừ kho, và ngược lại.

| Service | Port | Vai trò |
|---|---|---|
| order-service | 8081 | Producer: lưu đơn, phát `OrderCreatedEvent` |
| email-service | 8082 | Consumer (`email-service`): gửi mail xác nhận (giả lập 2s) |
| inventory-service | 8083 | Consumer (`inventory-service`): trừ kho (giả lập 1s) |
| Kafka | 9092 | `apache/kafka:3.8.0`, KRaft single-node |

## Các điểm thiết kế

**Producer (order-service)**
- `OrderService#placeOrder` lưu đơn rồi `publishEvent(...)`; `OrderEventPublisher` nghe bằng
  `@TransactionalEventListener(AFTER_COMMIT)` → chỉ gửi Kafka khi đơn **đã commit**, không có event "ma" cho đơn bị rollback.
- `kafkaTemplate.send()` bất đồng bộ — request không chờ broker ack. Kết quả gửi được log trong `whenComplete`.
- Key = `orderId` → mọi event của một đơn vào cùng partition, giữ thứ tự.
- `acks=all` + `enable.idempotence=true`: không mất/không trùng khi producer retry.
  `max.block.ms=3000`: Kafka sập thì request không bị treo quá 3s.
- `spring.json.add.type.headers=false`: message chỉ là JSON thuần, không mang tên class Java.

**Consumers**
- Mỗi service tự khai báo `OrderCreatedEvent` của riêng mình, chỉ gồm field cần dùng
  (email: tên, email, món hàng; inventory: `orderId` + `productId/quantity`). Field thừa bị bỏ qua →
  order-service thêm field mới không làm vỡ consumer.
- `ErrorHandlingDeserializer`: message JSON hỏng không làm consumer kẹt vòng lặp vô hạn.
- `DefaultErrorHandler`: lỗi tạm thời thử lại 3 lần (cách 1s) rồi log và bỏ qua.
- `listener.concurrency: 3` = số partition → 3 đơn ở 3 partition khác nhau được xử lý song song.

**inventory-service**
- Trừ kho bằng một câu `UPDATE ... SET quantity = quantity - :qty WHERE quantity >= :qty` → nguyên tử, không bao giờ âm kho.
- Cả đơn trong một transaction: thiếu một món → rollback, không trừ món nào; đơn được ghi `REJECTED`
  (lỗi nghiệp vụ, không retry).
- **Idempotent**: Kafka đảm bảo *at-least-once*, một event có thể đến 2 lần. Bảng `processed_orders`
  (ghi cùng transaction với trừ kho) giúp bỏ qua event trùng.

**Giới hạn (ngoài phạm vi bài):**
- Nếu app chết đúng lúc giữa "commit DB" và "gửi Kafka", event bị mất. Cách giải quyết chuẩn là *Transactional Outbox*.
- Hết hàng thì hiện chỉ ghi `REJECTED`. Thực tế nên phát `InventoryRejectedEvent` để order-service huỷ đơn (*Saga*).

## Chạy

Yêu cầu: JDK 21, Maven, Docker.

```bash
docker compose up -d                     # Kafka tại localhost:9092
mvn clean install -DskipTests

# mỗi lệnh 1 terminal
java -jar order-service/target/order-service-0.0.1-SNAPSHOT.jar
java -jar email-service/target/email-service-0.0.1-SNAPSHOT.jar
java -jar inventory-service/target/inventory-service-0.0.1-SNAPSHOT.jar
```

## Thử nghiệm

```bash
curl -s -w "\n%{time_total}s\n" -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerName":"Nguyen Van A","customerEmail":"a@example.com",
       "items":[{"productId":1,"productName":"iPhone 16 Pro","quantity":2,"unitPrice":28990000}]}'
# -> 201 trong vài ms, dù email mất 2s và trừ kho mất 1s

curl -s http://localhost:8083/api/inventory                    # tồn kho
curl -s http://localhost:8083/api/inventory/processed-orders   # RESERVED / REJECTED

# Xem event trên topic và offset của từng consumer group
docker exec order-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic order-events --from-beginning --property print.key=true
docker exec order-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --describe --all-groups
```

> Git Bash trên Windows: thêm `MSYS_NO_PATHCONV=1` trước `docker exec ... /opt/...`.

Kho mẫu (inventory-service): iPhone 16 Pro 100, Galaxy S25 50, MacBook Air M3 20, Sony WH-1000XM5 5.

## Test

```bash
mvn test     # dùng Embedded Kafka, không cần Docker
```

- `OrderServiceKafkaTest`: đặt đơn → đơn có trong DB và event JSON xuất hiện trên topic (key = orderId, không có header `__TypeId__`).
- `EmailServiceKafkaTest`: JSON có field thừa vẫn được nhận và gửi mail.
- `InventoryServiceKafkaTest`: event trùng chỉ trừ kho một lần; đơn thiếu hàng bị `REJECTED` và không trừ món nào.
