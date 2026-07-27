package com.ultracards.server.entity.games.durak;

import com.ultracards.cards.PokerCard;
import com.ultracards.games.durak.*;
import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.games.GameTypeDTO;
import com.ultracards.gateway.dto.games.games.GameCardDTO;
import com.ultracards.gateway.dto.games.games.durak.*;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.entity.games.GameEntity;
import com.ultracards.server.entity.lobby.DurakLobbyGameConfig;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * The live Durak game. Owns the monotonic {@code stateRevision} clients use for stale-action
 * checks; all rule validation stays inside {@link DurakGame}.
 */
@Getter
public class DurakGameEntity extends GameEntity<DurakGame, DurakLobbyGameConfig> {

    private final DurakGameConfig persistedGameConfig;
    @Setter private long stateRevision;
    @Setter private boolean finalizationPersisted;
    @Setter private boolean resultPublished;
    @Setter private boolean lobbyReopened;
    @Setter private boolean finishRetryScheduled;

    public DurakGameEntity(UUID lobbyId, String name, UserEntity owner, DurakLobbyGameConfig config,
                           List<UserEntity> players) {
        super(lobbyId, name, owner, players, GameTypeDTO.Durak,
                new DurakGame(toPlayers(players), config.getGameConfig()), config);
        this.persistedGameConfig = config.getGameConfig();
    }

    private static List<DurakPlayer> toPlayers(List<UserEntity> users) {
        var players = new ArrayList<DurakPlayer>();
        for (int seat = 0; seat < users.size(); seat++) {
            players.add(new DurakPlayerEntity(users.get(seat), seat));
        }
        return players;
    }

    /* ******************** actions ******************** */

    /**
     * Applies one authenticated action. The caller must already hold the game lock.
     *
     * @throws DurakRuleException for a stale revision, a wrong actor, or any rule violation
     */
    public DurakActionResult apply(UserEntity user, DurakActionRequestDTO request) {
        var actor = player(user);
        if (actor == null) {
            throw new DurakRuleException(DurakErrorCode.DURAK_NOT_ACTION_PLAYER, "You are not in this game.");
        }
        if (request.getExpectedRevision() == null || request.getExpectedRevision() != stateRevision) {
            throw new DurakRuleException(DurakErrorCode.DURAK_STALE_REVISION,
                    "This action was based on an outdated game state.");
        }
        var result = getGame().apply(actor, toAction(request));
        stateRevision++;
        setTurnNumber(getTurnNumber() + 1);
        return result;
    }

    private DurakAction toAction(DurakActionRequestDTO request) {
        var type = DurakActionType.valueOf(request.getType().name());
        DurakCard card = null;
        if (request.getCard() != null) {
            card = toDurakCard(request.getCard());
        }
        return new DurakAction(type, card, request.getTargetSlotId());
    }

    public static DurakCard toDurakCard(GameCardDTO dto) {
        var card = dto.toCard();
        if (!(card instanceof PokerCard<?> poker)) {
            throw new DurakRuleException(DurakErrorCode.DURAK_CARD_NOT_IN_HAND, "Durak uses poker cards.");
        }
        return new DurakCard(poker.getSuit(), poker.getValue());
    }

    public DurakPlayerEntity player(UserEntity user) {
        for (var raw : getGame().getPlayers()) {
            var player = (DurakPlayerEntity) raw;
            if (player.getUser().equals(user)) return player;
        }
        return null;
    }

    public DurakPlayerEntity getActionPlayer() {
        var field = getGame().getPlayingField();
        return field == null || field.getActionPlayer() == null
                ? null : (DurakPlayerEntity) field.getActionPlayer();
    }

    /* ******************** public state ******************** */

