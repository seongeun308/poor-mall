package io.github.seongeun308.poormall.global.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import static org.springframework.http.HttpStatus.*;

@AllArgsConstructor
@Getter
public enum GlobalErrorCode implements ErrorCode {
    INVALID_INPUT(BAD_REQUEST, "C001", "잘못된 입력입니다."),
    INVALID_TYPE(BAD_REQUEST, "C002", "요청 값의 타입이 올바르지 않습니다."),
    MISSING_PARAMETER(BAD_REQUEST, "C003", "필수 파라미터가 누락되었습니다."),
    UNREADABLE_BODY(BAD_REQUEST, "C004", "요청 본문을 읽을 수 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "C005", "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C006", "지원하지 않는 HTTP 메서드입니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "C007", "접근 권한이 없습니다."),
    CONTENT_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "C008", "요청 크기가 허용 범위를 초과했습니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "C009", "지원하지 않는 콘텐츠 타입입니다."),
    CLIENT_ERROR(HttpStatus.BAD_REQUEST, "C010", "잘못된 요청입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C999", "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    public static GlobalErrorCode fromStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 403 -> ACCESS_DENIED;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 413 -> CONTENT_TOO_LARGE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            default  -> CLIENT_ERROR;
        };
    }
}
