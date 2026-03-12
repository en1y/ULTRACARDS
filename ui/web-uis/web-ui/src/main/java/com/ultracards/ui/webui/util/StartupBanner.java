package com.ultracards.ui.webui.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class StartupBanner {

    private final WebServerApplicationContext webCtx;

    @EventListener(ApplicationReadyEvent.class)
    public void printBanner() {
        int port = webCtx.getWebServer().getPort();

        log.info("=================================================");
        log.info("-        ULTRACARDS Web UI Application          -");
        log.info("=================================================");
        log.info("Access the application at: http://localhost:{}",  port);
        log.info("=================================================");
    }
}
