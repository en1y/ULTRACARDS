package com.ultracards.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.games.games.GameCardDTO;
import com.ultracards.gateway.dto.games.games.GameEventDTO;
import com.ultracards.gateway.dto.games.games.treseta.TresetaGameConfigDTO;
import com.ultracards.gateway.dto.games.games.treseta.TresetaGameEntityDTO;
import com.ultracards.gateway.dto.games.games.treseta.TresetaGameHistoryDTO;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class StompGatewayServiceTest {
    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void decodesGenericStompPayloadsWithTheirFullTypes() throws Exception {
        var player = new GamePlayerDTO("player", 1L);
        var config = new TresetaGameConfigDTO(2, 10, false, List.of(player));
        var game = new TresetaGameEntityDTO(UUID.randomUUID(), UUID.randomUUID(), "Treseta",
                List.of(player), Map.of(player, 10), List.of(), 20, Map.of(player, 0), player,
                Instant.parse("2026-07-11T10:00:00Z"), 30, config);
        var event = new GameEventDTO<>(game, GameEventDTO.GameEventTypeDTO.UPDATED);
        var eventType = new ParameterizedTypeReference<GameEventDTO<TresetaGameEntityDTO>>() {}.getType();

        var decodedEvent = StompGatewayService.<GameEventDTO<TresetaGameEntityDTO>>decode(
                mapper, mapper.writeValueAsBytes(event), eventType);
        var decodedCards = StompGatewayService.<List<GameCardDTO>>decode(mapper,
                "[{\"cardType\":\"ITALIAN\",\"card\":\"B1\"}]".getBytes(),
                new ParameterizedTypeReference<List<GameCardDTO>>() {}.getType());

        assertInstanceOf(TresetaGameEntityDTO.class, decodedEvent.getGameEntity());
        assertInstanceOf(TresetaGameConfigDTO.class, decodedEvent.getGameEntity().getGameConfig());
        assertEquals(Instant.parse("2026-07-11T10:00:00Z"), decodedEvent.getGameEntity().getTurnEndTime());
        assertInstanceOf(GameCardDTO.class, decodedCards.getFirst());
    }

    @Test
    void decodesHistoryMapsAtEveryLevel() throws Exception {
        var player = new GamePlayerDTO("Doe, John", 1L);
        var config = new TresetaGameConfigDTO(2, 10, false, List.of(player));
        var round = new TresetaGameHistoryDTO.TresetaRoundHistoryDTO(0, Map.of(player, List.of()),
                List.of(), player, 3, Map.of(player, 3));
        var history = new TresetaGameHistoryDTO(UUID.randomUUID(), UUID.randomUUID(), "game", player,
                null, null, config, List.of(player), List.of(), List.of(round), Map.of(player, 3), List.of(player));

        var copy = StompGatewayService.<TresetaGameHistoryDTO>decode(
                mapper, mapper.writeValueAsBytes(history), TresetaGameHistoryDTO.class);

        assertEquals(3, copy.getFinalPoints().get(player));
        assertEquals(List.of(), copy.getRounds().getFirst().getPlayerHands().get(player));
        assertEquals(3, copy.getRounds().getFirst().getPointsAfterRound().get(player));
    }
}
