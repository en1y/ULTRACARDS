# `poker-cards` module

## Overview
- It contains the main functionality for poker card management.
- It is a child module of the `cards` module.
- All the children modules are based on the `cards-template` module which is imported via maven as a dependency.
- It shall contain the following classes:
  - `PokerCard`: Represents a single poker card.
  - `PokerCardType`: Represents the type of poker card (e.g., hearts, diamonds, clubs, spades).
  - `PokerCardValue`: Represents the value of a poker card (e.g., 2, 3, 4, ..., 10, J, Q, K, A).
  - `PokerCardFactory`: A factory class to create poker cards.