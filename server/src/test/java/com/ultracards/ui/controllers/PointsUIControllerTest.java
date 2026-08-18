package com.ultracards.ui.controllers;

import com.ultracards.gateway.dto.points.PointTransactionPageDTO;
import com.ultracards.gateway.dto.points.PointEventDTO;
import com.ultracards.gateway.dto.points.PointsAccountDTO;
import com.ultracards.gateway.dto.points.PointsSettingsDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.points.PointsService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PointsUIControllerTest {
    @Test
    void preloadsThePlayerPointsPage() {
        var service = mock(PointsService.class);
        var controller = new PointsUIController(service);
        var user = new UserEntity("player@ultracards.test", "player");
        user.setId(42L);
        var account = new PointsAccountDTO(1_500, 250, false, null, null, List.of());
        var transactions = new PointTransactionPageDTO(List.of(), 0, 25, 0, 0);
        var settings = new PointsSettingsDTO(12, 1_500, 1_500);
        when(service.summary(user)).thenReturn(account);
        when(service.events(user)).thenReturn(List.of());
        when(service.transactions(user, 0, 25)).thenReturn(transactions);
        when(service.series(42L, 3)).thenReturn(List.of());
        when(service.settings()).thenReturn(settings);
        var model = new ExtendedModelMap();

        assertThat(controller.points(user, model)).isEqualTo("ui/points");
        assertThat(model.get("pointsAccount")).isSameAs(account);
        assertThat(model.get("pointsBalance")).isEqualTo(1_500L);
        assertThat(model.get("pointsEvents")).isEqualTo(List.of());
        assertThat(model.get("pointsTransactions")).isSameAs(transactions);
        assertThat(model.get("pointsSeries")).isEqualTo(List.of());
        assertThat(model.get("pointsSettings")).isSameAs(settings);
        assertThat(model.get("pointsNow")).isNotNull();
    }

    @Test
    void keepsACompletedNeverEndingEventBehindFinishedHistory() {
        var service = mock(PointsService.class);
        var controller = new PointsUIController(service);
        var user = new UserEntity("history@ultracards.test", "history");
        user.setId(43L);
        var completed = mock(PointEventDTO.class);
        var ended = mock(PointEventDTO.class);
        when(completed.active()).thenReturn(false);
        when(completed.endsAt()).thenReturn(null);
        when(ended.active()).thenReturn(false);
        when(ended.endsAt()).thenReturn(Instant.now().minusSeconds(60));
        when(service.summary(user)).thenReturn(new PointsAccountDTO(1_500, 0, false, null, null, List.of()));
        when(service.events(user)).thenReturn(List.of(completed, ended));
        when(service.transactions(user, 0, 25)).thenReturn(new PointTransactionPageDTO(List.of(), 0, 25, 0, 0));
        when(service.series(43L, 3)).thenReturn(List.of());
        when(service.settings()).thenReturn(new PointsSettingsDTO(12, 1_500, 1_500));
        var model = new ExtendedModelMap();

        controller.points(user, model);

        assertThat(model.get("pointsEvents")).isEqualTo(List.of(ended));
        assertThat(model.get("pointsMoreEvents")).isEqualTo(List.of(completed));
    }
}
