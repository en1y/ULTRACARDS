# `game-template` module

## Overview

- This module contains templates for the game logic.
- The classes/interfaces in this module define the structure and behavior of the game. And those classes/interfaces are:

## Interfaces
- `GameInterface` - An interface that defines the game logic.
  - Has methods for starting the game, playing a turn, and checking the game state.
  - Has a `*start` and `*end` methods that are implemented for the customizability and may be overridden by the child classes.
  - You can look at the `GameInterface` to get a deeper understanding of the game logic.
- `PlayerInterface`
  - Defines methods usually needed for a player in a game.
- `HandInterface`
  - Defines methods usually needed for a hand in a game.
- `DeckInterface`
  - Defines methods usually needed for a deck in a game.
- `PlayingFieldInterface`
  - Defines methods usually needed for a playing field in a game.

## Abstract Classes
- `AbstractGame` 
  - An abstract class that implements the `GameInterface`.
  - Provides default implementations for some methods and defines the structure of the game.
- `AbstractPlayer` 
  - An abstract class that implements the `PlayerInterface`.
  - Provides default implementations for some methods and defines the structure of a player.
- `AbstractDeck` 
  - An abstract class that implements the deck logic.
  - Provides default implementations for some methods and defines the structure of a deck.
- `AbstractHand` 
  - An abstract class that implements the hand logic.
  - Provides default implementations for some methods and defines the structure of a hand.
- `AbstractPlayingField`
  - An abstract class that implements the playing field logic.
  - Provides default implementations for some methods and defines the structure of a playing field.

## Exceptions

- `DeckException`
- `HandException`
- `PlayingFieldException`

---

# GameInterface Methods Workflow

## init(numberOfPlayers, cardsNum, cardsInHandNum)
- **Sets up game parameters and prepares for game start.**
- Calls:
  - `setNumberOfPlayers(numberOfPlayers)`
  - `setPlayers(new ArrayList<>(getNumberOfPlayers()))`
  - `setCardsNum(cardsNum)`
  - `setCardsInHandNum(cardsInHandNum)`
  - `setPlayingField(null)`
  - `preGameCreateCheck(numberOfPlayers, cardsNum)`

## start()
- **Begins the game and handles the main setup and loop.**
- Calls:
  - `setDeck(createDeck(getCardsNum()))`
  - `removeNotNeededCards(getDeck(), getCardsInHandNum())`
  - `createPlayers()`
    - Sets the list via `setPlayers(players)`
    - Throws if created player count doesn't match `getNumberOfPlayers()`
  - `createPlayersHands(getDeck(), getPlayers())`
  - Sets number of players: `setNumberOfPlayers(getPlayers().size())`
  - Main game loop:
    - While `isGameActive(getDeck(), getPlayers())` returns true:
      - Calls `roundCycle()`
  - After the loop:
    - `setPlayingField(null)`
    - `gameEnd()`

## restart()
- **Restarts the game.**
- Calls:
  - `start()`

---

### Key Internal Calls

#### roundCycle()
- Calls:
  - `setPlayingField(createPlayingField())`
  - `playTurn(getPlayingField(), getPlayers())`
  - `determineRoundWinner(getPlayingField())`
  - `postRoundWinnerDeterminedActions(roundWinner, getPlayingField())`
  - `drawCards(getPlayers(), getDeck())`
  - `roundEnd(getPlayingField(), roundWinner)`

#### gameEnd()
- Calls:
  - `determineGameWinners(getPlayers())`
  - `postGameWinnersDeterminedActions(winners)`

---

**Summary:**
- `init` sets game parameters.
- `start` creates the deck, players, hands, then repeatedly runs `roundCycle` until the game ends, then finalizes with `gameEnd`.
- `restart` simply calls `start()`.

