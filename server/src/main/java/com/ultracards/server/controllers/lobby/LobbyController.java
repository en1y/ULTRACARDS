package com.ultracards.server.controllers.lobby;

import com.ultracards.gateway.dto.games.lobby.GameLobbyDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.lobby.LobbyEventPublisher;
import com.ultracards.server.service.lobby.LobbyManager;
import com.ultracards.server.service.lobby.LobbyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.ultracards.gateway.dto.games.lobby.GameLobbyEventDTO.GameLobbyEventType.*;

@RestController
@RequestMapping("/api/lobby")
@RequiredArgsConstructor
public class LobbyController {

    private final LobbyService lobbyService;
    private final LobbyManager lobbyManager;
    private final SimpMessagingTemplate messagingTemplate;
    private final LobbyEventPublisher eventPublisher;

    @PostMapping("/create")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<GameLobbyDTO> createLobby(
            @AuthenticationPrincipal UserEntity user,
            @RequestBody @Valid GameLobbyDTO lobbyDTO
    ){
        var lobby = lobbyService.createLobby(
                user, lobbyDTO
        );

        eventPublisher.publish(lobby, CREATED);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(lobby);
    }

    @PostMapping("/join")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<Boolean> joinLobby(
            @AuthenticationPrincipal UserEntity user,
            @RequestBody @NotNull UUID lobbyId
    ){
        var res = lobbyService.joinLobby(
                lobbyId, user
        );

        if (res) eventPublisher.publish(lobbyManager.getLobby(lobbyId), UPDATED);

        return res ?
                ResponseEntity.ok(true) :
                ResponseEntity.status(HttpStatus.CONFLICT).body(false);
    }

    @PostMapping("/leave")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<Boolean> leaveLobby(
            @AuthenticationPrincipal UserEntity user,
            @RequestBody @NotNull UUID lobbyId
    ){
        var res = lobbyService.leaveLobby(
                lobbyId, user
        );

        if (res) eventPublisher.publish(lobbyManager.getLobby(lobbyId), UPDATED);

        return res ?
                ResponseEntity.ok(true) :
                ResponseEntity.status(HttpStatus.NO_CONTENT).body(false);
    }

    @PostMapping("/start")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<Boolean> startLobby(
            @AuthenticationPrincipal UserEntity user
    ){
        var res = lobbyService.startLobby(user);

        if (res) eventPublisher.publish(lobbyManager.getLobby(user), STARTED);

        return res ?
                ResponseEntity.ok(res) :
                ResponseEntity.status(HttpStatus.NO_CONTENT).body(res);
    }

    @PutMapping("/update")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<GameLobbyDTO> updateLobby(
            @AuthenticationPrincipal UserEntity user,
            @RequestBody @Valid GameLobbyDTO lobbyDTO
    ){
        var res = lobbyService.updateLobby(
                lobbyDTO, user
        );

        if (res != null) eventPublisher.publish(res, UPDATED);

        return res != null ?
                ResponseEntity.ok(res):
                ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
    }

    @PostMapping("/kick-player")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<GameLobbyDTO> kickPlayer(
            @AuthenticationPrincipal UserEntity user,
            @RequestBody @NotNull Long playerToKickId
            ){
        var lobby = lobbyService.kickPlayer(
                playerToKickId, user
        );

        if (lobby != null) eventPublisher.publish(lobby, UPDATED);

        return lobby != null ?
                ResponseEntity.ok(lobby):
                ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
    }

    @DeleteMapping("/delete")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<Void> deleteLobby(
            @AuthenticationPrincipal UserEntity user
    ){
        var lobby = lobbyManager.getLobby(user);
        var res = lobbyService.deleteLobby(user);

        if (res) eventPublisher.publish(lobby, DELETED);

        return res ?
                ResponseEntity.ok().build():
                ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
    }

    @GetMapping("/get-lobbies")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<List<GameLobbyDTO>> getLobbies() {
        return ResponseEntity.ok(lobbyService.getLobbies());
        // TODO: create mappings to get just one game type
    }
}
