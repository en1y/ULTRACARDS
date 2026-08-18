package com.ultracards.server.service.admin;

import com.ultracards.gateway.dto.admin.AdminPointsPatchDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.repositories.UserRepository;
import com.ultracards.server.service.points.PointsService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminPointsServiceTest {
    @Test
    void rejectsAnAdjustmentThatOverflowsTheBalance() {
        var users = mock(UserRepository.class);
        var points = mock(PointsService.class);
        var audit = mock(AdminAuditService.class);
        var service = new AdminPointsService(users, points, audit);
        var actor = mock(UserEntity.class);
        var user = mock(UserEntity.class);
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(user.getPointsBalance()).thenReturn(Long.MAX_VALUE);

        assertThatThrownBy(() -> service.adjust(actor, 7L, new AdminPointsPatchDTO(1L, "test", true)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(points, audit);
    }
}
