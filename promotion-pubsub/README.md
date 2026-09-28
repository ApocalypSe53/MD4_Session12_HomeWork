# promotion-pubsub — Cập nhật giá khuyến mãi thời gian thực với Redis Pub/Sub

Admin đổi giá khuyến mãi → mọi instance đang hiển thị giá nhận thông báo qua Redis channel
`promotion-updates` và xóa cache của sản phẩm đó → lần đọc kế tiếp lấy giá mới từ DB.

```
 Admin ── PUT /api/admin/promotions/1 ──► promotion-service (Publisher)
                                           1. UPDATE promotions (transaction)
                                           2. AFTER COMMIT: PUBLISH promotion-updates
                                              {"product_id":1,"action":"UPDATED","published_at":"..."}
                                                        │
                              Redis channel: promotion-updates (fan-out)
                           ┌────────────────────────────┴───────────────────────────┐
                           ▼                                                        ▼
            product-service #1 (Web, :8096)                          product-service #2 (App, :8097)
            DEL products::1  (+ xóa lần 2 sau 500ms)                 DEL products::1  (+ xóa lần 2 sau 500ms)
                           │                                                        │
            GET /api/products/1 ─► cache miss ─► DB ─► giá mới ─► ghi lại cache (TTL 30 phút)
```

| Thành phần | Port | Vai trò |
|---|---|---|
| promotion-service | 8095 | Publisher: quản lý khuyến mãi, publish `product_id` |
| product-service | 8096, 8097 | Subscriber: hiển thị giá (cache Redis), xóa cache khi nhận tin |
| Redis | 6382 | Cache + Pub/Sub |
| PostgreSQL | 5434 | DB dùng chung, schema ở [db/init.sql](db/init.sql) |

## Các điểm thiết kế

**Publisher** ([PromotionPublisher](promotion-service/src/main/java/com/rikkei/promotionservice/event/PromotionPublisher.java))
- Publish trong `@TransactionalEventListener(AFTER_COMMIT)`. Nếu publish trước khi commit, subscriber xóa
  cache rồi nạp lại ngay **giá cũ** (DB chưa commit) → cache sai tới hết TTL.
- Thông điệp chỉ chứa `product_id` (+ `action`, `published_at` để đo độ trễ). Publisher không cần biết
  subscriber cache thế nào, key ra sao.
- `convertAndSend` trả về số subscriber đã nhận → log `-> 2 subscriber(s)`.

**Subscriber** ([PromotionUpdateSubscriber](product-service/src/main/java/com/rikkei/productservice/messaging/PromotionUpdateSubscriber.java))
- `RedisMessageListenerContainer` + `MessageListener` lắng nghe `promotion-updates`,
  gọi `@CacheEvict` xóa `products::{product_id}`.
- **Delayed double delete**: xóa thêm lần 2 sau 500ms. Nếu một request đã đọc DB (giá cũ) ngay trước khi admin
  commit nhưng ghi cache *sau* lần xóa đầu, lần xóa thứ 2 sẽ dọn giá cũ đó.
- Tin nhắn hỏng (JSON sai, thiếu `product_id`) chỉ bị log và bỏ qua, listener vẫn sống.

**Giới hạn của Redis Pub/Sub:** fire-and-forget, không lưu tin. Instance nào đang tắt hoặc mất kết nối
lúc publish sẽ không nhận được tin; khi đó TTL 30 phút là lưới an toàn cuối cùng. Cần đảm bảo chắc chắn
thì dùng Redis Streams hoặc Kafka (như bài `kafka-order`).

## Chạy

Yêu cầu: JDK 21, Maven, Docker.

```bash
docker compose up -d          # Redis :6382, Postgres :5434 (tự chạy db/init.sql)
mvn package -DskipTests

java -jar promotion-service/target/promotion-service-0.0.1-SNAPSHOT.jar
PORT=8096 INSTANCE_ID=web java -jar product-service/target/product-service-0.0.1-SNAPSHOT.jar
PORT=8097 INSTANCE_ID=app java -jar product-service/target/product-service-0.0.1-SNAPSHOT.jar
```

## Thử nghiệm

```bash
curl -s localhost:8096/api/products/1        # giá gốc, lần sau lấy từ cache
curl -s localhost:8097/api/products/1

# Admin đặt giá khuyến mãi
curl -s -X PUT localhost:8095/api/admin/promotions/1 \
  -H "Content-Type: application/json" -d '{"salePrice":24990000}'

curl -s localhost:8096/api/products/1        # ngay lập tức: finalPrice = 24990000, onSale = true
curl -s localhost:8097/api/products/1

curl -s -X DELETE localhost:8095/api/admin/promotions/1   # kết thúc KM -> về giá gốc

# Theo dõi channel trực tiếp
docker exec -it promo-redis redis-cli SUBSCRIBE promotion-updates
```

## Test

```bash
mvn test     # Testcontainers bật Redis thật; DB dùng H2 nạp cùng db/init.sql
```

- `PromotionPublisherTest`: upsert → tin có `product_id`, và lúc subscriber nhận tin thì DB **đã** có giá mới
  (chứng minh publish sau commit); kết thúc KM → `ENDED`; sản phẩm không tồn tại → không publish gì.
- `PromotionUpdateSubscriberTest`: nhận tin → cache bị xóa **trong < 1s** và lần đọc sau trả giá mới;
  tin hỏng không làm chết listener; giá cũ bị ghi lại vào cache sau lần xóa đầu sẽ bị lần xóa thứ 2 dọn sạch.
