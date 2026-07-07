package com.ultracards.gateway.app;

import org.springframework.messaging.simp.stomp.StompSession;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class GatewaySocketHandle implements AutoCloseable {
    private final List<GatewaySubscription> subscriptions = new CopyOnWriteArrayList<>();
    private final Runnable disconnect;

    public GatewaySocketHandle(Runnable disconnect) {
        this.disconnect = disconnect;
    }

    public GatewaySubscription track(StompSession.Subscription subscription) {
        var tracked = GatewaySubscription.of(subscription);
        subscriptions.add(tracked);
        return tracked;
    }

    @Override
    public void close() {
        for (var subscription : subscriptions) {
            subscription.close();
        }
        subscriptions.clear();
        disconnect.run();
    }
}
