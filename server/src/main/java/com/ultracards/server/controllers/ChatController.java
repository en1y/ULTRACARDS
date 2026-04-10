package com.ultracards.server.controllers;

import com.ultracards.gateway.dto.games.chat.ChatMessageDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.chat.ChatService;
import com.ultracards.server.service.lobby.LobbyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;
    private final LobbyService lobbyService;

    @GetMapping
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<?> getMessages(
            @AuthenticationPrincipal UserEntity user) {
        var lobby = lobbyService.getLobbyByUser(user);
        if (lobby == null) {
            return ResponseEntity.notFound().build();
        }
        var chat = chatService.getChat(lobby.getId());
        if (!chat.isOpen()) {
            return ResponseEntity.badRequest().body("Chat is closed");
        }
        return ResponseEntity.ok(chat.toDto());
    }

    @PostMapping
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<?> sendMessage(
            @AuthenticationPrincipal UserEntity user,
            @Valid @RequestBody ChatMessageDTO messageDTO
    ) {
        var lobby = lobbyService.getLobbyByUser(user);
        if (lobby == null) {
            return ResponseEntity.notFound().build();
        }
        var chat = chatService.getChat(lobby.getId());
        if (!chat.isOpen()) {
            return ResponseEntity.badRequest().body("Chat is closed");
        }
        chatService.sendMessage(lobby.getId(), user, messageDTO.getMessage());
        return ResponseEntity.ok().build();
    }
}
