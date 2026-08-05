package com.ultracards.games.durak;

import com.ultracards.cards.PokerCardSuit;
import com.ultracards.cards.PokerCardValue;
import com.ultracards.templates.game.model.AbstractGame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Durak. Unlike the trick games in this repository a bout is not "one card per player and someone
 * wins", so this game owns an explicit bout state machine and never calls the generic
 * {@code roundCycle()}.
 *
 * <p>All rule validation lives here and in {@link DurakPlayingField}; the server only orchestrates
 * authentication, locking, timers, recording and publication.
 */
public class DurakGame extends AbstractGame
        <PokerCardSuit, PokerCardValue, DurakCard, DurakHand, DurakDeck, DurakPlayer, DurakPlayingField> {

    private final DurakGameConfig config;
    private final RandomGenerator random;
    private final List<DurakCard> fixedDeckOrder;

    private final List<DurakCard> discardPile = new ArrayList<>();
    private final List<DurakPlayer> activePlayers = new ArrayList<>();
    private final List<DurakPlayer> finishOrder = new ArrayList<>();

    private ResolvedBout lastResolvedBout;

    private PokerCardSuit trumpSuit;
    private DurakCard trumpIndicator;
    private boolean active;
    private boolean draw;
    private DurakPlayer loser;
    private int boutNumber;
    private DurakPlayer nextLeadAttacker;

    public DurakGame(List<DurakPlayer> players, DurakGameConfig config) {
        this(players, config, RandomGenerator.getDefault(), null);
    }

    public DurakGame(List<DurakPlayer> players, DurakGameConfig config, RandomGenerator random) {
        this(players, config, random, null);
    }

    /** Deterministic entry point: {@code deckOrder} is dealt front to back, last card is the trump indicator. */
    public DurakGame(List<DurakPlayer> players, DurakGameConfig config, List<DurakCard> deckOrder) {
        this(players, config, RandomGenerator.getDefault(), deckOrder);
    }

    private DurakGame(List<DurakPlayer> players, DurakGameConfig config,
                      RandomGenerator random, List<DurakCard> deckOrder) {
        super(List.copyOf(players), config.effectiveCardCount(), DurakGameConfig.CARDS_IN_HAND);
        this.config = config;
        this.random = Objects.requireNonNull(random, "random");
        this.fixedDeckOrder = deckOrder == null ? null : List.copyOf(deckOrder);
        if (players.size() != config.numberOfPlayers()) {
            throw new DurakRuleException(DurakErrorCode.DURAK_INVALID_PLAYER_COUNT,
                    "Expected %d players, got %d.", config.numberOfPlayers(), players.size());
        }
    }

    /* ******************** lifecycle ******************** */

    @Override
    public void start() {
        var deck = fixedDeckOrder == null
                ? DurakDeck.shuffled(config, random)
                : DurakDeck.ordered(config, fixedDeckOrder);
        setDeck(deck);
        trumpIndicator = deck.getCards().getLast();
        trumpSuit = trumpIndicator.getSuit();

        for (var player : getPlayers()) {
            player.setHand(deck.createEmptyHand());
        }
        var toDeal = config.numberOfPlayers() * DurakGameConfig.CARDS_IN_HAND;
        for (int i = 0; i < toDeal; i++) {
            getPlayers().get(i % config.numberOfPlayers()).getHand().addCard(deck.drawCard());
        }

        activePlayers.clear();
        activePlayers.addAll(getPlayers());
        active = true;
        getGameRecordingHook().gameStarted(this);

        nextLeadAttacker = chooseFirstAttacker();
        setPlayingField(createPlayingField());
        getGameRecordingHook().roundStarted(getPlayingField());
    }

    private DurakPlayer chooseFirstAttacker() {
        DurakPlayer best = null;
        int bestRank = Integer.MAX_VALUE;
        for (var player : getPlayers()) {
            for (var card : player.getHand().getCards()) {
                if (card.getSuit() == trumpSuit && card.rank() < bestRank) {
                    bestRank = card.rank();
                    best = player;
                }
            }
        }
        return best != null ? best : getPlayers().getFirst();
    }

    /* ******************** actions ******************** */

    /**
     * Validates and applies one action. Every rejection throws {@link DurakRuleException} without
     * mutating any state.
     */
    /** A snapshot of a bout at the moment it resolved, before any card moved off the table. */
    public record ResolvedBout(DurakPlayingField field, Map<DurakPlayer, Integer> handSizes,
                               int cardsLeftInDeck, List<DurakPlayer> finishOrder) {}

    public ResolvedBout getLastResolvedBout() {
        return lastResolvedBout;
    }

    public DurakActionResult apply(DurakPlayer actor, DurakAction action) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");
        if (!active) {
            throw new DurakRuleException(DurakErrorCode.DURAK_GAME_FINISHED, "The game is already finished.");
        }
        var field = getPlayingField();
        if (action.card() != null && action.card().isJoker() && !config.jokersEnabled()) {
            throw new DurakRuleException(DurakErrorCode.DURAK_JOKER_DISABLED,
                    "Jokers are disabled in this game.");
        }
        // Throwing a card in is not a turn: an attacker may toss a matching card onto an
        // open bout whenever they like, including while the defender is still thinking,
        // and may toss several. ATTACK and THROW_IN are the same move to a client.
        if (isThrow(action) && mayThrowInNow(actor, field)) {
            return applyThrowIn(actor, action, field);
        }
        // The throw window runs for everyone at once rather than seat by seat, so any
        // eligible attacker may declare themselves done whenever they like.
        if (action.type() == DurakActionType.DONE && isThrowWindow(field) && mayThrowInNow(actor, field)) {
            return applyDone(actor, field);
        }
        if (actor != field.getActionPlayer()) {
            throw new DurakRuleException(DurakErrorCode.DURAK_NOT_ACTION_PLAYER,
                    "It is not %s's turn to act.", actor.getName());
        }
        return switch (field.getPhase()) {
            case WAITING_FOR_ATTACK -> applyOpeningAttack(actor, action, field);
            case WAITING_FOR_DEFENSE -> applyDefensePhase(actor, action, field);
            case WAITING_FOR_THROW_IN, THROW_AFTER_TAKE -> applyThrowPhase(actor, action, field);
            case FINISHED -> throw new DurakRuleException(DurakErrorCode.DURAK_GAME_FINISHED,
                    "The game is already finished.");
        };
    }

    private DurakActionResult applyOpeningAttack(DurakPlayer actor, DurakAction action, DurakPlayingField field) {
        requirePhaseAllows(action.type() == DurakActionType.ATTACK, action, field);
        var card = requireInHand(actor, action.card());
        playToTable(actor, card, field, "ATTACK", null);
        field.setPhase(DurakPhase.WAITING_FOR_DEFENSE);
        field.setActionPlayer(field.getDefender());
        return new DurakActionResult(DurakActionType.ATTACK, null, false);
    }

    private DurakActionResult applyDefensePhase(DurakPlayer actor, DurakAction action, DurakPlayingField field) {
        return switch (action.type()) {
            case DEFEND -> applyDefend(actor, action, field);
            case PASS -> applyPass(actor, action, field);
            case TAKE -> applyTake(field);
            default -> throw invalidForPhase(action, field);
        };
    }

    private DurakActionResult applyDefend(DurakPlayer actor, DurakAction action, DurakPlayingField field) {
        var slot = field.slot(action.targetSlotId());
        if (slot == null || slot.covered()) {
            throw new DurakRuleException(DurakErrorCode.DURAK_INVALID_DEFENSE_TARGET,
                    "Attack slot %s is not open.", action.targetSlotId());
        }
        var card = requireInHand(actor, action.card());
        if (!canBeat(slot.attackCard(), card)) {
            throw new DurakRuleException(DurakErrorCode.DURAK_CARD_CANNOT_BEAT,
                    "%s cannot beat %s.", card, slot.attackCard());
        }
        actor.playCard(card);
        slot.cover(actor, card);
        field.addCard(card);
        getGameRecordingHook().cardPlayed(field, actor, card, "DEFEND", slot.attackPlayOrder());
        field.nextPlayOrder();

        if (field.allCovered()) {
            field.setPhase(DurakPhase.WAITING_FOR_THROW_IN);
            var outcome = continueThrowWindow(field);
            return new DurakActionResult(DurakActionType.DEFEND, outcome, !active);
        }
        field.setActionPlayer(field.getDefender());
        return new DurakActionResult(DurakActionType.DEFEND, null, false);
    }

    private DurakActionResult applyPass(DurakPlayer actor, DurakAction action, DurakPlayingField field) {
        if (!config.passingEnabled()) {
            throw new DurakRuleException(DurakErrorCode.DURAK_PASS_DISABLED, "Passing is disabled in this game.");
        }
        // Beating even one card commits you to defending the whole bout: the attack can
        // only be handed on while the table is still entirely uncovered.
        if (field.getAttackSlots().stream().anyMatch(DurakAttackSlot::covered)) {
            throw new DurakRuleException(DurakErrorCode.DURAK_PASS_ALREADY_DEFENDED,
                    "The attack cannot be passed on after a card has been beaten.");
        }
        var card = action.card();
        if (field.uncoveredSlots().stream().noneMatch(s -> s.attackCard().rank() == card.rank())) {
            throw new DurakRuleException(DurakErrorCode.DURAK_PASS_RANK_MISMATCH,
                    "%s does not match any uncovered attack card.", card);
        }
        var nextDefender = nextActiveAfter(actor);
        if (nextDefender == actor) {
            throw new DurakRuleException(DurakErrorCode.DURAK_NEXT_DEFENDER_CAPACITY,
                    "There is no other active player to pass to.");
        }
        var capacity = Math.min(DurakGameConfig.CARDS_IN_HAND, nextDefender.handSize());
        if (field.getAttackSlots().size() + 1 > capacity) {
            throw new DurakRuleException(DurakErrorCode.DURAK_NEXT_DEFENDER_CAPACITY,
                    "%s cannot defend %d attack cards.", nextDefender.getName(), field.getAttackSlots().size() + 1);
        }
        requireInHand(actor, card);

        playToTable(actor, card, field, "PASS", null);
        field.recordPass(actor);
        field.setDefender(nextDefender);
        field.setMaxAttackCards(Math.min(DurakGameConfig.CARDS_IN_HAND, nextDefender.handSize()));
        field.setEligibleThrowers(computeEligibleThrowers(field.getDefender(), actor));
        field.startThrowWindow();
        field.setActionPlayer(nextDefender);
        return new DurakActionResult(DurakActionType.PASS, null, false);
    }

    private DurakActionResult applyTake(DurakPlayingField field) {
        if (field.uncoveredSlots().isEmpty()) {
            throw new DurakRuleException(DurakErrorCode.DURAK_INVALID_ACTION_FOR_PHASE,
                    "There is nothing to take: every attack is covered.");
        }
        field.declareTake();
        field.setPhase(DurakPhase.THROW_AFTER_TAKE);
        field.startThrowWindow();
        var outcome = continueThrowWindow(field);
        return new DurakActionResult(DurakActionType.TAKE, outcome, !active);
    }

    private static boolean isThrow(DurakAction action) {
        return action.type() == DurakActionType.THROW_IN || action.type() == DurakActionType.ATTACK;
    }

    /** True when {@code actor} may add a card to the open bout right now, turn or not. */
    public boolean mayThrowInNow(DurakPlayer actor, DurakPlayingField field) {
        return active
                && field != null
                && field.getPhase() != DurakPhase.WAITING_FOR_ATTACK
                && field.getPhase() != DurakPhase.FINISHED
                && actor != field.getDefender()
                && field.getEligibleThrowers().contains(actor)
                && !field.getDoneThrowers().contains(actor);
    }

    public boolean mayThrowInNow(DurakPlayer actor) {
        return mayThrowInNow(actor, getPlayingField());
    }

    private static boolean isThrowWindow(DurakPlayingField field) {
        return field.getPhase() == DurakPhase.WAITING_FOR_THROW_IN
                || field.getPhase() == DurakPhase.THROW_AFTER_TAKE;
    }

    private DurakActionResult applyDone(DurakPlayer actor, DurakPlayingField field) {
        field.markDone(actor);
        field.advanceThrowCursor();
        var outcome = continueThrowWindow(field);
        return new DurakActionResult(DurakActionType.DONE, outcome, !active);
    }

    /**
     * A turn timeout closes the whole throw window instead of stepping one seat on, so every
     * attacker is racing the same clock rather than being handed the clock in turn.
     */
    public DurakActionResult applyTimeout() {
        var field = getPlayingField();
        if (!active || field == null) {
            throw new DurakRuleException(DurakErrorCode.DURAK_GAME_FINISHED, "The game is already finished.");
        }
        if (!isThrowWindow(field)) {
            return apply(field.getActionPlayer(), timeoutAction());
        }
        for (var thrower : field.getEligibleThrowers()) field.markDone(thrower);
        var outcome = continueThrowWindow(field);
        return new DurakActionResult(DurakActionType.DONE, outcome, !active);
    }

    private DurakActionResult applyThrowPhase(DurakPlayer actor, DurakAction action, DurakPlayingField field) {
        return switch (action.type()) {
            case THROW_IN, ATTACK -> applyThrowIn(actor, action, field);
            case DONE -> applyDone(actor, field);
            default -> throw invalidForPhase(action, field);
        };
    }

    private DurakActionResult applyThrowIn(DurakPlayer actor, DurakAction action, DurakPlayingField field) {
        if (field.getAttackSlots().size() >= field.getMaxAttackCards()) {
            throw new DurakRuleException(DurakErrorCode.DURAK_ATTACK_LIMIT_REACHED,
                    "This bout already holds %d attack cards.", field.getMaxAttackCards());
        }
        var card = action.card();
        if (!field.ranksOnTable().contains(card.rank())) {
            throw new DurakRuleException(DurakErrorCode.DURAK_THROW_RANK_NOT_ON_TABLE,
                    "%s does not match any rank on the table.", card);
        }
        requireInHand(actor, card);
        playToTable(actor, card, field, "THROW_IN", null);
        field.resumeThrowWindowAfter(actor);

        if (field.getPhase() == DurakPhase.THROW_AFTER_TAKE) {
            var outcome = continueThrowWindow(field);
            return new DurakActionResult(DurakActionType.THROW_IN, outcome, !active);
        }
        field.setPhase(DurakPhase.WAITING_FOR_DEFENSE);
        field.setActionPlayer(field.getDefender());
        return new DurakActionResult(DurakActionType.THROW_IN, null, false);
    }

    /**
     * Walks the throw cursor to the next thrower who can still act; when nobody can, the bout
     * resolves. Returns the bout outcome when it resolved, otherwise {@code null}.
     */
    private DurakBoutOutcome continueThrowWindow(DurakPlayingField field) {
        var throwers = field.getEligibleThrowers();
        while (field.getDoneThrowers().size() < throwers.size()) {
            var candidate = throwers.get(field.getThrowCursor());
            if (!field.getDoneThrowers().contains(candidate)
                    && candidate.hasCards()
                    && field.getAttackSlots().size() < field.getMaxAttackCards()) {
                field.setActionPlayer(candidate);
                return null;
            }
            field.markDone(candidate);
            field.advanceThrowCursor();
        }
        return resolveBout(field);
    }

    private void playToTable(DurakPlayer actor, DurakCard card, DurakPlayingField field,
                             String actionType, Integer targetPlayOrder) {
        actor.playCard(card);
        var slot = field.addAttack(actor, card);
        slot.setAttackPlayOrder(field.nextPlayOrder());
        getGameRecordingHook().cardPlayed(field, actor, card, actionType, targetPlayOrder);
    }

    /* ******************** bout resolution ******************** */

    private DurakBoutOutcome resolveBout(DurakPlayingField field) {
        if (field.getOutcome() != null) {
            return field.getOutcome(); // resolution is idempotent
        }
        // The bout as it looked the instant it ended — table still full, nobody refilled.
        // Clients need that picture: the move that ended the bout is otherwise never seen.
        var handSizes = new LinkedHashMap<DurakPlayer, Integer>();
        for (var player : getPlayers()) handSizes.put(player, player.handSize());
        lastResolvedBout = new ResolvedBout(field, handSizes, getCardsLeftInDeck(), List.copyOf(finishOrder));
        var tableCards = field.allTableCards();
        DurakBoutOutcome outcome;
        DurakPlayer nextLead;
        if (field.isTakeDeclared()) {
            outcome = DurakBoutOutcome.TAKEN;
            field.getDefender().getHand().addCards(tableCards);
            nextLead = nextActiveAfter(field.getDefender());
        } else {
            outcome = DurakBoutOutcome.DEFENDED;
            discardPile.addAll(tableCards);
            nextLead = field.getDefender();
        }
        field.setOutcome(outcome);
        refill(field);
        getGameRecordingHook().roundEnded(field, null);
        retireEmptyHandedPlayers(field);

        if (activePlayers.size() <= 1) {
            finishGame(field);
            return outcome;
        }
        nextLeadAttacker = activePlayers.contains(nextLead) ? nextLead : nextActiveFromSeat(nextLead.getSeat());
        setPlayingField(createPlayingField());
        getGameRecordingHook().roundStarted(getPlayingField());
        return outcome;
    }

    private void refill(DurakPlayingField field) {
        var order = new ArrayList<DurakPlayer>();
        for (var player : ringFrom(field.getInitialAttacker())) {
            if (player != field.getDefender()) order.add(player);
        }
        order.add(field.getDefender()); // the final defender always draws last
        for (var player : order) {
            while (player.handSize() < DurakGameConfig.CARDS_IN_HAND && !getDeck().isEmpty()) {
                player.getHand().addCard(getDeck().drawCard());
            }
        }
    }

    private void retireEmptyHandedPlayers(DurakPlayingField field) {
        if (!getDeck().isEmpty()) {
            return; // players only finish once the stock is exhausted
        }
        for (var player : ringFrom(field.getInitialAttacker())) {
            if (!player.hasCards()) {
                finishOrder.add(player);
                activePlayers.remove(player);
            }
        }
    }

    private void finishGame(DurakPlayingField field) {
        active = false;
        field.setPhase(DurakPhase.FINISHED);
        if (activePlayers.size() == 1) {
            loser = activePlayers.getFirst();
        } else {
            draw = true;
        }
        getGameRecordingHook().gameEnded(this, determineGameWinners());
    }

    /* ******************** rules that clients may ask about ******************** */

    /** True when {@code defense} legally covers {@code attack} under the current trump. */
    public boolean canBeat(DurakCard attack, DurakCard defense) {
        if (attack.isJoker()) {
            return false; // nothing beats a Joker; the defender must pass it or take
        }
        if (defense.isJoker()) {
            return defense.isRed() == attack.isRed(); // colour rule, trumps included
        }
        if (defense.getSuit() == attack.getSuit()) {
            return defense.rank() > attack.rank();
        }
        return defense.getSuit() == trumpSuit;
    }

    public boolean canThrowIn(DurakCard card) {
        var field = getPlayingField();
        if (field == null || !active) return false;
        if (card.isJoker() && !config.jokersEnabled()) return false;
        return field.getAttackSlots().size() < field.getMaxAttackCards()
                && field.ranksOnTable().contains(card.rank());
    }

    public boolean canPass(DurakPlayer defender, DurakCard card) {
        var field = getPlayingField();
        if (field == null || !active || !config.passingEnabled()) return false;
        if (field.getPhase() != DurakPhase.WAITING_FOR_DEFENSE || defender != field.getDefender()) return false;
        if (card.isJoker() && !config.jokersEnabled()) return false;
        if (field.getAttackSlots().stream().anyMatch(DurakAttackSlot::covered)) return false;
        if (field.uncoveredSlots().stream().noneMatch(s -> s.attackCard().rank() == card.rank())) return false;
        var next = nextActiveAfter(defender);
        return next != defender
                && field.getAttackSlots().size() + 1 <= Math.min(DurakGameConfig.CARDS_IN_HAND, next.handSize());
    }

    public List<DurakPlayer> eligibleThrowers() {
        return getPlayingField() == null ? List.of() : getPlayingField().getEligibleThrowers();
    }

    /** The action a phase timeout should apply on the current action player's behalf. */
    public DurakAction timeoutAction() {
        var field = getPlayingField();
        return switch (field.getPhase()) {
            case WAITING_FOR_ATTACK -> DurakAction.attack(lowestOpeningCard(field.getActionPlayer()));
            case WAITING_FOR_DEFENSE -> DurakAction.take();
            default -> DurakAction.done();
        };
    }

    /** Lowest non-trump card, falling back to the lowest card overall. Deterministic for tests. */
    private DurakCard lowestOpeningCard(DurakPlayer player) {
        Comparator<DurakCard> order = Comparator
                .comparing((DurakCard c) -> c.getSuit() == trumpSuit || c.isJoker())
                .thenComparingInt(DurakCard::rank)
                .thenComparing(DurakCard::code);
        return player.getHand().getCards().stream().min(order).orElseThrow();
    }

    /* ******************** seating helpers ******************** */

    /** Active players clockwise starting at {@code start} (inclusive). */
    private List<DurakPlayer> ringFrom(DurakPlayer start) {
        var res = new ArrayList<DurakPlayer>(activePlayers.size());
        var index = activePlayers.indexOf(start);
        if (index < 0) {
            // the player already left the rotation: start from the next occupied seat
            index = indexOfNextActiveSeat(start.getSeat());
        }
        for (int i = 0; i < activePlayers.size(); i++) {
            res.add(activePlayers.get((index + i) % activePlayers.size()));
        }
        return res;
    }

    private DurakPlayer nextActiveAfter(DurakPlayer player) {
        var index = activePlayers.indexOf(player);
        if (index < 0) return nextActiveFromSeat(player.getSeat());
        return activePlayers.get((index + 1) % activePlayers.size());
    }

    private DurakPlayer previousActiveBefore(DurakPlayer player) {
        var index = activePlayers.indexOf(player);
        if (index < 0) return nextActiveFromSeat(player.getSeat());
        return activePlayers.get((index - 1 + activePlayers.size()) % activePlayers.size());
    }

    private DurakPlayer nextActiveFromSeat(int seat) {
        return activePlayers.get(indexOfNextActiveSeat(seat));
    }

    private int indexOfNextActiveSeat(int seat) {
        for (int i = 0; i < activePlayers.size(); i++) {
            if (activePlayers.get(i).getSeat() > seat) return i;
        }
        return 0;
    }

    private List<DurakPlayer> computeEligibleThrowers(DurakPlayer defender, DurakPlayer ringStart) {
        var res = new ArrayList<DurakPlayer>();
        if (config.throwInPolicy() == DurakThrowInPolicy.EVERYONE) {
            for (var player : ringFrom(ringStart)) {
                if (player != defender) res.add(player);
            }
        } else {
            for (var neighbour : List.of(previousActiveBefore(defender), nextActiveAfter(defender))) {
                if (neighbour != defender && !res.contains(neighbour)) res.add(neighbour);
            }
        }
        return res;
    }

    /* ******************** AbstractGame plumbing ******************** */

    @Override
    public DurakPlayingField createPlayingField() {
        var lead = nextLeadAttacker;
        var defender = nextActiveAfter(lead);
        var field = new DurakPlayingField(List.copyOf(activePlayers), this, ++boutNumber, lead, defender,
                Math.min(DurakGameConfig.CARDS_IN_HAND, defender.handSize()));
        field.setEligibleThrowers(computeEligibleThrowers(defender, lead));
        return field;
    }

    @Override
    public DurakDeck createDeck(int cardsNum) {
        return DurakDeck.shuffled(config, random);
    }

    @Override
    public void createPlayersHands(DurakDeck deck, List<DurakPlayer> players) {
        // Durak deals one card at a time in start(); nothing to do here.
    }

    @Override
    public List<DurakPlayer> createPlayers() {
        return getPlayers();
    }

    @Override
    public boolean isGameActive() {
        return active;
    }

    @Override
    public void preGameCreateCheck(int numberOfPlayers, int cardsNum) {
        if (cardsNum < numberOfPlayers * DurakGameConfig.CARDS_IN_HAND) {
            throw new DurakRuleException(DurakErrorCode.DURAK_INVALID_PLAYER_COUNT,
                    "%d cards cannot deal six each to %d players.", cardsNum, numberOfPlayers);
        }
    }

    @Override
    public List<DurakPlayer> determineGameWinners() {
        if (draw) return List.of();
        return getPlayers().stream().filter(p -> p != loser).toList();
    }

    /* ******************** state ******************** */

    public DurakGameConfig getConfig() {
        return config;
    }

    public PokerCardSuit getTrumpSuit() {
        return trumpSuit;
    }

    public DurakCard getTrumpIndicator() {
        return trumpIndicator;
    }

    public List<DurakCard> getDiscardPile() {
        return Collections.unmodifiableList(discardPile);
    }

    public List<DurakPlayer> activePlayers() {
        return List.copyOf(activePlayers);
    }

    public List<DurakPlayer> getFinishOrder() {
        return List.copyOf(finishOrder);
    }

    public DurakPlayer getLoser() {
        return loser;
    }

    public boolean isDraw() {
        return draw;
    }

    public int getCardsLeftInDeck() {
        return getDeck() == null ? 0 : getDeck().getSize();
    }

    /* ******************** small helpers ******************** */

    private DurakCard requireInHand(DurakPlayer actor, DurakCard card) {
        if (!actor.getHand().getCards().contains(card)) {
            throw new DurakRuleException(DurakErrorCode.DURAK_CARD_NOT_IN_HAND,
                    "%s is not in your hand.", card);
        }
        return card;
    }

    private void requirePhaseAllows(boolean allowed, DurakAction action, DurakPlayingField field) {
        if (!allowed) throw invalidForPhase(action, field);
    }

    private DurakRuleException invalidForPhase(DurakAction action, DurakPlayingField field) {
        return new DurakRuleException(DurakErrorCode.DURAK_INVALID_ACTION_FOR_PHASE,
                "%s is not allowed while %s.", action.type(), field.getPhase());
    }
}
