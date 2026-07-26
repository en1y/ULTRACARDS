package com.ultracards.games.durak;

import java.util.ArrayList;
import java.util.List;

/** Builds fully deterministic Durak games from card codes. */
final class DurakTestSupport {

    private DurakTestSupport() {
    }

    static DurakCard card(String code) {
        return DurakCardFactory.fromCode(code);
    }

    static List<DurakCard> cards(String... codes) {
        return List.of(codes).stream().map(DurakTestSupport::card).toList();
    }

    /**
     * Deals exactly the given hands (six codes each, one per player) and puts {@code indicator}
     * at the bottom of the stock so it becomes the trump. Every remaining pack card fills the
     * stock in canonical order.
     */
    static DurakGame game(DurakGameConfig config, List<String> indicatorAndHands) {
        var indicator = card(indicatorAndHands.getFirst());
        var hands = new ArrayList<List<DurakCard>>();
        for (int p = 0; p < config.numberOfPlayers(); p++) {
            var hand = new ArrayList<DurakCard>();
            for (var code : indicatorAndHands.get(p + 1).split(" ")) {
                hand.add(card(code));
            }
            if (hand.size() != DurakGameConfig.CARDS_IN_HAND) {
                throw new IllegalArgumentException("Each hand needs six cards, got " + hand);
            }
            hands.add(hand);
        }

        var dealt = new ArrayList<DurakCard>();
        for (int i = 0; i < DurakGameConfig.CARDS_IN_HAND; i++) {
            for (var hand : hands) {
                dealt.add(hand.get(i));
            }
        }

        var order = new ArrayList<>(dealt);
        var remaining = new ArrayList<>(new DurakCardFactory().createPack(config));
        remaining.removeAll(dealt);
        remaining.remove(indicator);
        order.addAll(remaining);
        order.add(indicator);

        var players = new ArrayList<DurakPlayer>();
        for (int p = 0; p < config.numberOfPlayers(); p++) {
            players.add(new DurakPlayer("P" + p, p));
        }
        var game = new DurakGame(players, config, order);
        game.start();
        return game;
    }

    static DurakPlayer player(DurakGame game, int seat) {
        return game.getPlayers().get(seat);
    }

    /** Applies an action on behalf of whoever must act right now. */
    static DurakActionResult act(DurakGame game, DurakAction action) {
        return game.apply(game.getPlayingField().getActionPlayer(), action);
    }

    static int slotOf(DurakGame game, String attackCode) {
        for (var slot : game.getPlayingField().getAttackSlots()) {
            if (slot.attackCard().code().equals(attackCode)) return slot.slotId();
        }
        throw new IllegalArgumentException("No attack slot holding " + attackCode);
    }
}
