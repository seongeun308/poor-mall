package io.github.seongeun308.poormall.wallet.domain;

import io.github.seongeun308.poormall.common.entity.BaseTimeEntity;
import io.github.seongeun308.poormall.common.utils.Guard;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
}