package io.github.seongeun308.poormall.common.utils;

import io.github.seongeun308.poormall.global.error.InvalidArgumentException;

import java.util.Map;

public final class Guard {
    private Guard() {}

    public static <T> T notNull(T obj, String fieldName) {
        if (obj == null) {
            throw new InvalidArgumentException("값이 null 일 수 없습니다", Map.of("field", fieldName));
        }
        return obj;
    }

    public static String notBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidArgumentException("값이 비어 있을 수 없습니다", Map.of("field", fieldName));
        }
        return value;
    }

    /** 필수 문자열: 비어 있지 않고 최대 길이 이하 */
    public static String notBlank(String value, String fieldName, int maxLength) {
        return maxLength(notBlank(value, fieldName), fieldName, maxLength);
    }

    /** 선택 문자열: null 이면 통과, 값이 있으면 최대 길이 이하 */
    public static String maxLength(String value, String fieldName, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new InvalidArgumentException("최대 길이를 초과했습니다",
                    Map.of("field", fieldName, "length", value.length(), "maxLength", maxLength));
        }
        return value;
    }

    public static long positive(long value, String fieldName) {
        if (value <= 0) {
            throw new InvalidArgumentException("값은 양수여야 합니다",
                    Map.of("field", fieldName, "value", value));
        }
        return value;
    }

    public static long notNegative(long value, String fieldName) {
        if (value < 0) {
            throw new InvalidArgumentException("값은 음수일 수 없습니다",
                    Map.of("field", fieldName, "value", value));
        }
        return value;
    }
}
