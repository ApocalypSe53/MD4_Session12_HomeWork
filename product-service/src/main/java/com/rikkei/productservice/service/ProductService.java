package com.rikkei.productservice.service;

import com.rikkei.productservice.config.AppProperties;
import com.rikkei.productservice.config.CacheNames;
import com.rikkei.productservice.dto.ProductRequest;
import com.rikkei.productservice.dto.ProductResponse;
import com.rikkei.productservice.entity.Product;
import com.rikkei.productservice.exception.ProductNotFoundException;
import com.rikkei.productservice.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final Duration simulatedLatency;

    public ProductService(ProductRepository productRepository, AppProperties props) {
        this.productRepository = productRepository;
        this.simulatedLatency = props.db().simulatedLatency();
    }

    /**
     * Cache-aside: Spring tra Redis theo key "products::{id}" trước.
     * Hit  -> trả về ngay, không vào method.
     * Miss -> chạy method (đọc DB) rồi ghi kết quả vào Redis với TTL 30 phút.
     */
    @Cacheable(cacheNames = CacheNames.PRODUCTS, key = "#id")
    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        log.debug("Cache MISS products::{} -> querying database", id);
        simulateSlowQuery();
        return productRepository.findById(id)
                .map(ProductResponse::from)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Product product = new Product();
        apply(product, request);
        return ProductResponse.from(productRepository.save(product));
    }

    /**
     * Admin cập nhật -> xóa entry cache của sản phẩm này.
     * Lần đọc kế tiếp sẽ miss và nạp lại dữ liệu mới từ DB.
     * Evict chạy sau khi method trả về thành công (beforeInvocation = false),
     * nên nếu update lỗi thì cache cũ vẫn giữ nguyên.
     */
    @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#id")
    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        apply(product, request);
        log.debug("Updated product {} -> evicting products::{}", id, id);
        return ProductResponse.from(productRepository.saveAndFlush(product));
    }

    @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#id")
    @Transactional
    public void delete(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        productRepository.deleteById(id);
    }

    private static void apply(Product product, ProductRequest request) {
        product.setName(request.name());
        product.setDescription(request.description());
        product.setImageUrl(request.imageUrl());
        product.setPrice(request.price());
    }

    private void simulateSlowQuery() {
        if (simulatedLatency.isZero()) {
            return;
        }
        try {
            Thread.sleep(simulatedLatency);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
