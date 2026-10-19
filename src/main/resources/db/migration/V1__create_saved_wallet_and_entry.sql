-- =========================================================
-- 1단계: 단일 기입 원장 기반 아낀 돈 지갑 (saved_wallet / saved_entry)
-- payment 는 3단계(V2), pg_payment_record·reconciliation_result 는
-- 4단계(V3) 마이그레이션으로 추가한다.
-- 실제 금전 거래는 없으며, 모든 금액은 현금 가치가 없는 가상 금액이다.
--
-- 복식부기를 쓰지 않은 이유: 금액의 당사자가 사용자 한 명뿐이고 판매자도 없어
-- 상대 계정이 의미 없는 숫자만 만든다. 사용자 간 금액 이동이 생기면 확장한다.
-- =========================================================

-- 아낀 돈 지갑: 사용자당 1개. balance 는 엔트리 합의 캐시다.
CREATE TABLE saved_wallet (
                              id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                              user_id     BIGINT      NOT NULL,
                              balance     BIGINT      NOT NULL DEFAULT 0,  -- 원(KRW) 단위 정수
                              version     BIGINT      NOT NULL DEFAULT 0,  -- 2단계 낙관적 락 비교용
                              created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
                              updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- 최후의 방어선: 아낀 돈은 어떤 경우에도 음수가 될 수 없다
                              CONSTRAINT chk_saved_wallet_non_negative CHECK (balance >= 0),
                              CONSTRAINT uk_saved_wallet_user UNIQUE (user_id)
);


-- 원장 엔트리: 잔액 변동 기록. 수정·삭제하지 않고 쌓기만 한다.
CREATE TABLE saved_entry (
                             id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                             wallet_id        BIGINT       NOT NULL REFERENCES saved_wallet (id),
                             entry_type       VARCHAR(20)  NOT NULL,
                             amount           BIGINT       NOT NULL,   -- 부호 있는 금액: SAVE 는 +, 되돌림은 -
                             balance_after    BIGINT       NOT NULL,   -- 반영 직후 잔액 스냅샷 (감사·디버깅용)
                             idempotency_key  VARCHAR(100) NOT NULL,   -- 같은 요청이 두 번 와도 한 번만 반영
                             reversal_of      BIGINT REFERENCES saved_entry (id),  -- 되돌림이 가리키는 원 SAVE 엔트리
                             payment_id       BIGINT,                  -- 3단계 payment 테이블 생성 시 FK 추가 (V2)
                             reason_code      VARCHAR(50),             -- USER_CANCEL, PAYMENT_FAILED, RECON_MISSING_PG ...
                             created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- SAVE         : 결제 승인으로 적립
    -- CANCEL       : 숙려 기간 내 사용자의 참기 취소
    -- COMPENSATION : 승인했던 결제가 실패·취소로 확정됨 (대사 결과 포함)
                             CONSTRAINT chk_entry_type
                                 CHECK (entry_type IN ('SAVE', 'CANCEL', 'COMPENSATION')),

    -- 적립은 양수이고 원 엔트리가 없다. 되돌림은 음수이고 원 엔트리를 반드시 가리킨다.
                             CONSTRAINT chk_entry_shape CHECK (
                                 (entry_type = 'SAVE' AND amount > 0 AND reversal_of IS NULL)
                                     OR (entry_type IN ('CANCEL', 'COMPENSATION') AND amount < 0 AND reversal_of IS NOT NULL)
                                 ),

                             CONSTRAINT chk_entry_balance_after CHECK (balance_after >= 0),
                             CONSTRAINT uk_saved_entry_idempotency UNIQUE (idempotency_key)
);

-- 한 적립은 한 번만 되돌릴 수 있다 (CANCEL 과 COMPENSATION 중 하나만, 이중 취소 방지)
-- 되돌림 금액이 원 SAVE 금액과 같은지, 원 엔트리가 SAVE 인지는 도메인에서 검증한다.
CREATE UNIQUE INDEX uk_saved_entry_reversal
    ON saved_entry (reversal_of) WHERE reversal_of IS NOT NULL;

-- 거래내역 조회: 지갑별 최신순 + no-offset 페이징 (WHERE wallet_id = ? AND id < ? ORDER BY id DESC)
CREATE INDEX idx_saved_entry_wallet_id_desc ON saved_entry (wallet_id, id DESC);

-- 결제 하나에 묶인 엔트리 조회 (대사 결과 반영, 참기 취소 시 원 SAVE 찾기)
CREATE INDEX idx_saved_entry_payment ON saved_entry (payment_id);

-- 월간 리포트: 기간별 집계
CREATE INDEX idx_saved_entry_created_at ON saved_entry (created_at);