package com.ultracards.gateway.app;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class GatewayAsync implements AutoCloseable {
    private final Executor background;
    private final Consumer<Runnable> ui;
    private final boolean ownsBackground;

    public GatewayAsync(Executor background, Consumer<Runnable> ui) {
        this(background, ui, false);
    }

    private GatewayAsync(Executor background, Consumer<Runnable> ui, boolean ownsBackground) {
        this.background = background;
        this.ui = ui;
        this.ownsBackground = ownsBackground;
    }

    public static GatewayAsync cached(Consumer<Runnable> ui) {
        return new GatewayAsync(Executors.newCachedThreadPool(), ui, true);
    }

    public static GatewayAsync direct() {
        return new GatewayAsync(Runnable::run, Runnable::run);
    }

    public <T> CompletableFuture<T> call(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, background);
    }

    public CompletableFuture<Void> run(Runnable action) {
        return CompletableFuture.runAsync(action, background);
    }

    public void runOnUi(Runnable action) {
        ui.accept(action);
    }

    public <T> void onUi(
            CompletableFuture<T> future,
            Consumer<T> onSuccess,
            Consumer<Throwable> onError
    ) {
        future.whenComplete((value, error) -> ui.accept(() -> {
            if (error == null) {
                onSuccess.accept(value);
            } else if (onError != null) {
                onError.accept(unwrap(error));
            }
        }));
    }

    private Throwable unwrap(Throwable error) {
        return error.getCause() != null ? error.getCause() : error;
    }

    @Override
    public void close() {
        if (ownsBackground && background instanceof ExecutorService service) {
            service.shutdownNow();
        }
    }
}
