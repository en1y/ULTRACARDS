package com.ultracards.ui.webui.service;

import com.ultracards.gateway.service.ServerService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import static com.ultracards.gateway.service.ServerService.ServerConnectionStatus.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServerConnectionService {

    private final ServerService serverService;
    private final ConfigurableApplicationContext context;

    @Value("${app.ultracards.server.await-server-connections-for-x-times:1}")
    private int AWAIT_SERVER_CONNECTIONS_FOR_X_TIMES;
    @Value("${spring.application.name}")
    private String appName;

    private int serverOffCounter = 0;
    private boolean shuttingDown = false;

    @Scheduled(fixedRateString = "${app.ultracards.server.check-interval-ms:10000}", initialDelayString = "5000")
    public void checkIsTheServerUp() {
        if (shuttingDown || !context.isActive() || Thread.currentThread().isInterrupted()) {
            return;
        }

        // TODO: get the logging back up
        if (true) return;

        var serverConnectionStatus = serverService.isTheServerUp();

        log.info("Server connection status is \"{}\"", serverConnectionStatus);

        if (serverConnectionStatus.equals(ERROR) ||
            serverConnectionStatus.equals(REFUSED)) {
            serverOffCounter++;
            log.warn("Server connection has been refused for {} times.", serverOffCounter);
        }

        if (serverConnectionStatus.equals(CONNECTED) && serverOffCounter > 0)
            serverOffCounter = 0;

        if (serverOffCounter >= AWAIT_SERVER_CONNECTIONS_FOR_X_TIMES) {
            log.error("Server connection has been refused for {} times. Turning \"{}\" off.", serverOffCounter, appName);
            exit();
        }
    }

    private void exit () {
        SpringApplication.exit(context, () -> 1);
        context.close();
    }

    @PreDestroy
    void shutdown() {
        shuttingDown = true;
        log.info("Shutting down: scheduler will no-op going forward.");
    }

}
