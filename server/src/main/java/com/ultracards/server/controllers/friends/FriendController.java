package com.ultracards.server.controllers.friends;

import com.ultracards.gateway.dto.friends.DetailedFriendDTO;
import com.ultracards.gateway.dto.friends.FriendDTO;
import com.ultracards.gateway.dto.friends.FriendRequestDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.friends.FriendService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    @GetMapping
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<List<FriendDTO>> getFriends(
            @AuthenticationPrincipal UserEntity user
    ) {
        return ResponseEntity.ok(friendService.getFriends(user));
    }

    @GetMapping("/blocked")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<List<FriendDTO>> getBlockedUsers(
            @AuthenticationPrincipal UserEntity user
    ) {
        return ResponseEntity.ok(friendService.getBlockedUsers(user));
    }

    @GetMapping("/requests/incoming")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<List<FriendRequestDTO>> getIncomingRequests(
            @AuthenticationPrincipal UserEntity user
    ) {
        return ResponseEntity.ok(friendService.getIncomingRequests(user));
    }

    @GetMapping("/requests/outgoing")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<List<FriendRequestDTO>> getOutgoingRequests(
            @AuthenticationPrincipal UserEntity user
    ) {
        return ResponseEntity.ok(friendService.getOutgoingRequests(user));
    }

    @GetMapping("/{friendUserId}/details")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<DetailedFriendDTO> getDetailedFriend(
            @AuthenticationPrincipal UserEntity user,
            @PathVariable Long friendUserId
    ) {
        return ResponseEntity.ok(friendService.getDetailedFriend(user, friendUserId));
    }

    @PostMapping("/requests/send/{id}")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<FriendRequestDTO> sendFriendRequest(
            @AuthenticationPrincipal UserEntity user,
            @PathVariable Long id
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(friendService.sendFriendRequest(user, id));
    }

    @PostMapping("/requests/{id}/accept")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<FriendRequestDTO> acceptFriendRequest(
            @AuthenticationPrincipal UserEntity user,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(friendService.acceptRequest(user, id));
    }

    @PostMapping("/requests/{id}/decline")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<FriendRequestDTO> declineFriendRequest(
            @AuthenticationPrincipal UserEntity user,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(friendService.declineRequest(user, id));
    }

    @PostMapping("/requests/{id}/block")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<FriendRequestDTO> blockFriendRequest(
            @AuthenticationPrincipal UserEntity user,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(friendService.blockRequest(user, id));
    }

    @DeleteMapping("/{friendUserId}")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<Void> removeFriend(
            @AuthenticationPrincipal UserEntity user,
            @PathVariable Long friendUserId
    ) {
        friendService.removeFriend(user, friendUserId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/blocks/{blockedUserId}")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<Void> unblockUser(
            @AuthenticationPrincipal UserEntity user,
            @PathVariable Long blockedUserId
    ) {
        friendService.unblockUser(user, blockedUserId);
        return ResponseEntity.noContent().build();
    }
}
