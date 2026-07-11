package com.ultracards.games.treseta;

public enum TresetaGameConfig {
    TWO_PLAYERS(2, 10, false, 40 / 2), // show cards, 10 cards per person
    THREE_PLAYERS(3, 13, false, 40 / 3),
    FOUR_PLAYERS_WITH_TEAMS(4, 10, true, 40 / 4),
    FOUR_PLAYERS_NO_TEAMS(4, 10, false, 40 / 4);

    private final int numberOfPlayers;
    private final boolean teamsEnabled;
    private final int cardsInHandNum;
    private final int roundsNum;

    TresetaGameConfig(int numberOfPlayers, int cardsInHandNum, boolean teamsEnabled, int roundsNum) {
        this.numberOfPlayers = numberOfPlayers;
        this.cardsInHandNum = cardsInHandNum;
        this.teamsEnabled = teamsEnabled;
        this.roundsNum = roundsNum;
    }

    public int getNumberOfPlayers() {
        return numberOfPlayers;
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
