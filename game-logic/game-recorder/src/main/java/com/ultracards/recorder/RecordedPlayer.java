package com.ultracards.recorder;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class RecordedPlayer {
    @Column(name = "user_id")
    private Long id;
    private String name;

    protected RecordedPlayer() {
    }

    public RecordedPlayer(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }
}
