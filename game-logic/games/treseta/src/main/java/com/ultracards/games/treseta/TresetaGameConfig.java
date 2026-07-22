package com.ultracards.games.treseta;

public enum TresetaGameConfig {
    TWO_PLAYERS(2, 10, false, 40 / 2, false), // show cards, 10 cards per person
    THREE_PLAYERS(3, 13, false, 40 / 3, false),
    FOUR_PLAYERS_WITH_TEAMS(4, 10, true, 40 / 4, false),
    FOUR_PLAYERS_NO_TEAMS(4, 10, false, 40 / 4, false),
    TWO_PLAYERS_WITH_DECLARATIONS(2, 10, false, 40 / 2, true),
    THREE_PLAYERS_WITH_DECLARATIONS(3, 13, false, 40 / 3, true),
    FOUR_PLAYERS_WITH_TEAMS_WITH_DECLARATIONS(4, 10, true, 40 / 4, true),
    FOUR_PLAYERS_NO_TEAMS_WITH_DECLARATIONS(4, 10, false, 40 / 4, true);

    private final int numberOfPlayers;
    private final boolean teamsEnabled;
    private final int cardsInHandNum;
    private final int roundsNum;
    private final boolean declarationsEnabled;

    TresetaGameConfig(int numberOfPlayers, int cardsInHandNum, boolean teamsEnabled, int roundsNum,
                      boolean declarationsEnabled) {
        this.numberOfPlayers = numberOfPlayers;
        this.cardsInHandNum = cardsInHandNum;
        this.teamsEnabled = teamsEnabled;
        this.roundsNum = roundsNum;
        this.declarationsEnabled = declarationsEnabled;
    }

    public boolean areDeclarationsEnabled() {
        return declarationsEnabled;
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