    public DurakGameEntityDTO createGameDTO() {
        var dto = new DurakGameEntityDTO();
        var game = getGame();
        var field = game.getPlayingField();

        var order = new ArrayList<GamePlayerDTO>();
        var cardCounts = new HashMap<GamePlayerDTO, Integer>();
        for (var raw : game.getPlayers()) {
            var player = (DurakPlayerEntity) raw;
            var playerDto = player.getGamePlayerDTO();
            order.add(playerDto);
            cardCounts.put(playerDto, player.handSize()); // counts only: never another player's cards
        }

        dto.setId(getId());
        dto.setLobbyId(getLobbyId());
        dto.setName(getName());
        dto.setPlayersOrder(order);
        dto.setPlayersCardsMap(cardCounts);
        dto.setCardsLeftInDeck(game.getCardsLeftInDeck());
        dto.setGameConfig(getGameConfig().toDto());
        dto.setPlayedCards(field == null ? List.of()
                : field.allTableCards().stream().map(GameCardDTO::createCardDTO).toList());

        dto.setTrumpSuit(game.getTrumpSuit() == null ? null : game.getTrumpSuit().name());
        dto.setTrumpIndicator(game.getTrumpIndicator() == null
                ? null : GameCardDTO.createCardDTO(game.getTrumpIndicator()));
        dto.setStateRevision(stateRevision);
        dto.setPassingEnabled(persistedGameConfig.passingEnabled());
        dto.setJokersEnabled(persistedGameConfig.jokersEnabled());
        dto.setThrowInPolicy(DurakLobbyGameConfig.toDto(persistedGameConfig.throwInPolicy()));
        dto.setDiscardedCardsNum(game.getDiscardPile().size());
        dto.setFinishOrder(toDtos(game.getFinishOrder()));
        dto.setFinishedPlayers(toDtos(game.getFinishOrder()));
        dto.setTurnEndTime(getTurnEndTime());
        dto.setTurnDurationSeconds(getTurnDurationSeconds());

        if (field != null) {
            dto.setPhase(DurakPhaseDTO.valueOf(field.getPhase().name()));
            dto.setBoutNumber(field.getBoutNumber());
            dto.setLeadAttacker(playerDto(field.getLeadAttacker()));
            dto.setDefender(playerDto(field.getDefender()));
            dto.setActionPlayer(playerDto(field.getActionPlayer()));
            dto.setMaxAttackCards(field.getMaxAttackCards());
            dto.setTakeDeclared(field.isTakeDeclared());
            dto.setEligibleThrowers(toDtos(field.getEligibleThrowers()));
            dto.setDoneThrowers(toDtos(new ArrayList<>(field.getDoneThrowers())));
            var slots = new ArrayList<DurakAttackSlotDTO>();
            for (var slot : field.getAttackSlots()) {
                slots.add(new DurakAttackSlotDTO(slot.slotId(), playerDto(slot.attacker()),
                        GameCardDTO.createCardDTO(slot.attackCard()), playerDto(slot.defender()),
                        slot.defenseCard() == null ? null : GameCardDTO.createCardDTO(slot.defenseCard())));
            }
            dto.setAttackSlots(slots);
        } else {
            dto.setAttackSlots(List.of());
            dto.setEligibleThrowers(List.of());
            dto.setDoneThrowers(List.of());
        }
        return dto;
    }

    /** What this specific player may legally do right now. Advisory; the server stays authoritative. */
    public DurakLegalActionsDTO legalActions(DurakPlayerEntity player) {
        var types = new ArrayList<DurakActionTypeDTO>();
        var slotIds = new ArrayList<Integer>();
        var throwable = new ArrayList<String>();
        var passable = new ArrayList<String>();
        var game = getGame();
        var field = game.getPlayingField();

        if (game.isGameActive() && field != null && field.getActionPlayer() == player) {
            switch (field.getPhase()) {
                // Any card in hand opens a bout, so no per-card list is needed.
                case WAITING_FOR_ATTACK -> types.add(DurakActionTypeDTO.ATTACK);
                case WAITING_FOR_DEFENSE -> {
                    for (var slot : field.uncoveredSlots()) {
                        for (var card : player.getHand().getCards()) {
                            if (game.canBeat(slot.attackCard(), card)) {
                                if (!slotIds.contains(slot.slotId())) slotIds.add(slot.slotId());
                                break;
                            }
                        }
                    }
                    if (!slotIds.isEmpty()) types.add(DurakActionTypeDTO.DEFEND);
                    for (var card : player.getHand().getCards()) {
                        if (game.canPass(player, card)) passable.add(card.code());
                    }
                    if (!passable.isEmpty()) types.add(DurakActionTypeDTO.PASS);
                    types.add(DurakActionTypeDTO.TAKE);
                }
                case WAITING_FOR_THROW_IN, THROW_AFTER_TAKE -> {
                    for (var card : player.getHand().getCards()) {
                        if (game.canThrowIn(card)) throwable.add(card.code());
                    }
                    if (!throwable.isEmpty()) types.add(DurakActionTypeDTO.THROW_IN);
                    types.add(DurakActionTypeDTO.DONE);
                }
                default -> { /* FINISHED: nothing is allowed */ }
            }
        }
        return new DurakLegalActionsDTO(stateRevision, types, slotIds, throwable, passable);
    }

    public DurakGameResultDTO createResultDTO() {
        var game = getGame();
        return new DurakGameResultDTO(toDtos(game.determineGameWinners()), playerDto(game.getLoser()),
                toDtos(game.getFinishOrder()), game.isDraw());
    }

    private static GamePlayerDTO playerDto(DurakPlayer player) {
        return player == null ? null : ((DurakPlayerEntity) player).getGamePlayerDTO();
    }

    private static List<GamePlayerDTO> toDtos(List<? extends DurakPlayer> players) {
        var res = new ArrayList<GamePlayerDTO>();
        for (var player : players) res.add(playerDto(player));
        return res;
    }
}
