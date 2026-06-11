package com.huasen.common.exception;

import com.huasen.common.dto.HuasenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 对应Node.js: handleRequestError中间件
 * 将BusinessException按tag路由到对应的响应格式
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<HuasenResponse> handleBusinessException(BusinessException ex) {
        log.warn("业务异常: tag={}, msg={}", ex.getTag(), ex.getMessage());

        return switch (ex.getTag()) {
            case "SUCCESS" -> HuasenResponse.success(null, ex.getMessage());
            case "FORBIDDEN" -> HuasenResponse.forbidden(ex.getMessage());
            case "AUTH" -> HuasenResponse.auth(ex.getMessage());
            default -> HuasenResponse.error(ex.getMessage());
        };
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<HuasenResponse> handleException(Exception ex) {
        log.error("未知异常: {}", ex.getMessage(), ex);
        return HuasenResponse.error("发生未知错误");
    }
}
