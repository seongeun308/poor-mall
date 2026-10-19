package io.github.seongeun308.poormall.wallet.domain;

import io.github.seongeun308.poormall.common.entity.BaseTimeEntity;
import io.github.seongeun308.poormall.common.utils.Guard;
import io.github.seongeun308.poormall.global.error.InvalidArgumentException;
import io.github.seongeun308.poormall.global.error.InvalidStateException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "wallet")
public class Wallet extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    private Long userId;

    @Column(name = "balance", nullable = false)
    private long balance;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    private Wallet(Long userId) {
        this.userId = Guard.notNull(userId, "userId");
        this.balance = 0L;
    }

    public static Wallet create(Long userId) {
        return new Wallet(userId);
    }

    public WalletEntry save(long amount, String idempotencyKey, Long paymentId) {
        Guard.notNull(this.id, "walletId");   // 엔트리가 wallet_id 를 가리키므로 저장된 지갑만 적립할 수 있다
        Guard.positive(amount, "amount");

        // 엔트리 생성(검증)이 성공한 뒤에 잔액을 바꾼다
        long next = nextBalance(amount);
        WalletEntry entry = WalletEntry.save(this.id, amount, next, idempotencyKey, paymentId);
        this.balance = next;
        return entry;
    }

    public WalletEntry cancel(WalletEntry original, String idempotencyKey, String reasonCode) {
        return reverse(WalletEntryType.CANCEL, original, idempotencyKey, reasonCode);
    }

    public WalletEntry compensate(WalletEntry original, String idempotencyKey, String reasonCode) {
        return reverse(WalletEntryType.COMPENSATION, original, idempotencyKey, reasonCode);
    }

    private WalletEntry reverse(WalletEntryType type, WalletEntry original,
                                String idempotencyKey, String reasonCode) {
        validateReversible(original);

        long amount = -original.getAmount();
        long next = nextBalance(amount);
        // 이중 되돌림은 uk_wallet_entry_reversal(부분 유니크 인덱스)이 최종적으로 막는다.
        WalletEntry entry = WalletEntry.reversal(type, this.id, original, amount, next,
                idempotencyKey, reasonCode);
        this.balance = next;
        return entry;
    }

    private void validateReversible(WalletEntry original) {
        Guard.notNull(this.id, "walletId");
        Guard.notNull(original, "original");
        Guard.notNull(original.getId(), "originalEntryId");   // 저장되지 않은 엔트리는 reversal_of 로 가리킬 수 없다

        if (!(original.getEntryType() == WalletEntryType.SAVE)) {
            throw new InvalidArgumentException("SAVE 엔트리만 되돌릴 수 있습니다",
                    Map.of("originalEntryId", original.getId(),
                            "entryType", original.getEntryType()));
        }
        if (!original.belongsTo(this)) {
            throw new InvalidArgumentException("다른 지갑의 엔트리는 되돌릴 수 없습니다",
                    Map.of("walletId", this.id,
                            "originalEntryId", original.getId(),
                            "originalWalletId", original.getWalletId()));
        }
    }

    /** delta 를 반영한 잔액을 계산한다. 결과가 음수라면 balance 캐시가 원장과 이미 어긋난 것이다. */
    private long nextBalance(long delta) {
        long next = Math.addExact(this.balance, delta);
        if (next < 0) {
            throw new InvalidStateException("잔액이 음수가 됩니다",
                    Map.of("walletId", this.id,
                            "balance", this.balance,
                            "delta", delta));
        }
        return next;
    }
}