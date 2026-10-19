package io.github.seongeun308.poormall.wallet.domain;

import io.github.seongeun308.poormall.global.error.InvalidArgumentException;
import io.github.seongeun308.poormall.global.error.InvalidStateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static io.github.seongeun308.poormall.wallet.domain.WalletFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Wallet")
class WalletTest {

    @Test
    @DisplayName("새 지갑의 잔액은 0이다")
    void createdWithZeroBalance() {
        assertThat(Wallet.create(10L).getBalance()).isZero();
    }

    /* =====================================================================
     * 적립
     * ===================================================================== */
    @Nested
    @DisplayName("적립(save)")
    class Save {

        @Test
        @DisplayName("적립하면 잔액이 늘고, 그 금액과 반영 후 잔액을 담은 SAVE 엔트리가 생긴다")
        void increasesBalanceAndCreatesSaveEntry() {
            Wallet wallet = wallet();

            WalletEntry entry = wallet.save(1_000, "save-1", 77L);

            assertThat(wallet.getBalance()).isEqualTo(1_000);
            assertThat(entry.getWalletId()).isEqualTo(WALLET_ID);
            assertThat(entry.getEntryType()).isEqualTo(WalletEntryType.SAVE);
            assertThat(entry.getAmount()).isEqualTo(1_000);
            assertThat(entry.getBalanceAfter()).isEqualTo(1_000);
            assertThat(entry.getIdempotencyKey()).isEqualTo("save-1");
            assertThat(entry.getPaymentId()).isEqualTo(77L);
            assertThat(entry.getReversalOf()).isNull();
            assertThat(entry.getReasonCode()).isNull();
        }

        @Test
        @DisplayName("여러 번 적립하면 잔액이 누적되고, 각 엔트리의 balanceAfter 는 그 시점 잔액이다")
        void accumulates() {
            Wallet wallet = wallet();

            WalletEntry first = wallet.save(1_000, key(), null);
            WalletEntry second = wallet.save(500, key(), null);

            assertThat(wallet.getBalance()).isEqualTo(1_500);
            assertThat(first.getBalanceAfter()).isEqualTo(1_000);
            assertThat(second.getBalanceAfter()).isEqualTo(1_500);
        }

        @ParameterizedTest(name = "amount={0}")
        @ValueSource(longs = {0, -1, -1_000})
        @DisplayName("0 이하 금액은 거절하고 잔액은 바뀌지 않는다")
        void rejectsNonPositiveAmount(long amount) {
            Wallet wallet = wallet();
            wallet.save(1_000, key(), null);

            assertThatThrownBy(() -> wallet.save(amount, key(), null))
                    .isInstanceOfSatisfying(InvalidArgumentException.class, e ->
                            assertThat(e.getErrorContext())
                                    .containsEntry("field", "amount")
                                    .containsEntry("value", amount));
            assertThat(wallet.getBalance()).isEqualTo(1_000);
        }

        @Test
        @DisplayName("잔액이 long 범위를 넘으면 거절하고 잔액은 바뀌지 않는다")
        void rejectsOverflow() {
            Wallet wallet = wallet();
            forceBalance(wallet, Long.MAX_VALUE - 10);

            assertThatThrownBy(() -> wallet.save(11, key(), null))
                    .isInstanceOf(ArithmeticException.class);
            assertThat(wallet.getBalance()).isEqualTo(Long.MAX_VALUE - 10);
        }
    }

    /* =====================================================================
     * 되돌림 (참기 취소 / 보상)
     * ===================================================================== */
    @Nested
    @DisplayName("되돌림(cancel / compensate)")
    class Reverse {

        @Test
        @DisplayName("참기 취소하면 원 금액만큼 잔액이 줄고, 원 SAVE 를 가리키는 CANCEL 엔트리가 생긴다")
        void cancel() {
            Wallet wallet = wallet();
            WalletEntry original = persisted(wallet.save(1_000, key(), 77L));
            saved(wallet, 300);

            WalletEntry cancel = wallet.cancel(original, "cancel-1", "USER_CANCEL");

            assertThat(wallet.getBalance()).isEqualTo(300);
            assertThat(cancel.getEntryType()).isEqualTo(WalletEntryType.CANCEL);
            assertThat(cancel.getAmount()).isEqualTo(-1_000);
            assertThat(cancel.getBalanceAfter()).isEqualTo(300);
            assertThat(cancel.getReversalOf()).isEqualTo(original.getId());
            assertThat(cancel.getPaymentId()).as("원 SAVE 와 같은 결제에 묶인다").isEqualTo(77L);
            assertThat(cancel.getReasonCode()).isEqualTo("USER_CANCEL");
            assertThat(cancel.getIdempotencyKey()).isEqualTo("cancel-1");
        }

