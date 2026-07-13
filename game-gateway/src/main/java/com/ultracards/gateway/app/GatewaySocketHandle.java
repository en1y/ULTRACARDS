package com.ultracards.gateway.app;

import org.springframework.messaging.simp.stomp.StompSession;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class GatewaySocketHandle implements AutoCloseable {
    private final List<GatewaySubscription> subscriptions = new CopyOnWriteArrayList<>();
    private final Runnable disconnect;
    private final AtomicBoolean closed = new AtomicBoolean();

    public GatewaySocketHandle(Runnable disconnect) {
        this.disconnect = disconnect;
    }

    public GatewaySubscription track(StompSession.Subscription subscription) {
        var tracked = GatewaySubscription.of(subscription);
        if (closed.get()) {
            tracked.close();
            return tracked;
        }
        subscriptions.add(tracked);
        if (closed.get() && subscriptions.remove(tracked)) tracked.close();
        return tracked;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        RuntimeException failure = null;
        for (var subscription : subscriptions) {
            try {
                subscription.close();
            } catch (RuntimeException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        subscriptions.clear();
        try {
            disconnect.run();
        } catch (RuntimeException error) {
            if (failure == null) failure = error;
            else failure.addSuppressed(error);
        }
        if (failure != null) throw failure;
    }
}
