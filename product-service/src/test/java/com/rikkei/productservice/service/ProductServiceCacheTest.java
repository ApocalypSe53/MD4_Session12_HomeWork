package com.rikkei.productservice.service;

import com.rikkei.productservice.config.CacheNames;
import com.rikkei.productservice.dto.ProductRequest;
import com.rikkei.productservice.dto.ProductResponse;
import com.rikkei.productservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cache.CacheManager;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Kiểm tra hành vi cache-aside / evict của service.
 * Dùng cache in-memory ("simple") để test chạy được không cần Redis.
 */
@SpringBootTest(properties = {
        "spring.cache.type=simple",
        "app.db.simulated-latency=0ms"
})
class ProductServiceCacheTest {

    @Autowired
    ProductService productService;

    @Autowired
    CacheManager cacheManager;

    @SpyBean
    ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        cacheManager.getCache(CacheNames.PRODUCTS).clear();
        clearInvocations(productRepository);
    }

    @Test
    void secondReadIsServedFromCache() {
        ProductResponse first = productService.getById(1L);
        ProductResponse second = productService.getById(1L);

        assertThat(second).isEqualTo(first);
        verify(productRepository, times(1)).findById(1L);
        assertThat(cacheManager.getCache(CacheNames.PRODUCTS).get(1L)).isNotNull();
    }

    @Test
    void updateEvictsCacheSoNextReadSeesNewData() {
        productService.getById(2L);
        assertThat(cacheManager.getCache(CacheNames.PRODUCTS).get(2L)).isNotNull();

        productService.update(2L, new ProductRequest(
                "Galaxy S25 Ultra", "Mô tả mới", "https://cdn.example.com/s25u.jpg", new BigDecimal("30990000")));

        assertThat(cacheManager.getCache(CacheNames.PRODUCTS).get(2L)).isNull();

        clearInvocations(productRepository);
        ProductResponse afterUpdate = productService.getById(2L);
        assertThat(afterUpdate.name()).isEqualTo("Galaxy S25 Ultra");
        verify(productRepository, times(1)).findById(2L);
    }

    @Test
    void updateOnlyEvictsTheUpdatedProduct() {
        productService.getById(3L);
        productService.getById(4L);

        productService.update(3L, new ProductRequest("MacBook Air M4", null, null, new BigDecimal("34990000")));

        assertThat(cacheManager.getCache(CacheNames.PRODUCTS).get(3L)).isNull();
        assertThat(cacheManager.getCache(CacheNames.PRODUCTS).get(4L)).isNotNull();
    }
}
