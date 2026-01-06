package com.ultracards.server.service.games;

import com.ultracards.cards.ItalianCard;
import com.ultracards.games.briskula.BriskulaCard;
import com.ultracards.gateway.dto.updated.games.games.GameCardDTO;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.GameEntity;
import com.ultracards.server.entity.games.briskula.BriskulaGameEntity;
import com.ultracards.server.entity.games.briskula.BriskulaPlayerEntity;
import com.ultracards.server.entity.lobby.LobbyEntity;
import com.ultracards.server.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.ultracards.gateway.dto.updated.games.games.GameEventDTO.GameEventTypeDTO.*;

@Service
@RequiredArgsConstructor
public class GameService {
    private final GameManager gameManager;
    private final GameEventPublisher eventPublisher;
    private final UserService userService;

    public GameEntity<?> startGame(LobbyEntity lobby) {
        var game = gameManager.createGame(lobby.createGame());
        eventPublisher.publish(game, STARTED);
        return game;
    }

    public void playCard(UserEntity user, GameCardDTO cardDTO) {
        var game = gameManager.getGame(user.getId());
        var genericCard = cardDTO.toCard();
        if (game instanceof BriskulaGameEntity && genericCard instanceof ItalianCard<?>) {
            var card = new BriskulaCard(((ItalianCard<?>) genericCard).getSuit(), ((ItalianCard<?>) genericCard).getValue());
            var playingField = ((BriskulaGameEntity) game).getGame().getPlayingField();
            var playerEntity = (BriskulaPlayerEntity) playingField.getCurrentPlayer();
            if (playerEntity.getUser().getId().equals(user.getId())) {
                playingField.play(card, playerEntity);
                eventPublisher.publish(game, UPDATED);
                if (!game.getGame().isGameActive()) {
                    eventPublisher.publish(game, RESULTED);
                }
            }
        }
    }
}
