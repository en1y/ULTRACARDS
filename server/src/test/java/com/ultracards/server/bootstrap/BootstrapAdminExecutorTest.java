package com.ultracards.server.bootstrap;

import com.ultracards.gateway.dto.EmailDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.enums.UserRole;
import com.ultracards.server.enums.UserStatus;
import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.service.admin.AdminAuditService;
import com.ultracards.server.service.users.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BootstrapAdminExecutorTest {
    private final UserRepository repository = mock(UserRepository.class);
    private final UserService userService = mock(UserService.class);
    private final AdminAuditService auditService = mock(AdminAuditService.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private BootstrapAdminExecutor executor;

    @BeforeEach
    void setUp() {
        when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        executor = new BootstrapAdminExecutor(repository, userService, auditService, transactions);
    }

    @Test
    void refusesWhenAnAdministratorAlreadyExists() {
        when(repository.findAllByRoleForUpdate(UserRole.ADMIN)).thenReturn(List.of(user(1L, "admin@example.com")));

        var result = executor.execute("other@example.com", "Other", false);

        assertThat(result.exitCode()).isEqualTo(5);
        verifyNoInteractions(userService, auditService);
    }

    @Test
    void createsAndAuditsTheFirstAdministrator() {
        var created = user(7L, "admin@example.com");
        when(repository.findAllByRoleForUpdate(UserRole.ADMIN)).thenReturn(List.of());
        when(repository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(userService.createUser(any(EmailDTO.class))).thenReturn(created);

        var result = executor.execute("admin@example.com", "Administrator", false);

        assertThat(result.exitCode()).isZero();
        assertThat(created.getUsername()).isEqualTo("Administrator");
        assertThat(created.getRoles()).contains(UserRole.USER, UserRole.ADMIN);
        assertThat(created.isEnabled()).isTrue();
        assertThat(created.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(repository).saveAndFlush(created);
        verify(auditService).record(isNull(), eq("BOOTSTRAP_ADMIN"), eq("USER"), eq("7"),
                eq("local bootstrap"), anyString(), eq("SUCCESS"));
    }

    @Test
    void forcePromotesAnExistingUserForRecovery() {
        var existing = user(9L, "recover@example.com");
        existing.setEnabled(false);
        existing.setStatus(UserStatus.DISABLED);
        when(repository.findAllByRoleForUpdate(UserRole.ADMIN)).thenReturn(List.of(user(1L, "old@example.com")));
        when(repository.findByEmail("recover@example.com")).thenReturn(Optional.of(existing));

        var result = executor.execute("recover@example.com", "Recovery", true);

        assertThat(result.exitCode()).isZero();
        assertThat(existing.getRoles()).contains(UserRole.USER, UserRole.ADMIN);
        assertThat(existing.isEnabled()).isTrue();
        assertThat(existing.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verifyNoInteractions(userService);
    }

    private UserEntity user(Long id, String email) {
        var user = new UserEntity(email, "user");
        user.setId(id);
        user.setStatus(UserStatus.ACTIVE);
        user.addRole(UserRole.USER);
        return user;
    }
}
