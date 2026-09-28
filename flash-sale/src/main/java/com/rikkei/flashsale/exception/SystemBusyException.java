package com.rikkei.flashsale.exception;

public class SystemBusyException extends RuntimeException {

    public SystemBusyException() {
        super("Hệ thống đang bận, vui lòng thử lại");
    }
}
