package com.ultracards.config;

import com.ultracards.server.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.messaging.access.intercept.AuthorizationChannelInterceptor;
import org.springframework.security.messaging.context.SecurityContextChannelInterceptor;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketConfigTest {
    private final AuthorizationManager<Message<?>> authorizationManager =
            WebSocketConfig.messageAuthorizationManager();

    @Test
    void preservesOutboundMessageOrder() {
        var registry = mock(MessageBrokerRegistry.class);

        new WebSocketConfig(authorizationManager).configureMessageBroker(registry);

        verify(registry).setPreservePublishOrder(true);
    }

    @Test
    void installsInboundSecurityInterceptors() {
        var registration = mock(ChannelRegistration.class);

        new WebSocketConfig(authorizationManager).configureClientInboundChannel(registration);

        verify(registration).interceptors(
                any(SecurityContextChannelInterceptor.class),
                any(AuthorizationChannelInterceptor.class)
        );
    }

    @Test
    void keepsUserIdAndAuthoritiesInWebSocketPrincipal() {
        var user = mock(UserEntity.class);
        var authority = new SimpleGrantedAuthority("ROLE_USER");
        when(user.getId()).thenReturn(42L);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(user, null, List.of(authority));

        var principal = assertInstanceOf(Authentication.class, WebSocketConfig.websocketPrincipal(authentication));

        assertEquals("42", principal.getName());
        assertTrue(principal.getAuthorities().contains(authority));
    }

    @Test
    void protectsBrokerAndUserDestinations() {
        assertTrue(isAllowed(SimpMessageType.SUBSCRIBE, "/user/queue/game/cards"));
        assertTrue(isAllowed(SimpMessageType.SUBSCRIBE, "/topic/game/123"));
        assertTrue(isAllowed(SimpMessageType.MESSAGE, "/app/game/play"));

        assertFalse(isAllowed(SimpMessageType.SUBSCRIBE, "/queue/game/cards-user-victim-session"));
        assertFalse(isAllowed(SimpMessageType.MESSAGE, "/queue/game/cards"));
        assertFalse(isAllowed(SimpMessageType.MESSAGE, "/topic/game/123"));
    }

    private boolean isAllowed(SimpMessageType type, String destination) {
        var accessor = SimpMessageHeaderAccessor.create(type);
        accessor.setDestination(destination);
        var message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        var decision = authorizationManager.authorize(authenticatedUser(), message);
        return decision != null && decision.isGranted();
    }

    private Supplier<Authentication> authenticatedUser() {
        return () -> UsernamePasswordAuthenticationToken.authenticated(
                "user", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
