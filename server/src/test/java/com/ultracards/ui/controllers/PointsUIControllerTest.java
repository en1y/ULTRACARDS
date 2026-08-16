package com.ultracards.ui.controllers;

import com.ultracards.gateway.dto.points.PointTransactionPageDTO;
import com.ultracards.gateway.dto.points.PointsAccountDTO;
import com.ultracards.gateway.dto.points.PointsSettingsDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.points.PointsService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

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
        var settings = new PointsSettingsDTO(12);
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
}