        @Test
        @DisplayName("보상하면 COMPENSATION 엔트리가 생기고 잔액이 줄어든다")
        void compensate() {
            Wallet wallet = wallet();
            WalletEntry original = saved(wallet, 1_000);

            WalletEntry compensation = wallet.compensate(original, key(), "PAYMENT_FAILED");

            assertThat(wallet.getBalance()).isZero();
            assertThat(compensation.getEntryType()).isEqualTo(WalletEntryType.COMPENSATION);
            assertThat(compensation.getAmount()).isEqualTo(-1_000);
            assertThat(compensation.getReversalOf()).isEqualTo(original.getId());
        }

        @Test
        @DisplayName("되돌린 결과 잔액이 정확히 0인 경우는 허용한다 (경계값)")
        void allowsZeroBalanceAfterReversal() {
            Wallet wallet = wallet();
            WalletEntry original = saved(wallet, 1_000);

            WalletEntry cancel = wallet.cancel(original, key(), "USER_CANCEL");

            assertThat(wallet.getBalance()).isZero();
            assertThat(cancel.getBalanceAfter()).isZero();
        }

        @Test
        @DisplayName("SAVE 가 아닌 엔트리(되돌림 엔트리)는 되돌릴 수 없다")
        void rejectsNonSaveEntry() {
            Wallet wallet = wallet();
            saved(wallet, 5_000);
            WalletEntry original = saved(wallet, 1_000);
            WalletEntry cancel = persisted(wallet.cancel(original, key(), "USER_CANCEL"));
            long before = wallet.getBalance();

            assertThatThrownBy(() -> wallet.compensate(cancel, key(), "PAYMENT_FAILED"))
                    .isInstanceOfSatisfying(InvalidArgumentException.class, e ->
                            assertThat(e.getErrorContext())
                                    .containsEntry("originalEntryId", cancel.getId())
                                    .containsEntry("entryType", WalletEntryType.CANCEL));
            assertThat(wallet.getBalance()).isEqualTo(before);
        }

        @Test
        @DisplayName("다른 지갑의 엔트리는 되돌릴 수 없다")
        void rejectsEntryOfOtherWallet() {
            Wallet mine = wallet(WALLET_ID);
            Wallet other = wallet(OTHER_WALLET_ID);
            saved(mine, 1_000);
            WalletEntry othersEntry = saved(other, 1_000);

            assertThatThrownBy(() -> mine.cancel(othersEntry, key(), "USER_CANCEL"))
                    .isInstanceOfSatisfying(InvalidArgumentException.class, e ->
                            assertThat(e.getErrorContext())
                                    .containsEntry("walletId", WALLET_ID)
                                    .containsEntry("originalEntryId", othersEntry.getId())
                                    .containsEntry("originalWalletId", OTHER_WALLET_ID));
            assertThat(mine.getBalance()).isEqualTo(1_000);
        }

        @Test
        @DisplayName("원 엔트리가 null 이면 거절한다")
        void rejectsNullOriginal() {
            Wallet wallet = wallet();

            assertThatThrownBy(() -> wallet.cancel(null, key(), "USER_CANCEL"))
                    .isInstanceOfSatisfying(InvalidArgumentException.class, e ->
                            assertThat(e.getErrorContext()).containsEntry("field", "original"));
        }
    }

    /* =====================================================================
     * 잔액 음수 거절
     * ===================================================================== */
    @Nested
    @DisplayName("잔액 음수 거절")
    class NegativeBalance {

        @Test
        @DisplayName("잔액이 원장과 어긋나 되돌리면 음수가 되는 경우 InvalidStateException 으로 거절하고 잔액은 그대로다")
        void rejectsReversalThatMakesBalanceNegative() {
            Wallet wallet = wallet();
            WalletEntry original = saved(wallet, 1_000);
            forceBalance(wallet, 500);   // 원장상 1,000 이어야 하는데 캐시가 500 으로 손상됨

            assertThatThrownBy(() -> wallet.cancel(original, key(), "USER_CANCEL"))
                    .isInstanceOfSatisfying(InvalidStateException.class, e ->
                            assertThat(e.getErrorContext())
                                    .containsEntry("walletId", WALLET_ID)
                                    .containsEntry("balance", 500L)
                                    .containsEntry("delta", -1_000L));
            assertThat(wallet.getBalance()).isEqualTo(500);
        }
    }

