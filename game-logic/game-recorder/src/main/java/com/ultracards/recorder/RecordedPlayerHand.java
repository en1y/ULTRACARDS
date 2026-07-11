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

    public List<RecordedCard> cards() {
        return List.copyOf(cards);
    }
}
