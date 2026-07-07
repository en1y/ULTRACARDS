package com.ultracards.gateway.app;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class GatewayState<T> {
    private final List<Consumer<T>> listeners = new CopyOnWriteArrayList<>();
    private volatile T value;

    public GatewayState() {
    }

    public GatewayState(T value) {
        this.value = value;
    }

    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = value;
        for (var listener : listeners) {
            listener.accept(value);
        }
    }

    public GatewayListener listen(Consumer<T> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }
}
