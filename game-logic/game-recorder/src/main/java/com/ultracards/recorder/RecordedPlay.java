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
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_id", nullable = false)
    private RecordedRound round;
    @Embedded
    private RecordedPlayer player;
    @Embedded
    private RecordedCard card;
    /**
     * The role of this card play. Trick games record {@code PLAY}; Durak records
     * {@code ATTACK}, {@code DEFEND}, {@code THROW_IN} or {@code PASS}.
     */
    @Column(name = "action_type", nullable = false, length = 32)
    private String actionType = PLAY;
    /** For a Durak defense: the play order of the attack this card covers. */
    @Column(name = "target_play_order")
    private Integer targetPlayOrder;

    public static final String PLAY = "PLAY";

    protected RecordedPlay() {
    }

    public RecordedPlay(int order, RecordedPlayer player, RecordedCard card) {
        this(order, player, card, PLAY, null);
    }

    public RecordedPlay(int order, RecordedPlayer player, RecordedCard card,
                        String actionType, Integer targetPlayOrder) {
        this.order = order;
        this.player = player;
        this.card = card;
        this.actionType = actionType == null || actionType.isBlank() ? PLAY : actionType;
        this.targetPlayOrder = targetPlayOrder;
    }

    public String actionType() {
        return actionType;
    }

    public Integer targetPlayOrder() {
        return targetPlayOrder;
    }

    public int order() {
        return order;
    }

    RecordedRound round() {
        return round;
    }

    void setRound(RecordedRound round) {
        this.round = round;
    }

    public RecordedPlayer player() {
        return player;
    }

    public RecordedCard card() {
        return card;
    }
}
