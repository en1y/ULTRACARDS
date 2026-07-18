package com.ultracards.server.service.admin;

import com.ultracards.gateway.dto.admin.AdminStatusDTO;
import com.ultracards.server.service.games.GameManager;
import com.ultracards.server.service.lobby.LobbyManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AdminSystemService {
    private final DataSource dataSource;
    private final LobbyManager lobbyManager;
    private final GameManager gameManager;
    private final AdminReportService reportService;

    @Value("${app.version:unknown}")
    private String serverVersion;

    public AdminStatusDTO status() {
        return new AdminStatusDTO(1, serverVersion,
                (System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getStartTime()) / 1000,
                databaseAvailable(), reportService.flywayVersion(), lobbyManager.getLobbies().size(),
                gameManager.getGames().size(), Instant.now());
    }

    private boolean databaseAvailable() {
        try (var connection = dataSource.getConnection()) { return connection.isValid(2); }
        catch (Exception ignored) { return false; }
    }
}
