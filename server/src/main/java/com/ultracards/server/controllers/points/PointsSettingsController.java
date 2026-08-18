package com.ultracards.server.controllers.points;

import com.ultracards.gateway.dto.points.PointsSettingsDTO;
import com.ultracards.server.service.games.GameAvailabilityService;
import com.ultracards.server.service.points.PointsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PointsSettingsController {
    private final PointsService pointsService;
    private final GameAvailabilityService availability;

    /**
     * Without a game this is the global fee; with one it is what that game and mode
     * actually charge, so a lobby can quote its own number.
     */
    @GetMapping("/api/points/settings")
    public PointsSettingsDTO settings(@RequestParam(required = false) String game,
                                      @RequestParam(required = false) String mode) {
        if (game == null || game.isBlank()) return pointsService.settings();
        var gameType = availability.game(game);
        return pointsService.settings(gameType, availability.mode(gameType, mode));
    }
}
