package com.ultracards.server.service.games.briskula;

import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.gateway.dto.games.games.GameCardDTO;
import com.ultracards.gateway.dto.games.games.GameEventDTO;
import com.ultracards.gateway.dto.games.games.briskula.BriskulaGameResultDTO;
import com.ultracards.server.entity.games.GameEntity;
import com.ultracards.server.entity.games.briskula.BriskulaGameEntity;
import com.ultracards.server.entity.games.briskula.BriskulaPlayerEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

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
    }
}
