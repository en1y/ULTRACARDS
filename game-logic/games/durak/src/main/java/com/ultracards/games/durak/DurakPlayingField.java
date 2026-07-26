package com.ultracards.games.durak;

import com.ultracards.cards.PokerCardSuit;
import com.ultracards.cards.PokerCardValue;
import com.ultracards.templates.game.model.AbstractPlayingField;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One Durak bout: the attack slots on the table plus the cursor state that decides who may act.
 * A bout has no winner, so {@link #determineRoundWinner()} is always {@code null}.
 */
public class DurakPlayingField
        extends AbstractPlayingField<PokerCardSuit, PokerCardValue, DurakCard, DurakHand, DurakDeck, DurakPlayer> {

    private final int boutNumber;
    private final DurakPlayer initialAttacker;
    private final List<DurakAttackSlot> attackSlots = new ArrayList<>();
    private final Set<DurakPlayer> doneThrowers = new LinkedHashSet<>();
    private final List<DurakPlayer> passChain = new ArrayList<>();

    private DurakPhase phase = DurakPhase.WAITING_FOR_ATTACK;
    private DurakPlayer defender;
    private DurakPlayer actionPlayer;
    private int maxAttackCards;
    private List<DurakPlayer> eligibleThrowers = List.of();
    private int throwCursor;
    private boolean takeDeclared;
    private DurakBoutOutcome outcome;
    private int nextSlotId;
    private int playOrder;

    DurakPlayingField(List<DurakPlayer> players, DurakGame game, int boutNumber,
                      DurakPlayer initialAttacker, DurakPlayer defender, int maxAttackCards) {
        super(players, game);
        this.boutNumber = boutNumber;
        this.initialAttacker = initialAttacker;
        this.defender = defender;
        this.maxAttackCards = maxAttackCards;
        this.actionPlayer = initialAttacker;
    }

    @Override
    public DurakPlayer determineRoundWinner() {
        return null; // a bout is resolved, not won
    }

    DurakAttackSlot addAttack(DurakPlayer attacker, DurakCard card) {
        var slot = new DurakAttackSlot(nextSlotId++, attacker, card);
        attackSlots.add(slot);
        addCard(card);
        return slot;
    }

    DurakAttackSlot slot(int slotId) {
        for (var slot : attackSlots) {
            if (slot.slotId() == slotId) return slot;
        }
        return null;
    }

    public List<DurakAttackSlot> uncoveredSlots() {
        return attackSlots.stream().filter(s -> !s.covered()).toList();
    }

    public boolean allCovered() {
        return attackSlots.stream().allMatch(DurakAttackSlot::covered);
    }

    /** Every rank currently visible on the table, attack and defense cards alike. */
    public Set<Integer> ranksOnTable() {
        var res = new LinkedHashSet<Integer>();
        for (var slot : attackSlots) {
            res.add(slot.attackCard().rank());
            if (slot.covered()) res.add(slot.defenseCard().rank());
        }
        return res;
    }

    /** Every card on the table, in play order. */
    public List<DurakCard> allTableCards() {
        var res = new ArrayList<DurakCard>();
        for (var slot : attackSlots) {
            res.add(slot.attackCard());
            if (slot.covered()) res.add(slot.defenseCard());
        }
        return res;
    }

    int nextPlayOrder() {
        return playOrder++;
    }

    public int getBoutNumber() {
        return boutNumber;
    }

    public DurakPhase getPhase() {
        return phase;
    }

    void setPhase(DurakPhase phase) {
        this.phase = phase;
    }

    public DurakPlayer getInitialAttacker() {
        return initialAttacker;
    }

    /** Alias of {@link #getInitialAttacker()}: passing changes the defender, never the opener. */
    public DurakPlayer getLeadAttacker() {
        return initialAttacker;
    }

    public DurakPlayer getDefender() {
        return defender;
    }

    void setDefender(DurakPlayer defender) {
        this.defender = defender;
    }

    public DurakPlayer getActionPlayer() {
        return actionPlayer;
    }

    void setActionPlayer(DurakPlayer actionPlayer) {
        this.actionPlayer = actionPlayer;
    }

    public int getMaxAttackCards() {
        return maxAttackCards;
    }

    void setMaxAttackCards(int maxAttackCards) {
        this.maxAttackCards = maxAttackCards;
    }

    public List<DurakAttackSlot> getAttackSlots() {
        return List.copyOf(attackSlots);
    }

    public List<DurakPlayer> getEligibleThrowers() {
        return eligibleThrowers;
    }

    void setEligibleThrowers(List<DurakPlayer> eligibleThrowers) {
        this.eligibleThrowers = List.copyOf(eligibleThrowers);
    }

    public int getThrowCursor() {
        return throwCursor;
    }

    public Set<DurakPlayer> getDoneThrowers() {
        return Set.copyOf(doneThrowers);
    }

    void markDone(DurakPlayer player) {
        doneThrowers.add(player);
    }

    void startThrowWindow() {
        doneThrowers.clear();
        throwCursor = 0;
    }

    void resumeThrowWindowAfter(DurakPlayer player) {
        doneThrowers.clear();
        var index = eligibleThrowers.indexOf(player);
        throwCursor = index < 0 || eligibleThrowers.isEmpty()
                ? 0
                : (index + 1) % eligibleThrowers.size();
    }

    void advanceThrowCursor() {
        if (!eligibleThrowers.isEmpty()) {
            throwCursor = (throwCursor + 1) % eligibleThrowers.size();
        }
    }

    public boolean isTakeDeclared() {
        return takeDeclared;
    }

    void declareTake() {
        this.takeDeclared = true;
    }

    public List<DurakPlayer> getPassChain() {
        return List.copyOf(passChain);
    }

    void recordPass(DurakPlayer formerDefender) {
        passChain.add(formerDefender);
    }

    public DurakBoutOutcome getOutcome() {
        return outcome;
    }

    void setOutcome(DurakBoutOutcome outcome) {
        this.outcome = outcome;
    }

    @Override
    public String toString() {
        return "Bout " + boutNumber + " " + phase + " def=" + defender + " " + attackSlots;
    }
}
