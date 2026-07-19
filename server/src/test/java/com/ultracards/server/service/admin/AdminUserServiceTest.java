package com.ultracards.server.service.admin;

import com.ultracards.gateway.dto.admin.AdminUserPatchDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.enums.UserRole;
import com.ultracards.server.enums.UserStatus;
import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.service.auth.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AdminUserServiceTest {
    private final UserRepository repository = mock(UserRepository.class);
    private final SessionService sessionService = mock(SessionService.class);
    private final AdminAuditService auditService = mock(AdminAuditService.class);
    private AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(repository, sessionService, auditService);
        ReflectionTestUtils.setField(service, "maxUsernameLength", 30);
        ReflectionTestUtils.setField(service, "maxEmailLength", 150);
    }

    @Test
    void listsUsersByIdAscending() {
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.list(0, 25);

        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("id"))
                .isNotNull()
                .extracting(Sort.Order::getDirection)
                .isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void dryRunReturnsPreviewWithoutWriting() {
        var actor = user(1L, "admin@example.com", "Admin", UserRole.ADMIN);
        var target = user(2L, "user@example.com", "Old", UserRole.USER);
        when(repository.findByIdForUpdate(2L)).thenReturn(Optional.of(target));

        var result = service.patch(actor, 2L,
                new AdminUserPatchDTO("New", null, null, null, "preview", true));

        assertThat(result.username()).isEqualTo("New");
        assertThat(target.getUsername()).isEqualTo("Old");
        verify(repository, never()).save(any());
        verifyNoInteractions(sessionService, auditService);
    }

    @Test
    void emailChangeRevokesSessionsAtomicallyWithTheEdit() {
        var actor = user(1L, "admin@example.com", "Admin", UserRole.ADMIN);
        var target = user(2L, "old@example.com", "User", UserRole.USER);
        when(repository.findByIdForUpdate(2L)).thenReturn(Optional.of(target));

        service.patch(actor, 2L,
                new AdminUserPatchDTO(null, "new@example.com", null, null, "correct address", false));

        assertThat(target.getEmail()).isEqualTo("new@example.com");
        verify(repository).save(target);
        verify(sessionService).revokeAllSessions(2L);
        verify(auditService).record(eq(1L), eq("UPDATE_USER"), eq("USER"), eq("2"),
                eq("correct address"), anyString(), eq("SUCCESS"), any());
    }

    @Test
    void enablingAlsoRestoresActiveState() {
        var actor = user(1L, "admin@example.com", "Admin", UserRole.ADMIN);
        var target = user(2L, "user@example.com", "User", UserRole.USER);
        target.setEnabled(false);
        target.setStatus(UserStatus.DISABLED);
        when(repository.findByIdForUpdate(2L)).thenReturn(Optional.of(target));

        service.patch(actor, 2L,
                new AdminUserPatchDTO(null, null, true, null, "restore access", false));

        assertThat(target.isEnabled()).isTrue();
        assertThat(target.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void userRoleCannotBeRevoked() {
        var actor = user(1L, "admin@example.com", "Admin", UserRole.ADMIN);

        assertThatThrownBy(() -> service.revokeRole(actor, 2L, "USER", "bad request"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(repository, sessionService, auditService);
    }

    @Test
    void administratorCannotDisableSelf() {
        var actor = user(1L, "admin@example.com", "Admin", UserRole.ADMIN);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(actor));

        assertThatThrownBy(() -> service.patch(actor, 1L,
                new AdminUserPatchDTO(null, null, false, null, "self disable", false)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void lastActiveAdministratorCannotBeDemoted() {
        var target = user(1L, "first@example.com", "First", UserRole.ADMIN);
        var actor = user(2L, "recovery@example.com", "Recovery", UserRole.ADMIN);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(target));
        when(repository.findAllByRoleForUpdate(UserRole.ADMIN)).thenReturn(List.of(target));

        assertThatThrownBy(() -> service.revokeRole(actor, 1L, "ADMIN", "unsafe"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(repository, never()).save(any());
    }

    private UserEntity user(Long id, String email, String username, UserRole... roles) {
        var user = new UserEntity(email, username);
        user.setId(id);
        user.setStatus(UserStatus.ACTIVE);
        for (var role : roles) user.addRole(role);
        return user;
    }
}
