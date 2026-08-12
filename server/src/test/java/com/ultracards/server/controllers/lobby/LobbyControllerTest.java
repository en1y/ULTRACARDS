package com.ultracards.server.controllers.lobby;

import com.ultracards.server.controllers.errors.ApiExceptionHandler;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.lobby.LobbyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LobbyControllerTest {

    private final LobbyService lobbyService = mock(LobbyService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new LobbyController(lobbyService))
                .setCustomArgumentResolvers(new TestAuthenticationPrincipalResolver())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void invitesFriendToLobby() throws Exception {
        var user = user(1L, "User");

        mockMvc.perform(post("/api/lobby/invite/{friendUserId}", 2L).with(authentication(user)))
                .andExpect(status().isCreated());

        verify(lobbyService).inviteFriendToLobby(user, 2L);
    }

    @Test
    void reportsWhyAPlayersPointsPreventTheLobbyFromStarting() throws Exception {
        var user = user(1L, "Host");
        when(lobbyService.startLobby(user)).thenThrow(new ResponseStatusException(
                HttpStatus.CONFLICT, "Insufficient Points for 100P: PoorPlayer"));

        mockMvc.perform(post("/api/lobby/start").with(authentication(user)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Insufficient Points for 100P: PoorPlayer"));
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
