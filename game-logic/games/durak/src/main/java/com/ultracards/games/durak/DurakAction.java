package com.ultracards.games.durak;

/**
 * One requested Durak action. {@code card} is required for ATTACK/DEFEND/THROW_IN/PASS,
 * {@code targetSlotId} only for DEFEND.
 */
public record DurakAction(DurakActionType type, DurakCard card, Integer targetSlotId) {

    public DurakAction {
        if (type == null) {
            throw new DurakRuleException(DurakErrorCode.DURAK_INVALID_ACTION_FOR_PHASE, "Action type is required.");
        }
        if (type.requiresCard() && card == null) {
            throw new DurakRuleException(DurakErrorCode.DURAK_CARD_NOT_IN_HAND, "%s requires a card.", type);
        }
        if (!type.requiresCard() && card != null) {
            throw new DurakRuleException(DurakErrorCode.DURAK_INVALID_ACTION_FOR_PHASE, "%s must not carry a card.", type);
        }
        if (type == DurakActionType.DEFEND && targetSlotId == null) {
            throw new DurakRuleException(DurakErrorCode.DURAK_INVALID_DEFENSE_TARGET, "DEFEND requires a target slot.");
        }
        if (type != DurakActionType.DEFEND && targetSlotId != null) {
            throw new DurakRuleException(DurakErrorCode.DURAK_INVALID_DEFENSE_TARGET,
                    "%s must not carry a defense target.", type);
        }
    }

    public static DurakAction attack(DurakCard card) {
        return new DurakAction(DurakActionType.ATTACK, card, null);
    }

    public static DurakAction defend(DurakCard card, int slotId) {
        return new DurakAction(DurakActionType.DEFEND, card, slotId);
    }

    public static DurakAction throwIn(DurakCard card) {
        return new DurakAction(DurakActionType.THROW_IN, card, null);
    }

    public static DurakAction pass(DurakCard card) {
        return new DurakAction(DurakActionType.PASS, card, null);
    }

    public static DurakAction take() {
        return new DurakAction(DurakActionType.TAKE, null, null);
    }

    public static DurakAction done() {
        return new DurakAction(DurakActionType.DONE, null, null);
    }
}