    /* =====================================================================
     * 실패하면 잔액은 그대로
     *
     * 잔액을 먼저 바꾸고 엔트리를 만들면, 엔트리 생성이 실패했을 때
     * 잔액만 바뀐 채로 예외가 나간다. 트랜잭션이 롤백되더라도
     * 도메인 객체 자체는 원장과 어긋난 상태가 된다.
     * ===================================================================== */
    @Nested
    @DisplayName("실패하면 잔액은 그대로다")
    class AtomicOnFailure {

        @Test
        @DisplayName("멱등 키가 비어 있어 적립 엔트리 생성이 실패하면 잔액은 바뀌지 않는다")
        void saveWithBlankKey() {
            Wallet wallet = wallet();

            assertThatThrownBy(() -> wallet.save(1_000, " ", null))
                    .isInstanceOf(InvalidArgumentException.class);
            assertThat(wallet.getBalance()).isZero();
        }

        @Test
        @DisplayName("사유 코드가 너무 길어 되돌림 엔트리 생성이 실패하면 잔액은 바뀌지 않는다")
        void cancelWithTooLongReasonCode() {
            Wallet wallet = wallet();
            WalletEntry original = saved(wallet, 1_000);

            assertThatThrownBy(() -> wallet.cancel(original, key(), "R".repeat(51)))
                    .isInstanceOf(InvalidArgumentException.class);
            assertThat(wallet.getBalance()).isEqualTo(1_000);
        }
    }

    /* =====================================================================
     * 잔액 = 엔트리 합
     * ===================================================================== */
    @Nested
    @DisplayName("잔액 = 엔트리 합")
    class BalanceEqualsSumOfEntries {

        @Test
        @DisplayName("적립과 되돌림이 섞여도 잔액은 엔트리 합과 같고, balanceAfter 는 누적 합과 같다")
        void mixedScenario() {
            Wallet wallet = wallet();
            List<WalletEntry> entries = new ArrayList<>();

            WalletEntry s1 = saved(wallet, 1_000);
            entries.add(s1);
            entries.add(saved(wallet, 2_000));
            entries.add(persisted(wallet.cancel(s1, key(), "USER_CANCEL")));
            WalletEntry s3 = saved(wallet, 500);
            entries.add(s3);
            entries.add(persisted(wallet.compensate(s3, key(), "PAYMENT_FAILED")));

            assertThat(wallet.getBalance()).isEqualTo(2_000);
            assertBalanceEqualsSumOf(wallet, entries);

            long running = 0;
            for (WalletEntry entry : entries) {
                running += entry.getAmount();
                assertThat(entry.getBalanceAfter()).isEqualTo(running);
            }
        }

        @Test
        @DisplayName("무작위 순서의 적립·되돌림 500회 동안 매 단계 불변식이 유지된다")
        void randomSequence() {
            Wallet wallet = wallet();
            Random random = new Random(20261019L);   // 실패 시 재현할 수 있도록 고정 시드
            List<WalletEntry> entries = new ArrayList<>();
            List<WalletEntry> notYetReversed = new ArrayList<>();

            for (int i = 0; i < 500; i++) {
                WalletEntry entry;
                if (notYetReversed.isEmpty() || random.nextInt(3) > 0) {
                    entry = saved(wallet, 1 + random.nextInt(100_000));
                    notYetReversed.add(entry);
                } else {
                    // 한 SAVE 는 한 번만 되돌린다 (DB 의 uk_wallet_entry_reversal 과 같은 규칙)
                    WalletEntry original = notYetReversed.remove(random.nextInt(notYetReversed.size()));
                    entry = persisted(random.nextBoolean()
                            ? wallet.cancel(original, key(), "USER_CANCEL")
                            : wallet.compensate(original, key(), "PAYMENT_FAILED"));
                }
                entries.add(entry);

                assertThat(entry.getBalanceAfter()).isEqualTo(wallet.getBalance());
                assertThat(wallet.getBalance()).isNotNegative();
                assertBalanceEqualsSumOf(wallet, entries);
            }

            long unreversedSum = notYetReversed.stream().mapToLong(WalletEntry::getAmount).sum();
            assertThat(wallet.getBalance())
                    .as("잔액은 아직 되돌리지 않은 SAVE 의 합과 같다")
                    .isEqualTo(unreversedSum);
        }
    }
}