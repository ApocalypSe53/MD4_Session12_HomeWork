# MD4 — Session 12 Homework

Bốn bài tập về Redis và Kafka trong kiến trúc microservice, viết bằng Spring Boot 3.3 và Java 21.
Mỗi thư mục là một project độc lập, có `docker-compose.yml`, README riêng và test tự động.

| # | Bài tập | Thư mục | Công nghệ chính |
|---|---|---|---|
| 1 | Tối ưu hóa hiển thị sản phẩm với Redis Cache | [product-service](product-service/) | Spring Cache, Cache-aside, TTL 30 phút, `@CacheEvict` |
| 2 | Xử lý đơn hàng bất đồng bộ với Kafka | [kafka-order](kafka-order/) | Producer/Consumer, 2 consumer group độc lập |
| 3 | Chống "bán lố" bằng Redis Distributed Lock | [flash-sale](flash-sale/) | Redisson `RLock`, `lock:product:{id}`, unlock trong `finally` |
| 4 | Cập nhật giá khuyến mãi thời gian thực với Redis Pub/Sub | [promotion-pubsub](promotion-pubsub/) | `convertAndSend`, `RedisMessageListenerContainer` |

## Kết quả đã kiểm chứng

| Bài | Yêu cầu | Kết quả đo được |
|---|---|---|
| 1 | Lần đầu ~200ms (DB), sau đó <10ms (Redis) | 235ms → 2.8–3.5ms; TTL 1799s; cập nhật sản phẩm thì cache bị xóa |
| 2 | Đặt hàng kết thúc ngay khi gửi vào Kafka | `POST /api/orders` trả về trong 5–7ms; email (2s) và trừ kho (1s) chạy nền, độc lập |
| 3 | Chỉ số người mua bằng tồn kho thành công | 100 request / 2 instance: tồn 1 → 1 thành công, 99 "Sản phẩm đã hết hàng"; tồn 10 → 10 thành công |
| 4 | Giá mới cập nhật toàn hệ thống < 1 giây | Cả 2 instance thấy giá mới sau 97–214ms; subscriber nhận tin 3–4ms sau khi publish |

## Yêu cầu chung

- JDK 21, Maven 3.9+
- Docker (Redis, Kafka, PostgreSQL chạy bằng `docker compose up -d` trong từng thư mục)

Mỗi project dùng port riêng nên có thể chạy song song:

| Project | App | Hạ tầng |
|---|---|---|
| product-service | 8080 | Redis 6380 |
| kafka-order | 8081–8083 | Kafka 9092 |
| flash-sale | 8091, 8092 | Redis 6381, Postgres 5433 |
| promotion-pubsub | 8095–8097 | Redis 6382, Postgres 5434 |

```bash
cd <project>
docker compose up -d
mvn test          # bài 3, 4 dùng Testcontainers; bài 2 dùng Embedded Kafka
```
