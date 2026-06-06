package com.ultracards.server.service.chat;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.chat.ChatEntity;
import com.ultracards.server.entity.chat.ChatReadStateEntity;
import com.ultracards.server.entity.friends.FriendRelationEntity;
import com.ultracards.server.repositories.chat.ChatMessageRepository;
import com.ultracards.server.repositories.chat.ChatReadStateRepository;
import com.ultracards.server.repositories.chat.ChatRepository;
import com.ultracards.server.repositories.friends.FriendRelationRepository;
import com.ultracards.server.service.notifications.NotificationService;
import com.ultracards.server.service.ultrakill.UltrakillLevelService;
import lombok.RequiredArgsConstructor;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatService{
    private static final PolicyFactory NO_HTML_POLICY = new HtmlPolicyBuilder().toFactory();

    private final ChatManager chatManager;
    private final ChatRepository chatRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatReadStateRepository chatReadStateRepository;
    private final FriendRelationRepository friendRelationRepository;
    private final NotificationService notificationService;
    private final ChatEventPublisher eventPublisher;
    private final UltrakillLevelService ultrakillLevelService;
    private final UserEntity serverUser = new UserEntity("", "Server");

    public ChatEntity getChat(UUID lobbyId) {
        return chatManager.getChat(lobbyId);
    }

    public void sendMessage(UUID lobbyId, UserEntity sender, String message) {
        var chat = getChat(lobbyId);
        var sanitizedMessage = sanitizeMessage(message);
        var messageObj = chat.sendMessage(sender, sanitizedMessage);
        eventPublisher.publish(messageObj.toDto(), lobbyId);
        var levelNumbersInMessage = ultrakillLevelService.findLevelNumbers(messageObj.getMessage());
        if (levelNumbersInMessage.length > 0) {
            var serverMssg = new StringJoiner("\n");
            for (var s: ultrakillLevelService.createMessages(levelNumbersInMessage)) {
                serverMssg.add(s);
            }
            sendServerMessage(chat, serverMssg.toString());
        }
    }

    public void sendServerMessage(UUID lobbyId, String message) {
        sendServerMessage(getChat(lobbyId), message);
    }

    private void sendServerMessage(ChatEntity chat, String message) {
        var messageObj = chat.sendMessage(serverUser, sanitizeMessage(message));
        eventPublisher.publish(messageObj.toDto(), chat.getLobbyId());
    }

    public void deleteChat(UUID lobbyId) {
        chatManager.deleteChat(lobbyId);
    }

    public void createChat(UUID lobbyId) {
        chatManager.createChat(lobbyId);
    }

    @Transactional
    public ChatEntity createFriendChat(FriendRelationEntity friendRelation) {
        return chatRepository.findByFriendRelationId(friendRelation.getId())
                .orElseGet(() -> findFriendChatByPair(friendRelation)
                        .map(chat -> attachFriendRelation(chat, friendRelation))
                        .orElseGet(() -> chatRepository.save(new ChatEntity(friendRelation))));
    }

    @Transactional
    public ChatEntity openFriendChat(FriendRelationEntity friendRelation) {
        var chat = createFriendChat(friendRelation);
        chat.open();
        return chatRepository.save(chat);
    }

    @Transactional
    public void closeFriendChat(FriendRelationEntity friendRelation) {
        chatRepository.findByFriendRelationId(friendRelation.getId()).ifPresent(chat -> {
            chat.close();
            chatRepository.save(chat);
        });
    }

    @Transactional
    public void detachFriendChat(FriendRelationEntity friendRelation) {
        chatRepository.findByFriendRelationId(friendRelation.getId()).ifPresent(chat -> {
            chat.close();
            chat.detachFriendRelation();
            chatRepository.save(chat);
        });
    }

    @Transactional
    public ChatEntity getFriendChat(UserEntity user, Long friendUserId) {
        var friendRelation = getActiveFriendRelation(user, friendUserId);
        return createFriendChat(friendRelation);
    }

    @Transactional
    public ChatEntity sendFriendMessage(UserEntity user, Long friendUserId, String message) {
        var friendRelation = getActiveFriendRelation(user, friendUserId);
        var chat = createFriendChat(friendRelation);
        var sanitizedMessage = sanitizeMessage(message);

        if (!chat.isOpen()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Friend chat is closed");
        }

        var messageObj = chat.sendMessage(user, sanitizedMessage);
        chat = chatRepository.save(chat);
        eventPublisher.publishFriendMessage(
                messageObj.toDto(),
                chat.getId(),
                friendRelation.getUserOne(),
                friendRelation.getUserTwo()
        );
        notificationService.createTextNotification(user, friendRelation.getOtherUser(user).getId(), sanitizedMessage);
        return chat;
    }

    @Transactional
    public ChatEntity readAllFriendMessages(UserEntity user, Long friendUserId) {
        var chat = getFriendChat(user, friendUserId);
        var lastMessage = chatMessageRepository.findFirstByChatIdOrderByCreatedAtDesc(chat.getId()).orElse(null);
        var readState = chatReadStateRepository.findByChatIdAndUserId(chat.getId(), user.getId())
                .orElseGet(() -> new ChatReadStateEntity(chat, user));

        readState.setLastReadMessage(lastMessage);
        chatReadStateRepository.save(readState);
        notificationService.markUnreadTextNotificationsFromSenderRead(user, friendUserId);
        return chat;
    }

    private FriendRelationEntity getActiveFriendRelation(UserEntity user, Long friendUserId) {
        var userOneId = Math.min(user.getId(), friendUserId);
        var userTwoId = Math.max(user.getId(), friendUserId);
        var friendRelation = friendRelationRepository.findByNormalizedPair(userOneId, userTwoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active friend relation not found"));
        if (!friendRelation.contains(user)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Active friend relation not found");
        }
        return friendRelation;
    }

    private Optional<ChatEntity> findFriendChatByPair(FriendRelationEntity friendRelation) {
        return chatRepository.findByUserOneIdAndUserTwoId(
                friendRelation.getUserOne().getId(),
                friendRelation.getUserTwo().getId()
        );
    }

    private ChatEntity attachFriendRelation(ChatEntity chat, FriendRelationEntity friendRelation) {
        chat.attachFriendRelation(friendRelation);
        return chatRepository.save(chat);
    }

    private String sanitizeMessage(String message) {
        if (message == null) {
            return null;
        }

        var sanitizedMessage = HtmlUtils.htmlUnescape(NO_HTML_POLICY.sanitize(message)).trim();
        if (!StringUtils.hasText(sanitizedMessage)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat message must not be blank");
        }
        return sanitizedMessage;
    }
}
