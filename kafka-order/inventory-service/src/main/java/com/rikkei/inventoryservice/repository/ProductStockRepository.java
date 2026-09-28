package com.rikkei.inventoryservice.repository;

import com.rikkei.inventoryservice.entity.ProductStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductStockRepository extends JpaRepository<ProductStock, Long> {

    /**
     * Trừ kho nguyên tử bằng một câu UPDATE có điều kiện: không bao giờ âm kho,
     * kể cả khi nhiều consumer cùng trừ một sản phẩm.
     * @return 1 nếu trừ thành công, 0 nếu không đủ hàng hoặc không có sản phẩm
     */
    @Modifying
    @Query("update ProductStock s set s.quantity = s.quantity - :qty "
            + "where s.productId = :productId and s.quantity >= :qty")
    int deduct(@Param("productId") Long productId, @Param("qty") int qty);
}
