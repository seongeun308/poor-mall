package io.github.seongeun308.poormall.wallet.domain;

import io.github.seongeun308.poormall.common.utils.Guard;
import io.github.seongeun308.poormall.global.error.InvalidArgumentException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Immutable
@Table(name = "wallet_entry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalletEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wallet_id", nullable = false, updatable = false)
    private Long walletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20, updatable = false)
    private WalletEntryType entryType;

    /** 부호 있는 금액: SAVE 는 +, 되돌림은 - */
    @Column(name = "amount", nullable = false, updatable = false)
    private long amount;

    /** 반영 직후 잔액 스냅샷 (감사·디버깅용) */
    @Column(name = "balance_after", nullable = false, updatable = false)
    private long balanceAfter;

    /** 같은 요청이 두 번 와도 한 번만 반영 */
    @Column(name = "idempotency_key", nullable = false, length = 100, unique = true, updatable = false)
    private String idempotencyKey;

    @Column(name = "reversal_of", updatable = false)
    private Long reversalOf; // 되돌림이 가리키는 원 SAVE 엔트리 ID

    @Column(name = "payment_id", updatable = false)
    private Long paymentId; // 3단계(V2) 연동용

    /** USER_CANCEL, PAYMENT_FAILED, RECON_MISSING_PG ... */
    @Column(name = "reason_code", length = 50, updatable = false)
    private String reasonCode;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    private WalletEntry(Long walletId, WalletEntryType entryType, long amount, long balanceAfter,
                       String idempotencyKey, Long reversalOf, Long paymentId, String reasonCode) {
        this.walletId = Guard.notNull(walletId, "walletId");
        this.entryType = Guard.notNull(entryType, "entryType");
        this.amount = amount;
        this.balanceAfter = Guard.notNegative(balanceAfter, "balanceAfter");
        this.idempotencyKey = Guard.notBlank(idempotencyKey, "idempotencyKey", 100);
        this.reversalOf = reversalOf;
        this.paymentId = paymentId;
        this.reasonCode = Guard.maxLength(reasonCode, "reasonCode", 50);

        validateShape(entryType, amount, reversalOf);
    }

    static WalletEntry save(Long walletId, long amount, long balanceAfter,
                            String idempotencyKey, Long paymentId) {
        return new WalletEntry(walletId, WalletEntryType.SAVE, amount, balanceAfter,
                idempotencyKey, null, paymentId, null);
    }

    static WalletEntry reversal(WalletEntryType type, Long walletId, WalletEntry original,
                                long amount, long balanceAfter, String idempotencyKey, String reasonCode) {
        if (!type.isReversal()) {
            throw new InvalidArgumentException("되돌림 유형이 아닙니다",
                    Map.of("entryType", type));
        }
        if (amount != -original.getAmount()) {
            throw new InvalidArgumentException("되돌림 금액은 원 SAVE 금액과 같아야 합니다",
                    Map.of("amount", amount,
                            "expectedAmount", -original.getAmount(),
                            "originalEntryId", original.getId()));
        }
        // 되돌림도 원 SAVE 와 같은 결제에 묶어 둔다 (idx_wallet_entry_payment 로 함께 조회)
        return new WalletEntry(walletId, type, amount, balanceAfter,
                idempotencyKey, original.getId(), original.getPaymentId(), reasonCode);
    }

    private void validateShape(WalletEntryType entryType, long amount, Long reversalOf) {
        if (!isValidShape(entryType, amount, reversalOf)) {
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("entryType", entryType);
            context.put("amount", amount);
            context.put("reversalOf", reversalOf);   // SAVE 면 null 이라 Map.of 를 쓸 수 없다
            throw new InvalidArgumentException("엔트리 형태가 올바르지 않습니다", context);
        }
    }

    private boolean isValidShape(WalletEntryType entryType, Long amount, Long reversalOf) {
        return switch (entryType) {
            case SAVE -> amount > 0 && reversalOf == null;
            case CANCEL, COMPENSATION -> amount < 0 && reversalOf != null;
        };
    }
}
