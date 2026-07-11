package com.ultracards.gateway.app;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class GatewayTest {
    private GatewayTest() {
    }

    static void main(String[] args) {
        var state = new GatewayState<>("initial");
        var seen = new AtomicReference<String>();
        var listener = state.listen(seen::set);

        state.set("updated");
        assert "updated".equals(seen.get());

        listener.close();
        state.set("ignored");
        assert "updated".equals(seen.get());

        var async = GatewayAsync.direct();
        assert "done".equals(async.call(() -> "done").join());

        var uiSeen = new AtomicReference<String>();
        async.onUi(CompletableFuture.completedFuture("ui"), uiSeen::set, error -> {
            throw new AssertionError(error);
        });
        assert "ui".equals(uiSeen.get());
    }
}
