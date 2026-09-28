package com.rikkei.flashsale.config;

import com.rikkei.flashsale.entity.Product;
import com.rikkei.flashsale.repository.ProductRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;

@Configuration
public class DataSeeder {

    /** Chỉ seed khi bảng trống — instance thứ 2 khởi động sau không ghi đè dữ liệu. */
    @Bean
    ApplicationRunner seedProducts(ProductRepository productRepository) {
        return args -> {
            if (productRepository.count() == 0) {
                productRepository.saveAll(List.of(
                        new Product(1L, "iPhone 16 Pro - Flash Sale 1K", new BigDecimal("1000"), 1),
                        new Product(2L, "AirPods Pro 2 - Flash Sale", new BigDecimal("990000"), 10)));
            }
        };
    }
}
