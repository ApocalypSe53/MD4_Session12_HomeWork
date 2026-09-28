package com.rikkei.flashsale.repository;

import com.rikkei.flashsale.entity.FlashSaleOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlashSaleOrderRepository extends JpaRepository<FlashSaleOrder, Long> {

    long countByProductId(Long productId);

    void deleteByProductId(Long productId);
}
