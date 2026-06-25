package com.ultracards.server.entity.games.briskula;

import com.ultracards.cards.ItalianCard;
import com.ultracards.cards.ItalianCardSuit;
import com.ultracards.cards.ItalianCardValue;
import com.ultracards.games.briskula.*;
import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.gateway.dto.games.games.GameCardDTO;
import com.ultracards.gateway.dto.games.games.briskula.BriskulaGameEntityDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.GameEntity;
import com.ultracards.server.entity.lobby.BriskulaLobbyGameConfig;
import com.ultracards.templates.cards.AbstractCard;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.*;

@Entity
@Getter
@Table(name = "briskula_games")
public class BriskulaGameEntity extends GameEntity<BriskulaGame, BriskulaLobbyGameConfig> {

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("fieldOrder ASC")
    private final List<BriskulaPlayingFieldEntity> briskulaPlayingFields = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "briskula_game_team_players",
            joinColumns = @JoinColumn(name = "briskula_game_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @OrderColumn(name = "player_order")
    private final List<UserEntity> teamPlayers = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "game_config", nullable = false, length = 80)
    private BriskulaGameConfig persistedGameConfig;

    @Enumerated(EnumType.STRING)
    @Column(name = "trump_card_suit", nullable = false, length = 30)
    private ItalianCardSuit trumpCardSuit;

    @Enumerated(EnumType.STRING)
    @Column(name = "trump_card_value", nullable = false, length = 30)
    private ItalianCardValue trumpCardValue;

    @Column(name = "ended_at", nullable = false)
    private Instant endedAt;

    @Transient
    @Getter @Setter
    private boolean haveTeammateCardBeenDisplayed = false;

    protected BriskulaGameEntity() {
    }

    public BriskulaGameEntity(UUID lobbyId, String name, UserEntity owner, BriskulaLobbyGameConfig briskulaConfig, List<UserEntity> players) {
        var briskulaPlayers = new ArrayList<BriskulaPlayer>();
        for (var user : players) {
            briskulaPlayers.add(new BriskulaPlayerEntity(user.getUsername(), user));
        }
        super(lobbyId, name, owner, players, GameTypeDTO.Briskula, new BriskulaGame(briskulaConfig.getGameConfig(), briskulaPlayers), briskulaConfig);
        persistedGameConfig = briskulaConfig.getGameConfig();
        getGame().start();
        trumpCardSuit = getGame().getGameTrumpCard().getSuit();
        trumpCardValue = getGame().getGameTrumpCard().getValue();
        addTeamPlayersIfNeeded(briskulaConfig.getOrderedUsers());
    }

    public void markEnded() {
        if (endedAt == null) {
            endedAt = Instant.now();
        }
    }

    public BriskulaGameEntityDTO createGameDTO() {
        var playerCardsMap = new HashMap<GamePlayerDTO, Integer>();
        var playerPointsMap = new HashMap<GamePlayerDTO, Integer>();
        for (var p: getGame().getPlayers()) {
            var person = (BriskulaPlayerEntity)p;
            var dto = person.getGamePlayerDTO();
            playerCardsMap.put(dto, person.getHand().getCardsNum());
            playerPointsMap.put(dto, person.getPoints());
        }
        var playedCards = new ArrayList<GameCardDTO>();
        var playingField = getGame().getPlayingField();
        GamePlayerDTO currentPlayer = null;
        if (playingField != null) {
            for (var card: playingField.getPlayedCards()) {
                playedCards.add(GameCardDTO.createCardDTO(card));
            }
            var tempCurrentPlayer = (BriskulaPlayerEntity) playingField.getCurrentPlayer();
            if (tempCurrentPlayer != null) {
                currentPlayer = tempCurrentPlayer.getGamePlayerDTO();
            }
        }
        var playersOrder = new ArrayList<GamePlayerDTO>();
        for (var p: getGame().getPlayers())
            playersOrder.add(((BriskulaPlayerEntity)p).getGamePlayerDTO());

        return new BriskulaGameEntityDTO(
                getId(), getLobbyId(), getName(), playersOrder, playerCardsMap,
                playedCards, getGame().getDeck().getSize(), playerPointsMap, currentPlayer, getTurnEndTime(),
                getTurnDurationSeconds(), GameCardDTO.createCardDTO(getGame().getGameTrumpCard()),
                getGameConfig().toDto());
    }

    public boolean playCard(UserEntity user, AbstractCard<?, ?, ? extends AbstractCard<?, ?, ?>> genericCard) {
        var card = new BriskulaCard(((ItalianCard<?>) genericCard).getSuit(), ((ItalianCard<?>) genericCard).getValue());
        var playerEntity = (BriskulaPlayerEntity) getGame().getPlayingField().getCurrentPlayer();
        if (user.equals(playerEntity.getUser())) {
            var playingField = getOrCreatePlayingField();
            getGame().getPlayingField().play(card, playerEntity);
            playingField.addPlayedCard(playerEntity.getUser(), card);
            if (playingField.isComplete(persistedGameConfig)) {
                playingField.markComplete(trumpCardSuit);
            }
            setTurnNumber(getTurnNumber() + 1);
            return true;
        }
        return false;
    }

    public BriskulaPlayerEntity getCurrentPlayer() {
        var pf = getGame().getPlayingField();
        if (pf != null) {
            var currPlayer = pf.getCurrentPlayer();
            if (currPlayer != null)
                return (BriskulaPlayerEntity) currPlayer;
        }
        return null;
    }

    private BriskulaPlayingFieldEntity getOrCreatePlayingField() {
        if (!briskulaPlayingFields.isEmpty()) {
            var lastPlayingField = briskulaPlayingFields.getLast();
            if (!lastPlayingField.isComplete(persistedGameConfig)) {
                return lastPlayingField;
            }
        }

        var playingField = new BriskulaPlayingFieldEntity(this, briskulaPlayingFields.size());
        playingField.savePlayerHands(getGame().getPlayers());
        briskulaPlayingFields.add(playingField);
        return playingField;
    }

    private void addTeamPlayersIfNeeded(List<UserEntity> players) {
        if (!persistedGameConfig.areTeamsEnabled()) {
            return;
        }

        teamPlayers.addAll(players);
    }
}
