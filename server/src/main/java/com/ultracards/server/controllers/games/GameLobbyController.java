package com.ultracards.server.controllers.games;

import com.ultracards.gateway.dto.games.*;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.games.GameService;
import com.ultracards.server.service.games.LobbyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameLobbyController {

    private final LobbyService lobbyService;
    private final GameService gameService;
    private final com.ultracards.server.service.games.GameRulesService rulesService;

    @PostMapping("/lobbies")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<LobbyDTO> createLobby(
            @AuthenticationPrincipal UserEntity user,
            @RequestBody @Valid LobbyCreateRequest req
    ) {
        var lobby = lobbyService.createLobby(user, req);
        return ResponseEntity.ok(toDto(lobby, req));
    }

    @GetMapping("/lobbies")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<List<LobbyDTO>> listLobbies() {
        var list = lobbyService.listLobbies().stream().map(l -> toDto(l, null)).toList();
        return ResponseEntity.ok(list);
    }

    @PostMapping("/lobbies/join")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<LobbyDTO> joinLobby(
            @AuthenticationPrincipal UserEntity user,
            @RequestBody @Valid LobbyJoinRequest req
    ) {
        var lobby = lobbyService.joinLobby(user, req.getLobbyId());
        return ResponseEntity.ok(toDto(lobby, null));
    }

    @PutMapping("/lobbies/{lobbyId}/settings")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<LobbyDTO> updateLobbySettings(
            @AuthenticationPrincipal UserEntity user,
            @PathVariable UUID lobbyId,
            @RequestBody LobbyCreateRequest req
    ) {
        try {
            var lobby = lobbyService.updateSettings(user, lobbyId, req.getLobbyName(), req.getMaxPlayers(), req.getConfigJson());
            return ResponseEntity.ok(toDto(lobby, req));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .header(org.springframework.http.HttpHeaders.WARNING, "PLAYER_COUNT_TOO_LOW")
                    .body(null);
        }
    }

    @PostMapping("/lobbies/{lobbyId}/kick")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<LobbyDTO> kickPlayer(
            @AuthenticationPrincipal UserEntity user,
            @PathVariable UUID lobbyId,
            @RequestBody @Valid LobbyKickRequest req
    ) {
        var lobby = lobbyService.kickPlayer(user, lobbyId, req.getPlayerId());
        return ResponseEntity.ok(toDto(lobby, null));
    }

    @DeleteMapping("/lobbies/{lobbyId}")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<Void> deleteLobby(
            @AuthenticationPrincipal UserEntity user,
            @PathVariable UUID lobbyId
    ) {
        lobbyService.disbandLobby(user, lobbyId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/lobbies/{lobbyId}/leave")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<Void> leaveLobby(
            @AuthenticationPrincipal UserEntity user,
            @PathVariable UUID lobbyId
    ) {
        lobbyService.leaveLobby(user, lobbyId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/start")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<GameDTO> startGame(
            @AuthenticationPrincipal UserEntity user,
            @RequestBody @Valid StartGameRequest req
    ) {
        var lobby = lobbyService.getLobby(req.getLobbyId())
                .orElseThrow(() -> new IllegalArgumentException("Lobby not found"));
        if (!lobby.getOwner().getId().equals(user.getId()))
            return ResponseEntity.status(403).build();
        // Validate player count before starting
        int required = lobby.getMaxPlayers() != null
                ? lobby.getMaxPlayers()
                : rulesService.defaultRequiredPlayers(lobby.getGameType());
        int current = lobby.getPlayers() != null ? lobby.getPlayers().size() : 0;
        if (current < required) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
                    .header(org.springframework.http.HttpHeaders.WARNING, "NOT_ENOUGH_PLAYERS")
                    .body(null);
        }

        var game = gameService.startGame(lobby, req.getConfigJson());
        lobbyService.disbandLobby(lobby.getId());
        return ResponseEntity.ok(gameService.toDto(game));
    }

    private LobbyDTO toDto(com.ultracards.server.entity.games.GameLobby lobby, LobbyCreateRequest req) {
        return new LobbyDTO(
                lobby.getId(),
                req != null ? req.getLobbyName() : lobby.getLobbyName(),
                lobby.getGameType() != null ? lobby.getGameType().name() : null,
                lobby.getCreatedAt() != null ? lobby.getCreatedAt() : Instant.now(),
                lobby.getOwner() != null ? lobby.getOwner().getId() : null,
                lobby.getOwner() != null ? lobby.getOwner().getUsername() : null,
                lobby.getPlayers() != null ? lobby.getPlayers().stream().map(UserEntity::getId).toList() : List.of(),
                lobby.getPlayers() != null ? lobby.getPlayers().stream()
                        .map(u -> new LobbyPlayerDTO(u.getId(), u.getUsername()))
                        .toList() : List.of(),
                req != null ? req.getMaxPlayers() : lobby.getMaxPlayers(),
                req != null ? req.getConfigJson() : lobby.getConfigJson()
        );
    }
}
