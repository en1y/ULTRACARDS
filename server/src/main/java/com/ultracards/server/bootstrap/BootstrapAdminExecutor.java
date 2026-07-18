package com.ultracards.server.bootstrap;

import com.ultracards.server.enums.UserRole;
import com.ultracards.server.enums.UserStatus;
import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.service.admin.AdminAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
public class BootstrapAdminExecutor {
    private final UserRepository userRepository;
    private final AdminAuditService auditService;
    private final TransactionTemplate transactionTemplate;

    public Result execute(Long userId, boolean force) {
        var result = transactionTemplate.execute(status -> provision(userId, force));
        return result == null ? new Result(7, null, "Bootstrap transaction failed") : result;
    }

    private Result provision(Long userId, boolean force) {
        var admins = userRepository.findAllByRoleForUpdate(UserRole.ADMIN);
        if (!admins.isEmpty() && !force)
            return new Result(5, null, "An administrator already exists; use --force only for recovery");
        var user = userRepository.findById(userId).orElse(null);
        if (user == null) return new Result(5, null, "User " + userId + " does not exist");
        user.setEnabled(true);
        user.setStatus(UserStatus.ACTIVE);
        user.addRole(UserRole.USER);
        user.addRole(UserRole.ADMIN);
        userRepository.saveAndFlush(user);
        var message = "Promoted existing user to administrator";
        auditService.record(null, "BOOTSTRAP_ADMIN", "USER", user.getId().toString(), "local bootstrap",
                message, "SUCCESS");
        return new Result(0, user.getId(), message);
    }

    public record Result(int exitCode, Long userId, String message) {}
}
