package com.ultracards.server.controllers.games;

import com.ultracards.gateway.dto.games.HeartbeatDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultracards.gateway.dto.games.PlayerActionDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.games.GameEventPublisher;
import com.ultracards.server.service.games.briskula.BriskulaRuntimeService;
import com.ultracards.server.service.games.briskula.BriskulaRuntimeService.PlayCardPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class GameWsController {

    private final GameEventPublisher eventPublisher;
    private final BriskulaRuntimeService briskulaRuntimeService;
    private final ObjectMapper objectMapper;

    @MessageMapping("/games/{gameId}/heartbeat")
    public void heartbeat(@DestinationVariable("gameId") UUID gameId, @Payload HeartbeatDTO heartbeat) {
        // Broadcast heartbeat presence to others
        eventPublisher.publish(gameId, "HEARTBEAT", "{\"sentAt\":\"" + Instant.now() + "\"}");
    }

    @MessageMapping("/games/{gameId}/action")
    public void onAction(@DestinationVariable("gameId") UUID gameId,
                         @Payload PlayerActionDTO action,
                         @AuthenticationPrincipal UserEntity user) {
        if (user == null) {
            throw new IllegalStateException("Unauthenticated user");
        }
        if ("PLAY_CARD".equalsIgnoreCase(action.getActionType())) {
            var payload = parsePlayCard(action.getPayloadJson());
            briskulaRuntimeService.handlePlayCard(gameId, user, payload);
            return;
        }
        String payload = action.getPayloadJson() != null ? action.getPayloadJson() : "{}";
        eventPublisher.publish(gameId, action.getActionType(), payload);
    }

    private PlayCardPayload parsePlayCard(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new IllegalArgumentException("Missing card payload");
        }
        try {
            return objectMapper.readValue(payloadJson, PlayCardPayload.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid card payload", ex);
        }
    }
}
