package com.ultracards.server.service.admin;

import com.ultracards.gateway.dto.admin.*;
import com.ultracards.server.entity.auth.UserSession;
import com.ultracards.server.enums.UserRole;
import com.ultracards.server.enums.UserStatus;
import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.repositories.admin.AdminAuditEventRepository;
import com.ultracards.server.repositories.auth.SessionRepository;
import com.ultracards.server.repositories.auth.TokenRepository;
import com.ultracards.server.repositories.games.BriskulaGameRepository;
import com.ultracards.server.repositories.games.RecordedGameRepository;
import com.ultracards.server.repositories.games.TresetaGameRepository;
import com.ultracards.server.repositories.games.UserBriskulaStatsRepository;
import com.ultracards.server.repositories.games.UserGamesStatsRepository;
import com.ultracards.server.repositories.games.UserTresetaStatsRepository;
import com.ultracards.server.repositories.notifications.NotificationRepository;
import com.ultracards.server.service.games.GameManager;
import com.ultracards.server.service.lobby.LobbyManager;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminReportService {
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final TokenRepository tokenRepository;
    private final NotificationRepository notificationRepository;
    private final AdminAuditEventRepository adminAuditEventRepository;
    private final RecordedGameRepository recordedGameRepository;
    private final BriskulaGameRepository briskulaGameRepository;
    private final TresetaGameRepository tresetaGameRepository;
    private final UserGamesStatsRepository userGamesStatsRepository;
    private final UserBriskulaStatsRepository userBriskulaStatsRepository;
    private final UserTresetaStatsRepository userTresetaStatsRepository;
    private final LobbyManager lobbyManager;
    private final GameManager gameManager;
    private final AdminUserService adminUserService;
    private final AdminGameRecordService adminGameRecordService;
    private final Flyway flyway;

    @Value("${app.presence.online-timeout-seconds:60}")
    private long onlineTimeoutSeconds;

    @Transactional(readOnly = true)
    public AdminOverviewDTO overview() {
        var now = Instant.now();
        var status = new LinkedHashMap<String, Long>();
        for (var value : UserStatus.values()) status.put(value.name(), userRepository.countByStatus(value));
        var roles = new LinkedHashMap<String, Long>();
        var users = userRepository.findAll();
        for (var role : UserRole.values())
            roles.put(role.name(), users.stream().filter(user -> user.hasRole(role)).count());
        var completed = new LinkedHashMap<String, Long>();
        completed.put("BRISKULA", briskulaGameRepository.countByEndedAtIsNotNull());
        completed.put("TRESETA", tresetaGameRepository.countByEndedAtIsNotNull());
        var incomplete = new LinkedHashMap<String, Long>();
        incomplete.put("BRISKULA", briskulaGameRepository.countByEndedAtIsNull());
        incomplete.put("TRESETA", tresetaGameRepository.countByEndedAtIsNull());
        return new AdminOverviewDTO(users.size(), status, roles, sessionRepository.countValid(now),
                sessionRepository.countOnlineUsers(now.minusSeconds(onlineTimeoutSeconds)), completed, incomplete,
                lobbyManager.getLobbies().size(), gameManager.getGames().size(), flywayVersion(), now);
    }

    public AdminPageDTO<AdminUserSummaryDTO> users(int page, int size) {
        return adminUserService.list(page, size);
    }

    public AdminPageDTO<AdminUserSummaryDTO> users(int page, int size, String statusValue, String roleValue,
                                                   String sortValue, String directionValue) {
        return users(page, size, null, false, statusValue, roleValue, sortValue, directionValue);
    }

    @Transactional(readOnly = true)
    public AdminPageDTO<AdminUserSummaryDTO> users(int page, int size, String queryValue, String statusValue, String roleValue,
                                                   String sortValue, String directionValue) {
        return users(page, size, queryValue, false, statusValue, roleValue, sortValue, directionValue);
    }

    @Transactional(readOnly = true)
    public AdminPageDTO<AdminUserSummaryDTO> users(int page, int size, String queryValue, boolean exact, String statusValue,
                                                   String roleValue, String sortValue, String directionValue) {
        var status = enumValue(UserStatus.class, statusValue, "user status");
        var role = enumValue(UserRole.class, roleValue, "role");
        var query = queryValue == null || queryValue.isBlank() ? null : queryValue.trim();
        var result = userRepository.findAdminReport(status, role, query, exact,
                page(page, size, sort(sortValue, "userCreatedAt", "userCreatedAt", "email", "username", "id"), directionValue));
        return new AdminPageDTO<>(result.getContent().stream().map(adminUserService::toDto).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminDatabaseOverviewDTO database() {
        var records = new LinkedHashMap<String, Long>();
        records.put("Users", userRepository.count());
        records.put("Sessions", sessionRepository.count());
        records.put("Tokens", tokenRepository.count());
        records.put("Recorded games", recordedGameRepository.count());
        records.put("Notifications", notificationRepository.count());
        records.put("Admin audit events", adminAuditEventRepository.count());
        records.put("Game-stat profiles", userGamesStatsRepository.count());
        records.put("Briskula stat rows", userBriskulaStatsRepository.count());
        records.put("Treseta stat rows", userTresetaStatsRepository.count());
        return new AdminDatabaseOverviewDTO(true, flywayVersion(), records, Instant.now());
    }

    @Transactional(readOnly = true)
    public AdminPageDTO<AdminRecordedGameDTO> games(int page, int size) {
        return games(page, size, null, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public AdminPageDTO<AdminRecordedGameDTO> games(int page, int size, String gameTypeValue, Boolean completed,
                                                    String sortValue, String directionValue) {
        return games(page, size, gameTypeValue, completed, null, sortValue, directionValue);
    }

    @Transactional(readOnly = true)
    public AdminPageDTO<AdminRecordedGameDTO> games(int page, int size, String gameTypeValue, Boolean completed,
                                                    String modeValue, String sortValue, String directionValue) {
        var gameType = optionalChoice(gameTypeValue, "game type", "BRISKULA", "TRESETA");
        var mode = optionalChoice(modeValue, "game mode", "TWO_PLAYERS", "TWO_PLAYERS_FOUR_CARDS_IN_HAND_EACH",
                "THREE_PLAYERS", "FOUR_PLAYERS_NO_TEAMS", "FOUR_PLAYERS_WITH_TEAMS");
        var result = recordedGameRepository.findAdminReport(gameType, completed, mode,
                page(page, size, sort(sortValue, "createdAt", "createdAt", "endedAt", "name", "id"), directionValue));
        return new AdminPageDTO<>(result.getContent().stream().map(adminGameRecordService::toDto).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminPageDTO<AdminSessionDTO> sessions(int page, int size) {
        return sessions(page, size, null, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public AdminPageDTO<AdminSessionDTO> sessions(int page, int size, Long userId, Boolean valid,
                                                  String sortValue, String directionValue) {
        return sessions(page, size, null, userId, valid, sortValue, directionValue);
    }

    @Transactional(readOnly = true)
    public AdminPageDTO<AdminSessionDTO> sessions(int page, int size, UUID id, Long userId, Boolean valid,
                                                  String sortValue, String directionValue) {
        var now = Instant.now();
        var result = sessionRepository.findAdminReport(id, userId, valid, now,
                page(page, size, sort(sortValue, "lastSeenAt", "lastSeenAt", "firstSeenAt", "lastAuthenticatedAt", "userId", "id"), directionValue));
        return new AdminPageDTO<>(result.getContent().stream().map(session -> toDto(session, now)).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    String flywayVersion() {
        var current = flyway.info().current();
        return current == null || current.getVersion() == null ? "none" : current.getVersion().getVersion();
    }

    private AdminSessionDTO toDto(UserSession session, Instant now) {
        var token = session.getToken();
        return new AdminSessionDTO(session.getId(), session.getUserId(), session.getClientType(), session.getOs(),
                session.getCountry(), session.getRegion(), session.getFirstSeenAt(), session.getLastSeenAt(),
                session.getLastAuthenticatedAt(), token.getExpiresAt(), token.isActive() && token.getExpiresAt().isAfter(now),
                token.getId());
    }

    @Transactional(readOnly = true)
    public AdminPageDTO<AdminTokenDTO> tokens(int page, int size, UUID id, Long userId, Boolean active) {
        var now = Instant.now();
        var result = tokenRepository.findAdminReport(id, userId, active,
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(200, size)), Sort.by(Sort.Direction.DESC, "expiresAt")));
        var sessionsByToken = new LinkedHashMap<UUID, UUID>();
        var tokenIds = result.getContent().stream().map(token -> token.getId()).toList();
        if (!tokenIds.isEmpty())
            sessionRepository.findByTokenIdIn(tokenIds).forEach(session -> sessionsByToken.put(session.getToken().getId(), session.getId()));
        return new AdminPageDTO<>(result.getContent().stream().map(token -> new AdminTokenDTO(
                token.getId(), token.getUser().getId(), sessionsByToken.get(token.getId()),
                token.isActive(), token.isActive() && token.getExpiresAt().isAfter(now),
                token.getExpiresAt(), token.getReuseUntil(),
                token.getReplacementToken() == null ? null : token.getReplacementToken().getId())).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private PageRequest page(int page, int size, String sort, String direction) {
        var sortDirection = direction == null || direction.isBlank() || direction.equalsIgnoreCase("desc")
                ? Sort.Direction.DESC : direction.equalsIgnoreCase("asc") ? Sort.Direction.ASC : null;
        if (sortDirection == null) throw badRequest("Sort direction must be asc or desc");
        return PageRequest.of(Math.max(0, page), Math.max(1, Math.min(200, size)), Sort.by(sortDirection, sort));
    }

    private String sort(String value, String defaultValue, String... allowed) {
        if (value == null || value.isBlank()) return defaultValue;
        for (var candidate : allowed) if (candidate.equalsIgnoreCase(value)) return candidate;
        throw badRequest("Unsupported sort field: " + value);
    }

    private String optionalChoice(String value, String label, String... allowed) {
        if (value == null || value.isBlank()) return null;
        for (var candidate : allowed) if (candidate.equalsIgnoreCase(value)) return candidate;
        throw badRequest("Unknown " + label + ": " + value);
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value, String label) {
        if (value == null || value.isBlank()) return null;
        try { return Enum.valueOf(type, value.trim().toUpperCase()); }
        catch (IllegalArgumentException ex) { throw badRequest("Unknown " + label + ": " + value); }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
