package com.ultracards.server.controllers.auth;

import com.ultracards.gateway.dto.auth.DetailedProfileStatsDTO;
import com.ultracards.gateway.dto.auth.ProfileDTO;
import com.ultracards.gateway.dto.auth.UserSessionDTO;
import com.ultracards.gateway.dto.auth.UsernameDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.auth.AuthService;
import com.ultracards.server.service.auth.SessionService;
import com.ultracards.server.service.games.UserGamesStatsService;
import com.ultracards.server.service.users.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    @Value("${app.max-length.username}")
    private Integer MAX_USERNAME_LENGTH;
    private final AuthService authService;
    private final ProfileService profileService;
    private final SessionService sessionService;
    private final UserGamesStatsService userGamesStatsService;
    @Value("${app.token.update-privilege-duration-minutes:4}")
    private long updateDuration;

    @GetMapping("/username")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<UsernameDTO> getUsername(
            @AuthenticationPrincipal UserEntity user
    ) {
        return ResponseEntity
                .ok(new UsernameDTO(user.getUsername()));
    }

    @PutMapping(
            value = "/username",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseBody
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<?> updateUsername(
            @AuthenticationPrincipal UserEntity user,
            @RequestAttribute("token") String token,
            @Valid @RequestBody UsernameDTO username,
            BindingResult errors) {
        if (errors.hasErrors() || username.getUsername().length() > MAX_USERNAME_LENGTH) {
            var errorsString = errors.getAllErrors().stream().map(e -> e.getDefaultMessage()).toList().toString();
            return ResponseEntity.badRequest().body(errorsString);
        }

        var res = authService.updateUsername(user, username, token);

        if (!res) return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Username can only be updated within " + updateDuration + " minutes of login");

        if (username.getUsername() == null)
            return redirectToLogout();
        return ResponseEntity.ok(username);
    }

    @GetMapping
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<ProfileDTO> getProfile(
            @AuthenticationPrincipal UserEntity user
    ) {
        var res = profileService.getProfile(user);
        return ResponseEntity.ok(res);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<DetailedProfileStatsDTO> getDetailedProfileStats(
            @AuthenticationPrincipal UserEntity user
    ) {
        return ResponseEntity.ok(userGamesStatsService.getDetailedStatsByUser(user));
    }

    @PostMapping
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<?> updateProfile(
            @AuthenticationPrincipal UserEntity user,
            @RequestAttribute("token") String token,
            @RequestBody @Valid ProfileDTO profileDTO,
            BindingResult errors
    ) {
        if (errors.hasErrors())
            return ResponseEntity.badRequest().build();

        var res = profileService.updateProfile(user, profileDTO, token);
        if (!res)
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Profile can only be updated within " + updateDuration + " minutes of login");

        return ResponseEntity.ok(profileService.getProfile(user));
    }

    @GetMapping("sessions")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<List<UserSessionDTO>> getSessions(
            @AuthenticationPrincipal UserEntity user,
            @RequestAttribute("token") String token
    ) {
        return ResponseEntity.ok(sessionService.getSessions(user, token));
    }

    @DeleteMapping("sessions")
    @PreAuthorize("hasRole(T(com.ultracards.server.enums.UserRole).USER.name())")
    public ResponseEntity<?> deleteSessions(
            @AuthenticationPrincipal UserEntity user,
            @RequestAttribute("token") String token,
            @Valid UserSessionDTO userSession
    ) {
        var deleteSession = sessionService.getSession(userSession.getId());
        if (deleteSession.getUserId().equals(user.getId())) {
            var currentSession = sessionService.getSession(token);
            var isCurrentSession = deleteSession.equals(currentSession);
            var res = sessionService.deleteSession(currentSession, deleteSession);
            if (!res) return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Username can only be updated within " + updateDuration + " minutes of login");
            if (isCurrentSession)
                return redirectToLogout();
            return ResponseEntity.ok("Session deleted");
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You can only delete your own sessions");
    }

    private <T> ResponseEntity<T> redirectToLogout() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "/api/auth/logout")
                .build();
    }
}
