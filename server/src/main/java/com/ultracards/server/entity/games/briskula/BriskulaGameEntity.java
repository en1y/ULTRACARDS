package com.ultracards.server.entity.games.briskula;

import com.ultracards.cards.ItalianCard;
import com.ultracards.games.briskula.BriskulaCard;
import com.ultracards.games.briskula.BriskulaGame;
import com.ultracards.games.briskula.BriskulaGameConfig;
import com.ultracards.games.briskula.BriskulaPlayer;
import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.gateway.dto.games.games.GameCardDTO;
import com.ultracards.gateway.dto.games.games.briskula.BriskulaGameEntityDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.GameEntity;
import com.ultracards.templates.cards.AbstractCard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class BriskulaGameEntity extends GameEntity<BriskulaGame> {

    public BriskulaGameEntity(UUID lobbyId, String name, UserEntity owner,BriskulaGameConfig briskulaConfig, List<UserEntity> players) {
        var briskulaPlayers = new ArrayList<BriskulaPlayer>();
        for (var user : players) {
            briskulaPlayers.add(new BriskulaPlayerEntity(user.getUsername(), user));
        }
        super(lobbyId, name, owner, players, GameTypeDTO.Briskula, new BriskulaGame(briskulaConfig, briskulaPlayers));
        getGame().start();
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
            currentPlayer = ((BriskulaPlayerEntity) playingField.getCurrentPlayer()).getGamePlayerDTO();
        }
        return new BriskulaGameEntityDTO(
            getId(), getLobbyId(), getName(), playerCardsMap, playedCards, getGame().getDeck().getSize(), playerPointsMap, currentPlayer, GameCardDTO.createCardDTO(getGame().getGameTrumpCard()));
    }

    public boolean playCard(UserEntity user, AbstractCard<?, ?, ? extends AbstractCard<?, ?, ?>> genericCard) {
        var card = new BriskulaCard(((ItalianCard<?>) genericCard).getSuit(), ((ItalianCard<?>) genericCard).getValue());
        var playerEntity = (BriskulaPlayerEntity) getGame().getPlayingField().getCurrentPlayer();
        if (user.equals(playerEntity.getUser())) {
            getGame().getPlayingField().play(card, playerEntity);
            return true;
        }
        return false;
    }
}
