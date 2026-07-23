package com.ultracards.gateway.app;

import com.ultracards.gateway.dto.games.games.GameEntityDTO;
import com.ultracards.gateway.dto.games.games.GameSnapshotDTO;
import com.ultracards.gateway.dto.games.games.briskula.BriskulaGameEntityDTO;
import com.ultracards.gateway.dto.games.games.treseta.TresetaGameEntityDTO;
import com.ultracards.gateway.service.AuthenticationService;
import com.ultracards.gateway.service.AdminService;
import com.ultracards.gateway.service.CardImageService;
import com.ultracards.gateway.service.ChatService;
import com.ultracards.gateway.service.ChatWsService;
import com.ultracards.gateway.service.ClientTokenHolder;
import com.ultracards.gateway.service.FriendService;
import com.ultracards.gateway.service.GameService;
import com.ultracards.gateway.service.GameWsService;
import com.ultracards.gateway.service.LobbyService;
import com.ultracards.gateway.service.LeaderboardService;
import com.ultracards.gateway.service.LobbyWsService;
import com.ultracards.gateway.service.NotificationService;
import com.ultracards.gateway.service.NotificationWsService;
import com.ultracards.gateway.service.ProfileService;
import com.ultracards.gateway.service.ServerService;
import com.ultracards.gateway.service.StompGatewayService;
import com.ultracards.gateway.service.UiPageService;
import com.ultracards.gateway.service.UserSearchService;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

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
    private final AdminService admin;
    private final LeaderboardService leaderboards;
    private final List<StompGatewayService> sockets = new CopyOnWriteArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean();

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
        this.admin = new AdminService(restTemplate, serverUrl, tokenHolder);
        this.leaderboards = new LeaderboardService(restTemplate, serverUrl, tokenHolder);
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

    public AdminService admin() {
        return admin;
    }

    public LeaderboardService leaderboards() {
        return leaderboards;
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

    public CompletableFuture<GatewayGameSession<BriskulaGameEntityDTO>> briskulaGame(UUID gameId) {
        return gameSocket().thenApply(socket -> GatewayGameSession.briskula(
                gameId, socket, async, () -> releaseSocket(socket)))
                .thenCompose(session -> seedSession(session, () -> game.getBriskulaSnapshot(gameId)));
    }

    public CompletableFuture<GatewayGameSession<TresetaGameEntityDTO>> tresetaGame(UUID gameId) {
        return gameSocket().thenApply(socket -> GatewayGameSession.treseta(
                gameId, socket, async, () -> releaseSocket(socket)))
                .thenCompose(session -> seedSession(session, () -> game.getTresetaSnapshot(gameId)));
    }

    private <T extends GameEntityDTO> CompletableFuture<GatewayGameSession<T>> seedSession(
            GatewayGameSession<T> session, Supplier<GameSnapshotDTO<T>> snapshot) {
        return async.call(snapshot).handle((value, error) -> {
            if (error != null) {
                try {
                    session.close();
                } catch (RuntimeException closeError) {
                    error.addSuppressed(closeError);
                }
                throw error instanceof CompletionException completion
                        ? completion : new CompletionException(error);
            }
            session.seed(value);
            return session;
        });
    }

    <T extends StompGatewayService> CompletableFuture<T> connect(T socket) {
        var ready = new CompletableFuture<T>();
        if (closed.get()) {
            var error = closedException();
            closeRejectedSocket(socket, error);
            return CompletableFuture.failedFuture(error);
        }

        sockets.add(socket);
        if (closed.get()) {
            releaseSocket(socket);
            return CompletableFuture.failedFuture(closedException());
        }

        try {
            socket.connect(() -> {
                if (closed.get()) {
                    ready.completeExceptionally(closedException());
                    releaseSocket(socket);
                } else if (!ready.complete(socket)) {
                    releaseSocket(socket);
                }
            }, error -> {
                ready.completeExceptionally(error);
                releaseSocket(socket);
            });
        } catch (RuntimeException error) {
            ready.completeExceptionally(error);
            releaseSocket(socket);
        }
        return ready;
    }

    void releaseSocket(StompGatewayService socket) {
        sockets.remove(socket);
        socket.close();
    }

    private void closeRejectedSocket(StompGatewayService socket, RuntimeException error) {
        try {
            socket.close();
        } catch (RuntimeException closeError) {
            error.addSuppressed(closeError);
        }
    }

    private IllegalStateException closedException() {
        return new IllegalStateException("Gateway client is closed.");
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        RuntimeException failure = null;
        for (var socket : sockets) {
            try {
                socket.close();
            } catch (RuntimeException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        sockets.clear();
        try {
            async.close();
        } catch (RuntimeException error) {
            if (failure == null) failure = error;
            else failure.addSuppressed(error);
        }
        if (failure != null) throw failure;
    }
}
