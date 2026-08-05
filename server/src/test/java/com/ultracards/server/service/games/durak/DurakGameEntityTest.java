package com.ultracards.server.service.games.durak;

import com.ultracards.games.durak.DurakErrorCode;
import com.ultracards.games.durak.DurakGameConfig;
import com.ultracards.games.durak.DurakRuleException;
import com.ultracards.games.durak.DurakThrowInPolicy;
import com.ultracards.gateway.dto.games.games.GameCardDTO;
import com.ultracards.gateway.dto.games.games.GameCardTypeDTO;
import com.ultracards.gateway.dto.games.games.durak.DurakActionRequestDTO;
import com.ultracards.gateway.dto.games.games.durak.DurakActionTypeDTO;
import com.ultracards.gateway.dto.games.games.durak.DurakGameConfigDTO;
import com.ultracards.gateway.dto.games.games.durak.DurakPhaseDTO;
import com.ultracards.gateway.dto.games.games.durak.DurakThrowInPolicyDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.durak.DurakGameEntity;
import com.ultracards.server.entity.games.durak.DurakPlayerEntity;
import com.ultracards.server.entity.lobby.DurakLobbyGameConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DurakGameEntityTest {

    private static UserEntity user(Long id, String name) {
        var user = new UserEntity(name + "@example.com", name);
        user.setId(id);
        return user;
    }

    private static DurakGameEntity game(int players, int deckSize, boolean jokers, boolean passing) {
        var users = new java.util.ArrayList<UserEntity>();
        for (long i = 1; i <= players; i++) users.add(user(i, "P" + i));
        var dto = new DurakGameConfigDTO(players, deckSize, jokers, DurakThrowInPolicyDTO.EVERYONE, passing, null);
        var config = new DurakLobbyGameConfig(dto, users);
        var game = new DurakGameEntity(UUID.randomUUID(), "table", users.getFirst(), config, users);
        game.getGame().start();
        return game;
    }

    private static DurakActionRequestDTO request(DurakActionTypeDTO type, String code, Integer slot, long revision) {
        return new DurakActionRequestDTO(type, code == null ? null : new GameCardDTO(GameCardTypeDTO.POKER, code),
                slot, revision);
    }

    @Test
    void appliesAnOpeningAttackAndBumpsTheRevision() {
        var game = game(2, 36, false, false);
        var actor = game.getActionPlayer();
        var card = actor.getHand().getCards().getFirst();

        game.apply(actor.getUser(), request(DurakActionTypeDTO.ATTACK, card.code(), null, 0L));

        assertThat(game.getStateRevision()).isEqualTo(1L);
        assertThat(game.createGameDTO().getPhase()).isEqualTo(DurakPhaseDTO.WAITING_FOR_DEFENSE);
        assertThat(game.createGameDTO().getAttackSlots()).hasSize(1);
    }

    @Test
    void rejectsAStaleRevisionWithoutMutatingAnything() {
        var game = game(2, 36, false, false);
        var actor = game.getActionPlayer();
        var card = actor.getHand().getCards().getFirst();

        assertThatThrownBy(() -> game.apply(actor.getUser(), request(DurakActionTypeDTO.ATTACK, card.code(), null, 7L)))
                .isInstanceOf(DurakRuleException.class)
                .extracting(ex -> ((DurakRuleException) ex).getCode())
                .isEqualTo(DurakErrorCode.DURAK_STALE_REVISION);
        assertThat(game.getStateRevision()).isZero();
        assertThat(actor.handSize()).isEqualTo(6);
    }

    @Test
    void rejectsAnActionFromAnyoneButTheActionPlayer() {
        var game = game(3, 36, false, false);
        var actor = game.getActionPlayer();
        var other = game.getGame().getPlayers().stream()
                .map(p -> (DurakPlayerEntity) p)
                .filter(p -> p != actor)
                .findFirst().orElseThrow();

        assertThatThrownBy(() -> game.apply(other.getUser(),
                request(DurakActionTypeDTO.ATTACK, other.getHand().getCards().getFirst().code(), null, 0L)))
                .isInstanceOf(DurakRuleException.class)
                .extracting(ex -> ((DurakRuleException) ex).getCode())
                .isEqualTo(DurakErrorCode.DURAK_NOT_ACTION_PLAYER);
    }

    @Test
    void rejectsANonParticipant() {
        var game = game(2, 36, false, false);
        assertThatThrownBy(() -> game.apply(user(99L, "Outsider"),
                request(DurakActionTypeDTO.TAKE, null, null, 0L)))
                .isInstanceOf(DurakRuleException.class);
    }

    @Test
    void publicStateExposesCardCountsButNeverCards() {
        var game = game(4, 54, true, true);
        var dto = game.createGameDTO();

        assertThat(dto.getPlayersCardsMap()).hasSize(4);
        assertThat(dto.getPlayersCardsMap().values()).allMatch(count -> count == 6);
        assertThat(dto.getPlayedCards()).isEmpty();
        assertThat(dto.getTrumpIndicator()).isNotNull();
        assertThat(dto.getJokersEnabled()).isTrue();
        assertThat(dto.getThrowInPolicy()).isEqualTo(DurakThrowInPolicyDTO.EVERYONE);
        assertThat(dto.getGameConfig()).isInstanceOf(DurakGameConfigDTO.class);
        assertThat(((DurakGameConfigDTO) dto.getGameConfig()).getModeKey())
                .isEqualTo("P4_D54_JOKERS_EVERYONE_PASS");
    }

    @Test
    void legalActionsAreEmptyForPlayersWhoCannotAct() {
        var game = game(3, 36, false, false);
        var actor = game.getActionPlayer();
        for (var raw : game.getGame().getPlayers()) {
            var player = (DurakPlayerEntity) raw;
            var legal = game.legalActions(player);
            assertThat(legal.getStateRevision()).isZero();
            if (player == actor) {
                assertThat(legal.getAllowedActionTypes()).containsExactly(DurakActionTypeDTO.ATTACK);
            } else {
                assertThat(legal.getAllowedActionTypes()).isEmpty();
            }
        }
    }

    @Test
    void defenderLegalActionsOfferDefendAndTake() {
        var game = game(2, 36, false, false);
        var attacker = game.getActionPlayer();
        game.apply(attacker.getUser(),
                request(DurakActionTypeDTO.ATTACK, attacker.getHand().getCards().getFirst().code(), null, 0L));

        var defender = game.getActionPlayer();
        var legal = game.legalActions(defender);
        assertThat(legal.getStateRevision()).isEqualTo(1L);
        assertThat(legal.getAllowedActionTypes()).contains(DurakActionTypeDTO.TAKE);
    }

    /** Throwing in needs no turn, so the attacker's cards must stay listed while the defender thinks. */
    @Test
    void anAttackerKeepsItsThrowableCardsWhileTheDefenderIsDeciding() {
        var game = game(2, 36, false, false);
        var attacker = game.getActionPlayer();
        game.apply(attacker.getUser(),
                request(DurakActionTypeDTO.ATTACK, attacker.getHand().getCards().getFirst().code(), null, 0L));

        var legal = game.legalActions(attacker);
        var throwable = attacker.getHand().getCards().stream()
                .filter(game.getGame()::canThrowIn)
                .map(card -> card.code())
                .toList();

        assertThat(game.getActionPlayer()).isNotSameAs(attacker);
        assertThat(legal.getThrowableCardCodes()).containsExactlyElementsOf(throwable);
        assertThat(legal.getAllowedActionTypes())
                .doesNotContain(DurakActionTypeDTO.TAKE, DurakActionTypeDTO.DONE, DurakActionTypeDTO.DEFEND);
        if (!throwable.isEmpty()) {
            assertThat(legal.getAllowedActionTypes()).contains(DurakActionTypeDTO.THROW_IN);
            game.apply(attacker.getUser(),
                    request(DurakActionTypeDTO.ATTACK, throwable.getFirst(), null, 1L));
            assertThat(game.getGame().getPlayingField().getAttackSlots()).hasSize(2);
            assertThat(game.getActionPlayer()).isNotSameAs(attacker);
        }
    }

    @Test
    void lobbyConfigRejectsInvalidCombinationsWithFourHundred() {
        var users = List.of(user(1L, "a"), user(2L, "b"), user(3L, "c"), user(4L, "d"), user(5L, "e"));
        var tooManyForShortPack = new DurakGameConfigDTO(5, 24, false, DurakThrowInPolicyDTO.EVERYONE, false, null);
        assertThatThrownBy(() -> new DurakLobbyGameConfig(tooManyForShortPack, users))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("400");

        var jokersOnShortPack = new DurakGameConfigDTO(2, 36, true, DurakThrowInPolicyDTO.EVERYONE, false, null);
        assertThatThrownBy(() -> new DurakLobbyGameConfig(jokersOnShortPack, users))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);

        var missingToggle = new DurakGameConfigDTO(2, 36, null, DurakThrowInPolicyDTO.EVERYONE, false, null);
        assertThatThrownBy(() -> new DurakLobbyGameConfig(missingToggle, users))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void lobbyConfigKeepsTheOwnerAndCollapsesDuplicateSeats() {
        var owner = user(1L, "owner");
        var second = user(2L, "second");
        var users = List.of(owner, second);
        var dto = new DurakGameConfigDTO(2, 36, false, DurakThrowInPolicyDTO.NEIGHBORS_ONLY, false,
                List.of(new com.ultracards.gateway.dto.games.GamePlayerDTO("second", 2L),
                        new com.ultracards.gateway.dto.games.GamePlayerDTO("second", 2L),
                        new com.ultracards.gateway.dto.games.GamePlayerDTO("ghost", 42L)));

        var config = DurakLobbyGameConfig.fromDto(dto, users, owner);

        assertThat(config.getOrderedUsers()).containsExactly(second, owner);
        assertThat(config.getGameConfig()).isEqualTo(new DurakGameConfig(2, 36, false,
                DurakThrowInPolicy.NEIGHBORS_ONLY, false));
    }

    @Test
    void lobbyConfigRefusesToEvictOccupantsImplicitly() {
        var users = List.of(user(1L, "a"), user(2L, "b"), user(3L, "c"));
        var dto = new DurakGameConfigDTO(2, 36, false, DurakThrowInPolicyDTO.EVERYONE, false, null);
        assertThatThrownBy(() -> DurakLobbyGameConfig.fromDto(dto, users, users.getFirst()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void startingWithTheWrongNumberOfPlayersIsRefused() {
        var users = List.of(user(1L, "a"), user(2L, "b"), user(3L, "c"));
        var dto = new DurakGameConfigDTO(2, 36, false, DurakThrowInPolicyDTO.EVERYONE, false, null);
        var config = new DurakLobbyGameConfig(dto, List.of(users.get(0), users.get(1)));
        assertThatThrownBy(() -> config.createGame(UUID.randomUUID(), "table", users.getFirst(), users))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("409");
    }
}
