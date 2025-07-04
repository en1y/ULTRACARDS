# `cards-template` module

## Overview

- This module contains templates for cards used in the game.
- The classes/interfaces in this module define the structure and behavior of the cards. And those classes/interfaces are:
  - `CardTypeValueInterface` - An interface that defines the type of a card.
    - Has a method `getName` and should be implemented by an enum.
  - `CardValueInterface` - An interface that defines the value of a card.
    - Has a method `getName` and should be implemented by an enum.
  - `CardFactoryInterface` - An interface that defines the factory for creating cards.
    - Has a method `createAllCards` that returns a list of all possible cards.