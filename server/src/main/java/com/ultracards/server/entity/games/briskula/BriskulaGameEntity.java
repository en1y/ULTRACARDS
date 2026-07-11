package com.ultracards.server.entity.games.briskula;

import com.ultracards.cards.ItalianCard;
import com.ultracards.games.briskula.*;
import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.gateway.dto.games.games.GameCardDTO;
import com.ultracards.gateway.dto.games.games.briskula.BriskulaGameEntityDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.GameEntity;
import com.ultracards.server.entity.lobby.BriskulaLobbyGameConfig;
import com.ultracards.templates.cards.AbstractCard;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

@Getter
public class BriskulaGameEntity extends GameEntity<BriskulaGame, BriskulaLobbyGameConfig> {
    private final List<UserEntity> teamPlayers = new ArrayList<>();
    private BriskulaGameConfig persistedGameConfig;

    @Getter @Setter
    private boolean haveTeammateCardBeenDisplayed = false;


    public BriskulaGameEntity(UUID lobbyId, String name, UserEntity owner, BriskulaLobbyGameConfig briskulaConfig, List<UserEntity> players) {
        var briskulaPlayers = new ArrayList<BriskulaPlayer>();
        for (var user : players) {
            briskulaPlayers.add(new BriskulaPlayerEntity(user.getUsername(), user));
        }
        super(lobbyId, name, owner, players, GameTypeDTO.Briskula, new BriskulaGame(briskulaConfig.getGameConfig(), briskulaPlayers), briskulaConfig);
        persistedGameConfig = briskulaConfig.getGameConfig();
        addTeamPlayersIfNeeded(briskulaConfig.getOrderedUsers());
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
            getGame().getPlayingField().play(card, playerEntity);
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


    private void addTeamPlayersIfNeeded(List<UserEntity> players) {
        if (!persistedGameConfig.areTeamsEnabled()) {
            return;
        }

        teamPlayers.addAll(players);
    }

}
