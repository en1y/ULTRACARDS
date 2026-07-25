package com.ultracards.config;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WebSocketConfigTest {

    @Test
    void preservesOutboundMessageOrder() {
        var registry = mock(MessageBrokerRegistry.class);

        new WebSocketConfig().configureMessageBroker(registry);

        verify(registry).setPreservePublishOrder(true);
    }
}
