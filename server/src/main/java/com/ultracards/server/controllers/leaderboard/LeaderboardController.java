package com.ultracards.server.controllers.leaderboard;

import com.ultracards.gateway.dto.leaderboard.LeaderboardPageDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.leaderboard.LeaderboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leaderboards")
@RequiredArgsConstructor
public class LeaderboardController {
    private final LeaderboardService leaderboardService;

    @GetMapping
    public LeaderboardPageDTO get(
            @RequestParam(defaultValue = "GAMES_PLAYED") String metric,
            @RequestParam(required = false) String gameType,
            @RequestParam(required = false) String mode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @AuthenticationPrincipal UserEntity currentUser
    ) {
        return leaderboardService.get(metric, gameType, mode, page, size, currentUser);
    }
}
