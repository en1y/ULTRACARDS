package com.ultracards.games.treseta;

public enum TresetaGameConfig {
    TWO_PLAYERS(10, false, 40/2), // show cards, 10 cards per person
    THREE_PLAYERS(13, false, 40/3),
    FOUR_PLAYERS_WITH_TEAMS(10, true,  40/4),
    FOUR_PLAYERS_NO_TEAMS(10, false, 40/4);

    private final boolean teamsEnabled;
    private final int cardsInHandNum;
    private final int roundsNum;

    TresetaGameConfig(int cardsInHandNum, boolean teamsEnabled, int roundsNum) {
        this.cardsInHandNum = cardsInHandNum;
        this.teamsEnabled = teamsEnabled;
        this.roundsNum = roundsNum;
    }

    public boolean areTeamsEnabled() {
        return teamsEnabled;
    }

    public int getCardsInHandNum() {
        return cardsInHandNum;
    }

    public int getRoundsNum() {
        return roundsNum;
    }
}
