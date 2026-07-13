package com.ultracards.recorder;

import com.ultracards.templates.cards.AbstractCard;
import com.ultracards.templates.game.interfaces.*;
import com.ultracards.templates.game.model.AbstractPlayer;

import java.util.*;
import java.util.function.Function;

public class GameRecorder implements GameRecordingHook {
    private final RecordedGame recordedGame;
    private final GameRecordAttributes attributes;
    private final Function<AbstractPlayer<?, ?, ?, ?, ?>, RecordedPlayer> playerMapper;
    private CurrentRound currentRound;

    public GameRecorder(RecordedGame game, GameRecordAttributes attributes, Function<AbstractPlayer<?, ?, ?, ?, ?>, RecordedPlayer> playerMapper) {
        this.recordedGame = game;
        this.attributes = attributes == null ? GameRecordAttributes.NONE : attributes;
        this.playerMapper = playerMapper;
    }

    public void attach(GameInterface<?, ?, ?, ?, ?, ?, ?> game) {
        game.setGameRecordingHook(this);
    }

    public RecordedGame recording() {
        return recordedGame;
    }

    @Override
    public void gameStarted(GameInterface<?, ?, ?, ?, ?, ?, ?> game) {
        recordedGame.started(players(game.getPlayers()), attributes.gameAttributes(game));
        currentRound = null;
    }

    @Override
    public void roundStarted(PlayingFieldInterface<?, ?, ?, ?, ?, ?> field) {
        currentRound = new CurrentRound(recordedGame.rounds().size(), hands(field.getPlayers()));
    }

    @Override
    public void cardPlayed(PlayingFieldInterface<?, ?, ?, ?, ?, ?> field, AbstractPlayer<?, ?, ?, ?, ?> player, AbstractCard<?, ?, ?> card) {
        if (currentRound == null) roundStarted(field);
        currentRound.plays.add(new RecordedPlay(currentRound.plays.size(), player(player), card(card)));
    }

    @Override
    public void roundEnded(PlayingFieldInterface<?, ?, ?, ?, ?, ?> field, AbstractPlayer<?, ?, ?, ?, ?> winner) {
        if (currentRound == null) roundStarted(field);
        recordedGame.addRound(new RecordedRound(currentRound.order, currentRound.hands, currentRound.plays, player(winner), attributes.roundAttributes(field, winner)));
        currentRound = null;
    }

    @Override
    public void gameEnded(GameInterface<?, ?, ?, ?, ?, ?, ?> game, List<? extends AbstractPlayer<?, ?, ?, ?, ?>> winners) {
        recordedGame.ended(attributes.gameAttributes(game));
    }

    private List<RecordedPlayer> players(List<? extends AbstractPlayer<?, ?, ?, ?, ?>> source) {
        var r = new ArrayList<RecordedPlayer>();
        for (var p : source) r.add(player(p));
        return r;
    }

    private List<RecordedPlayerHand> hands(List<? extends AbstractPlayer<?, ?, ?, ?, ?>> source) {
        var r = new ArrayList<RecordedPlayerHand>();
        for (var p : source) {
            var cards = new ArrayList<RecordedCard>();
            for (var c : p.getHand().getCards()) cards.add(card(c));
            r.add(new RecordedPlayerHand(player(p), cards));
        }
        return r;
    }

    private RecordedPlayer player(AbstractPlayer<?, ?, ?, ?, ?> player) {
        return playerMapper.apply(player);
    }

    private RecordedCard card(AbstractCard<?, ?, ?> card) {
        return new RecordedCard(card.getSuit().toString(), card.getValue().toString(), card.getValue().getNumber());
    }

    private static class CurrentRound {
        private final int order;
        private final List<RecordedPlayerHand> hands;
        private final List<RecordedPlay> plays = new ArrayList<>();

        private CurrentRound(int order, List<RecordedPlayerHand> hands) {
            this.order = order;
            this.hands = hands;
        }
    }
}
