package com.huasen.common.exception;

import lombok.Getter;

/**
 * 业务异常
 * 用于在Service层抛出，由GlobalExceptionHandler统一处理
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 响应标签: SUCCESS / ERROR / FORBIDDEN / AUTH */
    private final String tag;

    public BusinessException(String tag, String message) {
        super(message);
        this.tag = tag;
    }

    public BusinessException(String tag, String message, Throwable cause) {
        super(message, cause);
        this.tag = tag;
    }
}
