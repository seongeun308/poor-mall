package io.github.seongeun308.poormall.wallet.domain;

import io.github.seongeun308.poormall.global.error.InvalidArgumentException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.seongeun308.poormall.wallet.domain.WalletFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WalletEntry 팩토리(package-private)를 직접 호출해 엔티티 수준 검증을 확인한다.
 * Wallet 을 거치면 막히는 잘못된 입력이, 팩토리를 직접 호출해도 막히는지 보는 테스트다.
 */
@DisplayName("WalletEntry")
class WalletEntryTest {

    /* =====================================================================
     * 금액 검증
     * ===================================================================== */
    @Nested
    @DisplayName("금액 검증")
    class AmountValidation {

        @ParameterizedTest(name = "amount={0}")
        @ValueSource(longs = {0, -1})
        @DisplayName("SAVE 엔트리의 금액이 양수가 아니면 형태 검증에서 거절한다")
        void saveMustBePositive(long amount) {
            assertThatThrownBy(() -> WalletEntry.save(WALLET_ID, amount, 0, key(), null))
                    .isInstanceOfSatisfying(InvalidArgumentException.class, e ->
                            assertThat(e.getErrorContext())
                                    .containsEntry("entryType", WalletEntryType.SAVE)
                                    .containsEntry("amount", amount));
        }

        @Test
        @DisplayName("되돌림 금액이 원 SAVE 금액과 다르면 거절한다")
        void reversalAmountMustMatchOriginal() {
            Wallet wallet = wallet();
            WalletEntry original = saved(wallet, 1_000);

            assertThatThrownBy(() -> WalletEntry.reversal(WalletEntryType.CANCEL, WALLET_ID, original,
                    -999, 1, key(), "USER_CANCEL"))
                    .isInstanceOfSatisfying(InvalidArgumentException.class, e ->
                            assertThat(e.getErrorContext())
                                    .containsEntry("amount", -999L)
                                    .containsEntry("expectedAmount", -1_000L)
                                    .containsEntry("originalEntryId", original.getId()));
        }

        @Test
        @DisplayName("되돌림 금액의 부호가 반대(양수)여도 거절한다")
        void reversalAmountMustBeNegative() {
            Wallet wallet = wallet();
            WalletEntry original = saved(wallet, 1_000);

            assertThatThrownBy(() -> WalletEntry.reversal(WalletEntryType.CANCEL, WALLET_ID, original,
                    1_000, 2_000, key(), "USER_CANCEL"))
                    .isInstanceOf(InvalidArgumentException.class);
        }

        @Test
        @DisplayName("반영 후 잔액(balanceAfter)이 음수면 거절한다")
        void balanceAfterMustNotBeNegative() {
            assertThatThrownBy(() -> WalletEntry.save(WALLET_ID, 1_000, -1, key(), null))
                    .isInstanceOfSatisfying(InvalidArgumentException.class, e ->
                            assertThat(e.getErrorContext())
                                    .containsEntry("field", "balanceAfter")
                                    .containsEntry("value", -1L));
        }
    }

    /* =====================================================================
     * 되돌림 유형
     * ===================================================================== */
    @Nested
    @DisplayName("되돌림 유형")
    class ReversalType {

        @Test
        @DisplayName("SAVE 유형으로 되돌림 엔트리를 만들 수 없다")
        void saveTypeIsNotReversal() {
            Wallet wallet = wallet();
            WalletEntry original = saved(wallet, 1_000);

            assertThatThrownBy(() -> WalletEntry.reversal(WalletEntryType.SAVE, WALLET_ID, original,
                    -1_000, 0, key(), "USER_CANCEL"))
                    .isInstanceOfSatisfying(InvalidArgumentException.class, e ->
                            assertThat(e.getErrorContext())
                                    .containsEntry("entryType", WalletEntryType.SAVE));
        }

        @ParameterizedTest
        @EnumSource(value = WalletEntryType.class, names = {"CANCEL", "COMPENSATION"})
        @DisplayName("CANCEL 과 COMPENSATION 은 되돌림 유형이다")
        void reversalTypes(WalletEntryType type) {
            assertThat(type.isReversal()).isTrue();
        }

