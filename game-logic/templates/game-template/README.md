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