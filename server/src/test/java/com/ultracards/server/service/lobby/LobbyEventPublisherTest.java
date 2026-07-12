package com.ultracards.server.service.lobby;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultracards.gateway.dto.games.lobby.GameLobbyDTO;
import com.ultracards.gateway.dto.games.lobby.GameLobbyEventDTO;
import com.ultracards.server.entity.games.GameEntity;
import com.ultracards.server.entity.lobby.LobbyEntity;
import com.ultracards.server.service.games.GameManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.UUID;

import static com.ultracards.gateway.dto.games.lobby.GameLobbyEventDTO.GameLobbyEventType.STARTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class LobbyEventPublisherTest {

    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final GameManager gameManager = mock(GameManager.class);
    private final LobbyEventPublisher publisher = new LobbyEventPublisher(messagingTemplate, gameManager);

    @Test
    @SuppressWarnings("unchecked")
    void startedPublishesOneTypedPrivateEventContainingTheGameId() throws Exception {
        var lobbyId = UUID.randomUUID();
        var gameId = UUID.randomUUID();
        var publicLobby = new GameLobbyDTO();
        publicLobby.setId(lobbyId);
        var privateLobby = new GameLobbyDTO();
        privateLobby.setId(lobbyId);
        var lobby = mock(LobbyEntity.class);
        var game = mock(GameEntity.class);
        when(lobby.createLobbyDTO(false)).thenReturn(publicLobby);
        when(lobby.createLobbyDTO(true)).thenReturn(privateLobby);
        when(gameManager.getGameByLobbyId(lobbyId)).thenReturn(game);
        when(game.getId()).thenReturn(gameId);

        publisher.publish(lobby, STARTED);

        verify(messagingTemplate).convertAndSend(eq("/topic/lobbies"), any(GameLobbyEventDTO.class));
        var payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/lobbies/" + lobbyId), payload.capture());
        verifyNoMoreInteractions(messagingTemplate);

        var event = assertInstanceOf(GameLobbyEventDTO.class, payload.getValue());
        assertEquals(STARTED, event.getType());
        assertEquals(gameId, event.getGameId());

        var mapper = new ObjectMapper();
        var json = mapper.writeValueAsBytes(event);
        var decoded = mapper.readValue(json, GameLobbyEventDTO.class);
        assertEquals(gameId, decoded.getGameId());
        assertEquals(lobbyId, decoded.getLobbyDto().getId());
        assertNull(new GameLobbyEventDTO(STARTED, privateLobby).getGameId());
    }
}
