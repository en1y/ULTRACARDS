package com.ultracards.server.entity.friends;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.enums.friends.FriendRelationStatus;
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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "friend_relations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_friend_relations_user_pair",
                columnNames = {"user_one_id", "user_two_id"}
        )
)
@NoArgsConstructor
@Getter
@Setter
public class FriendRelationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_one_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_friend_relations_user_one")
    )
    private UserEntity userOne;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_two_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_friend_relations_user_two")
    )
    private UserEntity userTwo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FriendRelationStatus status = FriendRelationStatus.FRIENDS;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "removed_by_user_id",
            foreignKey = @ForeignKey(name = "fk_friend_relations_removed_by")
    )
    private UserEntity removedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    public FriendRelationEntity(UserEntity user, UserEntity friend) {
        setUserPair(user, friend);
    }

    public boolean contains(UserEntity user) {
        return userOne.equals(user) || userTwo.equals(user);
    }

    public UserEntity getOtherUser(UserEntity user) {
        if (userOne.equals(user))
            return userTwo;
        if (userTwo.equals(user))
            return userOne;
        throw new IllegalArgumentException("User is not part of this friend relation");
    }

    public void activate() {
        status = FriendRelationStatus.FRIENDS;
        removedBy = null;
        removedAt = null;
    }

    public void remove(UserEntity removedBy) {
        status = FriendRelationStatus.BLOCKED;
        this.removedBy = removedBy;
        removedAt = Instant.now();
    }

    private void setUserPair(UserEntity user, UserEntity friend) {
        if (user.getId() <= friend.getId()) {
            userOne = user;
            userTwo = friend;
        } else {
            userOne = friend;
            userTwo = user;
        }
    }
}
