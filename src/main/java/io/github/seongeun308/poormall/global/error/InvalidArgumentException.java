package io.github.seongeun308.poormall.global.error;

import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public class InvalidArgumentException extends IllegalArgumentException implements HasErrorContext {
    private final Map<String, Object> errorContext;

    public InvalidArgumentException(String reason, Map<String, Object> errorContext) {
        super(reason);
        this.errorContext = Collections.unmodifiableMap(new LinkedHashMap<>(errorContext));
    }
}
