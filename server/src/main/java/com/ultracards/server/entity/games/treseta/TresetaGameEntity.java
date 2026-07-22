package com.ultracards.server.entity.games.treseta;

import com.ultracards.cards.ItalianCard;
import com.ultracards.games.treseta.TresetaCard;
import com.ultracards.games.treseta.TresetaDeclaration;
import com.ultracards.games.treseta.TresetaGame;
import com.ultracards.games.treseta.TresetaGameConfig;
import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.gateway.dto.games.games.GameCardDTO;
import com.ultracards.gateway.dto.games.games.treseta.TresetaDeclarationDTO;
import com.ultracards.gateway.dto.games.games.treseta.TresetaGameEntityDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.GameEntity;
import com.ultracards.server.entity.lobby.TresetaLobbyGameConfig;
import com.ultracards.templates.cards.AbstractCard;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Getter
public class TresetaGameEntity extends GameEntity<TresetaGame, TresetaLobbyGameConfig> {
    private final List<UserEntity> teamPlayers = new ArrayList<>();
    private final TresetaGameConfig persistedGameConfig;

    public TresetaGameEntity(UUID lobbyId, String name, UserEntity owner, TresetaLobbyGameConfig config,
                             List<UserEntity> players) {
        super(lobbyId, name, owner, players, GameTypeDTO.Treseta, new TresetaGame(toPlayers(players), config.getGameConfig()), config);
        persistedGameConfig = config.getGameConfig();
        if (persistedGameConfig.areTeamsEnabled()) teamPlayers.addAll(config.getOrderedUsers());
    }

    private static ArrayList<com.ultracards.games.treseta.TresetaPlayer> toPlayers(List<UserEntity> users) {
        var players = new ArrayList<com.ultracards.games.treseta.TresetaPlayer>();
        for (var user : users) players.add(new TresetaPlayerEntity(user.getUsername(), user));
        return players;
    }

    public TresetaGameEntityDTO createGameDTO() {
        var cards = new HashMap<GamePlayerDTO, Integer>();
        var points = new HashMap<GamePlayerDTO, Integer>();
        var order = new ArrayList<GamePlayerDTO>();
        var declarations = new ArrayList<TresetaDeclarationDTO>();
        var canDeclareUserIds = new ArrayList<Long>();
        for (var raw : getGame().getPlayers()) {
            var player = (TresetaPlayerEntity) raw;
            var dto = player.getGamePlayerDTO();
            order.add(dto);
            cards.put(dto, player.getHand().getCardsNum());
            points.put(dto, player.getPoints());
            if (persistedGameConfig.areDeclarationsEnabled() && player == getCurrentPlayer() && player.canDeclare())
                canDeclareUserIds.add(player.getUser().getId());
            for (var declaration : player.getDeclarations()) {
                var suits = new ArrayList<String>();
                for (var suit : declaration.suits()) suits.add(suit.name());
                declarations.add(new TresetaDeclarationDTO(dto, declaration.type().name(), suits,
                        declaration.getPoints()));
            }
        }
        var played = new ArrayList<GameCardDTO>();
        GamePlayerDTO current = null;
        var field = getGame().getPlayingField();
        if (field != null) {
            for (var card : field.getPlayedCards()) played.add(GameCardDTO.createCardDTO(card));
            if (field.getCurrentPlayer() != null)
                current = ((TresetaPlayerEntity) field.getCurrentPlayer()).getGamePlayerDTO();
        }
        return new TresetaGameEntityDTO(getId(), getLobbyId(), getName(), order, cards, played,
                getGame().getDeck().getSize(), points, current, getTurnEndTime(), getTurnDurationSeconds(),
                getGameConfig().toDto(), declarations, canDeclareUserIds);
    }

    public void declare(UserEntity user, List<TresetaCard> cards) {
        for (var raw : getGame().getPlayers()) {
            var player = (TresetaPlayerEntity) raw;
            if (player.getUser().equals(user)) {
                getGame().declare(player, TresetaDeclaration.fromCards(cards));
                return;
            }
        }
        throw new IllegalArgumentException("Player is not part of this game.");
    }

    public boolean playCard(UserEntity user, AbstractCard<?, ?, ? extends AbstractCard<?, ?, ?>> genericCard) {
        if (!(genericCard instanceof ItalianCard<?> italianCard)) return false;
        var player = (TresetaPlayerEntity) getGame().getPlayingField().getCurrentPlayer();
        if (!user.equals(player.getUser())) return false;
        getGame().getPlayingField().play(new TresetaCard(italianCard.getSuit(), italianCard.getValue()), player);
        setTurnNumber(getTurnNumber() + 1);
        return true;
    }

    public TresetaPlayerEntity getCurrentPlayer() {
        var field = getGame().getPlayingField();
        return field == null || field.getCurrentPlayer() == null ? null : (TresetaPlayerEntity) field.getCurrentPlayer();
    }
}
