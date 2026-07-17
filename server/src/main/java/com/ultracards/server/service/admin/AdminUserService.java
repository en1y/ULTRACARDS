package com.ultracards.server.service.admin;

import com.ultracards.gateway.dto.admin.AdminPageDTO;
import com.ultracards.gateway.dto.admin.AdminUserPatchDTO;
import com.ultracards.gateway.dto.admin.AdminUserSummaryDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.enums.UserRole;
import com.ultracards.server.enums.UserStatus;
import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.service.auth.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;

@Service
@RequiredArgsConstructor
public class AdminUserService {
    private final UserRepository userRepository;
    private final SessionService sessionService;
    private final AdminAuditService auditService;

    @Value("${app.max-length.username:30}")
    private int maxUsernameLength;
    @Value("${app.max-length.email:150}")
    private int maxEmailLength;

    @Transactional(readOnly = true)
    public AdminPageDTO<AdminUserSummaryDTO> list(int page, int size) {
        var safePage = Math.max(0, page);
        var safeSize = Math.max(1, Math.min(200, size));
        var users = userRepository.findAll(PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Direction.DESC, "userCreatedAt")));
        return new AdminPageDTO<>(users.getContent().stream().map(this::toDto).toList(),
                users.getNumber(), users.getSize(), users.getTotalElements(), users.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminUserSummaryDTO get(Long id) {
        return toDto(find(id));
    }

    @Transactional
    public AdminUserSummaryDTO patch(UserEntity actor, Long id, AdminUserPatchDTO patch) {
        requireReason(patch.reason());
        var user = lock(id);
        var nextUsername = patch.username() == null ? user.getUsername() : validateUsername(patch.username());
        var nextEmail = patch.email() == null ? user.getEmail() : validateEmail(user, patch.email());
        var nextEnabled = patch.enabled() == null ? user.isEnabled() : patch.enabled();
        var nextStatus = patch.status() == null ? user.getStatus() : parseStatus(patch.status());
        if (patch.enabled() != null)
            nextStatus = patch.enabled() ? UserStatus.ACTIVE : UserStatus.DISABLED;
        if (patch.status() != null)
            nextEnabled = nextStatus == UserStatus.ACTIVE;

        if ((!nextEnabled || nextStatus != UserStatus.ACTIVE) && user.hasRole(UserRole.ADMIN))
            guardAdminRemoval(actor, user);

        var preview = toDto(user, nextEmail, nextUsername, nextEnabled, nextStatus, user.getRoles());
        if (patch.dryRun()) return preview;

        var emailChanged = !user.getEmail().equalsIgnoreCase(nextEmail);
        var disabled = user.isEnabled() && !nextEnabled;
        user.setEmail(nextEmail);
        user.setUsername(nextUsername);
        user.setEnabled(nextEnabled);
        user.setStatus(nextStatus);
        userRepository.save(user);
        if (emailChanged || disabled) sessionService.revokeAllSessions(user.getId());
        auditService.record(actor.getId(), "UPDATE_USER", "USER", id.toString(), patch.reason(),
                "updated allowlisted user fields", "SUCCESS");
        return toDto(user);
    }

    @Transactional
    public AdminUserSummaryDTO grantRole(UserEntity actor, Long id, String value, String reason) {
        requireReason(reason);
        var role = mutableRole(value);
        var user = lock(id);
        var changed = user.addRole(role);
        userRepository.save(user);
        auditService.record(actor.getId(), "GRANT_ROLE", "USER", id.toString(), reason,
                role.name() + (changed ? " granted" : " already present"), "SUCCESS");
        return toDto(user);
    }

    @Transactional
    public AdminUserSummaryDTO revokeRole(UserEntity actor, Long id, String value, String reason) {
        requireReason(reason);
        var role = mutableRole(value);
        var user = lock(id);
        if (role == UserRole.ADMIN && user.hasRole(role)) guardAdminRemoval(actor, user);
        var changed = user.removeRole(role);
        userRepository.save(user);
        auditService.record(actor.getId(), "REVOKE_ROLE", "USER", id.toString(), reason,
                role.name() + (changed ? " revoked" : " was absent"), "SUCCESS");
        return toDto(user);
    }

    @Transactional
    public void revokeSessions(UserEntity actor, Long id, String reason) {
        requireReason(reason);
        find(id);
        sessionService.revokeAllSessions(id);
        auditService.record(actor.getId(), "REVOKE_SESSIONS", "USER", id.toString(), reason,
                "all sessions revoked", "SUCCESS");
    }

    private void guardAdminRemoval(UserEntity actor, UserEntity target) {
        if (actor.getId().equals(target.getId()))
            throw conflict("Administrators cannot demote or disable themselves");
        var activeAdmins = userRepository.findAllByRoleForUpdate(UserRole.ADMIN).stream()
                .filter(UserEntity::isEnabled)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .count();
        if (activeAdmins <= 1) throw conflict("The last active administrator cannot be removed");
    }

    private UserEntity find(Long id) {
        return userRepository.findById(id).orElseThrow(() -> notFound("User not found"));
    }

    private UserEntity lock(Long id) {
        return userRepository.findByIdForUpdate(id).orElseThrow(() -> notFound("User not found"));
    }

    private String validateUsername(String value) {
        var clean = value.trim();
        if (clean.isBlank() || clean.length() > maxUsernameLength)
            throw badRequest("Username must contain 1 to " + maxUsernameLength + " characters");
        return clean;
    }

    private String validateEmail(UserEntity user, String value) {
        var clean = value.trim().toLowerCase();
        if (!clean.contains("@") || clean.length() > maxEmailLength)
            throw badRequest("Invalid email address");
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(clean, user.getId()))
            throw conflict("Email address is already in use");
        return clean;
    }

    private UserStatus parseStatus(String value) {
        try { return UserStatus.valueOf(value.trim().toUpperCase()); }
        catch (RuntimeException ex) { throw badRequest("Unknown user status: " + value); }
    }

    private UserRole mutableRole(String value) {
        try {
            var role = UserRole.valueOf(value.trim().toUpperCase());
            if (role == UserRole.USER) throw badRequest("USER is the immutable baseline role");
            return role;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw badRequest("Unknown role: " + value);
        }
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank()) throw badRequest("A nonblank reason is required");
    }

    AdminUserSummaryDTO toDto(UserEntity user) {
        return toDto(user, user.getEmail(), user.getUsername(), user.isEnabled(), user.getStatus(), user.getRoles());
    }

    private AdminUserSummaryDTO toDto(UserEntity user, String email, String username, boolean enabled,
                                      UserStatus status, java.util.Set<UserRole> roles) {
        var roleNames = new HashSet<String>();
        for (var role : roles) roleNames.add(role.name());
        return new AdminUserSummaryDTO(user.getId(), email, username, enabled, status.name(), roleNames,
                user.getUserCreatedAt(), user.getUpdatedAt(), user.getLastLoginAt());
    }

    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
