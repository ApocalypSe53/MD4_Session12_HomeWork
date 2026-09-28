package com.rikkei.productservice.service;

import com.rikkei.productservice.config.CacheNames;
import com.rikkei.productservice.dto.ProductView;
import com.rikkei.productservice.repository.ProductQueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductQueryRepository productQueryRepository;

    public ProductService(ProductQueryRepository productQueryRepository) {
        this.productQueryRepository = productQueryRepository;
    }

    /** Cache-aside: hit -> Redis; miss -> đọc giá từ DB rồi ghi cache "products::{id}". */
    @Cacheable(cacheNames = CacheNames.PRODUCTS, key = "#id")
    public ProductView getById(Long id) {
        log.debug("Cache MISS products::{} -> querying database", id);
        return productQueryRepository.findView(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: " + id));
    }

    @CacheEvict(cacheNames = CacheNames.PRODUCTS, key = "#id")
    public void evict(Long id) {
        log.debug("Evicted products::{}", id);
    }
}
