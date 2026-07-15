package com.ultracards.server.controllers.games;

import com.ultracards.games.treseta.TresetaGameConfig;
import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.gateway.dto.games.games.ShortGameHistoryDTO;
import com.ultracards.gateway.dto.games.games.treseta.TresetaGameConfigDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.treseta.TresetaGameEntity;
import com.ultracards.server.entity.lobby.TresetaLobbyGameConfig;
import com.ultracards.server.service.games.GameManager;
import com.ultracards.server.service.games.briskula.BriskulaGameHistoryService;
import com.ultracards.server.service.games.treseta.TresetaGameHistoryService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GameControllerTest {
    private final GameManager gameManager = new GameManager();
    private final BriskulaGameHistoryService briskulaHistory = mock(BriskulaGameHistoryService.class);
    private final TresetaGameHistoryService tresetaHistory = mock(TresetaGameHistoryService.class);
    private final GameController controller = new GameController(gameManager, briskulaHistory, tresetaHistory);

    @Test
    void returnsTypedTresetaSnapshotWithOnlyRequestingPlayersHand() {
        var owner = user(1L, "Owner");
        var player = user(2L, "Player");
        var users = List.of(owner, player);
        var configDto = new TresetaGameConfigDTO(2, 10, false, null);
        var config = new TresetaLobbyGameConfig(configDto, users);
        var game = new TresetaGameEntity(UUID.randomUUID(), "game", owner, config, users);
        game.getGame().start();
        gameManager.createGame(game);

        var response = controller.getTresetaSnapshot(game.getId(), player);
        var outsider = controller.getTresetaSnapshot(game.getId(), user(3L, "Outsider"));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getGame()).isInstanceOf(com.ultracards.gateway.dto.games.games.treseta.TresetaGameEntityDTO.class);
        assertThat(response.getBody().getHand()).hasSize(TresetaGameConfig.TWO_PLAYERS.getCardsInHandNum());
        assertThat(outsider.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void paginatesAfterMergingAllGameTypes() {
        var user = user(1L, "Player");
        var games = new ArrayList<ShortGameHistoryDTO>();
        for (int i = 0; i < 25; i++)
            games.add(new ShortGameHistoryDTO(UUID.randomUUID(), UUID.randomUUID(), "game-" + i,
                    GameTypeDTO.Briskula, Instant.ofEpochSecond(i), Instant.ofEpochSecond(i),
                    null, List.of(), Map.of(), List.of()));
        when(briskulaHistory.getPastGames(user, "both", "latest")).thenReturn(games);
        when(tresetaHistory.getPastGames(user, "both", "latest")).thenReturn(List.of());

        var page = controller.getPastGames(user, 20, "both", "latest", "all").getBody();

        assertThat(page).hasSize(5);
        assertThat(page.getFirst().getEndedAt()).isEqualTo(Instant.ofEpochSecond(4));
    }

    @Test
    void filtersByGameTypeBeforePaginating() {
        var user = user(1L, "Player");
        var games = new ArrayList<ShortGameHistoryDTO>();
        for (int i = 0; i < 21; i++)
            games.add(new ShortGameHistoryDTO(UUID.randomUUID(), UUID.randomUUID(), "game-" + i,
                    GameTypeDTO.Treseta, Instant.ofEpochSecond(i), Instant.ofEpochSecond(i),
                    null, List.of(), Map.of(), List.of()));
        when(tresetaHistory.getPastGames(user, "both", "latest")).thenReturn(games);

        var page = controller.getPastGames(user, 20, "both", "latest", "treseta").getBody();

        assertThat(page).singleElement().extracting(ShortGameHistoryDTO::getEndedAt)
                .isEqualTo(Instant.EPOCH);
        verifyNoInteractions(briskulaHistory);
    }

    private UserEntity user(Long id, String name) {
        var user = new UserEntity(name + "@example.com", name);
        user.setId(id);
        return user;
    }
}
