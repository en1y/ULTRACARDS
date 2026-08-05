package com.ultracards.server.service.games.briskula;

import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.gateway.dto.games.games.GameCardDTO;
import com.ultracards.gateway.dto.games.games.GameEventDTO;
import com.ultracards.gateway.dto.games.games.briskula.BriskulaGameResultDTO;
import com.ultracards.gateway.dto.games.games.treseta.TresetaGameResultDTO;
import com.ultracards.server.entity.games.GameEntity;
import com.ultracards.games.durak.DurakErrorCode;
import com.ultracards.server.entity.games.briskula.BriskulaGameEntity;
import com.ultracards.server.entity.games.briskula.BriskulaPlayerEntity;
import com.ultracards.server.entity.games.durak.DurakGameEntity;
import com.ultracards.server.entity.games.durak.DurakPlayerEntity;
import com.ultracards.server.entity.games.treseta.TresetaGameEntity;
import com.ultracards.server.entity.games.treseta.TresetaPlayerEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.ultracards.gateway.dto.games.games.GameEventDTO.GameEventTypeDTO;

@Service
@RequiredArgsConstructor
public class GameEventPublisher {
    private final SimpMessagingTemplate messagingTemplate;

    public void publish(GameEntity<?, ?> gameEntity, GameEventTypeDTO gameEventDTO) {
        if (gameEntity.getGameType().equals(GameTypeDTO.Briskula)) {
            var briskulaGame = (BriskulaGameEntity) gameEntity;
            var event = new GameEventDTO(briskulaGame.createGameDTO(), gameEventDTO);
            if (gameEventDTO.equals(GameEventTypeDTO.RESULTED)) {
                var winners = briskulaGame.getGame().determineGameWinners();
                var res = new ArrayList<GamePlayerDTO>();
                for (var w: winners) {
                    res.add(((BriskulaPlayerEntity)w).getGamePlayerDTO());
                }
                var points = winners.getFirst().getPoints();
                event.setResult(new BriskulaGameResultDTO(res, points));
            }
            messagingTemplate.convertAndSend("/topic/game/" + gameEntity.getId(), event);
            if (!gameEventDTO.equals(GameEventTypeDTO.RESULTED)) {
                for (var p : briskulaGame.getGame().getPlayers()) {
                    var player = (BriskulaPlayerEntity) p;
                    var cards = player.getHand().getCards().stream()
                            .map(GameCardDTO::createCardDTO)
                            .toList();
                    messagingTemplate.convertAndSendToUser(
                            player.getUser().getId().toString(),
                            "/queue/game/cards",
                            cards
                    );
                }
            }
        }
        if (gameEntity.getGameType().equals(GameTypeDTO.Treseta)) {
            var game = (TresetaGameEntity) gameEntity;
            var event = new GameEventDTO(game.createGameDTO(), gameEventDTO);
            if (gameEventDTO.equals(GameEventTypeDTO.RESULTED)) {
                var winners = game.getGame().determineGameWinners();
                var result = new ArrayList<GamePlayerDTO>();
                for (var winner : winners) result.add(((TresetaPlayerEntity) winner).getGamePlayerDTO());
                event.setResult(new TresetaGameResultDTO(result, winners.getFirst().getPoints()));
            }
            messagingTemplate.convertAndSend("/topic/game/" + game.getId(), event);
            if (!gameEventDTO.equals(GameEventTypeDTO.RESULTED))
                for (var raw : game.getGame().getPlayers()) {
                    var player = (TresetaPlayerEntity) raw;
                    var cards = player.getHand().getCards().stream().map(GameCardDTO::createCardDTO).toList();
                    messagingTemplate.convertAndSendToUser(player.getUser().getId().toString(), "/queue/game/cards", cards);
                }
        }
        if (gameEntity.getGameType().equals(GameTypeDTO.Durak)) {
            publishDurak((DurakGameEntity) gameEntity, gameEventDTO);
        }
    }

    /**
     * Publishes the public state first, then each player's authoritative hand and their tailored
     * legal actions, so a newer public state is never followed by an older private hand.
     */
    private void publishDurak(DurakGameEntity game, GameEventTypeDTO eventType) {
        var event = new GameEventDTO(game.createGameDTO(), eventType);
        if (eventType.equals(GameEventTypeDTO.RESULTED)) {
            event.setResult(game.createResultDTO());
        }
        messagingTemplate.convertAndSend("/topic/game/" + game.getId(), event);
        if (eventType.equals(GameEventTypeDTO.RESULTED)) {
            return; // no cards are published once the result is out
        }
        for (var raw : game.getGame().getPlayers()) {
            var player = (DurakPlayerEntity) raw;
            var userId = player.getUser().getId().toString();
            var cards = player.getHand().getCards().stream().map(GameCardDTO::createCardDTO).toList();
            messagingTemplate.convertAndSendToUser(userId, "/queue/game/cards", cards);
            messagingTemplate.convertAndSendToUser(userId, "/queue/game/durak-actions", game.legalActions(player));
        }
    }

    /**
     * Pushes the just-ended bout out on its own, ahead of the state that replaces it.
     * Without it the deciding card is never on any state a client receives, so it hangs
     * mid-flight and the round ends with no pause at all.
     */
    public void publishDurakBoutClosed(DurakGameEntity game) {
        var closing = game.createClosingGameDTO();
        if (closing == null) return;
        messagingTemplate.convertAndSend("/topic/game/" + game.getId(),
                new GameEventDTO(closing, GameEventTypeDTO.UPDATED));
    }

    /** Reports a rejected Durak action to the acting user only. */
    public void publishDurakError(DurakGameEntity game, Long userId, DurakErrorCode code, String message) {
        messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/game/errors",
                new DurakActionError(code.name(), message, game.getStateRevision()));
    }

    public record DurakActionError(String code, String message, long currentRevision) {
    }

    public void publishOpponentDrawnCards(TresetaGameEntity game, Map<Long, List<com.ultracards.games.treseta.TresetaCard>> handsBeforePlay) {
        for (var raw : game.getGame().getPlayers()) {
            var drawer = (TresetaPlayerEntity) raw;
            var previous = handsBeforePlay.getOrDefault(drawer.getUser().getId(), List.of());
            var drawn = drawer.getHand().getCards().stream()
                    .filter(card -> !previous.contains(card))
                    .map(GameCardDTO::createCardDTO)
                    .toList();
            if (drawn.isEmpty()) continue;
            for (var recipientRaw : game.getGame().getPlayers()) {
                var recipient = (TresetaPlayerEntity) recipientRaw;
                if (!recipient.equals(drawer))
                    messagingTemplate.convertAndSendToUser(recipient.getUser().getId().toString(),
                            "/queue/game/opponent-drawn-cards", drawn);
            }
        }
    }
}
