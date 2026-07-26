package com.ultracards.games.durak;

/**
 * One attack card on the table, with the defense card covering it once it exists.
 * {@code slotId} is a durable identity; never use list indexes after serialization.
 */
public final class DurakAttackSlot {
    private final int slotId;
    private final DurakPlayer attacker;
    private final DurakCard attackCard;
    private DurakPlayer defender;
    private DurakCard defenseCard;
    private Integer attackPlayOrder;

    DurakAttackSlot(int slotId, DurakPlayer attacker, DurakCard attackCard) {
        this.slotId = slotId;
        this.attacker = attacker;
        this.attackCard = attackCard;
    }

    void cover(DurakPlayer defender, DurakCard defenseCard) {
        this.defender = defender;
        this.defenseCard = defenseCard;
    }

    public boolean covered() {
        return defenseCard != null;
    }

    public int slotId() {
        return slotId;
    }

    public DurakPlayer attacker() {
        return attacker;
    }

    public DurakCard attackCard() {
        return attackCard;
    }

    public DurakPlayer defender() {
        return defender;
    }

    public DurakCard defenseCard() {
        return defenseCard;
    }

    /** Zero-based order of the attack card within the bout's recorded plays. */
    public Integer attackPlayOrder() {
        return attackPlayOrder;
    }

    void setAttackPlayOrder(int attackPlayOrder) {
        this.attackPlayOrder = attackPlayOrder;
    }

    @Override
    public String toString() {
        return slotId + ":" + attackCard + (covered() ? "/" + defenseCard : "");
    }
}
