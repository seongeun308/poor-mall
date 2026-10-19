package io.github.seongeun308.poormall.global.error;

import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public class BusinessException extends RuntimeException implements HasErrorContext {
    private final ErrorCode errorCode;
    private final Map<String, Object> errorContext;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, Map.of(), null);
    }

    public BusinessException(ErrorCode errorCode, Map<String, Object> errorContext) {
        this(errorCode, errorContext, null);
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        this(errorCode, Map.of(), cause);
    }

    public BusinessException(ErrorCode errorCode, Map<String, Object> errorContext, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.errorContext = errorContext == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(errorContext));
    }
}
