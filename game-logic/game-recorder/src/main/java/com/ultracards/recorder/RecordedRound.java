package com.ultracards.recorder;

import jakarta.persistence.*;

import java.util.*;

@Entity
@Table(name = "recorded_rounds")
public class RecordedRound {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "round_order", nullable = false)
    private int order;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private RecordedGame game;
    @OneToMany(mappedBy = "round", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("order ASC")
    private List<RecordedPlayerHand> startingHands = new ArrayList<>();
    @OneToMany(mappedBy = "round", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("order ASC")
    private List<RecordedPlay> plays = new ArrayList<>();
    @Embedded
    private RecordedPlayer winner;
    @ElementCollection
    @CollectionTable(name = "recorded_round_attributes", joinColumns = @JoinColumn(name = "round_id"))
    @MapKeyColumn(name = "attribute_key")
    @Column(name = "attribute_value")
    private Map<String, String> attributes = new LinkedHashMap<>();

    protected RecordedRound() {
    }

    public RecordedRound(int order, List<RecordedPlayerHand> hands, List<RecordedPlay> plays, RecordedPlayer winner, Map<String, String> attributes) {
        this.order = order;
        for (var index = 0; index < hands.size(); index++) {
            var hand = hands.get(index);
            hand.setRound(this);
            hand.setOrder(index);
            this.startingHands.add(hand);
        }
        for (var play : plays) {
            play.setRound(this);
            this.plays.add(play);
        }
        this.winner = winner;
        this.attributes.putAll(attributes);
    }

    public int order() {
        return order;
    }

    RecordedGame game() {
        return game;
    }

    void setGame(RecordedGame game) {
        this.game = game;
    }

    public List<RecordedPlayerHand> startingHands() {
        return List.copyOf(startingHands);
    }

    public List<RecordedPlay> plays() {
        return List.copyOf(plays);
    }

    public RecordedPlayer winner() {
        return winner;
    }

    public Map<String, String> attributes() {
        return Map.copyOf(attributes);
    }
}
