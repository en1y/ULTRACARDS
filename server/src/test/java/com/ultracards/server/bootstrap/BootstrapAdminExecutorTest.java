package com.ultracards.server.bootstrap;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.enums.UserRole;
import com.ultracards.server.enums.UserStatus;
import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.service.admin.AdminAuditService;
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
    private final AdminAuditService auditService = mock(AdminAuditService.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private BootstrapAdminExecutor executor;

    @BeforeEach
    void setUp() {
        when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        executor = new BootstrapAdminExecutor(repository, auditService, transactions);
    }

    @Test
    void refusesWhenAnAdministratorAlreadyExists() {
        when(repository.findAllByRoleForUpdate(UserRole.ADMIN)).thenReturn(List.of(user(1L, "admin@example.com")));

        var result = executor.execute(2L, false);

        assertThat(result.exitCode()).isEqualTo(5);
        verifyNoInteractions(auditService);
    }

    @Test
    void promotesAndAuditsTheFirstAdministrator() {
        var existing = user(7L, "admin@example.com");
        when(repository.findAllByRoleForUpdate(UserRole.ADMIN)).thenReturn(List.of());
        when(repository.findById(7L)).thenReturn(Optional.of(existing));

        var result = executor.execute(7L, false);

        assertThat(result.exitCode()).isZero();
        assertThat(existing.getRoles()).contains(UserRole.USER, UserRole.ADMIN);
        assertThat(existing.isEnabled()).isTrue();
        assertThat(existing.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(repository).saveAndFlush(existing);
        verify(auditService).record(isNull(), eq("BOOTSTRAP_ADMIN"), eq("USER"), eq("7"),
                eq("local bootstrap"), anyString(), eq("SUCCESS"));
    }

    @Test
    void rejectsAnUnknownUserId() {
        when(repository.findAllByRoleForUpdate(UserRole.ADMIN)).thenReturn(List.of());
        when(repository.findById(99L)).thenReturn(Optional.empty());

        var result = executor.execute(99L, false);

        assertThat(result.exitCode()).isEqualTo(5);
        assertThat(result.message()).isEqualTo("User 99 does not exist");
        verifyNoInteractions(auditService);
    }

    @Test
    void forcePromotesAnExistingUserForRecovery() {
        var existing = user(9L, "recover@example.com");
        existing.setEnabled(false);
        existing.setStatus(UserStatus.DISABLED);
        when(repository.findAllByRoleForUpdate(UserRole.ADMIN)).thenReturn(List.of(user(1L, "old@example.com")));
        when(repository.findById(9L)).thenReturn(Optional.of(existing));

        var result = executor.execute(9L, true);

        assertThat(result.exitCode()).isZero();
        assertThat(existing.getRoles()).contains(UserRole.USER, UserRole.ADMIN);
        assertThat(existing.isEnabled()).isTrue();
        assertThat(existing.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    private UserEntity user(Long id, String email) {
        var user = new UserEntity(email, "user");
        user.setId(id);
        user.setStatus(UserStatus.ACTIVE);
        user.addRole(UserRole.USER);
        return user;
    }
}
