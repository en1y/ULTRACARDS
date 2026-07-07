package com.ultracards.gateway.app;

@FunctionalInterface
public interface GatewayListener extends AutoCloseable {
    @Override
    void close();
}
