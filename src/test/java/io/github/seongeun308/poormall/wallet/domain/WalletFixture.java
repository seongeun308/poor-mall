package io.github.seongeun308.poormall.wallet.domain;

import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 도메인 단위 테스트용 픽스처.
 * 엔티티 id 는 DB 가 채우므로, 단위 테스트에서는 "저장된 것처럼" 직접 넣어 준다.
 * (WalletEntry 는 walletId 가 필수이고, 되돌림은 원 엔트리 id 를 reversal_of 로 가진다)
 */
final class WalletFixture {

    static final long WALLET_ID = 1L;
    static final long OTHER_WALLET_ID = 2L;

    private static final AtomicLong ENTRY_SEQ = new AtomicLong(1_000);
    private static final AtomicLong KEY_SEQ = new AtomicLong();

    private WalletFixture() {}

    static Wallet wallet() {
        return wallet(WALLET_ID);
    }

    static Wallet wallet(long id) {
        Wallet wallet = Wallet.create(100L + id);
        ReflectionTestUtils.setField(wallet, "id", id);
        return wallet;
    }

    /** 저장된 것처럼 엔트리에 id 를 부여한다 */
    static WalletEntry persisted(WalletEntry entry) {
        ReflectionTestUtils.setField(entry, "id", ENTRY_SEQ.incrementAndGet());
        return entry;
    }

    /** 적립하고 저장된 상태의 SAVE 엔트리를 돌려준다 */
    static WalletEntry saved(Wallet wallet, long amount) {
        return persisted(wallet.save(amount, key(), null));
    }

    /** 원장과 어긋난 잔액(데이터 손상)을 흉내 낸다 */
    static void forceBalance(Wallet wallet, long balance) {
        ReflectionTestUtils.setField(wallet, "balance", balance);
    }

    static String key() {
        return "key-" + KEY_SEQ.incrementAndGet();
    }

    /** 핵심 불변식: 잔액은 엔트리 금액의 합과 같다 */
    static void assertBalanceEqualsSumOf(Wallet wallet, List<WalletEntry> entries) {
        long sum = entries.stream().mapToLong(WalletEntry::getAmount).sum();
        assertThat(wallet.getBalance())
                .as("잔액은 엔트리 금액의 합과 같아야 한다")
                .isEqualTo(sum);
    }
}