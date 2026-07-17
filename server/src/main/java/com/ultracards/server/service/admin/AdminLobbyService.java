package com.ultracards.server.service.admin;

import com.ultracards.gateway.dto.admin.AdminLobbyDTO;
import com.ultracards.gateway.dto.admin.AdminLobbyPatchDTO;
import com.ultracards.gateway.dto.games.lobby.GameLobbyDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.lobby.LobbyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminLobbyService {
    private final LobbyService lobbyService;
    private final AdminAuditService auditService;

    public List<AdminLobbyDTO> list() {
        return lobbyService.getAllLobbiesForAdministration().stream().map(this::toDto).toList();
    }

    public AdminLobbyDTO get(UUID id) {
        var lobby = lobbyService.getLobbyEntity(id);
        if (lobby == null) throw notFound();
        return new AdminLobbyDTO(lobby.createLobbyDTO(true), lobby.getLobbyState().name(), lobby.getCreatedAt());
    }

    public AdminLobbyDTO patch(UserEntity actor, UUID id, AdminLobbyPatchDTO patch) {
        requireReason(patch.reason());
        Boolean visibility = null;
        if (patch.visibility() != null) {
            if (patch.visibility().equalsIgnoreCase("public")) visibility = true;
            else if (patch.visibility().equalsIgnoreCase("private")) visibility = false;
            else throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Visibility must be public or private");
        }
        var result = lobbyService.updateLobby(id, cleanName(patch.name()), visibility, patch.mode());
        auditService.record(actor.getId(), "UPDATE_LOBBY", "LOBBY", id.toString(), patch.reason(),
                "updated lobby name, visibility, or mode", "SUCCESS");
        return toDto(result);
    }

    public void close(UserEntity actor, UUID id, String reason) {
        requireReason(reason);
        if (!Boolean.TRUE.equals(lobbyService.deleteLobby(id))) throw notFound();
        auditService.record(actor.getId(), "CLOSE_LOBBY", "LOBBY", id.toString(), reason,
                "lobby closed", "SUCCESS");
    }

    public AdminLobbyDTO kick(UserEntity actor, UUID id, Long userId, String reason) {
        requireReason(reason);
        var result = lobbyService.kickPlayer(id, userId);
        auditService.record(actor.getId(), "KICK_LOBBY_PLAYER", "LOBBY", id.toString(), reason,
                "removed user " + userId, "SUCCESS");
        return toDto(result);
    }

    public AdminLobbyDTO extend(UserEntity actor, UUID id, long seconds, String reason) {
        requireReason(reason);
        var result = lobbyService.extendLobby(id, seconds);
        auditService.record(actor.getId(), "EXTEND_LOBBY", "LOBBY", id.toString(), reason,
                "extended by " + seconds + " seconds", "SUCCESS");
        return toDto(result);
    }

    private AdminLobbyDTO toDto(GameLobbyDTO dto) {
        var lobby = lobbyService.getLobbyEntity(dto.getId());
        return new AdminLobbyDTO(dto, lobby == null ? "CLOSED" : lobby.getLobbyState().name(),
                lobby == null ? null : lobby.getCreatedAt());
    }

    private String cleanName(String name) {
        if (name == null) return null;
        var clean = name.trim();
        if (clean.isBlank() || clean.length() > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lobby name must contain 1 to 100 characters");
        return clean;
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A nonblank reason is required");
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Lobby not found");
    }
}
