package com.rikkei.flashsale.service;

import com.rikkei.flashsale.exception.OutOfStockException;
import com.rikkei.flashsale.repository.FlashSaleOrderRepository;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:flashsale;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class FlashSaleConcurrencyTest {

    static final int BUYERS = 100;

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    FlashSaleService flashSaleService;

    @Autowired
    FlashSaleOrderRepository orderRepository;

    @Autowired
    RedissonClient redissonClient;

    @Test
    void onlyOneOf100BuyersWinsTheLastItem() throws Exception {
        flashSaleService.reset(1L, 1);

        Map<String, Long> results = runConcurrently(user -> flashSaleService.purchase(1L, user, 1));

        assertThat(results).containsEntry("SUCCESS", 1L).containsEntry("Sản phẩm đã hết hàng", 99L);
        assertThat(flashSaleService.status(1L).stock()).isZero();
        assertThat(orderRepository.countByProductId(1L)).isEqualTo(1);
    }

    @Test
    void successfulBuyersEqualStock() throws Exception {
        flashSaleService.reset(2L, 10);

        Map<String, Long> results = runConcurrently(user -> flashSaleService.purchase(2L, user, 1));

        assertThat(results).containsEntry("SUCCESS", 10L).containsEntry("Sản phẩm đã hết hàng", 90L);
        assertThat(flashSaleService.status(2L).stock()).isZero();
        assertThat(orderRepository.countByProductId(2L)).isEqualTo(10);
    }

    /** Đối chứng: cùng nghiệp vụ, bỏ khóa -> nhiều người cùng "mua được" 1 món cuối cùng. */
    @Test
    void withoutLockTheLastItemIsOversold() throws Exception {
        flashSaleService.reset(1L, 1);

        Map<String, Long> results = runConcurrently(user -> flashSaleService.purchaseWithoutLock(1L, user, 1));

        assertThat(results.getOrDefault("SUCCESS", 0L)).isGreaterThan(1L);
        assertThat(orderRepository.countByProductId(1L)).isGreaterThan(1L);
    }

    @Test
    void purchaseWaitsForLockProductKeyHeldElsewhere() throws Exception {
        flashSaleService.reset(2L, 5);
        RLock foreign = redissonClient.getLock("lock:product:2");
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        // Một "instance khác" đang giữ khóa lock:product:2
        CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> {
            foreign.lock();
            held.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                foreign.unlock();
            }
        });
        held.await();

        CompletableFuture<?> purchase = CompletableFuture.supplyAsync(() -> flashSaleService.purchase(2L, "u", 1));
        Thread.sleep(500);
        assertThat(purchase).as("must block while lock:product:2 is held").isNotDone();
        assertThat(flashSaleService.status(2L).stock()).isEqualTo(5);

        release.countDown();
        purchase.get(5, TimeUnit.SECONDS);
        holder.get(5, TimeUnit.SECONDS);
        assertThat(flashSaleService.status(2L).stock()).isEqualTo(4);
    }

    @Test
    void lockIsReleasedInFinallyEvenWhenOutOfStock() {
        flashSaleService.reset(1L, 0);

        assertThatThrownBy(() -> flashSaleService.purchase(1L, "u", 1)).isInstanceOf(OutOfStockException.class);

        assertThat(redissonClient.getLock(FlashSaleService.lockKey(1L)).isLocked()).isFalse();
    }

    /** Chạy BUYERS luồng, cùng xuất phát một lúc, gom kết quả theo loại. */
    private Map<String, Long> runConcurrently(Function<String, ?> purchase) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(BUYERS);
        CountDownLatch ready = new CountDownLatch(BUYERS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < BUYERS; i++) {
                String user = "user-" + i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        purchase.apply(user);
                        return "SUCCESS";
                    } catch (RuntimeException e) {
                        return e.getMessage();
                    }
                }));
            }
            ready.await();
            start.countDown();

            List<String> outcomes = new ArrayList<>();
            for (Future<String> f : futures) {
                outcomes.add(f.get(60, TimeUnit.SECONDS));
            }
            return outcomes.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        } finally {
            pool.shutdownNow();
        }
    }
}
