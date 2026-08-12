package com.ultracards.server.controllers.points;

import com.ultracards.gateway.dto.points.PointsSettingsDTO;
import com.ultracards.server.service.points.PointsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PointsSettingsController {
    private final PointsService pointsService;

    @GetMapping("/api/points/settings")
    public PointsSettingsDTO settings() {
        return pointsService.settings();
    }
}
