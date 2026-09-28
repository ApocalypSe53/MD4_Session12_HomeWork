# flash-sale — Chống "bán lố" bằng Redisson Distributed Lock

100 người cùng bấm mua món hàng cuối cùng → chỉ 1 người thành công, 99 người nhận **"Sản phẩm đã hết hàng"**.

## Vấn đề

Nghiệp vụ mua hàng là *read → check → write*:

```
đọc stock (=1) ──► stock >= 1 ? ──► xử lý (tính KM, payment...) ──► stock = stock - 1, tạo đơn
```

Không có khóa, 100 request cùng đọc thấy `stock = 1` trước khi ai kịp trừ → cả nhóm đều "mua được".
Khóa `synchronized`/`ReentrantLock` chỉ có tác dụng trong **một** JVM; chạy 2+ instance sau load balancer
thì vẫn bán lố. Cần khóa **phân tán** đặt trên Redis mà mọi instance cùng thấy.

## Giải pháp

```
Request ─► FlashSaleService.purchase()
             │
             ├─ lock = redisson.getLock("lock:product:{id}")
             ├─ tryLock(waitTime = 10s)          ── quá hạn ─► 503 "Hệ thống đang bận"
             │     │ giành được khóa
             │     ▼
             │  StockService.checkAndDeduct()   (@Transactional)
             │     ├─ stock < qty ─► 409 "Sản phẩm đã hết hàng"
             │     └─ trừ kho + tạo đơn ─► COMMIT ─► 201
             │
             └─ finally: if (isHeldByCurrentThread) unlock()
```

Các điểm quan trọng trong [FlashSaleService.java](src/main/java/com/rikkei/flashsale/service/FlashSaleService.java):

- **Key** `lock:product:{id}`: khóa theo từng sản phẩm, nên mua sản phẩm A không chặn người mua sản phẩm B.
- **Unlock trong `finally`**: dù hết hàng, lỗi DB hay exception bất kỳ, khóa luôn được nhả.
  Chỉ unlock khi `isHeldByCurrentThread()` để không nhả nhầm khóa của người khác.
- **Commit trước, unlock sau**: `purchase()` cố ý *không* có `@Transactional`; transaction nằm trong
  `StockService`, nên đã commit xong khi quay về `finally`. Nếu unlock trước commit, người kế tiếp
  giành được khóa vẫn đọc thấy tồn kho cũ và bán lố.
- **Watchdog**: gọi `tryLock(waitTime)` không truyền `leaseTime` → Redisson tự gia hạn khóa khi xử lý lâu.
  Nếu instance chết giữa chừng, watchdog dừng và khóa tự hết hạn sau 30s, không treo hệ thống.
- **`tryLock` có thời gian chờ** thay vì `lock()`: request không bao giờ bị treo vô hạn.

`Product` cố ý không có `@Version` hay `UPDATE ... WHERE stock >= ?` để chứng minh khóa phân tán là thứ
duy nhất chặn bán lố. Hệ thống thật nên kết hợp thêm một trong hai cách đó làm lớp bảo vệ cuối ở DB.

## Chạy

Yêu cầu: JDK 21, Maven, Docker.

```bash
docker compose up -d        # Redis :6381, PostgreSQL :5433
mvn package -DskipTests

# 2 instance dùng chung DB + Redis (mô phỏng chạy sau load balancer)
PORT=8091 INSTANCE_ID=instance-A java -jar target/flash-sale-0.0.1-SNAPSHOT.jar
PORT=8092 INSTANCE_ID=instance-B java -jar target/flash-sale-0.0.1-SNAPSHOT.jar
```

| API | Mô tả |
|---|---|
| `POST /api/flash-sale/products/{id}/purchase` `{"userId":"u1","quantity":1}` | Mua **có khóa** → 201 / 409 hết hàng / 503 bận |
| `POST /api/flash-sale/products/{id}/purchase-unsafe` | Đối chứng, **không khóa** |
| `GET /api/flash-sale/products/{id}` | Tồn kho + số đơn đã tạo |
| `POST /api/flash-sale/products/{id}/reset?stock=1` | Đặt lại kịch bản |

Sản phẩm mẫu: `1` iPhone 16 Pro (tồn 1), `2` AirPods Pro 2 (tồn 10).

## Kết quả đo (100 request đồng thời, chia đều 2 instance)

| Kịch bản | Thành công | "Sản phẩm đã hết hàng" | Tồn kho cuối | Số đơn |
|---|---|---|---|---|
| Có khóa, tồn 1 | **1** | 99 | 0 | 1 |
| Có khóa, tồn 10 | **10** (A: 2, B: 8) | 90 | 0 | 10 |
| Không khóa, tồn 1 | **2** ❌ bán lố | 98 | 0 | 2 |

Với khóa, hai instance khác JVM vẫn không bán lố vì cùng tranh một key trên Redis.

## Test

```bash
mvn test    # Testcontainers tự bật Redis thật; DB dùng H2
```

[FlashSaleConcurrencyTest](src/test/java/com/rikkei/flashsale/service/FlashSaleConcurrencyTest.java), 100 luồng xuất phát cùng lúc:

- Tồn 1 → đúng 1 thành công, 99 "Sản phẩm đã hết hàng".
- Tồn 10 → đúng 10 thành công.
- Không khóa → nhiều hơn 1 đơn thành công (chứng minh race condition có thật).
- Khi `lock:product:2` bị giữ ở nơi khác, `purchase` phải chờ, không trừ kho; nhả khóa thì mới chạy.
- Hết hàng (exception) → khóa vẫn được nhả (`isLocked() == false`).

> Testcontainers được nâng lên 1.21.4 vì bản 1.19.x mặc định của Spring Boot 3.3 dùng Docker API cũ,
> bị Docker Engine 29+ từ chối.
