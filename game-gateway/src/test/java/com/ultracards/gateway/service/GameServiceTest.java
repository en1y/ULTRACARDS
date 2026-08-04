package com.ultracards.gateway.service;

import com.ultracards.gateway.dto.games.games.GameSnapshotDTO;
import com.ultracards.gateway.dto.games.games.durak.DurakGameEntityDTO;
import com.ultracards.gateway.dto.games.games.durak.DurakGameHistoryDTO;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class GameServiceTest {
    @Test
    void durakRestMethodsUseTypedServerEndpoints() {
        var template = new RecordingRestTemplate();
        var service = new GameService(template, "http://localhost:8080", new ClientTokenHolder());
        var gameId = UUID.randomUUID();
        var lobbyId = UUID.randomUUID();

        assertSame(template.history, service.getDurakGameHistory(gameId));
        assertSame(template.game, service.getDurakGameByLobby(lobbyId));
        assertSame(template.snapshot, service.getDurakSnapshot(gameId));
        assertEquals(List.of(
                "http://localhost:8080/api/games/history/durak/" + gameId,
                "http://localhost:8080/api/games/lobby/" + lobbyId,
                "http://localhost:8080/api/games/" + gameId + "/snapshot/durak"
        ), template.urls);
    }

    private static final class RecordingRestTemplate extends RestTemplate {
        private final List<String> urls = new ArrayList<>();
        private final DurakGameHistoryDTO history = new DurakGameHistoryDTO();
        private final DurakGameEntityDTO game = new DurakGameEntityDTO();
        private final GameSnapshotDTO<DurakGameEntityDTO> snapshot = new GameSnapshotDTO<>(game, List.of());

        @Override
        @SuppressWarnings("unchecked")
        public <T> ResponseEntity<T> exchange(String url, HttpMethod method, HttpEntity<?> requestEntity,
                                              Class<T> responseType, Object... uriVariables) {
            urls.add(url);
            return ResponseEntity.ok((T) (responseType == DurakGameHistoryDTO.class ? history : game));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> ResponseEntity<T> exchange(String url, HttpMethod method, HttpEntity<?> requestEntity,
                                              ParameterizedTypeReference<T> responseType, Object... uriVariables) {
            urls.add(url);
            return ResponseEntity.ok((T) snapshot);
        }
    }
}
