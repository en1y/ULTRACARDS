package com.ultracards.server.controllers.games;

import com.ultracards.server.service.games.GameManager;
import com.ultracards.server.service.games.GameService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GameWsControllerTest {

    @Test
    void returnsBadRequestPayloadForInvalidMoves() {
        var controller = new GameWsController(new GameManager(), mock(GameService.class));

        var error = controller.handleInvalidMove(new IllegalArgumentException("Follow suit"));

        assertThat(error.status()).isEqualTo(400);
        assertThat(error.message()).isEqualTo("Follow suit");
    }
}
