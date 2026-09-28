# product-service — Tối ưu hiển thị sản phẩm với Redis Cache

Spring Boot 3.3 + Spring Cache + Redis, áp dụng **Cache-aside pattern** cho trang chi tiết sản phẩm.

## Luồng xử lý

```
GET /api/products/{id}
   │
   ▼
@Cacheable("products", key=id) ──hit──► trả dữ liệu từ Redis (<10ms)
   │ miss
   ▼
Đọc DB (~200ms) ──► ghi vào Redis key "products::{id}", TTL 30 phút ──► trả về

PUT /api/admin/products/{id}
   │
   ▼
Cập nhật DB ──thành công──► @CacheEvict xóa "products::{id}"
                            (lần GET kế tiếp sẽ miss và nạp dữ liệu mới)
```

## Các điểm chính

| Yêu cầu | Vị trí |
|---|---|
| Cache theo `product_id` | `ProductService#getById` — `@Cacheable(cacheNames = "products", key = "#id")` |
| TTL 30 phút | `application.yml` → `app.cache.product-ttl: 30m`, áp dụng trong `RedisCacheConfig#cacheManager` |
| Serialization | Key: `StringRedisSerializer`; Value: `Jackson2JsonRedisSerializer<ProductResponse>` (JSON đọc được, hỗ trợ `Instant`) |
| Xóa cache khi admin cập nhật | `ProductService#update` / `#delete` — `@CacheEvict(cacheNames = "products", key = "#id")` |
| Redis sập không làm hỏng request | `RedisCacheConfig#errorHandler` — log cảnh báo và fallback xuống DB |

Ghi chú thiết kế:
- Cache **DTO** (`ProductResponse`) chứ không cache Entity, để không serialize proxy Hibernate.
- `disableCachingNullValues()` — không cache kết quả null; sản phẩm không tồn tại trả 404 và không bị cache.
- Evict chạy **sau** khi update thành công (mặc định `beforeInvocation = false`), nên update lỗi thì cache cũ vẫn giữ nguyên.
- H2 in-memory quá nhanh nên `app.db.simulated-latency: 200ms` giả lập độ trễ của truy vấn SQL thật. Đặt `0ms` khi dùng DB thật.

## Chạy

Yêu cầu: JDK 21, Maven, Docker.

```bash
docker compose up -d          # Redis tại localhost:6380 (6379 để dành cho Redis của session11)
mvn spring-boot:run
```

## Kiểm chứng kết quả

```bash
# Lần 1: cache miss -> đọc DB (~200ms)
curl -s -D - -o /dev/null http://localhost:8080/api/products/1 | grep X-Response-Time
# Lần 2+: cache hit -> đọc Redis (<10ms)
curl -s -D - -o /dev/null http://localhost:8080/api/products/1 | grep X-Response-Time

# Xem dữ liệu và TTL trong Redis
docker exec product-redis redis-cli GET "products::1"
docker exec product-redis redis-cli TTL "products::1"     # ~1800 giây

# Admin cập nhật -> cache bị xóa
curl -s -X PUT http://localhost:8080/api/admin/products/1 \
  -H "Content-Type: application/json" \
  -d '{"name":"iPhone 16 Pro Max","description":"Bản mới","imageUrl":"https://cdn.example.com/16pm.jpg","price":34990000}'
docker exec product-redis redis-cli EXISTS "products::1"  # 0

# Lần GET kế tiếp lại miss (~200ms) và trả dữ liệu mới
curl -s -D - http://localhost:8080/api/products/1
```

Log ứng dụng cũng in `Cache MISS products::{id} -> querying database` mỗi khi phải xuống DB.

## Test

```bash
mvn test
```

- `ProductServiceCacheTest`: đọc lần 2 không gọi repository; update xóa đúng entry và lần đọc sau thấy dữ liệu mới; update sản phẩm này không ảnh hưởng cache sản phẩm khác.
- `RedisCacheConfigTest`: cache `products` có TTL 30 phút, không cache null; serializer JSON round-trip đúng.