        @Test
        @DisplayName("SAVE 는 되돌림 유형이 아니다")
        void saveIsNotReversalType() {
            assertThat(WalletEntryType.SAVE.isReversal()).isFalse();
        }
    }

    /* =====================================================================
     * 필수 값과 길이 (DDL 제약과 같은 규칙)
     * ===================================================================== */
    @Nested
    @DisplayName("필수 값과 길이")
    class RequiredAndLength {

        @Test
        @DisplayName("walletId 가 없으면 거절한다")
        void walletIdRequired() {
            assertThatThrownBy(() -> WalletEntry.save(null, 1_000, 1_000, key(), null))
                    .isInstanceOfSatisfying(InvalidArgumentException.class, e ->
                            assertThat(e.getErrorContext()).containsEntry("field", "walletId"));
        }

        @ParameterizedTest(name = "idempotencyKey=\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\t"})
        @DisplayName("멱등 키가 null 이거나 비어 있으면 거절한다")
        void idempotencyKeyRequired(String idempotencyKey) {
            assertThatThrownBy(() -> WalletEntry.save(WALLET_ID, 1_000, 1_000, idempotencyKey, null))
                    .isInstanceOfSatisfying(InvalidArgumentException.class, e ->
                            assertThat(e.getErrorContext()).containsEntry("field", "idempotencyKey"));
        }

        @Test
        @DisplayName("멱등 키는 100자까지 허용하고 101자부터 거절한다")
        void idempotencyKeyMaxLength() {
            assertThatNoException().isThrownBy(() ->
                    WalletEntry.save(WALLET_ID, 1_000, 1_000, "k".repeat(100), null));

            assertThatThrownBy(() -> WalletEntry.save(WALLET_ID, 1_000, 1_000, "k".repeat(101), null))
                    .isInstanceOfSatisfying(InvalidArgumentException.class, e ->
                            assertThat(e.getErrorContext())
                                    .containsEntry("field", "idempotencyKey")
                                    .containsEntry("length", 101)
                                    .containsEntry("maxLength", 100));
        }

        @Test
        @DisplayName("사유 코드는 없어도 되고, 50자까지 허용하며 51자부터 거절한다")
        void reasonCodeOptionalWithMaxLength() {
            Wallet wallet = wallet();
            WalletEntry original = saved(wallet, 1_000);

            assertThatNoException().isThrownBy(() -> WalletEntry.reversal(
                    WalletEntryType.CANCEL, WALLET_ID, original, -1_000, 0, key(), null));
            assertThatNoException().isThrownBy(() -> WalletEntry.reversal(
                    WalletEntryType.CANCEL, WALLET_ID, original, -1_000, 0, key(), "R".repeat(50)));

            assertThatThrownBy(() -> WalletEntry.reversal(
                    WalletEntryType.CANCEL, WALLET_ID, original, -1_000, 0, key(), "R".repeat(51)))
                    .isInstanceOfSatisfying(InvalidArgumentException.class, e ->
                            assertThat(e.getErrorContext())
                                    .containsEntry("field", "reasonCode")
                                    .containsEntry("length", 51)
                                    .containsEntry("maxLength", 50));
        }
    }

    /* =====================================================================
     * 조회 메서드
     * ===================================================================== */
    @Nested
    @DisplayName("조회 메서드")
    class Queries {

        @Test
        @DisplayName("isSave 는 SAVE 엔트리에서만 참이다")
        void isSave() {
            Wallet wallet = wallet();
            WalletEntry save = saved(wallet, 1_000);
            WalletEntry cancel = wallet.cancel(save, key(), "USER_CANCEL");

            assertThat(save.getEntryType() == WalletEntryType.SAVE).isTrue();
            assertThat(cancel.getEntryType() == WalletEntryType.SAVE).isFalse();
        }

        @Test
        @DisplayName("belongsTo 는 같은 지갑의 엔트리에서만 참이다")
        void belongsTo() {
            Wallet mine = wallet(WALLET_ID);
            Wallet other = wallet(OTHER_WALLET_ID);
            WalletEntry entry = saved(mine, 1_000);

            assertThat(entry.belongsTo(mine)).isTrue();
            assertThat(entry.belongsTo(other)).isFalse();
        }
    }
}