package io.github.seongeun308.poormall.wallet.domain;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum WalletEntryType {
    SAVE("결제 승인으로 적립"),
    CANCEL("숙려 기간 내 사용자의 참기 취소"),
    COMPENSATION("승인했던 결제가 실패·취소로 확정됨 (대사 결과 포함)");

    private final String description;

    public boolean isReversal() {
        return this == CANCEL || this == COMPENSATION;
    }
}
