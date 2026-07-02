package com.ultracards.server.service.games.briskula;

import com.ultracards.gateway.dto.games.games.GameCardDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.briskula.BriskulaGameEntity;
import com.ultracards.server.entity.games.briskula.BriskulaPlayerEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BriskulaEventPublisher {
    private final SimpMessagingTemplate messagingTemplate;

    public void publishTeammateHands(BriskulaGameEntity game) {
        var teamPlayers = game.getTeamPlayers();
        for (var p : game.getGame().getPlayers()) {
            var player = (BriskulaPlayerEntity) p;
            var teammateUser = determineTeammate(teamPlayers, player.getUser());
            if (teammateUser == null) continue;

            var teammatePlayer = determineLivePlayer(game, teammateUser);
            if (teammatePlayer == null) continue;

            var teammateCards = teammatePlayer.getHand().getCards().stream()
                    .map(GameCardDTO::createCardDTO)
                    .toList();

            messagingTemplate.convertAndSendToUser(
                    player.getUser().getId().toString(),
                    "/queue/game/teammate-cards",
                    teammateCards
            );
        }
    }

    private BriskulaPlayerEntity determineLivePlayer(BriskulaGameEntity game, UserEntity user) {
        for (var p : game.getGame().getPlayers()) {
            var player = (BriskulaPlayerEntity) p;
            if (player.getUser().equals(user)) return player;
        }
        return null;
    }

    private UserEntity determineTeammate(List<UserEntity> teamPlayers, UserEntity user) {
        for (int i = 0; i < teamPlayers.size(); i++) {
            if (teamPlayers.get(i).equals(user)) {
                return i < 2 ? teamPlayers.get(1 - i) : teamPlayers.get(5 - i);
            }
        }
        return null;
    }
}
