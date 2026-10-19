package io.github.seongeun308.poormall.global.error;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.nio.file.AccessDeniedException;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /* ===== 비즈니스 예외 ===== */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        log.atWarn()
                .addKeyValue("errorCode", code.getCode())
                .log(e.getMessage());   // detail 메시지는 로그에만
        return toResponse(code);
    }

    /* ===== 요청 검증 실패 ===== */

    // @RequestBody @Valid 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldError> errors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(
                        fe.getField(),
                        String.valueOf(fe.getRejectedValue()),
                        fe.getDefaultMessage()))
                .toList();
        log.warn("Validation failed: {}", errors);
        return toResponse(errors);
    }

    // @ModelAttribute 바인딩 실패
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBind(BindException e) {
        List<ErrorResponse.FieldError> errors = e.getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(
                        fe.getField(),
                        String.valueOf(fe.getRejectedValue()),
                        fe.getDefaultMessage()))
                .toList();
        log.warn("Binding failed: {}", errors);
        return toResponse(errors);
    }

    // @PathVariable, @RequestParam 등에 붙인 제약 위반 (Spring 6.1+)
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException e) {
        log.warn("Method validation failed: {}", e.getMessage());
        return toResponse(GlobalErrorCode.INVALID_INPUT);
    }

    // 타입 불일치 (/members/abc 처럼 Long 자리에 문자열)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("Type mismatch: {}={}", e.getName(), e.getValue());
        return toResponse(GlobalErrorCode.INVALID_TYPE);
    }

    // 필수 파라미터 누락
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("Missing parameter: {}", e.getParameterName());
        return toResponse(GlobalErrorCode.MISSING_PARAMETER);
    }

    // JSON 파싱 실패, enum 값 불일치 등
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("Unreadable body: {}", e.getMessage());
        return toResponse(GlobalErrorCode.UNREADABLE_BODY);
    }

    /* ===== 라우팅 ===== */

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("Method not supported: {}", e.getMethod());
        return toResponse(GlobalErrorCode.METHOD_NOT_ALLOWED);
    }

    // 존재하지 않는 URL (Spring Boot 3.2+)
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        log.debug("No resource: {}", e.getResourcePath());   // 봇 스캔이 많아서 debug
        return toResponse(GlobalErrorCode.NOT_FOUND);
    }

    /* ===== 보안 (메서드 보안 @PreAuthorize 등에서 발생한 경우만 여기로 옴) ===== */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        log.warn("Access denied: {}", e.getMessage());
        return toResponse(GlobalErrorCode.ACCESS_DENIED);
    }

    /* ===== 그 외 전부 ===== */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);   // 스택트레이스 포함
        return toResponse(GlobalErrorCode.INTERNAL_ERROR);
    }

    /* ===== helper ===== */
    private ResponseEntity<ErrorResponse> toResponse(ErrorCode code) {
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code));
    }

    private ResponseEntity<ErrorResponse> toResponse(List<ErrorResponse.FieldError> errors) {
        return ResponseEntity.status(GlobalErrorCode.INVALID_INPUT.getStatus()).body(ErrorResponse.of(GlobalErrorCode.INVALID_INPUT, errors));
    }
}