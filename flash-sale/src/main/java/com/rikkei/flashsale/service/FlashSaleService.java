package com.rikkei.flashsale.service;

import com.rikkei.flashsale.dto.ProductStatus;
import com.rikkei.flashsale.dto.PurchaseResponse;
import com.rikkei.flashsale.entity.FlashSaleOrder;
import com.rikkei.flashsale.entity.Product;
import com.rikkei.flashsale.exception.ProductNotFoundException;
import com.rikkei.flashsale.exception.SystemBusyException;
import com.rikkei.flashsale.repository.FlashSaleOrderRepository;
import com.rikkei.flashsale.repository.ProductRepository;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class FlashSaleService {

    private static final Logger log = LoggerFactory.getLogger(FlashSaleService.class);

    public static String lockKey(Long productId) {
        return "lock:product:" + productId;
    }

    private final RedissonClient redissonClient;
    private final StockService stockService;
    private final ProductRepository productRepository;
    private final FlashSaleOrderRepository orderRepository;
    private final Duration lockWaitTime;
    private final String instanceId;

    public FlashSaleService(RedissonClient redissonClient, StockService stockService,
                            ProductRepository productRepository, FlashSaleOrderRepository orderRepository,
                            @Value("${app.lock.wait-time:10s}") Duration lockWaitTime,
                            @Value("${app.instance-id:local}") String instanceId) {
        this.redissonClient = redissonClient;
        this.stockService = stockService;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.lockWaitTime = lockWaitTime;
        this.instanceId = instanceId;
    }

    /**
     * Phải giành được khóa lock:product:{id} mới được kiểm tra và trừ kho.
     *
     * Method này cố ý KHÔNG có @Transactional: transaction trừ kho (trong StockService)
     * phải commit xong TRƯỚC khi unlock. Nếu unlock trước commit, người kế tiếp giành khóa
     * vẫn đọc được tồn kho cũ -> vẫn bán lố.
     */
    public PurchaseResponse purchase(Long productId, String userId, int quantity) {
        RLock lock = redissonClient.getLock(lockKey(productId));
        boolean acquired = false;
        try {
            // Không truyền leaseTime -> Redisson bật watchdog: tự gia hạn khóa (mặc định 30s)
            // khi còn đang xử lý; nếu instance chết, watchdog dừng và khóa tự hết hạn -> không treo vĩnh viễn.
            acquired = lock.tryLock(lockWaitTime.toMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                log.warn("User {} could not acquire {} within {}", userId, lockKey(productId), lockWaitTime);
                throw new SystemBusyException();
            }
            FlashSaleOrder order = stockService.checkAndDeduct(productId, userId, quantity);
            log.info("[{}] SOLD product={} to user={} (order {})", instanceId, productId, userId, order.getId());
            return toResponse(order, "Đặt hàng thành công");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SystemBusyException();
        } finally {
            // Luôn nhả khóa, kể cả khi hết hàng hoặc lỗi DB -> không treo các request khác.
            // Chỉ unlock khi chính thread này đang giữ (tránh IllegalMonitorStateException).
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** CHỈ ĐỂ SO SÁNH: cùng nghiệp vụ nhưng không có khóa -> bán lố khi có tải đồng thời. */
    public PurchaseResponse purchaseWithoutLock(Long productId, String userId, int quantity) {
        FlashSaleOrder order = stockService.checkAndDeduct(productId, userId, quantity);
        return toResponse(order, "Đặt hàng thành công (KHÔNG khóa)");
    }

    @Transactional(readOnly = true)
    public ProductStatus status(Long productId) {
        Product p = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        return new ProductStatus(p.getId(), p.getName(), p.getStock(), orderRepository.countByProductId(productId));
    }

    /** Đặt lại tồn kho và xoá đơn cũ để chạy lại kịch bản flash sale. */
    @Transactional
    public ProductStatus reset(Long productId, int stock) {
        Product p = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        p.setStock(stock);
        orderRepository.deleteByProductId(productId);
        return new ProductStatus(p.getId(), p.getName(), stock, 0);
    }

    private PurchaseResponse toResponse(FlashSaleOrder order, String message) {
        return new PurchaseResponse(order.getId(), order.getProductId(), order.getUserId(),
                order.getQuantity(), message, instanceId);
    }
}
