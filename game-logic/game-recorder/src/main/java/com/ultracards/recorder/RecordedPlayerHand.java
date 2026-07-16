package com.ultracards.recorder;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "recorded_player_hands")
public class RecordedPlayerHand {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_id", nullable = false)
    private RecordedRound round;
    @Column(name = "hand_order", nullable = false)
    private int order;
    @Embedded
    private RecordedPlayer player;
    @ElementCollection
    @CollectionTable(name = "recorded_hand_cards", joinColumns = @JoinColumn(name = "hand_id"))
    private List<RecordedCard> cards = new ArrayList<>();

    protected RecordedPlayerHand() {
    }

    public RecordedPlayerHand(RecordedPlayer player, List<RecordedCard> cards) {
        this.player = player;
        this.cards.addAll(cards);
    }

    public RecordedPlayer player() {
        return player;
    }

    RecordedRound round() {
        return round;
    }

    void setRound(RecordedRound round) {
        this.round = round;
    }

    int order() {
        return order;
    }

    void setOrder(int order) {
        this.order = order;
    }

    public List<RecordedCard> cards() {
        return List.copyOf(cards);
    }
}
