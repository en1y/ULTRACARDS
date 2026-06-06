package com.ultracards.server.entity.chat;

import com.ultracards.gateway.dto.games.chat.ChatDTO;
import com.ultracards.gateway.dto.games.chat.ChatMessageDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.friends.FriendRelationEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "chats",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chats_user_pair",
                columnNames = {"user_one_id", "user_two_id"}
        )
)
@NoArgsConstructor
@Getter
@Setter
public class ChatEntity {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @OneToMany(mappedBy = "chat", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<ChatMessageEntity> messages = new ArrayList<>();

    @Transient
    private UUID lobbyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_one_id",
            foreignKey = @ForeignKey(name = "fk_chats_user_one")
    )
    private UserEntity userOne;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_two_id",
            foreignKey = @ForeignKey(name = "fk_chats_user_two")
    )
    private UserEntity userTwo;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "friend_relation_id",
            unique = true,
            foreignKey = @ForeignKey(name = "fk_chats_friend_relation")
    )
    private FriendRelationEntity friendRelation;

    @Column(name = "is_open", nullable = false)
    private boolean isOpen = true;

    public ChatEntity (UUID lobbyId) {
        this.lobbyId = lobbyId;
    }

    public ChatEntity(FriendRelationEntity friendRelation) {
        attachFriendRelation(friendRelation);
    }

    public ChatMessageEntity sendMessage(UserEntity user, String message) {
        var mssg = new ChatMessageEntity(this, user, message);
        messages.add(mssg);
        return mssg;
    }

    public ChatDTO toDto() {
        var res = new ArrayList<ChatMessageDTO>();
        for (var m: messages)
            res.add(m.toDto());
        return new ChatDTO(res, isOpen);
    }

    public void attachFriendRelation(FriendRelationEntity friendRelation) {
        this.friendRelation = friendRelation;
        this.userOne = friendRelation.getUserOne();
        this.userTwo = friendRelation.getUserTwo();
    }

    public void detachFriendRelation() {
        this.friendRelation = null;
    }

    public void open() {isOpen = true;}
    public void close() {isOpen = false;}
}
