package com.ultracards.server.controllers.lobby;

import com.ultracards.gateway.dto.games.lobby.GameLobbyDTO;
import com.ultracards.gateway.dto.games.lobby.JoinLobbyRequestDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.lobby.LobbyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/lobby")
@RequiredArgsConstructor
public class LobbyController {

    private final LobbyService lobbyService;

    @PostMapping("/create")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<GameLobbyDTO> createLobby(
            @AuthenticationPrincipal UserEntity user,
            @RequestBody @Valid GameLobbyDTO lobbyDTO
    ){
        var lobby = lobbyService.createLobby(
                user, lobbyDTO
        );
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(lobby);
    }

    @PostMapping("/join")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<String> joinLobbyWithCode(
            @AuthenticationPrincipal UserEntity user,
            @RequestBody @Valid JoinLobbyRequestDTO joinLobbyRequest
            ){

        LobbyService.JoinLobbyResult res;

        if (joinLobbyRequest.hasLobbyCode()) {
            res = lobbyService.joinLobby(
                    joinLobbyRequest.lobbyCode(), user);
        } else {
            res = lobbyService.joinLobby(
                    joinLobbyRequest.lobbyId(), user);
        }

        if (res == LobbyService.JoinLobbyResult.JOINED)
            return ResponseEntity.ok("Joined");

        if (res == LobbyService.JoinLobbyResult.FULL)
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Lobby is full.");

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Lobby not found.");
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
        return res ?
                ResponseEntity.ok(true) :
                ResponseEntity.status(HttpStatus.NO_CONTENT).body(false);
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
        return lobby != null ?
                ResponseEntity.ok(lobby):
                ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
    }

    @DeleteMapping("/delete")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<Void> deleteLobby(
            @AuthenticationPrincipal UserEntity user
    ){
        var res = lobbyService.deleteLobby(user);
        return res ?
                ResponseEntity.ok().build():
                ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
    }

    @GetMapping("/get-lobbies")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<List<GameLobbyDTO>> getLobbies() {
        return ResponseEntity.ok(lobbyService.getLobbies());
    }

    @GetMapping("/get-lobbies/{gameType}/{gameSettingId}")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<?> getLobbiesByType(
            @PathVariable String gameType,
            @PathVariable Integer gameSettingId
    ) {
        var res = lobbyService.getLobbies(gameType, gameSettingId);
        if (res == null)
            return ResponseEntity.badRequest().body("The game type or the game setting ID provided are invalid.");
        return ResponseEntity.ok(res);
    }
}
