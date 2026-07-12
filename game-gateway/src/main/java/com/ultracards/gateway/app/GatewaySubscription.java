package com.ultracards.gateway.app;

import org.springframework.messaging.simp.stomp.StompSession;

import java.util.concurrent.atomic.AtomicBoolean;

public class GatewaySubscription implements AutoCloseable {
    private final StompSession.Subscription subscription;
    private final AtomicBoolean closed = new AtomicBoolean();

    private GatewaySubscription(StompSession.Subscription subscription) {
        this.subscription = subscription;
    }

    public static GatewaySubscription of(StompSession.Subscription subscription) {
        return new GatewaySubscription(subscription);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        if (subscription != null) subscription.unsubscribe();
    }
}
