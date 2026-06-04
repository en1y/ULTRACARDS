package com.ultracards.server.service.notifications;

import com.ultracards.gateway.dto.notifications.NotificationTypeDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.notifications.NotificationEntity;
import com.ultracards.server.enums.NotificationType;
import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.repositories.notifications.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationEventPublisher eventPublisher;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void createsGameInviteNotification() {
        var sender = user(1L, "Sender");
        var recipient = user(2L, "Recipient");
        var lobbyId = UUID.randomUUID();
        var savedNotification = notification(
                recipient,
                sender,
                NotificationType.GAME_INVITE,
                "Sender invited you to a lobby.",
                lobbyId
        );

        when(notificationRepository.save(any(NotificationEntity.class))).thenReturn(savedNotification);

        var result = notificationService.createGameInviteNotification(sender, recipient, lobbyId);

        var notificationCaptor = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        var saved = notificationCaptor.getValue();
        assertThat(saved.getRecipient()).isEqualTo(recipient);
        assertThat(saved.getSender()).isEqualTo(sender);
        assertThat(saved.getType()).isEqualTo(NotificationType.GAME_INVITE);
        assertThat(saved.getMessage()).isEqualTo("Sender invited you to a lobby.");
        assertThat(saved.getLobbyId()).isEqualTo(lobbyId);

        assertThat(result.getType()).isEqualTo(NotificationTypeDTO.GAME_INVITE);
        assertThat(result.getLobbyId()).isEqualTo(lobbyId);
        assertThat(result.getSender().getId()).isEqualTo(1L);
        assertThat(result.getSender().getName()).isEqualTo("Sender");
        assertThat(result.getRecipient().getId()).isEqualTo(2L);
        verify(eventPublisher).publish(savedNotification);
    }

    @Test
    void rejectsMissingRecipientForServerTextNotification() {
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.createTextNotification(2L, "Hello"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verifyNoInteractions(notificationRepository);
    }

    @Test
    void rejectsGameInviteWithoutLobbyId() {
        var sender = user(1L, "Sender");
        var recipient = user(2L, "Recipient");

        assertThatThrownBy(() -> notificationService.createGameInviteNotification(sender, recipient, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(notificationRepository);
    }

    @Test
    void rejectsFriendInviteWithoutRequestId() {
        var sender = user(1L, "Sender");
        var recipient = user(2L, "Recipient");

        assertThatThrownBy(() -> notificationService.createFriendInviteNotification(sender, recipient, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(notificationRepository);
    }

    @Test
    void rejectsServerTextNotificationWithoutMessage() {
        var recipient = user(2L, "Recipient");
        when(userRepository.findById(2L)).thenReturn(Optional.of(recipient));

        assertThatThrownBy(() -> notificationService.createTextNotification(2L, "   "))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(notificationRepository);
    }

    @Test
    void serverCreatesAndSanitizesTextNotification() {
        var recipient = user(2L, "Recipient");
        var savedNotification = notification(recipient, null, NotificationType.TEXT, "Hello", null);

        when(userRepository.findById(2L)).thenReturn(Optional.of(recipient));
        when(notificationRepository.save(any(NotificationEntity.class))).thenReturn(savedNotification);

        notificationService.createTextNotification(2L, "<b>Hello</b>");

        var notificationCaptor = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        var saved = notificationCaptor.getValue();
        assertThat(saved.getType()).isEqualTo(NotificationType.TEXT);
        assertThat(saved.getSender()).isNull();
        assertThat(saved.getRecipient()).isEqualTo(recipient);
        assertThat(saved.getMessage()).isEqualTo("Hello");
        verify(eventPublisher).publish(savedNotification);
    }

    @Test
    void senderCreatesAndSanitizesTextNotification() {
        var sender = user(1L, "Moderator");
        var recipient = user(2L, "Recipient");
        var savedNotification = notification(recipient, sender, NotificationType.TEXT, "Hello", null);

        when(userRepository.findById(2L)).thenReturn(Optional.of(recipient));
        when(notificationRepository.save(any(NotificationEntity.class))).thenReturn(savedNotification);

        notificationService.createTextNotification(sender, 2L, "<b>Hello</b>");

        var notificationCaptor = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        var saved = notificationCaptor.getValue();
        assertThat(saved.getType()).isEqualTo(NotificationType.TEXT);
        assertThat(saved.getSender()).isEqualTo(sender);
        assertThat(saved.getRecipient()).isEqualTo(recipient);
        assertThat(saved.getMessage()).isEqualTo("Hello");
        verify(eventPublisher).publish(savedNotification);
    }

    @Test
    void createsTextNotificationForAllUsers() {
        var sender = user(1L, "Admin");
        var firstRecipient = user(2L, "First");
        var secondRecipient = user(3L, "Second");

        when(userRepository.findAll()).thenReturn(List.of(firstRecipient, secondRecipient));
        when(notificationRepository.save(any(NotificationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var results = notificationService.createTextNotificationToAll(sender, "<b>Hello all</b>");

        assertThat(results).hasSize(2);
        var notificationCaptor = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository, times(2)).save(notificationCaptor.capture());

        assertThat(notificationCaptor.getAllValues()).extracting(NotificationEntity::getRecipient)
                .containsExactly(firstRecipient, secondRecipient);
        assertThat(notificationCaptor.getAllValues()).allSatisfy(notification -> {
            assertThat(notification.getSender()).isEqualTo(sender);
            assertThat(notification.getType()).isEqualTo(NotificationType.TEXT);
            assertThat(notification.getMessage()).isEqualTo("Hello all");
        });
        verify(eventPublisher, times(2)).publish(any(NotificationEntity.class));
    }

    @Test
    void listsNotificationsNewestFirstFromRepository() {
        var user = user(1L, "Recipient");
        var sender = user(2L, "Sender");
        var notification = notification(user, sender, NotificationType.TEXT, "Hello", null);
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(notification));

        var results = notificationService.getNotifications(user);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getMessage()).isEqualTo("Hello");
        verify(notificationRepository).findByRecipientIdOrderByCreatedAtDesc(1L);
    }

    @Test
    void marksOwnedNotificationRead() {
        var user = user(1L, "Recipient");
        var notificationId = UUID.randomUUID();
        var notification = notification(user, null, NotificationType.TEXT, "Hello", null);
        notification.setId(notificationId);

        when(notificationRepository.findByIdAndRecipientId(notificationId, 1L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification)).thenReturn(notification);

        var result = notificationService.markRead(user, notificationId);

        assertThat(result.isRead()).isTrue();
        assertThat(result.getReadAt()).isNotNull();
        verify(notificationRepository).save(notification);
    }

    @Test
    void marksOwnedNotificationUnread() {
        var user = user(1L, "Recipient");
        var notificationId = UUID.randomUUID();
        var notification = notification(user, null, NotificationType.TEXT, "Hello", null);
        notification.setId(notificationId);
        notification.markRead();

        when(notificationRepository.findByIdAndRecipientId(notificationId, 1L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification)).thenReturn(notification);

        var result = notificationService.markUnread(user, notificationId);

        assertThat(result.isRead()).isFalse();
        assertThat(result.getReadAt()).isNull();
        verify(notificationRepository).save(notification);
    }

    @Test
    void rejectsAccessToNotificationNotOwnedByUser() {
        var user = user(1L, "Recipient");
        var notificationId = UUID.randomUUID();
        when(notificationRepository.findByIdAndRecipientId(notificationId, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead(user, notificationId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deletesOwnedNotification() {
        var user = user(1L, "Recipient");
        var notificationId = UUID.randomUUID();
        var notification = notification(user, null, NotificationType.TEXT, "Hello", null);
        when(notificationRepository.findByIdAndRecipientId(notificationId, 1L)).thenReturn(Optional.of(notification));

        notificationService.deleteNotification(user, notificationId);

        verify(notificationRepository).delete(notification);
    }

    private UserEntity user(Long id, String username) {
        var user = new UserEntity(username + "@example.com", username);
        user.setId(id);
        return user;
    }

    private NotificationEntity notification(
            UserEntity recipient,
            UserEntity sender,
            NotificationType type,
            String message,
            UUID lobbyId
    ) {
        var notification = new NotificationEntity(recipient, sender, type, message, lobbyId);
        notification.setId(UUID.randomUUID());
        notification.setCreatedAt(Instant.now());
        return notification;
    }
}
