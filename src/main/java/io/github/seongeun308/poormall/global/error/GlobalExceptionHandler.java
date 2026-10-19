package io.github.seongeun308.poormall.global.error;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.nio.file.AccessDeniedException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String ERROR_CODE = "errorCode";
    private static final String EXCEPTION_TYPE = "exceptionType";

    /* ===== 비즈니스 예외 ===== */

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();

        LoggingEventBuilder event = warnEvent(code, e);
        if (e.getCause() != null) {
            // 감싼 예외가 있을 때만: BusinessException 부터 Caused by 원인까지 한 번에 남긴다
            event = event.setCause(e);
        }
        withContext(event, e).log("Business exception: {}", e.getMessage());

        return toResponse(code);
    }

    /* ===== 요청 검증 실패 ===== */

    // @RequestBody @Valid 실패(MethodArgumentNotValidException)와 @ModelAttribute 바인딩 실패를 함께 처리한다.
    // MethodArgumentNotValidException 은 BindException 의 하위 클래스다.
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBind(BindException e) {
        List<ErrorResponse.FieldError> errors = e.getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();

        warnEvent(GlobalErrorCode.INVALID_INPUT, e).log("Validation failed: {}", errors);
        return toResponse(errors);
    }

    // @PathVariable, @RequestParam 등에 붙인 제약 위반 (Spring 6.1+)
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException e) {
        return clientError(GlobalErrorCode.INVALID_INPUT, e,
                "Method validation failed: {}", e.getMessage());
    }

    // 타입 불일치 (/members/abc 처럼 Long 자리에 문자열)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return clientError(GlobalErrorCode.INVALID_TYPE, e,
                "Type mismatch: {}={}", e.getName(), e.getValue());
    }

    // 필수 파라미터 누락
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException e) {
        return clientError(GlobalErrorCode.MISSING_PARAMETER, e,
                "Missing parameter: {}", e.getParameterName());
    }

    // JSON 파싱 실패, enum 값 불일치 등
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
        return clientError(GlobalErrorCode.UNREADABLE_BODY, e,
                "Unreadable body: {}", e.getMessage());
    }

    /* ===== 라우팅 ===== */

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return clientError(GlobalErrorCode.METHOD_NOT_ALLOWED, e,
                "Method not supported: {}", e.getMethod());
    }

    // 존재하지 않는 URL (Spring Boot 3.2+). 봇 스캔이 많아서 debug 로 남긴다.
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        log.atDebug()
                .addKeyValue(ERROR_CODE, GlobalErrorCode.NOT_FOUND.getCode())
                .log("No resource: {}", e.getResourcePath());
        return toResponse(GlobalErrorCode.NOT_FOUND);
    }

    /* ===== 보안 (메서드 보안 @PreAuthorize 등에서 발생한 경우만 여기로 옴) ===== */

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return clientError(GlobalErrorCode.ACCESS_DENIED, e,
                "Access denied: {}", e.getMessage());
    }

    /* ===== 내부 불변식 위반(버그) ===== */

    @ExceptionHandler(InvalidArgumentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidArgument(InvalidArgumentException e) {
        withContext(errorEvent(e), e).log("Invalid argument: {}", e.getMessage());
        return toResponse(GlobalErrorCode.INTERNAL_ERROR);
    }

    @ExceptionHandler(InvalidStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidState(InvalidStateException e) {
        withContext(errorEvent(e), e).log("Invalid state: {}", e.getMessage());
        return toResponse(GlobalErrorCode.INTERNAL_ERROR);
    }

    /* ===== 그 외 전부 ===== */

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        // 전용 핸들러가 없는 Spring MVC 4xx 예외(415, 413 등): Spring 이 정한 상태를 그대로 쓴다
        if (e instanceof org.springframework.web.ErrorResponse springError
                && springError.getStatusCode().is4xxClientError()) {
            GlobalErrorCode code = GlobalErrorCode.fromStatus(springError.getStatusCode());
            warnEvent(code, e).log("Client error: {}", e.getMessage());
            return toResponse(code, springError.getStatusCode());
        }

        errorEvent(e).log("Unhandled exception: {}", e.getMessage());
        return toResponse(GlobalErrorCode.INTERNAL_ERROR);
    }

    /* ===== helper: 로그 ===== */

    /** 클라이언트 오류: WARN, 스택 트레이스 없음 */
    private LoggingEventBuilder warnEvent(ErrorCode code, Exception e) {
        return log.atWarn()
                .addKeyValue(ERROR_CODE, code.getCode())
                .addKeyValue(EXCEPTION_TYPE, e.getClass().getSimpleName());
    }

    /** 서버 오류: ERROR, 스택 트레이스 포함 */
    private LoggingEventBuilder errorEvent(Exception e) {
        return log.atError()
                .setCause(e)
                .addKeyValue(ERROR_CODE, GlobalErrorCode.INTERNAL_ERROR.getCode())
                .addKeyValue(EXCEPTION_TYPE, e.getClass().getSimpleName());
    }

    private static LoggingEventBuilder withContext(LoggingEventBuilder event, HasErrorContext e) {
        for (Map.Entry<String, Object> entry : e.getErrorContext().entrySet()) {
            event = event.addKeyValue(entry.getKey(), entry.getValue());
        }
        return event;
    }

    /** WARN 로그를 남기고 에러 코드의 상태로 응답한다 */
    private ResponseEntity<ErrorResponse> clientError(ErrorCode code, Exception e,
                                                      String format, Object... args) {
        warnEvent(code, e).log(format, args);
        return toResponse(code);
    }

    /* ===== helper: 응답 ===== */

    private ResponseEntity<ErrorResponse> toResponse(ErrorCode code) {
        return toResponse(code, code.getStatus());
    }

    private ResponseEntity<ErrorResponse> toResponse(ErrorCode code, HttpStatusCode status) {
        return ResponseEntity.status(status).body(ErrorResponse.of(code));
    }

    private ResponseEntity<ErrorResponse> toResponse(List<ErrorResponse.FieldError> errors) {
        return ResponseEntity.status(GlobalErrorCode.INVALID_INPUT.getStatus()).body(ErrorResponse.of(GlobalErrorCode.INVALID_INPUT, errors));
    }
}
