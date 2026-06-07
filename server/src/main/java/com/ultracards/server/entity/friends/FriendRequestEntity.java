package com.ultracards.server.entity.friends;

import com.ultracards.gateway.dto.friends.FriendRequestDTO;
import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.enums.friends.FriendRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "friend_requests")
@NoArgsConstructor
@Getter
@Setter
public class FriendRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "requester_user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_friend_requests_requester")
    )
    private UserEntity requester;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "recipient_user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_friend_requests_recipient")
    )
    private UserEntity recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FriendRequestStatus status = FriendRequestStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    public FriendRequestEntity(UserEntity requester, UserEntity recipient) {
        this.requester = requester;
        this.recipient = recipient;
    }

    public void accept() {
        respond(FriendRequestStatus.ACCEPTED);
    }

    public void decline() {
        respond(FriendRequestStatus.DECLINED);
    }

    public void block() {
        respond(FriendRequestStatus.BLOCKED);
    }

    public FriendRequestDTO toDto() {
        return new FriendRequestDTO(
                id,
                toPlayerDto(requester),
                toPlayerDto(recipient),
                status.toDto(),
                createdAt,
                updatedAt,
                respondedAt
        );
    }

    private void respond(FriendRequestStatus newStatus) {
        status = newStatus;
        respondedAt = Instant.now();
    }

    private GamePlayerDTO toPlayerDto(UserEntity user) {
        return new GamePlayerDTO(user.getUsername(), user.getId());
    }
}
