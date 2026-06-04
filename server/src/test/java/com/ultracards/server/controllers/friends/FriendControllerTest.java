package com.ultracards.server.controllers.friends;

import com.ultracards.gateway.dto.friends.FriendDTO;
import com.ultracards.gateway.dto.friends.FriendPlayCountDTO;
import com.ultracards.gateway.dto.friends.FriendRequestDTO;
import com.ultracards.gateway.dto.friends.FriendRequestStatusDTO;
import com.ultracards.gateway.dto.friends.FriendRelationStatusDTO;
import com.ultracards.gateway.dto.friends.UserPresenceStatusDTO;
import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.server.controllers.errors.ApiExceptionHandler;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.friends.FriendService;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FriendControllerTest {

    private final FriendService friendService = mock(FriendService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new FriendController(friendService))
                .setCustomArgumentResolvers(new TestAuthenticationPrincipalResolver())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void returnsFriendsForCurrentUser() throws Exception {
        var user = user(1L, "User");
        var friendRelationId = UUID.randomUUID();
        var friend = new FriendDTO(
                friendRelationId,
                new GamePlayerDTO("Friend", 2L),
                FriendRelationStatusDTO.FRIENDS,
                UserPresenceStatusDTO.IN_LOBBY,
                3,
                List.of(new FriendPlayCountDTO(GameTypeDTO.Briskula, 3)),
                Instant.parse("2026-05-26T10:00:00Z"),
                null
        );
        when(friendService.getFriends(user)).thenReturn(List.of(friend));

        mockMvc.perform(get("/api/friends").with(authentication(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].friendRelationId").value(friendRelationId.toString()))
                .andExpect(jsonPath("$[0].user.id").value(2))
                .andExpect(jsonPath("$[0].presenceStatus").value("IN_LOBBY"))
                .andExpect(jsonPath("$[0].totalPlayedTogether").value(3))
                .andExpect(jsonPath("$[0].playedTogetherByGameType[0].gameType").value("Briskula"));

        verify(friendService).getFriends(user);
    }

    @Test
    void sendsFriendRequest() throws Exception {
        var user = user(1L, "Requester");
        var response = friendRequest(UUID.randomUUID());
        when(friendService.sendFriendRequest(eq(user), eq(2L))).thenReturn(response);

        mockMvc.perform(post("/api/friends/requests/send/{id}", 2L)
                        .with(authentication(user)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.recipient.id").value(2));

        verify(friendService).sendFriendRequest(user, 2L);
    }

    @Test
    void acceptsFriendRequest() throws Exception {
        var user = user(2L, "Recipient");
        var requestId = UUID.randomUUID();
        var response = friendRequest(requestId);
        response.setStatus(FriendRequestStatusDTO.ACCEPTED);
        when(friendService.acceptRequest(user, requestId)).thenReturn(response);

        mockMvc.perform(post("/api/friends/requests/{id}/accept", requestId).with(authentication(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        verify(friendService).acceptRequest(user, requestId);
    }

    @Test
    void removesFriend() throws Exception {
        var user = user(1L, "User");

        mockMvc.perform(delete("/api/friends/{friendUserId}", 2L).with(authentication(user)))
                .andExpect(status().isNoContent());

        verify(friendService).removeFriend(user, 2L);
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

    private FriendRequestDTO friendRequest(UUID id) {
        return new FriendRequestDTO(
                id,
                new GamePlayerDTO("Requester", 1L),
                new GamePlayerDTO("Recipient", 2L),
                FriendRequestStatusDTO.PENDING,
                Instant.parse("2026-05-26T10:00:00Z"),
                Instant.parse("2026-05-26T10:00:00Z"),
                null
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
