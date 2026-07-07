package com.ultracards.gateway.app;

import com.ultracards.gateway.service.AuthenticationService;
import com.ultracards.gateway.service.CardImageService;
import com.ultracards.gateway.service.ChatService;
import com.ultracards.gateway.service.ChatWsService;
import com.ultracards.gateway.service.ClientTokenHolder;
import com.ultracards.gateway.service.FriendService;
import com.ultracards.gateway.service.GameService;
import com.ultracards.gateway.service.GameWsService;
import com.ultracards.gateway.service.LobbyService;
import com.ultracards.gateway.service.LobbyWsService;
import com.ultracards.gateway.service.NotificationService;
import com.ultracards.gateway.service.NotificationWsService;
import com.ultracards.gateway.service.ProfileService;
import com.ultracards.gateway.service.ServerService;
import com.ultracards.gateway.service.StompGatewayService;
import com.ultracards.gateway.service.UiPageService;
import com.ultracards.gateway.service.UserSearchService;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;

public class GatewayAppClient implements AutoCloseable {
    private final ClientTokenHolder tokenHolder;
    private final String wsUrl;
    private final GatewayAsync async;
    private final AuthenticationService authentication;
    private final ProfileService profile;
    private final LobbyService lobby;
    private final GameService game;
    private final FriendService friend;
    private final ChatService chat;
    private final NotificationService notification;
    private final UserSearchService users;
    private final CardImageService cards;
    private final ServerService server;
    private final UiPageService uiPages;

    public GatewayAppClient(String serverUrl, String wsUrl) {
        this(new RestTemplate(), serverUrl, wsUrl, new ClientTokenHolder(), GatewayAsync.cached(Runnable::run));
    }

    public GatewayAppClient(
            RestTemplate restTemplate,
            String serverUrl,
            String wsUrl,
            ClientTokenHolder tokenHolder,
            GatewayAsync async
    ) {
        this.tokenHolder = tokenHolder;
        this.wsUrl = wsUrl;
        this.async = async;
        this.authentication = new AuthenticationService(restTemplate, serverUrl, tokenHolder);
        this.profile = new ProfileService(restTemplate, serverUrl, tokenHolder);
        this.lobby = new LobbyService(restTemplate, serverUrl, tokenHolder);
        this.game = new GameService(restTemplate, serverUrl, tokenHolder);
        this.friend = new FriendService(restTemplate, serverUrl, tokenHolder);
        this.chat = new ChatService(restTemplate, serverUrl, tokenHolder);
        this.notification = new NotificationService(restTemplate, serverUrl, tokenHolder);
        this.users = new UserSearchService(restTemplate, serverUrl);
        this.cards = new CardImageService(restTemplate, serverUrl, tokenHolder);
        this.server = new ServerService(restTemplate, serverUrl);
        this.uiPages = new UiPageService(restTemplate, serverUrl, tokenHolder);
    }

    public GatewayAsync async() {
        return async;
    }

    public ClientTokenHolder tokenHolder() {
        return tokenHolder;
    }

    public AuthenticationService authentication() {
        return authentication;
    }

    public ProfileService profile() {
        return profile;
    }

    public LobbyService lobby() {
        return lobby;
    }

    public GameService game() {
        return game;
    }

    public FriendService friend() {
        return friend;
    }

    public ChatService chat() {
        return chat;
    }

    public NotificationService notification() {
        return notification;
    }

    public UserSearchService users() {
        return users;
    }

    public CardImageService cards() {
        return cards;
    }

    public ServerService server() {
        return server;
    }

    public UiPageService uiPages() {
        return uiPages;
    }

    public CompletableFuture<GameWsService> gameSocket() {
        return connect(new GameWsService(wsUrl, tokenHolder));
    }

    public CompletableFuture<LobbyWsService> lobbySocket() {
        return connect(new LobbyWsService(wsUrl, tokenHolder));
    }

    public CompletableFuture<ChatWsService> chatSocket() {
        return connect(new ChatWsService(wsUrl, tokenHolder));
    }

    public CompletableFuture<NotificationWsService> notificationSocket() {
        return connect(new NotificationWsService(wsUrl, tokenHolder));
    }

    private <T extends StompGatewayService> CompletableFuture<T> connect(T socket) {
        var ready = new CompletableFuture<T>();
        socket.connect(() -> ready.complete(socket), ready::completeExceptionally);
        return ready;
    }

    @Override
    public void close() {
        async.close();
    }
}
