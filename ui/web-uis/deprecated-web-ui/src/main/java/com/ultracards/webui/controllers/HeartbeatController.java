package com.ultracards.webui.controllers;

import com.ultracards.gateway.service.GameService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class HeartbeatController {

    private final GameService gameService;
    private final ConcurrentHashMap<Long, HeartbeatPayload> userHeartbeatMap = new ConcurrentHashMap<>();

    public HeartbeatController(GameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<Void> receiveHeartbeat(@RequestBody HeartbeatPayload payload, HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) {
            return ResponseEntity.status(401).build();
        }

        var userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        if (payload.getTimestamp() == null) {
            return ResponseEntity.badRequest().build(); // missing or invalid payload
        }

        var token = (String) session.getAttribute("token");

        if (token == null) {
            return ResponseEntity.status(401).build();
        }

        payload.setToken(token);

        userHeartbeatMap.put(userId, payload);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/active-users")
    public Set<Long> getActiveUsers() {
        long now = System.currentTimeMillis();

        return userHeartbeatMap.entrySet().stream()
                .filter(e -> now - e.getValue().getTimestamp() < 30000)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    @Scheduled(fixedRate = 60000) // every minute
    public void cleanupOldHeartbeats() {
        long cutoff = System.currentTimeMillis() - 30000; // 30 seconds inactivity
        userHeartbeatMap.entrySet().removeIf(e -> {

            var value = e.getValue();
            var res = value.getTimestamp() < cutoff;

            if (!res) gameService.stopGamesByPlayer(e.getKey(), value.getToken());
            // TODO: After implementing the admin role just pass the user into the designated method for removal of games which are created by the player who is offline

            return res;
        });
    }

    public static class HeartbeatPayload {
        private Long timestamp;
        private String token;

        public HeartbeatPayload() {}

        public Long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Long timestamp) {
            this.timestamp = timestamp;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }

}
