package com.ultracards.ui.webui.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class StartupBanner {

    private final WebServerApplicationContext webCtx;

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE) // run as late as possible, is default now but should not be removed nonetheless
    public void printBanner() {
        int port = webCtx.getWebServer().getPort();

        log.info("=================================================");
        log.info("-        ULTRACARDS Web UI Application          -");
        log.info("=================================================");
        log.info("Access the application at: http://localhost:{}",  port);
        log.info("=================================================");
    }
}
