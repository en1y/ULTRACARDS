package com.ultracards.server.entity.chat;

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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "chat_read_states",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chat_read_states_chat_user",
                columnNames = {"chat_id", "user_id"}
        )
)
@NoArgsConstructor
@Getter
@Setter
public class ChatReadStateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "chat_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_chat_read_states_chat")
    )
    private ChatEntity chat;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_chat_read_states_user")
    )
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "last_read_message_id",
            foreignKey = @ForeignKey(name = "fk_chat_read_states_last_read_message")
    )
    private ChatMessageEntity lastReadMessage;

    @UpdateTimestamp
    @Column(name = "read_at", nullable = false)
    private Instant readAt = Instant.now();

    public ChatReadStateEntity(ChatEntity chat, UserEntity user) {
        this.chat = chat;
        this.user = user;
    }
}
