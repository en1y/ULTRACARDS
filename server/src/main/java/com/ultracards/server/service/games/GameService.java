package com.ultracards.server.service.games;

import com.ultracards.cards.ItalianCard;
import com.ultracards.gateway.dto.games.games.GameCardDTO;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.GameEntity;
import com.ultracards.server.entity.games.briskula.BriskulaGameEntity;
import com.ultracards.server.entity.lobby.LobbyEntity;
import com.ultracards.server.service.lobby.LobbyManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Optional;

import static com.ultracards.gateway.dto.games.games.GameEventDTO.GameEventTypeDTO.*;

@Service
@RequiredArgsConstructor
public class GameService {
    private final GameManager gameManager;
    private final GameEventPublisher eventPublisher;
    private final LobbyManager lobbyManager;
    private final HashMap<Long, GameEntity<?>> gameCache = new HashMap<>();

    public GameEntity<?> startGame(LobbyEntity lobby) {
        var game = gameManager.createGame(lobby.createGame());
        lobbyManager.putGame(lobby, game);
        eventPublisher.publish(game, STARTED);
        game.getPlayers().forEach(p -> gameCache.put(p.getId(), game));
        return game;
    }

    public Optional<GameEntity<?>> getGameByUser(UserEntity user) {
        return Optional.of(gameCache.get(user.getId()));
    }

    public void playCard(UserEntity user, GameCardDTO cardDTO) {
        var game = gameManager.getGame(user.getId());
        var genericCard = cardDTO.toCard();
        if (game instanceof BriskulaGameEntity game1 && genericCard instanceof ItalianCard<?>) {
            var success = game1.playCard(user, genericCard);
            if (success) {
                eventPublisher.publish(game, UPDATED);
                if (!game1.getGame().isGameActive()) {
                    eventPublisher.publish(game, RESULTED);
                    game.getPlayers().forEach(p -> gameCache.remove(p.getId()));
                }
            }
        }
    }
}
