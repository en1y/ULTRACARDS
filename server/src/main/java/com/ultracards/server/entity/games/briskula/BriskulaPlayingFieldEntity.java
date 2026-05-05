package com.ultracards.server.entity.games.briskula;

import com.ultracards.cards.ItalianCardSuit;
import com.ultracards.games.briskula.BriskulaCard;
import com.ultracards.games.briskula.BriskulaGameConfig;
import com.ultracards.games.briskula.BriskulaPlayer;
import com.ultracards.server.entity.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "briskula_playing_fields")
@Getter
public class BriskulaPlayingFieldEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "briskula_game_id", nullable = false)
    private BriskulaGameEntity game;

    @Column(name = "field_order", nullable = false)
    private Integer fieldOrder;

    @Column(name = "played_cards", nullable = false, length = 32)
    private String playedCards = "";

    @Column(name = "played_player_ids", nullable = false)
    private String playedPlayerIds = "";

    @Column(name = "player_hands", nullable = false, length = 512)
    private String playerHands = "";

    @Transient
    private final List<BriskulaCard> playedCardValues = new ArrayList<>();

    @Transient
    private final List<UserEntity> playedCardPlayers = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_user_id")
    private UserEntity winner;

    @Column(name = "total_points")
    private Integer totalPoints;

    protected BriskulaPlayingFieldEntity() {
    }

    BriskulaPlayingFieldEntity(BriskulaGameEntity game, Integer fieldOrder) {
        this.game = game;
        this.fieldOrder = fieldOrder;
    }

    void savePlayerHands(List<BriskulaPlayer> players) {
        var hands = new StringBuilder();
        for (var player : players) {
            var playerEntity = (BriskulaPlayerEntity) player;
            if (!hands.isEmpty()) {
                hands.append("|");
            }
            hands.append(playerEntity.getUser().getId()).append(":");

            var cards = playerEntity.getHand().getCards();
            for (int i = 0; i < cards.size(); i++) {
                if (i > 0) {
                    hands.append("-");
                }
                hands.append(toConciseCard(cards.get(i)));
            }
        }
        playerHands = hands.toString();
    }

    void addPlayedCard(UserEntity player, BriskulaCard card) {
        playedCardPlayers.add(player);
        playedCardValues.add(card);
        playedCards = playedCards.isBlank() ? toConciseCard(card) : playedCards + "," + toConciseCard(card);
        playedPlayerIds = playedPlayerIds.isBlank() ? player.getId().toString() : playedPlayerIds + "," + player.getId();
    }

    boolean isComplete(BriskulaGameConfig gameConfig) {
        return playedCardValues.size() >= expectedCardsInField(gameConfig);
    }

    void markComplete(ItalianCardSuit gameTrumpCardSuit) {
        if (playedCardValues.isEmpty()) {
            return;
        }

        var points = 0;
        for (var card : playedCardValues) {
            points += card.getPoints();
        }
        totalPoints = points;

        var roundTrumpCardSuit = playedCardValues.getFirst().getSuit();
        var winningCard = playedCardValues.getFirst();
        for (int i = 1; i < playedCardValues.size(); i++) {
            var card = playedCardValues.get(i);
            if (card.compareTo(gameTrumpCardSuit, roundTrumpCardSuit, winningCard) > 0) {
                winningCard = card;
            }
        }
        winner = playedCardPlayers.get(playedCardValues.indexOf(winningCard));
    }

    private int expectedCardsInField(BriskulaGameConfig gameConfig) {
        if (gameConfig.equals(BriskulaGameConfig.TWO_PLAYERS_FOUR_CARDS_IN_HAND_EACH)) {
            return 4;
        }
        return gameConfig.getNumberOfPlayers();
    }

    private String toConciseCard(BriskulaCard card) {
        return card.getSuit().name().substring(0, 1) + card.getValue().getNumber();
    }
}
