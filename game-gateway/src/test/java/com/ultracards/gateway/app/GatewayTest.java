package com.ultracards.gateway.app;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.games.games.GameCardDTO;
import com.ultracards.gateway.dto.games.games.GameEventDTO;
import com.ultracards.gateway.dto.games.games.GameEntityDTO;
import com.ultracards.gateway.dto.games.games.GameResultDTO;
import com.ultracards.gateway.dto.games.games.GameSnapshotDTO;
import com.ultracards.gateway.dto.games.games.treseta.TresetaGameConfigDTO;
import com.ultracards.gateway.dto.games.games.treseta.TresetaGameEntityDTO;
import com.ultracards.gateway.service.ClientTokenHolder;
import com.ultracards.gateway.service.GameWsService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayTest {
    @Test
    void tokenIsVisibleAcrossGatewayThreads() throws Exception {
        var token = ClientTokenHolder.class.getDeclaredField("token");
        assertTrue(Modifier.isVolatile(token.getModifiers()));
    }

    @Test
    void socketCleanupHandlesAreIdempotent() {
        var unsubscribes = new AtomicInteger();
        var disconnects = new AtomicInteger();
        var handle = new GatewaySocketHandle(disconnects::incrementAndGet);
        handle.track(subscription(unsubscribes));

        handle.close();
        handle.close();

        assertEquals(1, unsubscribes.get());
        assertEquals(1, disconnects.get());

        var lateUnsubscribes = new AtomicInteger();
        handle.track(subscription(lateUnsubscribes));
        assertEquals(1, lateUnsubscribes.get());
    }

    @Test
    void stateAndAsyncDispatchCanDriveAUiViewModel() {
        var state = new GatewayState<>("initial");
        var seen = new AtomicReference<String>();
        var listener = state.listen(seen::set);

        state.set("updated");
        assertEquals("updated", seen.get());

        listener.close();
        state.set("ignored");
        assertEquals("updated", seen.get());

        var async = GatewayAsync.direct();
        assertEquals("done", async.call(() -> "done").join());
        async.onUi(CompletableFuture.completedFuture("ui"), seen::set, null);
        assertEquals("ui", seen.get());
    }

    @Test
    void typedTresetaEventsRoundTripThroughJackson() throws Exception {
        var player = new GamePlayerDTO("player", 1L);
        var config = new TresetaGameConfigDTO(2, 10, false, List.of(player));
        var game = new TresetaGameEntityDTO(UUID.randomUUID(), UUID.randomUUID(), "Treseta",
                List.of(player), Map.of(player, 10), List.of(), 20, Map.of(player, 0), player,
                null, 30, config);
        var event = new GameEventDTO<>(game, GameEventDTO.GameEventTypeDTO.RESULTED);
        event.setResult(new GameResultDTO(List.of(player), 33));

        var mapper = new ObjectMapper();
        var json = mapper.writeValueAsString(event);
        var copy = mapper.readValue(json, new TypeReference<GameEventDTO<TresetaGameEntityDTO>>() {});

        assertInstanceOf(TresetaGameEntityDTO.class, copy.getGameEntity());
        assertInstanceOf(TresetaGameConfigDTO.class, copy.getGameEntity().getGameConfig());
        assertEquals(33, copy.getResult().getWinnerPointsNum());

        var generic = mapper.readValue(json, new TypeReference<GameEventDTO<GameEntityDTO>>() {});
        assertInstanceOf(GameEntityDTO.class, generic.getGameEntity());
        assertEquals("Treseta", generic.getGameEntity().getName());
    }

    @Test
    void tresetaSessionMarshalsSocketUpdatesAndOwnsCleanup() {
        var uiTasks = new ArrayDeque<Runnable>();
        var async = new GatewayAsync(Runnable::run, uiTasks::add);
        var socket = new FakeGameSocket();
        var session = GatewayGameSession.treseta(UUID.randomUUID(), socket, async, socket::close);
        var game = new TresetaGameEntityDTO();
        var event = new GameEventDTO<>(game, GameEventDTO.GameEventTypeDTO.UPDATED);

        socket.gameHandler.accept(event);
        assertNull(session.game().get());
        uiTasks.remove().run();
        assertSame(game, session.game().get());

        var card = new GameCardDTO();
        session.playCard(card);
        assertSame(card, socket.playedCard);

        session.close();
        session.close();
        assertTrue(socket.closed);
        assertEquals(1, socket.closeCalls);
    }

    @Test
    void sessionSeedFillsInitialStateButNeverOverwritesSocketUpdates() {
        var uiTasks = new ArrayDeque<Runnable>();
        var async = new GatewayAsync(Runnable::run, uiTasks::add);
        var socket = new FakeGameSocket();
        var session = GatewayGameSession.treseta(UUID.randomUUID(), socket, async, socket::close);

        var seededGame = new TresetaGameEntityDTO();
        session.seed(new GameSnapshotDTO<>(seededGame, List.of(new GameCardDTO())));
        while (!uiTasks.isEmpty()) uiTasks.remove().run();
        assertSame(seededGame, session.game().get());
        assertEquals(1, session.hand().get().size());

        var liveGame = new TresetaGameEntityDTO();
        socket.gameHandler.accept(new GameEventDTO<>(liveGame, GameEventDTO.GameEventTypeDTO.UPDATED));
        session.seed(new GameSnapshotDTO<>(seededGame, List.of()));
        while (!uiTasks.isEmpty()) uiTasks.remove().run();
        assertSame(liveGame, session.game().get());
        assertEquals(1, session.hand().get().size());

        session.close();
    }

    @Test
    void appClientRejectsConnectionsThatFinishAfterClose() {
        var client = client();
        var inFlight = new FakeGameSocket();
        var ready = client.connect(inFlight);

        client.close();
        inFlight.completeConnection();

        assertThrows(CompletionException.class, ready::join);
        assertTrue(inFlight.closed);

        var rejected = new FakeGameSocket();
        var rejectedReady = client.connect(rejected);
        assertThrows(CompletionException.class, rejectedReady::join);
        assertEquals(0, rejected.connectCalls);
        assertTrue(rejected.closed);
    }

    @Test
    void typedSessionReleasesItsSocketFromTheAppClient() {
        var client = client();
        var socket = new FakeGameSocket();
        var ready = client.connect(socket);
        socket.completeConnection();
        assertSame(socket, ready.join());

        var session = GatewayGameSession.treseta(
                UUID.randomUUID(), socket, GatewayAsync.direct(), () -> client.releaseSocket(socket));
        session.close();
        session.close();
        assertEquals(1, socket.closeCalls);

        client.close();
        assertEquals(1, socket.closeCalls);
    }

    private static GatewayAppClient client() {
        return new GatewayAppClient(new RestTemplate(), "http://localhost", "ws://localhost/ws",
                new ClientTokenHolder(), GatewayAsync.direct());
    }

    private static StompSession.Subscription subscription(AtomicInteger unsubscribes) {
        return (StompSession.Subscription) Proxy.newProxyInstance(
                GatewayTest.class.getClassLoader(),
                new Class<?>[]{StompSession.Subscription.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("unsubscribe")) unsubscribes.incrementAndGet();
                    if (method.getReturnType().equals(boolean.class)) return false;
                    return null;
                });
    }

    private static final class FakeGameSocket extends GameWsService {
        private Consumer<GameEventDTO<TresetaGameEntityDTO>> gameHandler;
        private Runnable connected;
        private GameCardDTO playedCard;
        private boolean closed;
        private int connectCalls;
        private int closeCalls;

        private FakeGameSocket() {
            super("ws://localhost/ws", new ClientTokenHolder());
        }

        @Override
        public void connect(Runnable onConnected, Consumer<Throwable> onError) {
            connectCalls++;
            connected = onConnected;
        }

        private void completeConnection() {
            connected.run();
        }

        @Override
        public StompSession.Subscription subscribeToTresetaGame(
                UUID gameId, Consumer<GameEventDTO<TresetaGameEntityDTO>> handler) {
            gameHandler = handler;
            return null;
        }

        @Override
        public StompSession.Subscription subscribeToCards(Consumer<List<GameCardDTO>> handler) {
            return null;
        }

        @Override
        public StompSession.Subscription subscribeToTeammateCards(Consumer<List<GameCardDTO>> handler) {
            return null;
        }

        @Override
        public void playCard(GameCardDTO card) {
            playedCard = card;
        }

        @Override
        public void close() {
            closeCalls++;
            if (closed) return;
            closed = true;
            super.close();
        }
    }
}
