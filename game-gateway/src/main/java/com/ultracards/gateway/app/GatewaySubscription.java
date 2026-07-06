package com.ultracards.gateway.app;

import org.springframework.messaging.simp.stomp.StompSession;

public class GatewaySubscription implements AutoCloseable {
    private final StompSession.Subscription subscription;

    private GatewaySubscription(StompSession.Subscription subscription) {
        this.subscription = subscription;
    }

    public static GatewaySubscription of(StompSession.Subscription subscription) {
        return new GatewaySubscription(subscription);
    }

    @Override
    public void close() {
        if (subscription != null) {
            subscription.unsubscribe();
        }
    }
}
