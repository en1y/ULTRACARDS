package com.ultracards.server.entity.friends;

import com.ultracards.server.entity.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "friend_blocks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_friend_blocks_pair",
                columnNames = {"blocker_user_id", "blocked_user_id"}
        )
)
@NoArgsConstructor
@Getter
@Setter
public class FriendBlockEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "blocker_user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_friend_blocks_blocker")
    )
    private UserEntity blocker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "blocked_user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_friend_blocks_blocked")
    )
    private UserEntity blocked;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public FriendBlockEntity(UserEntity blocker, UserEntity blocked) {
        this.blocker = blocker;
        this.blocked = blocked;
    }
}
