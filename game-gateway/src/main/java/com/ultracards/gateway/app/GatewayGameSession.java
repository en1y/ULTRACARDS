package com.ultracards.gateway.app;

import com.ultracards.gateway.dto.games.games.GameCardDTO;
import com.ultracards.gateway.dto.games.games.GameEntityDTO;
import com.ultracards.gateway.dto.games.games.GameEventDTO;
import com.ultracards.gateway.dto.games.games.GameSnapshotDTO;
import com.ultracards.gateway.dto.games.games.briskula.BriskulaGameEntityDTO;
import com.ultracards.gateway.dto.games.games.treseta.TresetaGameEntityDTO;
import com.ultracards.gateway.service.GameWsService;
import org.springframework.messaging.simp.stomp.StompSession;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public final class GatewayGameSession<T extends GameEntityDTO> implements AutoCloseable {
    private final GameWsService socket;
    private final GatewayAsync async;
    private final GatewaySocketHandle handle;
    private final GatewayState<GameEventDTO<T>> event = new GatewayState<>();
    private final GatewayState<T> game = new GatewayState<>();
    private final GatewayState<List<GameCardDTO>> hand = new GatewayState<>(List.of());
    private final GatewayState<List<GameCardDTO>> teammateHand = new GatewayState<>(List.of());
    private final GatewayState<List<GameCardDTO>> opponentDrawnCards = new GatewayState<>(List.of());

    private GatewayGameSession(UUID gameId, GameWsService socket, GatewayAsync async, Runnable closeSocket,
                               BiFunction<UUID, Consumer<GameEventDTO<T>>, StompSession.Subscription> subscribe) {
        this.socket = socket;
        this.async = async;
        this.handle = new GatewaySocketHandle(closeSocket);
        try {
            handle.track(subscribe.apply(gameId, value -> async.runOnUi(() -> {
                event.set(value);
                game.set(value.getGameEntity());
            })));
            handle.track(socket.subscribeToCards(cards -> async.runOnUi(() -> hand.set(List.copyOf(cards)))));
        } catch (RuntimeException | Error error) {
            try {
                handle.close();
            } catch (RuntimeException closeError) {
                error.addSuppressed(closeError);
            }
            throw error;
        }
    }

    static GatewayGameSession<BriskulaGameEntityDTO> briskula(
            UUID gameId, GameWsService socket, GatewayAsync async, Runnable closeSocket
    ) {
        var session = new GatewayGameSession<>(gameId, socket, async, closeSocket, socket::subscribeToBriskulaGame);
        session.subscribeToTeammateCards();
        return session;
    }

    static GatewayGameSession<TresetaGameEntityDTO> treseta(
            UUID gameId, GameWsService socket, GatewayAsync async, Runnable closeSocket
    ) {
        var session = new GatewayGameSession<>(gameId, socket, async, closeSocket, socket::subscribeToTresetaGame);
        session.subscribeToOpponentDrawnCards();
        return session;
    }

    public GatewayState<GameEventDTO<T>> event() { return event; }
    public GatewayState<T> game() { return game; }
    public GatewayState<List<GameCardDTO>> hand() { return hand; }
    public GatewayState<List<GameCardDTO>> teammateHand() { return teammateHand; }
    public GatewayState<List<GameCardDTO>> opponentDrawnCards() { return opponentDrawnCards; }

    public void playCard(GameCardDTO card) {
        socket.playCard(card);
    }

    void seed(GameSnapshotDTO<T> snapshot) {
        if (snapshot == null) return;
        async.runOnUi(() -> {
            if (snapshot.getGame() != null && game.get() == null) game.set(snapshot.getGame());
            // ponytail: an empty hand delivered over WS before the seed lands gets overwritten
            // with the snapshot hand; harmless because the snapshot is at least as fresh
            if (snapshot.getHand() != null && hand.get().isEmpty()) hand.set(List.copyOf(snapshot.getHand()));
        });
    }

    private void subscribeToTeammateCards() {
        subscribe(socket.subscribeToTeammateCards(cards -> async.runOnUi(() -> teammateHand.set(List.copyOf(cards)))));
    }

    private void subscribeToOpponentDrawnCards() {
        subscribe(socket.subscribeToOpponentDrawnCards(cards -> async.runOnUi(() -> {
            var drawn = new ArrayList<>(opponentDrawnCards.get());
            drawn.addAll(cards);
            opponentDrawnCards.set(List.copyOf(drawn));
        })));
    }

    private void subscribe(StompSession.Subscription subscription) {
        try {
            handle.track(subscription);
        } catch (RuntimeException | Error error) {
            try {
                handle.close();
            } catch (RuntimeException closeError) {
                error.addSuppressed(closeError);
            }
            throw error;
        }
    }

    @Override
    public void close() {
        handle.close();
    }
}
