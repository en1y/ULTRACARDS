package com.ultracards.server.controllers;

import com.ultracards.gateway.dto.games.chat.ChatDTO;
import com.ultracards.gateway.dto.games.chat.ChatMessageDTO;
import com.ultracards.server.controllers.errors.ApiExceptionHandler;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.chat.ChatEntity;
import com.ultracards.server.service.chat.ChatService;
import com.ultracards.server.service.lobby.LobbyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatControllerTest {
    private final ChatService chatService = mock(ChatService.class);
    private final LobbyService lobbyService = mock(LobbyService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ChatController(chatService, lobbyService))
                .setCustomArgumentResolvers(new TestAuthenticationPrincipalResolver())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void getsFriendChat() throws Exception {
        var user = user(1L, "User");
        var chat = mock(ChatEntity.class);
        when(chat.toDto()).thenReturn(new ChatDTO(List.of(), true));
        when(chatService.getFriendChat(user, 2L)).thenReturn(chat);

        mockMvc.perform(get("/api/chat/friends/{friendUserId}", 2L).with(authentication(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isOpen").value(true));

        verify(chatService).getFriendChat(user, 2L);
    }

    @Test
    void sendsFriendMessage() throws Exception {
        var user = user(1L, "User");
        var chat = mock(ChatEntity.class);
        when(chat.toDto()).thenReturn(new ChatDTO(List.of(), true));
        when(chatService.sendFriendMessage(user, 2L, "hello")).thenReturn(chat);

        mockMvc.perform(post("/api/chat/friends/{friendUserId}", 2L)
                        .with(authentication(user))
                        .contentType("application/json")
                        .content("""
                                {"message":"hello"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isOpen").value(true));

        verify(chatService).sendFriendMessage(user, 2L, "hello");
    }

    @Test
    void marksFriendChatRead() throws Exception {
        var user = user(1L, "User");
        var chat = mock(ChatEntity.class);
        when(chat.toDto()).thenReturn(new ChatDTO(List.of(), true));
        when(chatService.readAllFriendMessages(user, 2L)).thenReturn(chat);

        mockMvc.perform(post("/api/chat/friends/{friendUserId}/read-all", 2L)
                        .with(authentication(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isOpen").value(true));

        verify(chatService).readAllFriendMessages(user, 2L);
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
