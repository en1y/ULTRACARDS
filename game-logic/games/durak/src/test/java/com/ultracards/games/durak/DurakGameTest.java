package com.ultracards.games.durak;

import com.ultracards.cards.PokerCardSuit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.ultracards.games.durak.DurakTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class DurakGameTest {

    private static DurakGameConfig twoPlayers(boolean passing) {
        return new DurakGameConfig(2, 24, false, DurakThrowInPolicy.NEIGHBORS_ONLY, passing);
    }

    /** Trump SPADES, P0 holds the only trump so it opens. */
    private static DurakGame twoPlayerGame(boolean passing) {
        return game(twoPlayers(passing), List.of(
                "S9",
                "S10 H9 H10 H11 H12 H13",
                "H14 D9 D10 D11 D12 D13"));
    }

    /* ******************** dealing and trump ******************** */

    @Test
    void everyPlayerGetsSixCardsAndTheIndicatorSitsAtTheBottom() {
        var game = twoPlayerGame(false);
        assertEquals(6, player(game, 0).handSize());
        assertEquals(6, player(game, 1).handSize());
        assertEquals(PokerCardSuit.SPADES, game.getTrumpSuit());
        assertEquals("S9", game.getTrumpIndicator().code());
        assertEquals(12, game.getCardsLeftInDeck());
    }

    @Test
    void theLowestTrumpOpensTheFirstBout() {
        var game = twoPlayerGame(false);
        assertEquals(player(game, 0), game.getPlayingField().getLeadAttacker());
        assertEquals(player(game, 1), game.getPlayingField().getDefender());
        assertEquals(DurakPhase.WAITING_FOR_ATTACK, game.getPlayingField().getPhase());
    }

    @Test
    void seatZeroOpensWhenNobodyHoldsATrump() {
        var game = game(twoPlayers(false), List.of(
                "S9",
                "H9 H10 H11 H12 H13 H14",
                "D9 D10 D11 D12 D13 D14"));
        assertEquals(player(game, 0), game.getPlayingField().getLeadAttacker());
    }

    @Test
    void aFullDealLeavesTheIndicatorInSomebodysHand() {
        var config = new DurakGameConfig(4, 24, false, DurakThrowInPolicy.NEIGHBORS_ONLY, false);
        var game = new DurakGame(List.of(
                new DurakPlayer("P0", 0), new DurakPlayer("P1", 1),
                new DurakPlayer("P2", 2), new DurakPlayer("P3", 3)),
                config, new DurakCardFactory().createPack(config));
        game.start();
        assertEquals(0, game.getCardsLeftInDeck());
        var indicator = game.getTrumpIndicator();
        assertTrue(game.getPlayers().stream().anyMatch(p -> p.getHand().getCards().contains(indicator)));
    }

    /* ******************** defense ******************** */

    @Test
    void beatingRulesFollowSuitTrumpAndRank() {
        var game = twoPlayerGame(false); // trump SPADES
        assertTrue(game.canBeat(card("H9"), card("H10")));
        assertFalse(game.canBeat(card("H10"), card("H9")));
        assertFalse(game.canBeat(card("H10"), card("H10")));
        assertTrue(game.canBeat(card("H14"), card("S9")));
        assertFalse(game.canBeat(card("S9"), card("H14")));
        assertTrue(game.canBeat(card("S9"), card("S10")));
        assertFalse(game.canBeat(card("S10"), card("S9")));
        assertFalse(game.canBeat(card("H9"), card("D10")));
    }

    @Test
    void aCoveredSlotCannotBeDefendedTwiceAndUnknownSlotsAreRejected() {
        var game = twoPlayerGame(false);
        act(game, DurakAction.attack(card("H13")));
        var slot = slotOf(game, "H13");
        act(game, DurakAction.defend(card("H14"), slot));

        var p1 = player(game, 1);
        assertThrows(DurakRuleException.class, () -> game.apply(p1, DurakAction.defend(card("D13"), slot)));
        assertThrows(DurakRuleException.class, () -> game.apply(p1, DurakAction.defend(card("D13"), 99)));
    }

    @Test
    void aCardThatCannotBeatIsRejectedWithoutLeavingTheHand() {
        var game = twoPlayerGame(false);
        act(game, DurakAction.attack(card("H13")));
        var slot = slotOf(game, "H13");
        var p1 = player(game, 1);
        var error = assertThrows(DurakRuleException.class,
                () -> game.apply(p1, DurakAction.defend(card("D13"), slot)));
        assertEquals(DurakErrorCode.DURAK_CARD_CANNOT_BEAT, error.getCode());
        assertEquals(6, p1.handSize());
    }

    @Test
    void aCardOutsideTheHandIsRejected() {
        var game = twoPlayerGame(false);
        var p0 = player(game, 0);
        var error = assertThrows(DurakRuleException.class, () -> game.apply(p0, DurakAction.attack(card("C9"))));
        assertEquals(DurakErrorCode.DURAK_CARD_NOT_IN_HAND, error.getCode());
    }

    @Test
    void onlyTheActionPlayerMayAct() {
        var game = twoPlayerGame(false);
        var p1 = player(game, 1);
        var error = assertThrows(DurakRuleException.class, () -> game.apply(p1, DurakAction.attack(card("D9"))));
        assertEquals(DurakErrorCode.DURAK_NOT_ACTION_PLAYER, error.getCode());
    }

    /* ******************** bout resolution ******************** */

    @Test
    void aSuccessfulDefenceDiscardsTheTableAndHandsTheLeadToTheDefender() {
        var game = twoPlayerGame(false);
        act(game, DurakAction.attack(card("H13")));
        act(game, DurakAction.defend(card("H14"), slotOf(game, "H13")));
        assertEquals(DurakPhase.WAITING_FOR_THROW_IN, game.getPlayingField().getPhase());
        assertEquals(player(game, 0), game.getPlayingField().getActionPlayer());

        var result = act(game, DurakAction.done());
        assertEquals(DurakBoutOutcome.DEFENDED, result.resolvedBout());
        assertEquals(List.of(card("H13"), card("H14")), game.getDiscardPile());
        assertEquals(player(game, 1), game.getPlayingField().getLeadAttacker());
        assertEquals(player(game, 0), game.getPlayingField().getDefender());
        assertEquals(6, player(game, 0).handSize());
        assertEquals(6, player(game, 1).handSize());
        assertEquals(10, game.getCardsLeftInDeck());
    }

    @Test
    void takingMovesTheWholeTableToTheDefenderAndSkipsThemAsAttacker() {
        var game = twoPlayerGame(false);
        act(game, DurakAction.attack(card("H13")));
        var result = act(game, DurakAction.take());
        assertNull(result.resolvedBout());
        assertEquals(DurakPhase.THROW_AFTER_TAKE, game.getPlayingField().getPhase());

        result = act(game, DurakAction.done()); // P0 has nothing of rank 13 to add
        assertEquals(DurakBoutOutcome.TAKEN, result.resolvedBout());
        assertTrue(game.getDiscardPile().isEmpty());
        assertEquals(7, player(game, 1).handSize());
        assertTrue(player(game, 1).getHand().getCards().contains(card("H13")));
        assertEquals(player(game, 0), game.getPlayingField().getLeadAttacker());
    }

    @Test
    void takingIsRejectedWhenEveryAttackIsAlreadyCovered() {
        var game = twoPlayerGame(false);
        act(game, DurakAction.attack(card("H13")));
        act(game, DurakAction.defend(card("H14"), slotOf(game, "H13")));
        var p0 = player(game, 0);
        assertThrows(DurakRuleException.class, () -> game.apply(p0, DurakAction.take()));
    }

    @Test
    void defenceAndPassAreRejectedAfterTaking() {
        var game = twoPlayerGame(true);
        act(game, DurakAction.attack(card("H13")));
        var slot = slotOf(game, "H13");
        act(game, DurakAction.take());
        var p1 = player(game, 1);
        assertThrows(DurakRuleException.class, () -> game.apply(p1, DurakAction.defend(card("H14"), slot)));
        assertThrows(DurakRuleException.class, () -> game.apply(p1, DurakAction.pass(card("D13"))));
    }

    /* ******************** throw-ins ******************** */

    private static DurakGame threePlayerGame(DurakThrowInPolicy policy, boolean passing) {
        return game(new DurakGameConfig(3, 36, false, policy, passing), List.of(
                "S6",
                "S7 H6 H7 H8 H9 H10",
                "D6 D7 D8 D9 D10 H11",
                "C6 C7 C8 C9 C10 C11"));
    }

    @Test
    void throwInRejectsRanksMissingFromTheTable() {
        var game = threePlayerGame(DurakThrowInPolicy.EVERYONE, false);
        act(game, DurakAction.attack(card("H8")));
        act(game, DurakAction.take());
        // P2 is the first eligible thrower under EVERYONE (ring starts at the lead attacker P0,
        // and P0 already played its only rank-8 card).
        var thrower = game.getPlayingField().getActionPlayer();
        var error = assertThrows(DurakRuleException.class,
                () -> game.apply(thrower, DurakAction.throwIn(card("C11"))));
        assertEquals(DurakErrorCode.DURAK_THROW_RANK_NOT_ON_TABLE, error.getCode());
    }

    @Test
    void everyonePolicyMakesAllNonDefendersEligible() {
        var game = threePlayerGame(DurakThrowInPolicy.EVERYONE, false);
        var field = game.getPlayingField();
        assertEquals(List.of(player(game, 0), player(game, 2)), field.getEligibleThrowers());
    }

    @Test
    void neighbourPolicyInATwoPlayerGameListsTheOpponentOnce() {
        var game = twoPlayerGame(false);
        assertEquals(List.of(player(game, 0)), game.getPlayingField().getEligibleThrowers());
    }

    @Test
    void neighbourPolicyInAThreePlayerGameListsBothOpponents() {
        var game = threePlayerGame(DurakThrowInPolicy.NEIGHBORS_ONLY, false);
        assertEquals(2, game.getPlayingField().getEligibleThrowers().size());
        assertFalse(game.getPlayingField().getEligibleThrowers().contains(player(game, 1)));
    }

    @Test
    void aThrowInDuringDefenceReturnsControlToTheDefender() {
        var game = threePlayerGame(DurakThrowInPolicy.EVERYONE, false);
        act(game, DurakAction.attack(card("H8")));           // P0 -> P1
        act(game, DurakAction.defend(card("H11"), slotOf(game, "H8")));
        assertEquals(DurakPhase.WAITING_FOR_THROW_IN, game.getPlayingField().getPhase());

        // P0 is asked first and can add its other rank-8 card? It has none, so it passes.
        act(game, DurakAction.done());
        var thrower = game.getPlayingField().getActionPlayer();
        assertEquals(player(game, 2), thrower);
        game.apply(thrower, DurakAction.throwIn(card("C8")));
        assertEquals(DurakPhase.WAITING_FOR_DEFENSE, game.getPlayingField().getPhase());
        assertEquals(player(game, 1), game.getPlayingField().getActionPlayer());
        assertEquals(2, game.getPlayingField().getAttackSlots().size());
    }

    @Test
    void throwInRotationContinuesAfterTheLastThrower() {
        var game = game(new DurakGameConfig(
                3, 36, false, DurakThrowInPolicy.EVERYONE, false), List.of(
                "S6",
                "S7 H6 H7 H8 D8 H10",
                "D6 D7 D9 D10 C9 H11",
                "C6 C7 C8 C10 C11 H9"));

        act(game, DurakAction.attack(card("H8")));
        act(game, DurakAction.defend(card("H11"), slotOf(game, "H8")));
        act(game, DurakAction.throwIn(card("D8")));
        act(game, DurakAction.defend(card("D9"), slotOf(game, "D8")));

        assertEquals(player(game, 2), game.getPlayingField().getActionPlayer());
    }

    @Test
    void onlyDefendMayCarryATargetSlot() {
        for (var type : DurakActionType.values()) {
            if (type == DurakActionType.DEFEND) continue;
            var actionCard = type.requiresCard() ? card("H8") : null;
            var error = assertThrows(DurakRuleException.class,
                    () -> new DurakAction(type, actionCard, 0));
            assertEquals(DurakErrorCode.DURAK_INVALID_DEFENSE_TARGET, error.getCode());
        }
    }

    @Test
    void theAttackLimitIsTheDefendersHandSizeCappedAtSix() {
        var game = twoPlayerGame(false);
        assertEquals(6, game.getPlayingField().getMaxAttackCards());
    }

    /* ******************** passing ******************** */

    @Test
    void passingMovesTheDefenceToTheNextPlayer() {
        var game = threePlayerGame(DurakThrowInPolicy.EVERYONE, true);
        act(game, DurakAction.attack(card("H6")));
        assertEquals(player(game, 1), game.getPlayingField().getActionPlayer());

        act(game, DurakAction.pass(card("D6")));
        var field = game.getPlayingField();
        assertEquals(player(game, 2), field.getDefender());
        assertEquals(player(game, 2), field.getActionPlayer());
        assertEquals(DurakPhase.WAITING_FOR_DEFENSE, field.getPhase());
        assertEquals(2, field.getAttackSlots().size());
        assertEquals(List.of(player(game, 1)), field.getPassChain());
        assertFalse(field.getEligibleThrowers().contains(player(game, 2)));
    }

    @Test
    void passingIsRejectedWhenDisabled() {
        var game = threePlayerGame(DurakThrowInPolicy.EVERYONE, false);
        act(game, DurakAction.attack(card("H6")));
        var p1 = player(game, 1);
        var error = assertThrows(DurakRuleException.class, () -> game.apply(p1, DurakAction.pass(card("D6"))));
        assertEquals(DurakErrorCode.DURAK_PASS_DISABLED, error.getCode());
    }

    @Test
    void passingRequiresAMatchingUncoveredRank() {
        var game = threePlayerGame(DurakThrowInPolicy.EVERYONE, true);
        act(game, DurakAction.attack(card("H6")));
        var p1 = player(game, 1);
        var error = assertThrows(DurakRuleException.class, () -> game.apply(p1, DurakAction.pass(card("D9"))));
        assertEquals(DurakErrorCode.DURAK_PASS_RANK_MISMATCH, error.getCode());
    }

    @Test
    void aPassChainCanCircleBackToTheOpeningAttacker() {
        var game = threePlayerGame(DurakThrowInPolicy.EVERYONE, true);
        act(game, DurakAction.attack(card("H6")));
        act(game, DurakAction.pass(card("D6")));
        act(game, DurakAction.pass(card("C6")));
        var field = game.getPlayingField();
        assertEquals(player(game, 0), field.getDefender());
        assertEquals(3, field.getAttackSlots().size());
        assertEquals(List.of(player(game, 1), player(game, 2)), field.getPassChain());
    }

    @Test
    void aPassIsRejectedWhenTheNextDefenderCannotHoldTheAttack() {
        // Two players, defender down to a single card: one extra attack card is already too many.
        var game = game(twoPlayers(true), List.of(
                "S9",
                "S10 H9 H10 H11 H12 H13",
                "H14 D9 D10 D11 D12 D13"));
        act(game, DurakAction.attack(card("H13")));
        act(game, DurakAction.take());
        act(game, DurakAction.done()); // P1 takes H13, P0 refills to 6
        // P0 leads again; P1 now holds 7 cards, so capacity is never the blocker here.
        assertEquals(player(game, 0), game.getPlayingField().getLeadAttacker());
    }

    /* ******************** jokers ******************** */

    private static DurakGame jokerGame() {
        return game(new DurakGameConfig(2, 54, true, DurakThrowInPolicy.EVERYONE, true), List.of(
                "S2",
                "JR H2 H3 H4 H5 H6",
                "JB D2 D3 D4 D5 D6"));
    }

    @Test
    void jokersBeatByColourOnly() {
        var game = jokerGame(); // trump SPADES
        assertTrue(game.canBeat(card("H14"), DurakCard.redJoker()));
        assertTrue(game.canBeat(card("D2"), DurakCard.redJoker()));
        assertTrue(game.canBeat(card("S14"), DurakCard.blackJoker()));   // matching-colour trump
        assertTrue(game.canBeat(card("C7"), DurakCard.blackJoker()));
        assertFalse(game.canBeat(card("H14"), DurakCard.blackJoker()));
        assertFalse(game.canBeat(card("C7"), DurakCard.redJoker()));
    }

    @Test
    void nothingBeatsAJoker() {
        var game = jokerGame();
        assertFalse(game.canBeat(DurakCard.redJoker(), card("S14")));
        assertFalse(game.canBeat(DurakCard.redJoker(), DurakCard.blackJoker()));
        assertFalse(game.canBeat(DurakCard.blackJoker(), DurakCard.redJoker()));
    }

    @Test
    void aJokerAttackCanOnlyBePassedOrTaken() {
        var game = jokerGame();
        act(game, DurakAction.attack(DurakCard.redJoker()));
        var p1 = player(game, 1);
        assertThrows(DurakRuleException.class,
                () -> game.apply(p1, DurakAction.defend(DurakCard.blackJoker(), slotOf(game, "JR"))));

        act(game, DurakAction.pass(DurakCard.blackJoker()));
        assertEquals(player(game, 0), game.getPlayingField().getDefender());
        assertEquals(2, game.getPlayingField().getAttackSlots().size());
    }

    @Test
    void jokerActionsAreRejectedWhenTheToggleIsOff() {
        var game = game(new DurakGameConfig(2, 54, false, DurakThrowInPolicy.EVERYONE, true), List.of(
                "S2",
                "S3 H2 H3 H4 H5 H6",
                "D2 D3 D4 D5 D6 D7"));
        var p0 = player(game, 0);
        var error = assertThrows(DurakRuleException.class,
                () -> game.apply(p0, DurakAction.attack(DurakCard.redJoker())));
        assertEquals(DurakErrorCode.DURAK_JOKER_DISABLED, error.getCode());
    }

    /* ******************** timeouts ******************** */

    @Test
    void timeoutActionsFollowThePhaseTable() {
        var game = twoPlayerGame(false);
        var opening = game.timeoutAction();
        assertEquals(DurakActionType.ATTACK, opening.type());
        assertEquals("H9", opening.card().code()); // lowest non-trump, not the S10 trump

        act(game, opening);
        assertEquals(DurakActionType.TAKE, game.timeoutAction().type());
        act(game, DurakAction.take());
        assertEquals(DurakActionType.DONE, game.timeoutAction().type());
    }
}
