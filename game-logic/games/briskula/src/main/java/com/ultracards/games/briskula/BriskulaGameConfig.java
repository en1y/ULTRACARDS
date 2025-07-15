package com.ultracards.games.briskula;

public enum BriskulaGameConfig {
    TWO_PLAYERS(2, 3, false),
    TWO_PLAYERS_FOUR_CARDS_IN_HAND_EACH(2, 4, false),
    THREE_PLAYERS(3, 3, false),
    FOUR_PLAYERS_NO_TEAMS(4, 3, false),
    FOUR_PLAYERS_WITH_TEAMS(4, 3, true),;

    private final int numberOfPlayers;
    private final int cardsInHandNum;
    private final boolean teamsEnabled;

    BriskulaGameConfig(int numberOfPlayers, int cardsInHandNum, boolean teamsEnabled) {
        this.numberOfPlayers = numberOfPlayers;
        this.cardsInHandNum = cardsInHandNum;
        this.teamsEnabled = teamsEnabled;
    }

    public int getNumberOfPlayers() {
        return numberOfPlayers;
    }

    public int getCardsInHandNum() {
        return cardsInHandNum;
    }

    public boolean areTeamsEnabled() {
        return teamsEnabled;
    }
}
