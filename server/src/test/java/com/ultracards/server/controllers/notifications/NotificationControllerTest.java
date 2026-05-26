package com.ultracards.server.controllers.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.notifications.CreateNotificationDTO;
import com.ultracards.gateway.dto.notifications.NotificationDTO;
import com.ultracards.gateway.dto.notifications.NotificationTypeDTO;
import com.ultracards.server.controllers.errors.ApiExceptionHandler;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.notifications.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationControllerTest {

    private final NotificationService notificationService = mock(NotificationService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new NotificationController(notificationService))
                .setCustomArgumentResolvers(new TestAuthenticationPrincipalResolver())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void returnsNotificationsForCurrentUser() throws Exception {
        var user = user(1L, "Recipient");
        var notification = notification(UUID.randomUUID(), NotificationTypeDTO.TEXT, "Hello", null, false);
        when(notificationService.getNotifications(user)).thenReturn(List.of(notification));

        mockMvc.perform(get("/api/notifications").with(authentication(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(notification.getId().toString()))
                .andExpect(jsonPath("$[0].type").value("TEXT"))
                .andExpect(jsonPath("$[0].message").value("Hello"))
                .andExpect(jsonPath("$[0].sender.id").value(2))
                .andExpect(jsonPath("$[0].recipient.id").value(1))
                .andExpect(jsonPath("$[0].read").value(false));

        verify(notificationService).getNotifications(user);
    }

    @Test
    void returnsUnreadNotificationsForCurrentUser() throws Exception {
        var user = user(1L, "Recipient");
        when(notificationService.getUnreadNotifications(user)).thenReturn(List.of());

        mockMvc.perform(get("/api/notifications/unread").with(authentication(user)))
                .andExpect(status().isOk());

        verify(notificationService).getUnreadNotifications(user);
    }

    @Test
    void createsNotification() throws Exception {
        var user = user(1L, "Sender");
        var request = new CreateNotificationDTO(2L, NotificationTypeDTO.TEXT, "Hello", null);
        var response = notification(UUID.randomUUID(), NotificationTypeDTO.TEXT, "Hello", null, false);
        when(notificationService.createNotification(eq(user), eq(request))).thenReturn(response);

        mockMvc.perform(post("/api/notifications")
                        .with(authentication(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("TEXT"))
                .andExpect(jsonPath("$.message").value("Hello"));

        verify(notificationService).createNotification(user, request);
    }

    @Test
    void invalidCreateRequestReturnsBadRequest() throws Exception {
        var user = user(1L, "Sender");
        var request = new CreateNotificationDTO(null, NotificationTypeDTO.TEXT, "Hello", null);

        mockMvc.perform(post("/api/notifications")
                        .with(authentication(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failure"));
    }

    @Test
    void marksNotificationRead() throws Exception {
        var user = user(1L, "Recipient");
        var notificationId = UUID.randomUUID();
        var response = notification(notificationId, NotificationTypeDTO.TEXT, "Hello", null, true);
        when(notificationService.markRead(user, notificationId)).thenReturn(response);

        mockMvc.perform(patch("/api/notifications/{id}/read", notificationId).with(authentication(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));

        verify(notificationService).markRead(user, notificationId);
    }

    @Test
    void marksNotificationUnread() throws Exception {
        var user = user(1L, "Recipient");
        var notificationId = UUID.randomUUID();
        var response = notification(notificationId, NotificationTypeDTO.TEXT, "Hello", null, false);
        when(notificationService.markUnread(user, notificationId)).thenReturn(response);

        mockMvc.perform(patch("/api/notifications/{id}/unread", notificationId).with(authentication(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(false));

        verify(notificationService).markUnread(user, notificationId);
    }

    @Test
    void deletesNotification() throws Exception {
        var user = user(1L, "Recipient");
        var notificationId = UUID.randomUUID();

        mockMvc.perform(delete("/api/notifications/{id}", notificationId).with(authentication(user)))
                .andExpect(status().isNoContent());

        verify(notificationService).deleteNotification(user, notificationId);
    }

    private RequestPostProcessor authentication(UserEntity user) {
        return request -> {
            request.setAttribute("currentUser", user);
            return request;
        };
    }

    private UserEntity user(Long id, String username) {
        var user = new UserEntity(username + "@example.com", username);
        user.setId(id);
        return user;
    }

    private NotificationDTO notification(
            UUID id,
            NotificationTypeDTO type,
            String message,
            UUID lobbyId,
            boolean read
    ) {
        return new NotificationDTO(
                id,
                type,
                message,
                lobbyId,
                new GamePlayerDTO("Sender", 2L),
                new GamePlayerDTO("Recipient", 1L),
                read,
                Instant.parse("2026-05-26T10:00:00Z"),
                read ? Instant.parse("2026-05-26T10:01:00Z") : null
        );
    }

    private static class TestAuthenticationPrincipalResolver implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                    && parameter.getParameterType().equals(UserEntity.class);
        }

        @Override
        public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                WebDataBinderFactory binderFactory
        ) {
            return webRequest.getAttribute("currentUser", NativeWebRequest.SCOPE_REQUEST);
        }
    }
}
