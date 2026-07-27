package com.ultracards.gateway.dto.games.games.durak;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ultracards.cards.PokerCard;
import com.ultracards.cards.PokerCardSuit;
import com.ultracards.cards.PokerCardValue;
import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.gateway.dto.games.games.GameCardDTO;
import com.ultracards.gateway.dto.games.games.GameCardTypeDTO;
import com.ultracards.gateway.dto.games.games.ShortGameHistoryDTO;
import com.ultracards.cards.ItalianCard;
import com.ultracards.cards.ItalianCardSuit;
import com.ultracards.cards.ItalianCardValue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DurakContractTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    /* ******************** card codec ******************** */

    @Test
    void pokerCardCodesAreStableAcrossLocales() {
        var card = new PokerCard<>(PokerCardSuit.HEARTS, PokerCardValue.ACE);
        var expected = "H14";
        for (var locale : List.of(Locale.ENGLISH, Locale.GERMAN, Locale.of("hr"), Locale.of("uk"))) {
            PokerCard.setLocale(locale);
            assertEquals(expected, GameCardDTO.createCardDTO(card).getCard(), locale.toString());
        }
        PokerCard.setLocale(Locale.ENGLISH);
    }

    @Test
    void everySuitedPokerCardRoundTrips() {
        for (var suit : PokerCardSuit.suits()) {
            for (var value : PokerCardValue.values()) {
                if (value == PokerCardValue.JOKER) continue;
                var dto = GameCardDTO.createCardDTO(new PokerCard<>(suit, value));
                var back = dto.toCard();
                assertEquals(suit, back.getSuit());
                assertEquals(value, back.getValue());
            }
        }
    }

    @Test
    void jokerCodesRoundTripExactly() {
        var red = GameCardDTO.createCardDTO(new PokerCard<>(PokerCardSuit.RED_JOKER, PokerCardValue.JOKER));
        var black = GameCardDTO.createCardDTO(new PokerCard<>(PokerCardSuit.BLACK_JOKER, PokerCardValue.JOKER));
        assertEquals("JR", red.getCard());
        assertEquals("JB", black.getCard());
        assertEquals(GameCardTypeDTO.POKER, red.getCardType());
        assertEquals(PokerCardSuit.RED_JOKER, red.toCard().getSuit());
        assertEquals(PokerCardSuit.BLACK_JOKER, black.toCard().getSuit());
        assertEquals(PokerCardValue.JOKER, black.toCard().getValue());
    }

    @Test
    void malformedCardDataIsRejected() {
        for (var bad : List.of("", "  ", "h14", "X5", "H", "H1", "H15", "H14X", "JOKER", "jr")) {
            var dto = new GameCardDTO(GameCardTypeDTO.POKER, bad);
            assertThrows(IllegalArgumentException.class, dto::toCard, "poker code " + bad);
        }
        assertThrows(IllegalArgumentException.class, () -> new GameCardDTO(GameCardTypeDTO.POKER, null).toCard());
        assertThrows(IllegalArgumentException.class, () -> new GameCardDTO(null, "H14").toCard());
    }

    @Test
    void jokerCodesAreRejectedForItalianCards() {
        assertThrows(IllegalArgumentException.class, () -> new GameCardDTO(GameCardTypeDTO.ITALIAN, "JR").toCard());
        assertThrows(IllegalArgumentException.class, () -> new GameCardDTO(GameCardTypeDTO.ITALIAN, "JB").toCard());
    }

    @Test
    void existingItalianCardCodesKeepWorking() {
        for (var suit : ItalianCardSuit.values()) {
            for (var value : ItalianCardValue.values()) {
                var dto = GameCardDTO.createCardDTO(new ItalianCard<>(suit, value));
                assertEquals(GameCardTypeDTO.ITALIAN, dto.getCardType());
                var back = dto.toCard();
                assertEquals(suit, back.getSuit());
                assertEquals(value, back.getValue());
            }
        }
    }

    /* ******************** config subtype ******************** */

    private static DurakGameConfigDTO config() {
        return new DurakGameConfigDTO(4, 54, true, DurakThrowInPolicyDTO.EVERYONE, true,
                List.of(new GamePlayerDTO("a", 1L), new GamePlayerDTO("b", 2L)));
    }

    @Test
    void configResolvesThroughTheDurakJsonSubtype() throws Exception {
        var history = new ShortGameHistoryDTO(UUID.randomUUID(), UUID.randomUUID(), "game",
                GameTypeDTO.Durak, null, null, config(), List.of(), null, List.of());
        var json = mapper.writeValueAsString(history);
        assertTrue(json.contains("\"gameType\":\"Durak\""), json);

        var back = mapper.readValue(json, ShortGameHistoryDTO.class);
        assertInstanceOf(DurakGameConfigDTO.class, back.getGameConfig());
        var durak = (DurakGameConfigDTO) back.getGameConfig();
        assertEquals(54, durak.getDeckSize());
        assertTrue(durak.getJokersEnabled());
        assertTrue(durak.getPassingEnabled());
        assertEquals(DurakThrowInPolicyDTO.EVERYONE, durak.getThrowInPolicy());
        assertEquals(6, durak.getCardsInHandNum());
    }

    /* ******************** action DTO ******************** */

    @Test
    void actionRequestRoundTripsEveryType() throws Exception {
        for (var type : DurakActionTypeDTO.values()) {
            var card = switch (type) {
                case TAKE, DONE -> null;
                default -> new GameCardDTO(GameCardTypeDTO.POKER, "S7");
            };
            var slot = type == DurakActionTypeDTO.DEFEND ? 3 : null;
            var request = new DurakActionRequestDTO(type, card, slot, 12L);
            var back = mapper.readValue(mapper.writeValueAsString(request), DurakActionRequestDTO.class);
            assertEquals(request, back);
        }
    }

    @Test
    void actionRequestRejectsUnknownFields() {
        var json = """
                {"type":"ATTACK","card":{"cardType":"POKER","card":"S7"},"expectedRevision":1,"userId":9}""";
        assertThrows(UnrecognizedPropertyException.class,
                () -> mapper.readValue(json, DurakActionRequestDTO.class));
    }

    /* ******************** state, result and history DTOs ******************** */

    @Test
    void entityResultAndHistoryDtosSerialize() throws Exception {
        var alice = new GamePlayerDTO("alice", 1L);
        var bob = new GamePlayerDTO("bob", 2L);

        var entity = new DurakGameEntityDTO();
        entity.setId(UUID.randomUUID());
        entity.setLobbyId(UUID.randomUUID());
        entity.setName("table");
        entity.setPlayersOrder(List.of(alice, bob));
        entity.setPlayersCardsMap(java.util.Map.of(alice, 6, bob, 5));
        entity.setPlayedCards(List.of());
        entity.setGameConfig(config());
        entity.setPhase(DurakPhaseDTO.WAITING_FOR_DEFENSE);
        entity.setStateRevision(7L);
        entity.setTrumpSuit("SPADES");
        entity.setTrumpIndicator(new GameCardDTO(GameCardTypeDTO.POKER, "S9"));
        entity.setAttackSlots(List.of(new DurakAttackSlotDTO(0, alice,
                new GameCardDTO(GameCardTypeDTO.POKER, "H9"), bob,
                new GameCardDTO(GameCardTypeDTO.POKER, "H10"))));
        entity.setThrowInPolicy(DurakThrowInPolicyDTO.EVERYONE);

        var entityJson = mapper.writeValueAsString(entity);
        assertFalse(entityJson.contains("\"hand\""), "public state must never carry a hand");
        var entityBack = mapper.readValue(entityJson, DurakGameEntityDTO.class);
        assertEquals(7L, entityBack.getStateRevision());
        assertEquals(1, entityBack.getAttackSlots().size());
        assertEquals(0, entityBack.getAttackSlots().getFirst().getSlotId());

        var result = new DurakGameResultDTO(List.of(alice), bob, List.of(alice), false);
        var resultBack = mapper.readValue(mapper.writeValueAsString(result), DurakGameResultDTO.class);
        assertEquals(bob, resultBack.getLoser());
        assertEquals(List.of(alice), resultBack.getGameWinners());
        assertFalse(resultBack.isDraw());

        var legal = new DurakLegalActionsDTO(7L, List.of(DurakActionTypeDTO.DEFEND, DurakActionTypeDTO.TAKE),
                List.of(0), List.of(), List.of("H9"));
        assertEquals(legal, mapper.readValue(mapper.writeValueAsString(legal), DurakLegalActionsDTO.class));

        var bout = new DurakGameHistoryDTO.DurakBoutHistoryDTO(1,
                java.util.Map.of(alice, List.of(new GameCardDTO(GameCardTypeDTO.POKER, "H9"))),
                alice, bob, 6,
                List.of(new DurakPlayHistoryDTOFixture().play(alice)), List.of(), "TAKEN");
        var history = new DurakGameHistoryDTO(UUID.randomUUID(), UUID.randomUUID(), "table", alice,
                null, null, config(), List.of(alice, bob), "SPADES",
                new GameCardDTO(GameCardTypeDTO.POKER, "S9"), List.of(bout), List.of(alice), bob,
                List.of(alice), false);
        var historyBack = mapper.readValue(mapper.writeValueAsString(history), DurakGameHistoryDTO.class);
        assertEquals("TAKEN", historyBack.getBouts().getFirst().getOutcome());
        assertEquals("ATTACK", historyBack.getBouts().getFirst().getPlays().getFirst().getActionType());
        assertEquals(bob, historyBack.getLoser());
    }

    private static final class DurakPlayHistoryDTOFixture {
        DurakGameHistoryDTO.DurakPlayHistoryDTO play(GamePlayerDTO player) {
            return new DurakGameHistoryDTO.DurakPlayHistoryDTO(0, "ATTACK", player,
                    new GameCardDTO(GameCardTypeDTO.POKER, "H9"), null);
        }
    }
}
