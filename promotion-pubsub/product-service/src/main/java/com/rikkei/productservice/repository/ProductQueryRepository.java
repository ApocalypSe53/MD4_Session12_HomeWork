package com.rikkei.productservice.repository;

import com.rikkei.productservice.dto.ProductView;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class ProductQueryRepository {

    private final JdbcClient jdbcClient;

    public ProductQueryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** Giá gốc + giá khuyến mãi đang hiệu lực (nếu có). */
    public Optional<ProductView> findView(Long productId) {
        return jdbcClient.sql("""
                        SELECT p.id, p.name, p.base_price, pr.sale_price
                        FROM products p
                        LEFT JOIN promotions pr ON pr.product_id = p.id AND pr.active = TRUE
                        WHERE p.id = :id
                        """)
                .param("id", productId)
                .query((rs, i) -> ProductView.of(rs.getLong("id"), rs.getString("name"),
                        rs.getBigDecimal("base_price"), rs.getBigDecimal("sale_price")))
                .optional();
    }
}
