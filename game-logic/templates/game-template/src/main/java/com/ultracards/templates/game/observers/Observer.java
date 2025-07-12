package com.ultracards.templates.game.observers;

public interface Observer<T> {
    void update(T event);
}
