package com.ultracards.server.controllers.games;

import com.ultracards.gateway.dto.games.HeartbeatDTO;
import com.ultracards.server.service.games.LobbyService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class LobbyWsController {

    private final LobbyService lobbyService;

    @MessageMapping("/lobbies/{lobbyId}/heartbeat")
    public void heartbeat(@DestinationVariable("lobbyId") UUID lobbyId,
                          @Payload HeartbeatDTO heartbeat) {
        // HeartbeatDTO now optionally carries userId; presence tracking relies on it here.
        if (heartbeat != null && heartbeat.getUserId() != null) {
            lobbyService.recordHeartbeat(lobbyId, heartbeat.getUserId());
        }
    }
}

