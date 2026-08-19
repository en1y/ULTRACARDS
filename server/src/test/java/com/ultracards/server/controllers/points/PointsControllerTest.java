package com.ultracards.server.controllers.points;

import com.ultracards.gateway.dto.points.PointTransactionDTO;
import com.ultracards.gateway.dto.points.PointTransactionPageDTO;
import com.ultracards.gateway.dto.points.PointsSeriesPointDTO;
import com.ultracards.server.service.points.AchievementService;
import com.ultracards.server.service.points.PointsService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PointsControllerTest {
    private final PointsService pointsService = mock(PointsService.class);
    private final AchievementService achievementService = mock(AchievementService.class);
    private final PointsController controller = new PointsController(pointsService, achievementService);

    @Test
    void exposesReadOnlyPointHistoryAndSeriesForAnotherUser() {
        var privateTransaction = new PointTransactionDTO(UUID.randomUUID(), 25L, 1_525L,
                "ADMIN_ADJUSTMENT", "internal admin note", Instant.now());
        var page = new PointTransactionPageDTO(List.of(privateTransaction), 0, 10, 1, 1);
        when(pointsService.transactions(42L, 0, 10)).thenReturn(page);
        when(pointsService.series(42L, 30)).thenReturn(List.of());

        var publicPage = controller.userTransactions(42L, 0, 10);
        assertThat(publicPage.items()).singleElement().satisfies(transaction -> {
            assertThat(transaction.id()).isEqualTo(privateTransaction.id());
            assertThat(transaction.referenceId()).isNull();
        });
        assertThat(controller.userSeries(42L, 30)).isEmpty();
        verify(pointsService).transactions(42L, 0, 10);
        verify(pointsService).series(42L, 30);
    }
}
