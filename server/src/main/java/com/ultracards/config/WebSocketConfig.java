package com.ultracards.config;

import com.ultracards.server.entity.UserEntity;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.core.Authentication;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.messaging.access.intercept.AuthorizationChannelInterceptor;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;
import org.springframework.security.messaging.context.SecurityContextChannelInterceptor;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

import static org.springframework.messaging.simp.SimpMessageType.MESSAGE;
import static org.springframework.messaging.simp.SimpMessageType.SUBSCRIBE;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final AuthorizationManager<Message<?>> messageAuthorizationManager;

    public WebSocketConfig(AuthorizationManager<Message<?>> messageAuthorizationManager) {
        this.messageAuthorizationManager = messageAuthorizationManager;
    }

    @Bean
    static AuthorizationManager<Message<?>> messageAuthorizationManager() {
        var messages = MessageMatcherDelegatingAuthorizationManager.builder();
        messages
                .nullDestMatcher().authenticated()
                .simpMessageDestMatchers("/app/**").hasRole("USER")
                .simpSubscribeDestMatchers("/user/queue/**", "/topic/**").hasRole("USER")
                .simpTypeMatchers(MESSAGE, SUBSCRIBE).denyAll()
                .anyMessage().denyAll();
        return messages.build();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setPreservePublishOrder(true);
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(
                new SecurityContextChannelInterceptor(),
                new AuthorizationChannelInterceptor(messageAuthorizationManager)
        );
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setHandshakeHandler(new DefaultHandshakeHandler() {
                    @Override
                    protected Principal determineUser(
                            @NonNull ServerHttpRequest request,
                            @NonNull WebSocketHandler wsHandler,
                            @NonNull Map<String, Object> attributes
                    ) {
                        return websocketPrincipal(request.getPrincipal());
                    }
                });
    }

    static Principal websocketPrincipal(Principal principal) {
        if (!(principal instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof UserEntity user)) return principal;
        var websocketAuthentication = UsernamePasswordAuthenticationToken.authenticated(
                user.getId().toString(), null, authentication.getAuthorities());
        websocketAuthentication.setDetails(authentication.getDetails());
        return websocketAuthentication;
    }
}
