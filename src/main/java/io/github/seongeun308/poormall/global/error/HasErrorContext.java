package io.github.seongeun308.poormall.global.error;

import java.util.Map;

public interface HasErrorContext {
    Map<String, Object> getErrorContext();
}
