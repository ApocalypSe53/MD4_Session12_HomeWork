package com.rikkei.flashsale.exception;

public class OutOfStockException extends RuntimeException {

    public OutOfStockException() {
        super("Sản phẩm đã hết hàng");
    }
}
