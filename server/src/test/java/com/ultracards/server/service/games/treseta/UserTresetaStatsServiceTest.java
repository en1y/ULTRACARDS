package com.ultracards.server.service.games.treseta;

import com.ultracards.games.treseta.TresetaGameConfig;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.gamestats.UserTresetaStats;
import com.ultracards.server.repositories.games.UserTresetaStatsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserTresetaStatsServiceTest {

    @Mock
    private UserTresetaStatsRepository repository;

    @Test
    void getByUserCreatesAndPersistsEmptyStatsWhenMissing() {
        var service = new UserTresetaStatsService(repository);
        var user = new UserEntity("player@example.com", "player");
        when(repository.findByUser(user)).thenReturn(Optional.empty());
        when(repository.save(any(UserTresetaStats.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var stats = service.getByUser(user);

        assertSame(user, stats.getUser());
        verify(repository).save(stats);
    }

    @Test
    void getByUserReturnsExistingStatsWithoutSaving() {
        var service = new UserTresetaStatsService(repository);
        var user = new UserEntity("player@example.com", "player");
        var existing = new UserTresetaStats(user);
        when(repository.findByUser(user)).thenReturn(Optional.of(existing));

        assertSame(existing, service.getByUser(user));
        verify(repository, never()).save(any());
    }

    @Test
    void addTresetaGameRecordsOntoFreshlyCreatedStats() {
        var service = new UserTresetaStatsService(repository);
        var user = new UserEntity("player@example.com", "player");
        var created = new UserTresetaStats(user);
        when(repository.findByUser(user)).thenReturn(Optional.empty());
        when(repository.save(any(UserTresetaStats.class))).thenReturn(created);

        service.addTresetaGame(user, TresetaGameConfig.TWO_PLAYERS, true);

        var entry = created.getConfigStats().get(TresetaGameConfig.TWO_PLAYERS);
        assertEquals(1, entry.getPlayed());
        assertEquals(1, entry.getWins());
    }
}
