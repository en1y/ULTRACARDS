package com.ultracards.gateway.dto.admin;

import java.time.LocalDate;
import java.util.List;

public record AdminEconomyDashboardDTO(
        int wagerFeePercent,
        long pointsInAccounts,
        long pointsInEscrow,
        long pointsInCirculation,
        long changeLast24Hours,
        long changeLast7Days,
        long changeLast30Days,
        long mintedAllTime,
        long removedAllTime,
        long totalWagers,
        long openWagers,
        long biggestWager,
        long totalStaked,
        long totalPaidOut,
        long totalRake,
        long dailyClaimsToday,
        List<DailyTotal> dailyTotals,
        List<LeaderboardEntry> leaderboard
) {
    public record DailyTotal(LocalDate day, long balance, long change) {
    }

    public record LeaderboardEntry(long position, Long userId, String username, long points) {
    }
}
