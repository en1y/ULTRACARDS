package com.ultracards.server.bootstrap;

import com.ultracards.gateway.dto.EmailDTO;
import com.ultracards.server.enums.UserRole;
import com.ultracards.server.enums.UserStatus;
import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.service.admin.AdminAuditService;
import com.ultracards.server.service.users.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
public class BootstrapAdminExecutor {
    private final UserRepository userRepository;
    private final UserService userService;
    private final AdminAuditService auditService;
    private final TransactionTemplate transactionTemplate;

    public Result execute(String email, String username, boolean force) {
        var result = transactionTemplate.execute(status -> provision(email, username, force));
        return result == null ? new Result(7, null, "Bootstrap transaction failed") : result;
    }

    private Result provision(String email, String username, boolean force) {
        var admins = userRepository.findAllByRoleForUpdate(UserRole.ADMIN);
        if (!admins.isEmpty() && !force)
            return new Result(5, null, "An administrator already exists; use --force only for recovery");
        var existing = userRepository.findByEmail(email);
        var created = existing.isEmpty();
        var user = existing.orElseGet(() -> userService.createUser(new EmailDTO(email)));
        if (username != null && !username.isBlank()) user.setUsername(username.trim());
        else if (created && user.getUsername().isBlank()) user.setUsername("admin");
        user.setEnabled(true);
        user.setStatus(UserStatus.ACTIVE);
        user.addRole(UserRole.USER);
        user.addRole(UserRole.ADMIN);
        userRepository.saveAndFlush(user);
        var message = created ? "Created first administrator" : "Promoted existing user to administrator";
        auditService.record(null, "BOOTSTRAP_ADMIN", "USER", user.getId().toString(), "local bootstrap",
                message, "SUCCESS");
        return new Result(0, user.getId(), message);
    }

    public record Result(int exitCode, Long userId, String message) {}
}
