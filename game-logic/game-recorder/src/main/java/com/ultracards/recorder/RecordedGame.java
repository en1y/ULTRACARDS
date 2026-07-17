package com.ultracards.recorder;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "recorded_games")
@Inheritance(strategy = InheritanceType.JOINED)
public class RecordedGame {
    @Id
    private UUID id;
    @Column(name = "lobby_id", nullable = false)
    private UUID lobbyId;
    @Column(nullable = false)
    private String name;
    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "started_at")
    private Instant startedAt;
    @Column(name = "ended_at")
    private Instant endedAt;
    @ElementCollection
    @CollectionTable(name = "recorded_game_players", joinColumns = @JoinColumn(name = "game_id"))
    @OrderColumn(name = "player_order")
    private List<RecordedPlayer> players = new ArrayList<>();
    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("order ASC")
    private List<RecordedRound> rounds = new ArrayList<>();
    @ElementCollection
    @CollectionTable(name = "recorded_game_attributes", joinColumns = @JoinColumn(name = "game_id"))
    @MapKeyColumn(name = "attribute_key")
    @Column(name = "attribute_value")
    private Map<String, String> attributes = new LinkedHashMap<>();

    protected RecordedGame() {
    }

    public RecordedGame(UUID id, UUID lobbyId, String name, Long ownerUserId) {
        this.id = id;
        this.lobbyId = lobbyId;
        this.name = name;
        this.ownerUserId = ownerUserId;
    }

    public UUID id() {
        return id;
    }

    public UUID lobbyId() {
        return lobbyId;
    }

    public String name() {
        return name;
    }

    public void rename(String name) {
        this.name = name;
    }

    public Long ownerUserId() {
        return ownerUserId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant endedAt() {
        return endedAt;
    }

    public List<RecordedPlayer> players() {
        return players.stream().filter(Objects::nonNull).toList();
    }

    public List<RecordedRound> rounds() {
        return List.copyOf(rounds);
    }

    public Map<String, String> attributes() {
        return Map.copyOf(attributes);
    }

    void started(List<RecordedPlayer> players, Map<String, String> attributes) {
        startedAt = Instant.now();
        endedAt = null;
        this.players = new ArrayList<>(players);
        rounds.clear();
        this.attributes = new LinkedHashMap<>(attributes);
    }

    void ended(Map<String, String> attributes) {
        endedAt = Instant.now();
        this.attributes = new LinkedHashMap<>(attributes);
    }

    void addRound(RecordedRound round) {
        round.setGame(this);
        rounds.add(round);
    }
}
