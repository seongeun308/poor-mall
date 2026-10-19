package io.github.seongeun308.poormall.wallet.error;

import io.github.seongeun308.poormall.global.error.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum WalletErrorCode implements ErrorCode {

    INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "W001", "지갑 잔액은 음수가 될 수 없습니다."),

    INVALID_ENTRY_SHAPE(HttpStatus.BAD_REQUEST, "WE001", "엔트리 타입과 금액, 원본 엔트리의 형식이 올바르지 않습니다."),
    INVALID_REVERSAL_TYPE(HttpStatus.BAD_REQUEST, "WE002", "올바르지 않은 되돌림 유형입니다."),
    INVALID_REVERSAL_AMOUNT(HttpStatus.BAD_REQUEST, "WE003", "되돌림 금액은 원본 엔트리 금액과 부호가 반대여야 합니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
