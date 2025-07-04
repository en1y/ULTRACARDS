# `italian-cards` module

## Overview
- It contains the main functionality for italian card management.
- It is a child module of the `cards` module.
- All the children modules are based on the `cards-template` module which is imported via maven as a dependency.
- It shall contain the following classes:
    - `ItalianCard`: Represents a single italian card.
    - `ItalianCardType`: Represents the type of italian card (e.g., "Coppe", "Denari", "Spade", "Bastoni").
    - `ItalianCardValue`: Represents the value of an italian card (e.g., "Ace", "Two", "Three", "Four", "Five", "Six", "Seven", "Fante", "Cavallo", "Re").
    - `ItalianCardFactory`: A factory class to create italian cards.