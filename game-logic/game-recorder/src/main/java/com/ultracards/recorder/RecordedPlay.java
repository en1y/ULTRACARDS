package com.ultracards.recorder;

import jakarta.persistence.*;

@Entity
@Table(name = "recorded_plays")
public class RecordedPlay {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "play_order", nullable = false)
    private int order;
    @Embedded
    private RecordedPlayer player;
    @Embedded
    private RecordedCard card;

    protected RecordedPlay() {
    }

    public RecordedPlay(int order, RecordedPlayer player, RecordedCard card) {
        this.order = order;
        this.player = player;
        this.card = card;
    }

    public int order() {
        return order;
    }

    public RecordedPlayer player() {
        return player;
    }

    public RecordedCard card() {
        return card;
    }
}
