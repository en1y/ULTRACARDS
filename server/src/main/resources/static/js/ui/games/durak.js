/* Durak live game.
 *
 * Durak is not a trick-taking game, so it does not go through live-game.js: the table
 * holds attack/defense pairs rather than one trick, several players may act in the same
 * bout, and every action carries the state revision the client based it on. Card
 * rendering, dragging and motion still come from the shared game.js toolkit.
 */
(() => {
    const ui = window.UltracardsGameUi;
    const gameEl = document.getElementById('game-container');
    if (!gameEl || !gameEl.dataset.gameId || gameEl.dataset.gameType !== 'durak') return;

    const gameId = gameEl.dataset.gameId;
    const currentUserId = gameEl.dataset.currentUserId ? String(gameEl.dataset.currentUserId) : null;
    const currentUsername = gameEl.dataset.username || '';
    const wsUrl = `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws`;
    const mobileQuery = window.matchMedia('(max-width: 900px)');
    const TURN_WARNING_MS = 7000;   // when the board starts nudging the player
    const PENDING_TIMEOUT_MS = 2600; // how long a played card may wait for the server
    const SLOT = '\u0000';           // sentinel for splitting a translated sentence
    const STATE_BUBBLE_MS = 1500;    // how long the board waits on a "Done"/"Taking" bubble

    const dom = {
        layout: document.querySelector('.game-layout'),
        connectionToast: document.getElementById('connection-toast'),
        tableArea: document.querySelector('.durak-table-area'),
        ring: document.getElementById('player-ring'),
        slots: document.getElementById('trick-area'),
        dropZone: document.getElementById('drop-zone'),
        tableSurface: document.querySelector('.table-surface'),
        turnOverlay: document.getElementById('table-turn-overlay'),
        turnMessage: document.getElementById('table-turn-message'),
        deckStack: document.getElementById('deck-stack'),
        deckTower: document.getElementById('deck-tower'),
        deckLeft: document.getElementById('deck-left'),
        trumpCard: document.getElementById('trump-card'),
        hand: document.getElementById('hand-cards'),
        summary: document.getElementById('player-summary'),
        summaryAvatar: document.getElementById('player-summary-avatar'),
        summaryName: document.getElementById('player-summary-name'),
        summaryPoints: document.getElementById('player-summary-points'),
        sortSuit: document.getElementById('hand-sort-suit'),
        sortRank: document.getElementById('hand-sort-rank'),
        previous: document.getElementById('prev-round-back'),
        current: document.getElementById('prev-round-forward'),
        actions: document.getElementById('durak-actions'),
        action: document.getElementById('durak-action'),
        hint: document.getElementById('durak-hint')
    };

    const state = {
        game: window.__INITIAL_GAME__ || null,
        hand: window.__INITIAL_HAND__ || [],
        legal: null,
        handSort: 'suit',
        handEls: new Map(),
        handZone: null,
        pending: new Set(),
        selectedKey: null,
        handSignature: '',
        draggingEl: null,
        dragSession: null,
        dealingKeys: new Set(),
        wsClient: null,
        wsConnected: false,
        hasConnected: false,
        wsReconnectTimer: null,
        turnTimer: null,
        hintTimer: null,
        turnDurationMs: 30000,
        endRedirect: null,
        finished: false,
        previousBout: null,
        replayingPrevious: false,
        rotateEl: null,
        pendingFlights: new Map(),
        pendingTimers: new Map(),
        announcements: new Map(),
        pendingStates: [],
        pendingHands: [],
        holdUntil: 0,
        holdTimer: null,
        pendingHandRemovals: new Set(),
        tableConfirmedRemovals: new Set(),
        hasRenderedSlots: false,
        targetRects: null,
        hoveredTarget: '',
        clearing: false,
        previousDeckCount: null
    };

    /* ---------------------------------- cards ---------------------------------- */

    const cardKey = (card) => (card ? `${card.cardType || 'POKER'}:${card.card || ''}` : '');
    const cardCode = (card) => String(card?.card || '');
    const isJoker = (code) => code === 'JR' || code === 'JB';
    const cardSuit = (code) => (isJoker(code) ? code : String(code).charAt(0));
    const cardRank = (code) => (isJoker(code) ? 15 : Number(String(code).slice(1)) || 0);
    const isRed = (code) => (isJoker(code) ? code === 'JR' : cardSuit(code) === 'H' || cardSuit(code) === 'D');

    /** Mirrors DurakGame.canBeat so illegal cards look illegal before the server answers. */
    function canBeat(attackCode, defenseCode) {
        if (isJoker(attackCode)) return false;                     // nothing beats a Joker
        if (isJoker(defenseCode)) return isRed(defenseCode) === isRed(attackCode);
        if (cardSuit(defenseCode) === cardSuit(attackCode)) return cardRank(defenseCode) > cardRank(attackCode);
        return cardSuit(defenseCode) === trumpSuitLetter();
    }

    function trumpSuitLetter() {
        const suit = String(state.game?.trumpSuit || '');
        return suit ? suit.charAt(0) : '';
    }

    const PreviousBoutStore = {
        key: `uc-prev-durak-bout-${gameId}`,
        save(value) {
            try { localStorage.setItem(this.key, JSON.stringify(value)); } catch (_) {}
        },
        load() {
            try { return JSON.parse(localStorage.getItem(this.key)); } catch (_) { return null; }
        },
        remove() {
            try { localStorage.removeItem(this.key); } catch (_) {}
        }
    };

    /* --------------------------------- players --------------------------------- */

    const playerKey = (player) => (player?.id != null ? `id:${player.id}` : `name:${player?.name || ''}`);
    const samePlayer = (a, b) => !!a && !!b && playerKey(a) === playerKey(b);
    const isSelf = (player) => player?.id != null && String(player.id) === currentUserId;

    function playerList() {
        const order = Array.isArray(state.game?.playersOrder) ? state.game.playersOrder : [];
        const counts = new Map();
        Object.entries(state.game?.playersCardsMap || {}).forEach(([key, value]) => {
            // Player map keys arrive as the serialized GamePlayerDTO; match on the id inside.
            const id = /id=(\d+)/.exec(key)?.[1] ?? /"id"\s*:\s*(\d+)/.exec(key)?.[1];
            if (id != null) counts.set(String(id), Number(value) || 0);
        });
        // Rotate so the current user always sits at the bottom seat.
        const selfIndex = order.findIndex(isSelf);
        const rotated = selfIndex > 0 ? [...order.slice(selfIndex), ...order.slice(0, selfIndex)] : order.slice();
        return rotated.map((player) => ({
            ...player,
            cards: counts.get(String(player.id)) ?? 0
        }));
    }

    function playerRole(player) {
        const game = state.game;
        if (!game) return '';
        // While a finished bout is on screen, the roles shown are that bout's roles —
        // the live defender may well be somebody else by now.
        if (state.replayingPrevious) {
            return samePlayer(state.previousBout?.defender, player) ? 'defender' : '';
        }
        if ((game.finishedPlayers || []).some((p) => samePlayer(p, player))) return 'finished';
        if (samePlayer(game.defender, player)) return 'defender';
        if (samePlayer(game.leadAttacker, player)) return 'attacker';
        if ((game.doneThrowers || []).some((p) => samePlayer(p, player))) return 'done';
        if ((game.eligibleThrowers || []).some((p) => samePlayer(p, player))) return 'thrower';
        return '';
    }

    const ROLE_LABELS = {
        finished: 'durak.role.finished',
        defender: 'durak.role.defender',
        attacker: 'durak.role.attacker',
        done: 'durak.role.done',
        thrower: 'durak.role.thrower'
    };

    /* ------------------------------ legal actions ------------------------------ */

    function currentLegal() {
        const revision = Number(state.game?.stateRevision);
        if (!state.legal || Number(state.legal.stateRevision) !== revision) {
            state.legal = inferLegalActions();
        }
        return state.legal;
    }

    const allows = (type) => Array.isArray(currentLegal()?.allowedActionTypes)
        && currentLegal().allowedActionTypes.includes(type);
    const isActionPlayer = () => !state.replayingPrevious
        && !!state.game?.actionPlayer && isSelf(state.game.actionPlayer);
    const selfPlayer = () => playerList().find(isSelf);
    const selfIsDefender = () => samePlayer(state.game?.defender, selfPlayer());
    const defenderHasOpenAttack = () => state.game?.phase === 'WAITING_FOR_DEFENSE'
        && (state.game?.attackSlots || []).some((slot) => !slot.defenseCard);
    const defenderHandIsIdle = () => selfIsDefender() && !defenderHasOpenAttack();
    const canAct = () => !state.replayingPrevious && !state.clearing
        && Array.isArray(currentLegal()?.allowedActionTypes)
        && currentLegal().allowedActionTypes.length > 0;

    function nextActivePlayer(player) {
        const order = Array.isArray(state.game?.playersOrder) ? state.game.playersOrder : [];
        const finished = state.game?.finishedPlayers || [];
        const start = order.findIndex((candidate) => samePlayer(candidate, player));
        for (let offset = 1; offset <= order.length; offset++) {
            const candidate = order[(start + offset) % order.length];
            if (candidate && !finished.some((entry) => samePlayer(entry, candidate))) return candidate;
        }
        return player;
    }

    function playerCardCount(player) {
        return playerList().find((candidate) => samePlayer(candidate, player))?.cards ?? 0;
    }

    /** Frontend fallback for the initial page load, before the first advisory STOMP hint arrives. */
    function inferLegalActions() {
        const result = {
            stateRevision: Number(state.game?.stateRevision) || 0,
            allowedActionTypes: [],
            defendableSlotIds: [],
            throwableCardCodes: [],
            passableCardCodes: []
        };
        if (state.replayingPrevious || state.clearing) return result;

        const slots = state.game.attackSlots || [];
        const phase = state.game.phase;
        if (phase === 'WAITING_FOR_ATTACK' && isActionPlayer()) {
            result.allowedActionTypes.push('ATTACK');
            return result;
        }
        if (phase === 'WAITING_FOR_DEFENSE' && samePlayer(state.game.defender, playerList().find(isSelf))) {
            result.defendableSlotIds = slots
                .filter((slot) => !slot.defenseCard
                    && state.hand.some((card) => canBeat(cardCode(slot.attackCard), cardCode(card))))
                .map((slot) => slot.slotId);
            if (result.defendableSlotIds.length) result.allowedActionTypes.push('DEFEND');

            // Beating one card commits you to the whole bout, so passing is only on the
            // table while nothing has been covered yet. Mirrors DurakGame.canPass.
            if (state.game.passingEnabled && !slots.some((slot) => slot.defenseCard)) {
                const openRanks = new Set(slots.filter((slot) => !slot.defenseCard)
                    .map((slot) => cardRank(cardCode(slot.attackCard))));
                if (hasPassCapacity()) {
                    result.passableCardCodes = state.hand
                        .filter((card) => openRanks.has(cardRank(cardCode(card))))
                        .map(cardCode);
                }
                if (result.passableCardCodes.length) result.allowedActionTypes.push('PASS');
            }
            result.allowedActionTypes.push('TAKE');
        }
        const self = playerList().find(isSelf);
        const eligible = (state.game.eligibleThrowers || []).some((player) => samePlayer(player, self));
        const done = (state.game.doneThrowers || []).some((player) => samePlayer(player, self));
        if (eligible && !done && ['WAITING_FOR_DEFENSE', 'WAITING_FOR_THROW_IN', 'THROW_AFTER_TAKE'].includes(phase)) {
            const ranks = new Set(slots.flatMap((slot) => [slot.attackCard, slot.defenseCard])
                .filter(Boolean).map((card) => cardRank(cardCode(card))));
            if (slots.length < Number(state.game.maxAttackCards || 0)) {
                result.throwableCardCodes = state.hand
                    .filter((card) => ranks.has(cardRank(cardCode(card))))
                    .map(cardCode);
            }
            if (result.throwableCardCodes.length) result.allowedActionTypes.push('THROW_IN');
            if (phase !== 'WAITING_FOR_DEFENSE') result.allowedActionTypes.push('DONE');
        }
        return result;
    }

    function defendableSlots() {
        const ids = new Set(currentLegal()?.defendableSlotIds || []);
        return (state.game?.attackSlots || []).filter((slot) => !slot.defenseCard && ids.has(slot.slotId));
    }

    /** Slots this specific card could cover, used for drop targets and legality shading. */
    function slotsBeatableBy(code) {
        return defendableSlots().filter((slot) => canBeat(cardCode(slot.attackCard), code));
    }

    /**
     * An attacker has exactly one verb: attack. Opening a bout and throwing an extra
     * card onto an open one are the same gesture here, and the server accepts ATTACK
     * for both, so the UI never asks the user which of the two they meant.
     */
    function cardActions(code) {
        const actions = [];
        if (!canAct() || state.pending.size) return actions;
        if (allows('ATTACK')
            || (allows('THROW_IN') && (currentLegal()?.throwableCardCodes || []).includes(code))) {
            actions.push('ATTACK');
        }
        if (allows('PASS') && (currentLegal()?.passableCardCodes || []).includes(code)) actions.push('PASS');
        if (allows('DEFEND') && slotsBeatableBy(code).length) actions.push('DEFEND');
        return actions;
    }

    /* -------------------------------- messaging -------------------------------- */

    function sendAction(type, card, targetSlotId) {
        if (!state.wsConnected || !state.wsClient) {
            showHint(t('game.connectionLost'));
            return false;
        }
        const revision = Number(state.game?.stateRevision);
        if (!Number.isFinite(revision)) return false;
        const payload = {type, expectedRevision: revision};
        if (card) payload.card = {cardType: card.cardType || 'POKER', card: card.card};
        if (targetSlotId != null) payload.targetSlotId = targetSlotId;
        if (card) {
            const key = cardKey(card);
            const source = state.handEls.get(key);
            state.pendingHandRemovals.delete(key);
            state.tableConfirmedRemovals.delete(key);
            state.pendingFlights.set(key, {
                element: source,
                session: state.dragSession,
                sourceRect: source?.isConnected ? source.getBoundingClientRect() : null
            });
            state.pending.add(key);
            armPendingWatchdog(key);
        }
        state.selectedKey = null;
        renderHand();
        renderActions();
        try {
            state.wsClient.send('/app/game/durak/action', {}, JSON.stringify(payload));
            return true;
        } catch (error) {
            if (card) {
                restorePendingCards();
            }
            showHint(t('game.connectionLost'));
            return false;
        }
    }

    /**
     * A card released onto the table is parked in the overlay until the server's next
     * state consumes it. If that never arrives — the action was refused silently, the
     * turn ended underneath the player, the socket stalled — the card would hang in
     * the middle of the screen forever. Give every parked card a deadline: past it,
     * fly it home and re-sync with the server.
     */
    function armPendingWatchdog(key) {
        const timer = state.pendingTimers.get(key);
        if (timer) clearTimeout(timer);
        state.pendingTimers.set(key, setTimeout(() => {
            state.pendingTimers.delete(key);
            if (!state.pendingFlights.has(key)) return;
            restorePendingCards();
            resync();
        }, PENDING_TIMEOUT_MS));
    }

    function clearPendingWatchdog(key) {
        const timer = state.pendingTimers.get(key);
        if (!timer) return;
        clearTimeout(timer);
        state.pendingTimers.delete(key);
    }

    /** A rejected send or broken socket must never strand a real card in the overlay. */
    function restorePendingCards() {
        state.pendingTimers.forEach((timer) => clearTimeout(timer));
        state.pendingTimers.clear();
        const sessions = new Set();
        state.pendingFlights.forEach((pending) => {
            if (pending?.session) sessions.add(pending.session);
        });
        if (state.dragSession) sessions.add(state.dragSession);
        sessions.forEach((session) => ui.cancelDragCard(session));
        state.pending.clear();
        state.pendingFlights.clear();
        state.pendingHandRemovals.clear();
        state.tableConfirmedRemovals.clear();
        state.dragSession = null;
        state.draggingEl = null;
        state.handSignature = '';
        renderHand();
        renderActions();
        highlightSlots();
    }

    function showHint(message) {
        if (!dom.hint) return;
        if (state.hintTimer) clearTimeout(state.hintTimer);
        dom.hint.textContent = message || '';
        dom.hint.classList.toggle('is-visible', !!message);
        if (!message) return;
        state.hintTimer = setTimeout(() => {
            dom.hint.classList.remove('is-visible');
            state.hintTimer = null;
        }, 2600);
    }

    /* -------------------------------- rendering -------------------------------- */

    function render() {
        renderSeats();
        renderSlots();
        renderDeck();
        renderHand();
        renderActions();
        renderTurn();
        refreshPreviousBoutControls();
    }

    /**
     * Seats sit on the upper arc of the felt, spread evenly from left to right; the
     * current user always occupies the bottom slot next to their own hand. The arc
     * stays above the deck (right edge, lower half) and outside the pair grid.
     */
    function seatSlot(index, count) {
        if (index === 0) return {x: 50, y: 112, side: 'bottom'};
        const others = Math.max(1, count - 1);
        const ratio = others === 1 ? 0.5 : (index - 1) / (others - 1);
        const side = ratio < 0.34 ? 'left' : ratio > 0.66 ? 'right' : 'top';
        // Four or five opponents no longer fit on an arc without overlapping, so
        // they share one even row along the top edge instead.
        if (others >= 4) {
            return {x: 10 + ratio * 80, y: 23 - Math.sin(Math.PI * ratio) * 9, side};
        }
        const angle = (Math.PI / 180) * (155 - ratio * 130);
        return {
            x: 50 + Math.cos(angle) * 36,
            y: 33 - Math.sin(angle) * 22,
            side
        };
    }

    function renderSeats() {
        if (!dom.ring) return;
        const players = playerList();
        dom.layout?.classList.toggle('is-two-player', players.length === 2);
        dom.layout?.classList.toggle('is-dense-player-ring', players.length >= 5);
        dom.layout?.style.setProperty('--durak-seat-count', String(players.length));

        const existing = new Map();
        Array.from(dom.ring.children).forEach((seat) => existing.set(seat.dataset.playerKey, seat));

        players.forEach((player, index) => {
            const key = playerKey(player);
            let seat = existing.get(key);
            if (!seat) {
                seat = document.createElement('div');
                seat.className = 'player-seat';
                seat.dataset.playerKey = key;
                seat.innerHTML = '<div class="seat-avatar"></div><div class="seat-name"></div>'
                    + '<div class="seat-team-badge" hidden></div><div class="seat-cards"></div>';
                dom.ring.appendChild(seat);
            }
            existing.delete(key);

            const role = playerRole(player);
            const self = isSelf(player);
            if (player.id != null) seat.dataset.playerId = String(player.id);
            seat.dataset.playerName = player.name || '';
            seat.dataset.role = role;
            seat.classList.toggle('is-self', self);
            seat.classList.toggle('is-turn', samePlayer(state.game?.actionPlayer, player));
            if (self) seat.dataset.isSelf = '1';
            else delete seat.dataset.isSelf;

            seat.querySelector('.seat-avatar').textContent = (player.name || 'P').charAt(0).toUpperCase();
            seat.querySelector('.seat-name').textContent = player.name || t('common.player');
            // Only the defender is labelled: everyone else at the table is an attacker
            // in some form, so naming them all just crowds the felt.
            const badge = seat.querySelector('.seat-team-badge');
            badge.textContent = role === 'defender' ? t(ROLE_LABELS.defender) : '';
            badge.hidden = role !== 'defender';

            const slot = seatSlot(index, players.length);
            seat.dataset.seatSide = slot.side;
            seat.style.left = `${slot.x}%`;
            seat.style.top = `${slot.y}%`;
            seat.style.transform = 'translate(-50%, -50%)';
            renderSeatCards(seat.querySelector('.seat-cards'), player.cards, !self);
        });

        existing.forEach((seat) => seat.remove());
        renderSelfSummary(players.find(isSelf));
        renderAnnouncements();
    }

    function renderSeatCards(container, count, animateDraws) {
        if (!container) return;
        const target = Math.max(0, Number(count) || 0);
        const added = [];
        while (container.children.length > target) container.lastElementChild.remove();
        while (container.children.length < target) {
            const card = ui.renderCardImage({cardType: 'POKER', className: 'seat-card', alt: t('game.cardBack.alt')});
            container.appendChild(card);
            added.push(card);
        }
        const middle = (target - 1) / 2;
        const available = Math.max(container.clientWidth || (mobileQuery.matches ? 64 : 96), 32);
        const cardWidth = container.firstElementChild?.offsetWidth || (mobileQuery.matches ? 24 : 32);
        const visualCardWidth = cardWidth * (mobileQuery.matches ? 1.85 : 1.45);
        const step = target > 1 ? Math.max(1, Math.min(mobileQuery.matches ? 5 : 8,
            (available - visualCardWidth) / (target - 1))) : 0;
        Array.from(container.children).forEach((card, index) => {
            const offset = index - middle;
            card.style.setProperty('--seat-fan-index', offset.toFixed(1));
            card.style.setProperty('--seat-fan-distance', Math.abs(offset).toFixed(1));
            card.style.setProperty('--seat-fan-x', `${(offset * step).toFixed(2)}px`);
        });
        const deckWasDrawn = state.previousDeckCount != null
            && Number(state.game?.cardsLeftInDeck) < state.previousDeckCount;
        const deckRect = deckWasDrawn ? dom.deckTower?.getBoundingClientRect() : null;
        if (animateDraws && deckRect?.width) {
            added.forEach((card, index) => {
                card.style.opacity = '0';
                ui.animateCardBetweenZones({
                    cardType: 'POKER',
                    sourceRect: deckRect,
                    toEl: card,
                    duration: 390,
                    delay: index * 45,
                    fromRot: '-8deg',
                    toRot: '0deg',
                    easing: 'out(3)'
                }).finally(() => {
                    card.style.opacity = '';
                });
            });
        }
        container.dataset.cardCount = String(Number(count) || 0);
    }

    function renderSelfSummary(self) {
        if (!dom.summary) return;
        dom.summary.style.visibility = 'visible';
        if (dom.summaryAvatar) dom.summaryAvatar.textContent = (currentUsername || 'P').charAt(0).toUpperCase();
        if (dom.summaryName) dom.summaryName.textContent = currentUsername;
        if (dom.summaryPoints) {
            dom.summaryPoints.textContent = '';
            dom.summaryPoints.hidden = true;
        }
    }

    function renderSlots() {
        if (!dom.slots) return;
        const slots = state.replayingPrevious ? state.previousBout?.attackSlots || [] : state.game?.attackSlots || [];
        const showRotate = showsRotate();
        const rotateReady = showRotate && canRotate();
        const signature = slots.map((slot) => `${slot.slotId}:${cardCode(slot.attackCard)}>${cardCode(slot.defenseCard)}`).join('|')
            + `#${state.replayingPrevious ? 'previous' : defendableSlots().map((slot) => slot.slotId).join(',')}`
            + `@${showRotate ? 'rotate' : ''}${rotateReady ? '!' : ''}`;
        if (dom.slots.dataset.signature === signature) return;
        dom.slots.dataset.signature = signature;

        const existingCards = new Map(Array.from(dom.slots.querySelectorAll('[data-card-key]'))
            .map((card) => [card.dataset.cardKey, card]));
        // Where every card sits right now. The grid reflows whenever a pair is covered
        // or the rotate target leaves, and cards slide to their new place from here
        // instead of jumping.
        const previousRects = new Map(Array.from(existingCards, ([key, card]) =>
            [key, card.getBoundingClientRect()]));
        const existingSlots = new Map(Array.from(dom.slots.querySelectorAll('.durak-slot:not(.durak-rotate-slot)'))
            .map((slot) => [slot.dataset.slotId, slot]));
        const previousCards = new Set(existingCards.keys());
        const nextContent = document.createDocumentFragment();
        const cardsToAnimate = [];
        dom.slots.classList.toggle('is-empty', !slots.length && !showRotate);
        dom.slots.classList.toggle('is-previous-round-replay', state.replayingPrevious);
        slots.forEach((slot) => {
            const slotId = String(slot.slotId);
            const el = existingSlots.get(slotId) || document.createElement('div');
            el.className = 'durak-slot';
            el.dataset.slotId = slotId;
            const attackKey = cardKey(slot.attackCard);
            const attackPending = takePendingTableCard(attackKey, 'durak-attack-card');
            const attack = existingCards.get(attackKey) || attackPending.element || ui.renderCardImage({
                    card: slot.attackCard, className: 'durak-attack-card', alt: cardCode(slot.attackCard)
                });
            attack.classList.add('durak-attack-card');
            existingCards.delete(attackKey);
            let defense = null;
            let defensePending = null;
            if (slot.defenseCard) {
                el.classList.add('is-covered');
                const defenseKey = cardKey(slot.defenseCard);
                defensePending = takePendingTableCard(defenseKey, 'durak-defense-card');
                defense = existingCards.get(defenseKey) || defensePending.element || ui.renderCardImage({
                        card: slot.defenseCard, className: 'durak-defense-card', alt: cardCode(slot.defenseCard)
                    });
                defense.classList.add('durak-defense-card');
                existingCards.delete(defenseKey);
            } else {
                el.classList.remove('is-covered');
            }
            el.replaceChildren(...[attack, defense, attackerTag(slot)].filter(Boolean));
            nextContent.appendChild(el);
            cardsToAnimate.push([attack, slot.attackCard, slot.attacker, -7, attackPending]);
            if (defense) {
                cardsToAnimate.push([
                    defense, slot.defenseCard, slot.defender || state.game?.defender, 8, defensePending
                ]);
            }
            if (!state.replayingPrevious) registerSlotTarget(el, slot.slotId);
        });
        if (showRotate) nextContent.appendChild(rotateSlot(rotateReady));
        existingCards.forEach((card) => ui.cancelAnimations(card));
        dom.slots.replaceChildren(nextContent);
        cardsToAnimate.forEach(([element, card, player, spin, pending]) =>
            animateTableCard(element, card, player, previousCards, spin, pending));
        slideMovedCards(previousRects, previousCards);
        state.hasRenderedSlots = true;
        forgetTargetRects();
        if (!state.replayingPrevious) highlightSlots();
    }

    /**
     * The rotate target remains on a defender's table while another attack can fit
     * in the next defender's hand. It is only interactive while a pass is legal.
     * Games created without passing never show it: there is nothing to rotate.
     */
    const showsRotate = () => !state.replayingPrevious
        && !!state.game?.passingEnabled
        && selfIsDefender()
        && !!(state.game?.attackSlots || []).length
        && hasPassCapacity()
        // Beating one card ends passing for the rest of the bout, so the target
        // leaves the table at that moment rather than lingering greyed out.
        && !(state.game?.attackSlots || []).some((slot) => slot.defenseCard);

    const canRotate = () => showsRotate() && !state.pending.size && canAct() && allows('PASS');

    /** Mirrors DurakGame.canPass: the passed card must fit in the next defender's hand. */
    function hasPassCapacity() {
        const defender = state.game?.defender;
        const next = nextActivePlayer(defender);
        return !!defender && !samePlayer(next, defender)
            && (state.game?.attackSlots || []).length + 1 <= Math.min(6, playerCardCount(next));
    }

    /**
     * Passing is offered as one more card on the table: a card-shaped, card-sized
     * target that sits in the row next to the attacks. Covering it the same way an
     * attack is covered sends PASS, so there is no separate "rotate" gesture and no
     * floating badge that can land on top of a real card.
     */
    function rotateSlot(ready) {
        let el = state.rotateEl;
        if (!el) {
            el = document.createElement('div');
            el.className = 'durak-slot durak-rotate-slot';
            el.dataset.slotId = 'rotate';
            el.setAttribute('aria-label', t('durak.rotate.aria'));
            const icon = document.createElement('img');
            icon.className = 'uc-icon';
            // No data-icon: the felt is dark green in both themes, so this icon must
            // stay the light one instead of following the page theme.
            icon.src = '/pics/light/refresh.svg';
            icon.alt = '';
            icon.setAttribute('aria-hidden', 'true');
            el.appendChild(icon);
            el.onclick = (event) => {
                event.stopPropagation();
                playOnRotate(selectedCard());
            };
            state.rotateEl = el;
        }
        el.classList.toggle('is-ready', !!ready);
        el.setAttribute('aria-disabled', String(!ready));
        return el;
    }

    /** A card that only changed place in the grid slides there; new cards fly in instead. */
    function slideMovedCards(previousRects, previousCards) {
        if (state.replayingPrevious) return;
        dom.slots.querySelectorAll('[data-card-key]').forEach((card) => {
            const key = card.dataset.cardKey;
            const from = previousRects.get(key);
            if (!from?.width || !previousCards.has(key)) return;
            const to = card.getBoundingClientRect();
            if (Math.abs(from.left - to.left) < 1 && Math.abs(from.top - to.top) < 1) return;
            ui.flyIntoSlot(card, from, {duration: 240, spin: 0, fromScale: 1, ease: 'power2.out'});
        });
    }

    /* ------------------------------ state bubbles ------------------------------ */

    /**
     * "Done" and "Taking" are decisions with no card to show for them, so without a
     * bubble over the player who made them the table just silently changes hands.
     */
    /**
     * Rebuilt from the state every time, never accumulated: an attacker who passed
     * and was then re-opened by somebody else's card is no longer done, and their
     * bubble has to go with it.
     */
    function announceStateChanges(next) {
        const before = new Set(state.announcements.keys());
        state.announcements.clear();
        if (next) {
            (next.doneThrowers || []).forEach((player) => announce(player, 'done'));
            if (next.takeDeclared) announce(next.defender, 'take');
        }
        // Something new to read: hold the board still long enough to read it.
        const fresh = [...state.announcements.keys()].some((key) => !before.has(key));
        if (fresh) state.holdUntil = Date.now() + STATE_BUBBLE_MS;
    }

    function announce(player, kind) {
        if (!player) return;
        state.announcements.set(playerKey(player), kind);
    }

    const ANNOUNCEMENT_LABELS = {done: 'durak.action.done', take: 'durak.state.taking'};

    function renderAnnouncements() {
        const hosts = new Map();
        dom.ring?.querySelectorAll('.player-seat').forEach((seat) => hosts.set(seat.dataset.playerKey, seat));
        const self = selfPlayer();
        // The player's own seat is hidden, so their bubble hangs off their tab instead.
        if (self && dom.summary) hosts.set(playerKey(self), dom.summary);
        // A recalled bout keeps its verdict on screen for as long as it is being looked at.
        const source = state.replayingPrevious
            ? new Map(state.previousBout?.takeDeclared && state.previousBout.defender
                ? [[playerKey(state.previousBout.defender), 'take']] : [])
            : state.announcements;
        hosts.forEach((host, key) => paintBubble(host, source.get(key) || null));
    }

    function paintBubble(host, kind) {
        if (!host) return;
        let bubble = host.querySelector(':scope > .durak-state-bubble');
        if (!kind) {
            bubble?.remove();
            return;
        }
        if (!bubble) {
            bubble = document.createElement('div');
            bubble.className = 'durak-state-bubble';
            host.appendChild(bubble);
        }
        // Hangs off the seat rather than sitting in it: a flow element pushed the
        // avatar and name up every time somebody said Done. It lands where the card
        // fan starts, covering the top of it.
        const cards = host.querySelector(':scope > .seat-cards');
        if (cards) bubble.style.setProperty('--bubble-top', `${cards.offsetTop}px`);
        bubble.dataset.kind = kind;
        bubble.textContent = t(ANNOUNCEMENT_LABELS[kind]);
    }

    /** Who played the pair. Only shown while recalling a finished bout. */
    function attackerTag(slot) {
        if (!state.replayingPrevious || !slot.attacker?.name) return null;
        const tag = document.createElement('span');
        tag.className = 'durak-slot-attacker';
        tag.textContent = slot.attacker.name;
        return tag;
    }

    function takePendingTableCard(key, tableClass) {
        const pending = state.pendingFlights.get(key);
        if (!pending) return {element: null, sourceRect: null};
        clearPendingWatchdog(key);
        state.pendingFlights.delete(key);
        state.pending.delete(key);
        if (!state.pendingHandRemovals.delete(key)) state.tableConfirmedRemovals.add(key);
        const element = pending.element?.isConnected ? pending.element : null;
        const sourceRect = element?.getBoundingClientRect() || pending.sourceRect;
        if (!element) return {element: null, sourceRect};
        if (pending.session) pending.session.cancelled = true;
        ui.cancelAnimations(element);
        // The node arrives straight out of the hand fan / drag overlay. Everything the
        // hand layout wrote on it has to go, or the card keeps its fan slot transform,
        // its fan z-index (which would put an attack card on top of its defense) and
        // its drag sizing once it is parked in a table slot.
        element.classList.remove(
            'hand-card', 'drag-ghost', 'is-selected', 'is-raised', 'is-playable', 'is-illegal',
            'is-disabled', 'game-hand-card', 'is-dealing', 'is-flying', 'is-flipping', 'is-rejected'
        );
        element.classList.add(tableClass);
        element.removeAttribute('aria-disabled');
        element.onclick = null;
        ['transform', 'opacity', 'width', 'height', 'z-index',
            '--slot-x', '--slot-y', '--slot-rot', '--slot-scale', '--tilt', '--lift',
            '--press-y', '--hover-scale', '--deal-x', '--deal-y', '--deal-rot', '--deal-scale']
            .forEach((property) => element.style.removeProperty(property));
        state.handEls.delete(key);
        return {element, sourceRect};
    }

    function animateTableCard(element, card, player, previousCards, spin, pending) {
        const key = cardKey(card);
        if (!state.hasRenderedSlots || state.replayingPrevious || previousCards.has(key)) return;
        if (pending?.sourceRect) {
            ui.flyIntoSlot(element, pending.sourceRect, {duration: 300, spin, ease: 'power3.out'});
            return;
        }
        const seat = Array.from(dom.ring?.querySelectorAll('.player-seat') || [])
            .find((candidate) => candidate.dataset.playerKey === playerKey(player));
        const from = seat?.querySelector('.seat-card:last-child, .seat-avatar')?.getBoundingClientRect();
        if (from?.width) ui.flyIntoSlot(element, from, {duration: 420, spin, ease: 'back.out(1.25)'});
    }

    function rememberCompletedBout(game) {
        const slots = (game?.attackSlots || []).map((slot) => ({
            ...slot,
            attackCard: slot.attackCard ? {...slot.attackCard} : null,
            defenseCard: slot.defenseCard ? {...slot.defenseCard} : null
        }));
        if (!slots.length) return;
        // The outcome and who defended are what make a recalled bout readable — without
        // them it is a row of cards with no story.
        state.previousBout = {
            boutNumber: game.boutNumber,
            attackSlots: slots,
            defender: game.defender ? {...game.defender} : null,
            takeDeclared: !!game.takeDeclared
        };
        PreviousBoutStore.save(state.previousBout);
    }

    function refreshPreviousBoutControls() {
        if (dom.previous) dom.previous.disabled = !state.previousBout || state.replayingPrevious;
        if (dom.current) dom.current.disabled = !state.replayingPrevious;
    }

    function showPreviousBout(showPrevious) {
        if (showPrevious && !state.previousBout) return;
        state.replayingPrevious = showPrevious;
        state.legal = null;
        state.handSignature = '';
        renderSeats();
        renderSlots();
        renderHand();
        renderActions();
        renderTurn();
        renderAnnouncements();
        refreshPreviousBoutControls();
    }

    /** Marks the slots the currently selected card could cover. Nothing else is outlined. */
    function highlightSlots() {
        const code = state.selectedKey ? state.selectedKey.split(':')[1] : '';
        const targetIds = new Set(code ? slotsBeatableBy(code).map((slot) => slot.slotId) : []);
        dom.slots?.querySelectorAll('.durak-slot').forEach((el) => {
            el.classList.toggle('is-target', targetIds.has(Number(el.dataset.slotId)));
        });
    }

    function renderDeck() {
        const left = Number(state.game?.cardsLeftInDeck) || 0;
        if (dom.deckLeft) dom.deckLeft.textContent = String(left);
        const featured = state.game?.trumpIndicator || null;
        const marker = featured || (trumpSuitLetter() ? {cardType: 'POKER', card: trumpSuitLetter()} : null);
        if (featured && dom.trumpCard?.dataset.cardKey !== cardKey(featured)) {
            const card = ui.renderCardImage({card: featured, className: 'trump-card', alt: cardCode(featured)});
            card.id = 'trump-card';
            dom.trumpCard?.replaceWith(card);
            dom.trumpCard = card;
        }
        ui.renderDeckTower(dom.deckTower, dom.deckStack, left, {
            cardType: 'POKER',
            className: 'deck-card',
            featuredCard: featured
        });
        if (left <= 0 && marker) ui.revealSuit(dom.trumpCard, marker);
        else if (featured) ui.revealCardFace(dom.trumpCard, featured);
        if (dom.trumpCard) dom.trumpCard.hidden = !marker;
        dom.deckStack?.classList.toggle('has-trump', !!marker);
    }

    function suitOrder(code) {
        if (isJoker(code)) return 5;
        const trump = trumpSuitLetter();
        const base = ({H: 0, D: 1, C: 2, S: 3})[cardSuit(code)] ?? 4;
        return cardSuit(code) === trump ? 10 + base : base;   // trumps last, grouped
    }

    function compareHandCards(left, right) {
        const a = cardCode(left);
        const b = cardCode(right);
        return state.handSort === 'rank'
            ? cardRank(a) - cardRank(b) || suitOrder(a) - suitOrder(b)
            : suitOrder(a) - suitOrder(b) || cardRank(a) - cardRank(b);
    }

    function renderHand() {
        if (!dom.hand) return;
        const visible = state.hand
            .filter((card) => !state.pending.has(cardKey(card))
                && !state.tableConfirmedRemovals.has(cardKey(card)))
            .sort(compareHandCards);
        const signature = visible
            .map(cardKey)
            .join(',') + `|${state.selectedKey}|${state.handSort}|${state.draggingEl ? 'drag' : ''}`;
        if (signature === state.handSignature) return;
        state.handSignature = signature;

        const nextKeys = new Set(visible.map(cardKey));
        const handInactive = defenderHandIsIdle();
        const ordered = visible.map((card) => {
            const key = cardKey(card);
            const interactionLocked = handInactive || state.dealingKeys.has(key);
            let el = state.handEls.get(key);
            if (!el) {
                const dealing = state.dealingKeys.has(key);
                el = ui.createCard({card, className: 'hand-card', alt: cardCode(card), flippable: dealing});
                if (dealing) {
                    el.classList.add('is-dealing');
                    el.cardApi?.showBack();
                }
                state.handEls.set(key, el);
            }
            el.classList.remove('is-playable', 'is-illegal', 'is-disabled', 'is-raised');
            el.classList.toggle('is-selected', key === state.selectedKey);
            el.classList.toggle('is-disabled', interactionLocked);
            el.setAttribute('aria-disabled', String(interactionLocked));
            el.onclick = interactionLocked ? null : () => selectCard(card);
            el.ondblclick = null;
            return el;
        });
        const forLayout = state.draggingEl ? ordered.filter((el) => el !== state.draggingEl) : ordered;

        ui.animateZoneChange(state.handZone, () => {
            state.handEls.forEach((el, key) => {
                if (!nextKeys.has(key) && el !== state.draggingEl) {
                    if (state.pending.has(key)) return;
                    el.remove();
                    state.handEls.delete(key);
                }
            });
            forLayout.forEach((el) => dom.hand.appendChild(el));
        }, {cards: forLayout, layout: handLayoutParams()});
    }

    function handLayoutParams() {
        return mobileQuery.matches
            ? {type: 'hand', spacingScale: 0.5, maxTilt: 12, yArc: 16, fitWithinZone: true}
            : {type: 'hand', spacingScale: 0.62, maxTilt: 8, yArc: 10, fitWithinZone: true};
    }

    /**
     * One button, because a player is never both. A defender can only take, an
     * attacker can only declare themselves done; the button is whichever of the two
     * the current role owns and is disabled until that action is actually legal.
     */
    function renderActions() {
        const defending = selfIsDefender();
        const allowed = canAct() && !state.pending.size && allows(defending ? 'TAKE' : 'DONE');
        if (dom.actions) dom.actions.hidden = false;
        if (dom.action) {
            dom.action.dataset.mode = defending ? 'take' : 'done';
            dom.action.textContent = t(defending ? 'durak.action.take' : 'durak.action.done');
            dom.action.disabled = !allowed;
        }
    }

    /**
     * The status pill is permanent, and it names whoever the table is waiting on:
     * the opening attacker until the first card is down, the defender after that.
     */
    function renderTurn() {
        if (dom.dropZone && !dom.dropZone.classList.contains('is-result')) {
            dom.dropZone.textContent = '';
            dom.dropZone.classList.toggle('ready', canAct() && (allows('ATTACK') || allows('THROW_IN')));
            dom.dropZone.classList.toggle('is-covered', !!(state.game?.attackSlots || []).length);
        }
        renderStatusPill();
        updateTurnTimer();
    }

    /**
     * The pill is the one place that says who is defending, now that the tab under the
     * hand no longer carries a role badge. The defender's name is picked out inside it,
     * and reads "You" when it is the player looking at the screen.
     */
    function renderStatusPill() {
        if (!dom.turnMessage) return;
        const game = state.game;
        const bout = state.replayingPrevious ? state.previousBout : game;
        const defender = bout?.defender;
        const attacking = !state.replayingPrevious && !(game?.attackSlots || []).length;
        const subject = attacking ? (game?.actionPlayer || game?.leadAttacker) : defender;
        const visible = !!game && !state.finished && !!subject;
        dom.turnOverlay?.classList.toggle('is-visible', visible);
        if (!visible) return;

        const name = document.createElement('strong');
        name.className = 'durak-status-name';
        name.dataset.role = attacking ? 'attacker' : 'defender';
        // Addressed to you, the whole line is the highlight — "You defends" is not a
        // sentence, so the self case gets its own wording rather than a substitution.
        if (isSelf(subject)) {
            name.classList.add('is-self');
            name.textContent = t(attacking ? 'durak.status.youAttack' : 'durak.status.youDefend');
            dom.turnMessage.replaceChildren(name);
            return;
        }
        name.textContent = subject.name || t('common.player');
        // The label is a translated sentence with the name inside it. Substitute a
        // sentinel and split on that, so word order stays the translator's business.
        const [before, after] = t(attacking ? 'durak.status.attacks' : 'durak.status.defends', SLOT)
            .split(SLOT);
        dom.turnMessage.replaceChildren(document.createTextNode(before || ''), name,
            document.createTextNode(after || ''));
    }

    /**
     * Whoever the clock is actually running against. A throw window belongs to every
     * attacker at once, so they all count down together instead of being handed the
     * timer one seat at a time.
     */
    function playersOnTheClock() {
        const game = state.game;
        if (!game || state.replayingPrevious) return [];
        if (['WAITING_FOR_THROW_IN', 'THROW_AFTER_TAKE'].includes(game.phase)) {
            const done = new Set((game.doneThrowers || []).map(playerKey));
            const open = (game.eligibleThrowers || []).filter((player) => !done.has(playerKey(player)));
            if (open.length) return open;
        }
        return game.actionPlayer ? [game.actionPlayer] : [];
    }

    function updateTurnTimer() {
        if (state.turnTimer) {
            clearTimeout(state.turnTimer);
            state.turnTimer = null;
        }
        dom.layout?.classList.remove('is-turn-running-out');
        const endsAt = state.game?.turnEndTime ? Date.parse(state.game.turnEndTime) : NaN;
        if (Number(state.game?.turnDurationSeconds) > 0) {
            state.turnDurationMs = Number(state.game.turnDurationSeconds) * 1000;
        }
        const waiting = new Set(playersOnTheClock().map(playerKey));
        const targets = [];
        dom.ring?.querySelectorAll('.player-seat').forEach((seat) => {
            const active = !state.finished && Number.isFinite(endsAt) && waiting.has(seat.dataset.playerKey);
            seat.classList.toggle('has-turn-indicator', active);
            if (!active) {
                seat.classList.remove('is-turn-warning');
                seat.querySelector('.seat-avatar')?.style.setProperty('--turn-progress', '0');
            } else {
                targets.push(seat);
            }
        });
        // The current user's table seat is hidden, so mirror the countdown onto the
        // summary avatar sitting under their hand.
        const self = selfPlayer();
        const selfActive = !state.finished && Number.isFinite(endsAt) && !!self && waiting.has(playerKey(self));
        dom.summary?.classList.toggle('has-turn-indicator', selfActive);
        if (selfActive && dom.summary) targets.push(dom.summary);
        if (!targets.length) return;

        // The ring is a masked conic gradient: every write re-rasters it, on every
        // avatar still on the clock — and in Durak that can be the whole table at once.
        // Writing only when the visible arc actually moves (1% steps) cuts that by an
        // order of magnitude; nobody can see a finer step.
        let painted = -1;
        const frame = () => {
            const remaining = Math.max(0, endsAt - Date.now());
            const progress = Math.max(0, Math.min(1, remaining / Math.max(state.turnDurationMs, 1)));
            const step = Math.round(progress * 100);
            const warning = remaining <= TURN_WARNING_MS;
            if (step !== painted) {
                painted = step;
                targets.forEach((el) => {
                    el.querySelector('.seat-avatar')?.style.setProperty('--turn-progress', (step / 100).toFixed(2));
                    el.classList.toggle('is-turn-warning', warning);
                });
            }
            // Only the player who is actually about to be timed out gets the nudge.
            dom.layout?.classList.toggle('is-turn-running-out', warning && selfActive && remaining > 0);
            if (remaining > 0) state.turnTimer = window.setTimeout(frame, 200);
            else dom.layout?.classList.remove('is-turn-running-out');
        };
        frame();
    }

    /* ------------------------------- interaction ------------------------------- */

    function selectCard(card) {
        const key = cardKey(card);
        state.selectedKey = state.selectedKey === key ? null : key;
        state.handSignature = '';
        renderHand();
        highlightSlots();
    }

    function selectedCard() {
        if (!state.selectedKey) return null;
        return state.hand.find((card) => cardKey(card) === state.selectedKey) || null;
    }

    function playOnSlot(card, slotId) {
        if (!card) return false;
        const code = cardCode(card);
        if (!slotsBeatableBy(code).some((slot) => slot.slotId === slotId)) {
            // "Cannot beat" only makes sense to someone who is actually defending;
            // an attacker aiming at a slot is simply out of turn.
            showHint(t(allows('DEFEND') ? 'durak.hint.cannotBeat' : 'durak.hint.notNow'));
            return false;
        }
        return sendAction('DEFEND', card, slotId);
    }

    /** Dropping on open felt means attack. Passing has its own card-shaped target. */
    function playOnTable(card) {
        if (!card) return false;
        const actions = cardActions(cardCode(card));
        if (!actions.includes('ATTACK')) {
            showHint(actions.includes('PASS')
                ? t('durak.hint.dropToRotate')
                : t(attackRefusal(cardCode(card))));
            return false;
        }
        return sendAction('ATTACK', card);
    }

    function attackRefusal(code) {
        const slots = state.game?.attackSlots || [];
        const maximum = Number(state.game?.maxAttackCards || 0);
        if (maximum > 0 && slots.length >= maximum) return 'durak.error.durak_attack_limit_reached';
        const ranks = new Set(slots.flatMap((slot) => [slot.attackCard, slot.defenseCard])
            .filter(Boolean).map((tableCard) => cardRank(cardCode(tableCard))));
        if (slots.length && !ranks.has(cardRank(code))) return 'durak.error.durak_throw_rank_not_on_table';
        return 'durak.hint.notNow';
    }

    /** An attacker owns the whole felt; only the defender targets a specific slot. */
    function playOnFelt(card, slotId) {
        if (!card) return false;
        if (cardActions(cardCode(card)).includes('ATTACK')) return playOnTable(card);
        if (slotId == null || !selfIsDefender()) return playOnTable(card);
        return playOnSlot(card, slotId);
    }

    /**
     * Only a defender ever aims at a particular card. Anyone else dropping onto the
     * pile is attacking — including onto a card already lying there, since they have
     * no defence to make — so their drop is always read as an attack on the table.
     */
    function targetForCard(card, target) {
        if (target?.kind !== 'slot' || !card) return target;
        if (cardActions(cardCode(card)).includes('ATTACK')) return {kind: 'table'};
        return selfIsDefender() ? target : {kind: 'table'};
    }

    function playOnRotate(card) {
        if (!card) return false;
        const code = cardCode(card);
        if (!allows('PASS') || !(currentLegal()?.passableCardCodes || []).includes(code)) {
            const [key, who] = rotateRefusal(code);
            showHint(t(key, who));
            return false;
        }
        return sendAction('PASS', card);
    }

    /**
     * Why the pass was refused. "Wrong rank" is the usual reason, but a matching rank
     * can still be impossible when the next player simply cannot hold that many
     * attacks — the least obvious rule at the table, so it gets its own message.
     */
    function rotateRefusal(code) {
        const slots = state.game?.attackSlots || [];
        const matchesRank = slots.some((slot) => !slot.defenseCard
            && cardRank(cardCode(slot.attackCard)) === cardRank(code));
        if (!matchesRank) return ['durak.hint.dropToRotateInvalid'];
        if (slots.some((slot) => slot.defenseCard)) return ['durak.hint.rotateAfterDefence'];
        const next = nextActivePlayer(state.game?.defender);
        const capacity = Math.min(6, playerCardCount(next));
        return slots.length + 1 > capacity
            ? ['durak.hint.rotateCapacity', next?.name || t('common.player')]
            : ['durak.hint.dropToRotateInvalid'];
    }

    function registerSlotTarget(el, slotId) {
        el.onclick = (event) => {
            event.stopPropagation();
            playOnFelt(selectedCard(), slotId);
        };
    }

    const cardFromEl = (el) => (el?.dataset?.cardCode
        ? {cardType: el.dataset.cardType || 'POKER', card: el.dataset.cardCode}
        : null);

    /**
     * Slots sit inside the table surface, so overlapping interact.js dropzones would
     * fire in an undefined order. Resolving the release point geometrically keeps one
     * unambiguous target: a slot if the pointer is over one, otherwise the felt.
     */
    function dropTargetAt(point, card) {
        if (!point) return null;
        // Table cards are small and the grab point sits wherever the pointer went down,
        // so an exact hit test rejected drops that plainly aimed at a card. Every slot
        // gets a generous catch ring, ties go to the nearest centre, and a slot this
        // card can actually cover always wins over one it cannot.
        const code = card ? cardCode(card) : '';
        const beatable = new Set(code ? slotsBeatableBy(code).map((slot) => slot.slotId) : []);
        const canPassCard = !!code && allows('PASS')
            && (currentLegal()?.passableCardCodes || []).includes(code);
        let best = null;
        let bestScore = [Infinity, Infinity];
        targetRects().slots.forEach((slot) => {
            const rect = slot.rect;
            const margin = Math.max(rect.width, rect.height) * 0.5;
            if (point.x < rect.left - margin || point.x > rect.right + margin
                || point.y < rect.top - margin || point.y > rect.bottom + margin) return;
            const dx = point.x - (rect.left + rect.width / 2);
            const dy = point.y - (rect.top + rect.height / 2);
            const usable = slot.rotate ? canPassCard : beatable.has(slot.slotId);
            const score = [usable ? 0 : 1, dx * dx + dy * dy];
            if (score[0] < bestScore[0] || (score[0] === bestScore[0] && score[1] < bestScore[1])) {
                bestScore = score;
                best = slot;
            }
        });
        if (best?.rotate) return {kind: 'rotate'};
        if (best) return {kind: 'slot', slotId: best.slotId};
        const rect = targetRects().felt;
        const onFelt = rect && point.x >= rect.left && point.x <= rect.right
            && point.y >= rect.top && point.y <= rect.bottom;
        return onFelt ? {kind: 'table'} : null;
    }

    /**
     * Drop targets do not move while a card is being dragged, so their boxes are
     * measured once per drag instead of on every pointer move: measuring after each
     * hover class toggle forced a full layout per move, which is what made dragging
     * crawl on phones.
     */
    function targetRects() {
        if (!state.targetRects) {
            state.targetRects = {
                slots: Array.from(dom.slots?.querySelectorAll('.durak-slot') || []).map((el) => ({
                    el,
                    rect: el.getBoundingClientRect(),
                    rotate: el.classList.contains('durak-rotate-slot'),
                    slotId: Number(el.dataset.slotId)
                })),
                felt: dom.tableSurface?.getBoundingClientRect() || null
            };
        }
        return state.targetRects;
    }

    const forgetTargetRects = () => { state.targetRects = null; };

    function setupInteraction() {
        if (dom.summary && dom.actions) dom.summary.appendChild(dom.actions);
        state.handZone = ui.registerHand(dom.hand, handLayoutParams());
        ui.enableHandHoverRaise(state.handZone, {
            isActive: () => !state.finished && !state.replayingPrevious && !state.clearing
                && !state.pending.size && !state.dealingKeys.size,
            isCardActive: (cardEl) => !defenderHandIsIdle()
                && !state.dealingKeys.has(cardEl.dataset.cardKey)
        });
        ui.enableHandCardDrag(state.handZone, {
            originZone: state.handZone,
            className: 'drag-ghost',
            isActive: () => !state.finished && !state.replayingPrevious && !state.clearing
                && !state.pending.size && !state.dealingKeys.size,
            isCardActive: (cardEl) => !defenderHandIsIdle()
                && !state.dealingKeys.has(cardEl.dataset.cardKey),
            onStart: (session, cardEl) => {
                state.draggingEl = cardEl;
                state.dragSession = session;
                highlightSlots();
                forgetTargetRects();
                state.hoveredTarget = '';
            },
            onMove: (session) => {
                const card = cardFromEl(session?.el) || cardFromEl(state.draggingEl);
                const target = targetForCard(card, dropTargetAt(session?.lastPoint, card));
                // Only touch the DOM when the aim actually moved to another target.
                const signature = `${target?.kind || ''}:${target?.slotId ?? ''}`;
                if (signature === state.hoveredTarget) return;
                state.hoveredTarget = signature;
                targetRects().slots.forEach((slot) => {
                    slot.el.classList.toggle('is-hovered', slot.rotate
                        ? target?.kind === 'rotate'
                        : target?.kind === 'slot' && slot.slotId === target.slotId);
                });
            },
            onEnd: (session) => {
                const card = cardFromEl(session?.el) || cardFromEl(state.draggingEl);
                const target = targetForCard(card, dropTargetAt(session?.lastPoint, card));
                targetRects().slots.forEach((slot) => slot.el.classList.remove('is-hovered'));
                forgetTargetRects();
                state.hoveredTarget = '';
                let accepted = false;
                if (card && target?.kind === 'slot') accepted = playOnFelt(card, target.slotId);
                else if (card && target?.kind === 'rotate') accepted = playOnRotate(card);
                else if (card && target?.kind === 'table') accepted = playOnTable(card);
                if (state.dragSession === session) {
                    state.dragSession = null;
                    state.draggingEl = null;
                }
                // An accepted card is left parked exactly where it was released; the
                // slot render flies it from there into its real place once the server
                // confirms. Flying it to a guessed spot first made every played card
                // travel twice, and the second hop started before the first finished.
                if (accepted) {
                    state.handSignature = '';
                    renderHand();
                    return;
                }
                ui.finishDragCard(session, {
                    accepted: false,
                    targetRect: session.originRect,
                    instantReject: true
                }).finally(() => {
                    state.handSignature = '';
                    renderHand();
                    if (session?.el?.isConnected) {
                        session.el.classList.add('is-rejected');
                        setTimeout(() => session.el?.classList.remove('is-rejected'), 320);
                    }
                });
            },
            onCancel: (session) => {
                if (state.dragSession === session) state.dragSession = null;
                state.draggingEl = null;
                dom.slots?.querySelectorAll('.durak-slot').forEach((el) => el.classList.remove('is-hovered'));
                state.handSignature = '';
                renderHand();
                highlightSlots();
            }
        });

        // A tap on empty felt commits the selected card as an attack.
        dom.tableSurface?.addEventListener('click', (event) => {
            if (event.target.closest('.durak-slot, .hand-card, .deck-stack')) return;
            playOnTable(selectedCard());
        });

        dom.action?.addEventListener('click', () =>
            sendAction(dom.action.dataset.mode === 'take' ? 'TAKE' : 'DONE'));
        dom.sortSuit?.addEventListener('click', () => setSort('suit'));
        dom.sortRank?.addEventListener('click', () => setSort('rank'));
        window.addEventListener('resize', () => {
            forgetTargetRects();
            renderSeats();
            ui.layoutZone(state.handZone, undefined, handLayoutParams());
        });
    }

    function setSort(sort) {
        state.handSort = sort;
        dom.sortSuit?.classList.toggle('is-active', sort === 'suit');
        dom.sortRank?.classList.toggle('is-active', sort === 'rank');
        state.handSignature = '';
        renderHand();
    }

    /* ---------------------------------- result --------------------------------- */

    function renderResult(result) {
        state.finished = true;
        state.legal = null;
        // A finished game must not leave a card parked in the overlay, and the winner's
        // own verdict comes first — "X is the durak" is not what a winner wants to read.
        restorePendingCards();
        if (!dom.dropZone) return;
        dom.dropZone.classList.remove('ready');
        dom.dropZone.classList.add('is-result');
        const loser = result?.loser;
        const selfLost = !!loser && isSelf(loser);
        const title = document.createElement('div');
        title.className = 'drop-zone-title';
        title.textContent = t('durak.result.title');
        const winner = document.createElement('div');
        winner.className = 'drop-zone-winner';
        winner.textContent = result?.draw
            ? t('durak.result.draw')
            : loser ? t('durak.result.loser', loser.name || t('history.unknownPlayer')) : t('history.noWinner');
        const verdict = document.createElement('div');
        verdict.className = 'drop-zone-verdict';
        if (!result?.draw && loser) {
            verdict.dataset.outcome = selfLost ? 'lost' : 'won';
            verdict.textContent = t(selfLost ? 'durak.result.youLost' : 'durak.result.youWon');
        }
        const meta = document.createElement('div');
        meta.className = 'drop-zone-meta';
        // "You are the durak" already says who lost; naming yourself underneath it
        // only says the same thing twice.
        dom.dropZone.replaceChildren(...(selfLost ? [title, verdict, meta] : [title, verdict, winner, meta]));
        if (dom.turnOverlay) dom.turnOverlay.classList.remove('is-visible');
        startLobbyReturnCountdown(meta);
    }

    function startLobbyReturnCountdown(meta) {
        if (state.endRedirect) return;
        let secondsLeft = 8;
        meta.textContent = t('briskula.returningIn', secondsLeft);
        state.endRedirect = setInterval(() => {
            secondsLeft -= 1;
            if (secondsLeft <= 0) {
                clearInterval(state.endRedirect);
                state.endRedirect = null;
                window.location.href = '/lobbies';
                return;
            }
            meta.textContent = t('briskula.returningIn', secondsLeft);
        }, 1000);
    }

    /* ------------------------------- connection -------------------------------- */

    function setConnectionStatus(connected, text) {
        if (!dom.connectionToast) return;
        dom.connectionToast.classList.toggle('is-visible', !connected);
        if (!connected && text) dom.connectionToast.textContent = text;
    }

    /**
     * Refetches the authoritative public state and this player's hand. Used after a
     * rejected action, so a stale revision self-heals without reloading the page.
     */
    async function resync() {
        try {
            const response = await fetch(`/api/games/${encodeURIComponent(gameId)}/snapshot/durak`, {credentials: 'include'});
            if (!response.ok) return;
            const snapshot = await response.json();
            if (snapshot?.game) applyGame(snapshot.game);
            if (Array.isArray(snapshot?.hand)) applyHand(snapshot.hand);
        } catch (error) {
            console.error('Durak resync failed', error);
        }
    }

    /**
     * A "Done"/"Taking" bubble is the only trace those decisions leave, so the board
     * waits on it: the next state — drawing cards, clearing the table, a new bout —
     * is held back until the bubble has been up long enough to read. A backlog drains
     * immediately rather than letting the client fall behind the server.
     */
    function applyGame(game) {
        if (!game) return;
        // A bout that resolves arrives as ONE state: the last "Done"/"Take" and the
        // cleared table in the same message. Rendering it straight away means nobody
        // ever sees the decision that ended the round, so the closing bubble is put
        // up against the OLD table first and the new bout waits behind it.
        if (state.game && !state.pendingStates.length
            && Number(game.boutNumber) !== Number(state.game.boutNumber)
            && showClosingBubble(state.game)) {
            state.holdUntil = Date.now() + STATE_BUBBLE_MS;
        }
        if (state.holdUntil > Date.now() && state.pendingStates.length < 2) {
            state.pendingStates.push(game);
            armHold();
            return;
        }
        if (state.holdTimer) {
            clearTimeout(state.holdTimer);
            state.holdTimer = null;
        }
        state.holdUntil = 0;
        flushHeld(true);
        applyGameNow(game);
    }

    /**
     * Releases the paused bout in the order it happened: one state at a time, stopping
     * again if that state has its own "Done"/"Taking" to show, and only then the hands
     * it produced. Draining the queue in one go is what made a take look half-finished —
     * cards drawn into a hand while the table they came from was still on screen.
     * A backlog (`force`) skips the pauses rather than letting the board fall behind.
     */
    function flushHeld(force) {
        while (state.pendingStates.length) {
            applyGameNow(state.pendingStates.shift());
            if (!force && state.holdUntil > Date.now()) {
                armHold();
                return;
            }
        }
        const hand = state.pendingHands.pop();
        state.pendingHands.length = 0;
        if (hand) applyHandNow(hand);
    }

    function armHold() {
        if (state.holdTimer) return;
        state.holdTimer = setTimeout(() => {
            state.holdTimer = null;
            state.holdUntil = 0;
            flushHeld(false);
        }, Math.max(0, state.holdUntil - Date.now()) + 30);
    }

    /**
     * Freezes every finished-bout verdict on screen: a taken bout still closes when
     * its remaining attackers say "Done", so "Taking" and "Done" can coexist.
     * Returns whether there was anything worth pausing for.
     */
    function showClosingBubble(finished) {
        state.announcements.clear();
        if (finished.takeDeclared) announce(finished.defender, 'take');
        (finished.eligibleThrowers || []).forEach((player) => announce(player, 'done'));
        renderAnnouncements();
        return state.announcements.size > 0;
    }

    function applyGameNow(game) {
        if (!game) return;
        const previous = state.game;
        const completedBout = previous && Number(game.boutNumber) !== Number(previous.boutNumber);
        announceStateChanges(game);
        if (completedBout) rememberCompletedBout(previous);
        const oldTableCards = completedBout
            ? Array.from(dom.slots?.querySelectorAll('.durak-slot .card-wrap') || []) : [];
        state.previousDeckCount = previous ? Number(previous.cardsLeftInDeck) : null;
        state.game = game;
        state.legal = null;
        state.handSignature = '';
        if (game.phase === 'FINISHED') {
            state.finished = true;
            PreviousBoutStore.remove();
        }
        if (oldTableCards.length) {
            state.clearing = true;
            dom.slots?.classList.add('is-clearing');
            renderSeats();
            renderDeck();
            renderHand();
            renderActions();
            renderTurn();
            refreshPreviousBoutControls();
            collectTableCards(oldTableCards, previous).finally(() => {
                state.clearing = false;
                dom.slots?.classList.remove('is-clearing');
                if (dom.slots) dom.slots.dataset.signature = '';
                render();
            });
            return;
        }
        render();
    }

    /**
     * A resolved bout goes somewhere specific: a taken table is scooped up by the
     * defender who took it, a beaten one is thrown out. Flying the cards to the right
     * place is the only feedback a player gets about where the pile went.
     */
    function collectTableCards(cards, finishedBout) {
        const taken = !!finishedBout?.takeDeclared;
        const target = taken ? seatRectFor(finishedBout?.defender) : null;
        if (!target) return ui.animateTrickCollect(cards);
        return Promise.all(cards.map((card, index) => {
            card.style.opacity = '0';
            return ui.animateCardBetweenZones({
                fromEl: card,
                targetRect: {left: target.left, top: target.top, width: target.width * 0.5, height: target.height * 0.5},
                duration: 420,
                delay: index * 55,
                toRot: `${-8 + index * 4}deg`,
                fadeOut: true,
                easing: 'inOut(2)'
            });
        }));
    }

    function seatRectFor(player) {
        if (!player) return null;
        if (isSelf(player)) return dom.summary?.getBoundingClientRect() || null;
        const seat = Array.from(dom.ring?.querySelectorAll('.player-seat') || [])
            .find((candidate) => candidate.dataset.playerKey === playerKey(player));
        const rect = seat?.querySelector('.seat-avatar')?.getBoundingClientRect();
        return rect?.width ? rect : null;
    }

    /**
     * Hands land with the table, never ahead of it: drawing behind a paused bout is what
     * made a take look half-finished — cards appearing in a hand that still sat on the felt.
     */
    function applyHand(cards) {
        if (state.holdUntil > Date.now()) {
            state.pendingHands.push(cards);
            return;
        }
        applyHandNow(cards);
    }

    function applyHandNow(cards) {
        const nextHand = Array.isArray(cards) ? cards : [];
        const nextKeys = new Set(nextHand.map(cardKey));
        const rejectedKeys = new Set([...state.pending].filter((key) => nextKeys.has(key)));
        rejectedKeys.forEach((key) => {
            const pending = state.pendingFlights.get(key);
            if (pending?.session) {
                ui.cancelDragCard(pending.session);
                if (state.dragSession === pending.session) {
                    state.dragSession = null;
                    state.draggingEl = null;
                }
            }
            state.pending.delete(key);
            state.pendingHandRemovals.delete(key);
            state.tableConfirmedRemovals.delete(key);
            state.pendingFlights.delete(key);
        });
        [...state.pending].forEach((key) => {
            if (nextKeys.has(key)) return;
            if (state.pendingFlights.has(key)) {
                // The hand queue confirmed removal before the public table event.
                // Keep the exact dragged node parked at the drop point until the
                // matching slot consumes it; otherwise a slow topic update creates
                // a visible disappear/recreate gap.
                state.pendingHandRemovals.add(key);
            } else {
                state.pending.delete(key);
                state.pendingHandRemovals.delete(key);
            }
        });
        [...state.tableConfirmedRemovals].forEach((key) => {
            if (!nextKeys.has(key)) state.tableConfirmedRemovals.delete(key);
        });
        const draggedCard = cardFromEl(state.dragSession?.el);
        if (draggedCard && rejectedKeys.has(cardKey(draggedCard))) {
            ui.cancelDragCard(state.dragSession);
            state.dragSession = null;
            state.draggingEl = null;
        }
        const previousKeys = new Set(state.hand.map(cardKey));
        const tableKeys = new Set([
            ...(state.game?.attackSlots || []),
            ...(state.previousBout?.attackSlots || [])
        ].flatMap((slot) => [slot.attackCard, slot.defenseCard]).filter(Boolean).map(cardKey));
        const drawn = nextHand.filter((card) => !previousKeys.has(cardKey(card)) && !tableKeys.has(cardKey(card)));
        drawn.forEach((card) => state.dealingKeys.add(cardKey(card)));
        state.hand = nextHand;
        state.legal = null;
        state.handSignature = '';
        renderHand();
        if (drawn.length) {
            const towerRect = dom.deckTower?.getBoundingClientRect();
            const deckRect = towerRect?.width ? towerRect : dom.deckStack?.getBoundingClientRect();
            ui.dealCardsIntoHand(dom.hand, drawn, {
                fromRect: deckRect,
                duration: 480,
                stagger: 65,
                ease: 'power3.out',
                onFinish(_el, card) {
                    state.dealingKeys.delete(cardKey(card));
                    state.handSignature = '';
                    renderHand();
                }
            });
            // A dealt card whose element never materialised (a re-render swallowed it
            // before the deal started) would otherwise keep its key in dealingKeys and
            // leave the whole hand locked for the rest of the game.
            const deadline = 480 + 65 * drawn.length + 700;
            setTimeout(() => {
                if (!drawn.some((card) => state.dealingKeys.has(cardKey(card)))) return;
                drawn.forEach((card) => state.dealingKeys.delete(cardKey(card)));
                state.handSignature = '';
                renderHand();
            }, deadline);
        }
        renderSelfSummary(playerList().find(isSelf));
    }

    function connectWs() {
        if (!window.Stomp) {
            setConnectionStatus(false, t('briskula.connUnavailable'));
            return;
        }
        if (state.wsReconnectTimer) {
            clearTimeout(state.wsReconnectTimer);
            state.wsReconnectTimer = null;
        }
        const client = Stomp.client(wsUrl);
        client.reconnect_delay = 0;
        client.debug = null;
        state.wsClient = client;
        client.connect({}, () => {
            const reconnecting = state.hasConnected;
            state.wsConnected = true;
            state.hasConnected = true;
            setConnectionStatus(true);
            client.subscribe(`/topic/game/${gameId}`, (msg) => {
                try {
                    const payload = JSON.parse(msg.body);
                    if (!payload?.gameEntity) return;
                    applyGame(payload.gameEntity);
                    if (payload.gameEvent === 'RESULTED') renderResult(payload.result);
                } catch (error) {
                    console.error('Durak game event error', error);
                }
            });
            client.subscribe('/user/queue/game/cards', (msg) => {
                try {
                    applyHand(JSON.parse(msg.body));
                } catch (error) {
                    console.error('Durak hand event error', error);
                }
            });
            client.subscribe('/user/queue/game/durak-actions', (msg) => {
                try {
                    const legal = JSON.parse(msg.body);
                    // A legal-action set for an older revision describes a state we have
                    // already moved past; keeping it would enable buttons that now fail.
                    if (state.legal && Number(legal?.stateRevision) < Number(state.legal.stateRevision)) return;
                    state.legal = legal;
                    state.handSignature = '';
                    renderHand();
                    // The rotate target lives in the table row, so a change in what is
                    // legal has to reach the slots too, not just the buttons.
                    renderSlots();
                    renderActions();
                    highlightSlots();
                } catch (error) {
                    console.error('Durak legal-actions event error', error);
                }
            });
            client.subscribe('/user/queue/game/errors', (msg) => {
                let message = t('durak.error.rejected');
                try {
                    const error = JSON.parse(msg.body);
                    message = translateError(error);
                } catch (error) {
                    console.error('Durak error event error', error);
                }
                restorePendingCards();
                showHint(message);
                resync();
            });
            if (state.game?.lobbyId) {
                client.subscribe(`/topic/lobbies/${state.game.lobbyId}/chat`, (msg) => {
                    try {
                        const payload = JSON.parse(msg.body);
                        if (payload?.message) chat?.addMessage(payload);
                    } catch (error) {
                        console.error('Durak chat event error', error);
                    }
                });
            }
            if (reconnecting) resync();
        }, () => {
            const hadConnection = state.hasConnected;
            state.wsConnected = false;
            if (state.pending.size || state.pendingFlights.size || state.dragSession) restorePendingCards();
            if (hadConnection) setConnectionStatus(false, t('game.connectionLost'));
            if (!state.wsReconnectTimer) {
                state.wsReconnectTimer = setTimeout(() => {
                    state.wsReconnectTimer = null;
                    connectWs();
                }, 1000);
            }
        });
    }

    // Server error codes get a translated message; anything unmapped falls back to the
    // server text so a new code is still readable rather than silently swallowed.
    function translateError(error) {
        const key = `durak.error.${String(error?.code || '').toLowerCase()}`;
        const translated = t(key);
        return translated === key ? (error?.message || t('durak.error.rejected')) : translated;
    }

    const chat = window.UltracardsChat?.create({
        initialChat: window.__INITIAL_GAME_CHAT__ || null,
        currentUserId,
        currentUsername,
        messagesId: 'chat-messages',
        formId: 'chat-form',
        inputId: 'chat-input',
        sendId: 'chat-send',
        messageClass: 'chat-message',
        metaClass: 'chat-meta',
        bubbleClass: 'chat-bubble',
        timeClass: 'chat-time',
        emptyClass: 'chat-empty',
        emptyText: t('chat.gameEmpty')
    });

    setupInteraction();
    state.previousBout = PreviousBoutStore.load();
    dom.previous?.addEventListener('click', () => showPreviousBout(true));
    dom.current?.addEventListener('click', () => showPreviousBout(false));
    setSort('suit');
    render();
    connectWs();
})();
