/* Browser-only Durak sandbox. It feeds the real Durak controller through the
 * same STOMP destinations as a live game; no server game is created. */
(() => {
    const gameEl = document.getElementById('game-container');
    const params = new URLSearchParams(window.location.search);
    if (!gameEl?.dataset.sandbox || params.get('type') !== 'durak') return;

    const GAME_ID = 'ui-sandbox';
    const SELF_ID = Number(gameEl.dataset.currentUserId) || 1;
    const SELF_NAME = gameEl.dataset.username || 'Admin';
    const BOT_NAMES = ['Bot Gojko', 'Bot Ivo', 'Bot Mia', 'Bot Niko', 'Bot Tara'];
    const SUITS = ['H', 'D', 'C', 'S'];
    const SUIT_NAMES = {H: 'HEARTS', D: 'DIAMONDS', C: 'CLUBS', S: 'SPADES'};
    // The deal is described by a real lobby config, so the sandbox can reach every
    // combination the game supports instead of four canned ones.
    let settings = sanitizeDurakConfig(parseDurakModeKey(params.get('mode') || '')
        || {numberOfPlayers: 3, ...DURAK_DEFAULTS});
    let state;
    let selectedCode = null;
    let pacedAction = null;

    const status = document.getElementById('sandbox-status');
    const gameTypeSelect = document.getElementById('sandbox-game-type');
    const modeField = document.getElementById('sandbox-mode-field');
    const durakOptions = document.getElementById('sandbox-durak-options');
    const OPTION_PREFIX = 'sandbox-';
    const playerSelect = document.getElementById('sandbox-player');
    const cardPicker = document.getElementById('sandbox-card-picker');
    const handCards = document.getElementById('sandbox-hand-cards');
    const handCount = document.getElementById('sandbox-hand-count');
    const setCardButton = document.getElementById('sandbox-set-card');
    const removeCardButton = document.getElementById('sandbox-remove-card');
    const mode = () => ({
        players: settings.numberOfPlayers,
        deck: settings.deckSize,
        jokers: settings.jokersEnabled,
        everyone: settings.throwInPolicy === 'EVERYONE',
        passing: settings.passingEnabled
    });
    const publicPlayer = (player) => ({id: player.id, name: player.name});
    const card = (code) => ({cardType: 'POKER', card: code});
    const codeOf = (value) => String(value?.card || value || '');
    const rank = (code) => ['JR', 'JB'].includes(code) ? 15 : Number(String(code).slice(1));
    const suit = (code) => String(code).charAt(0);
    const isRed = (code) => code === 'JR' || suit(code) === 'H' || suit(code) === 'D';
    const tr = (key, fallback, ...args) => (window.__I18N__ || {})[key] ? window.t(key, ...args) : fallback;
    const modeLabel = (config) => getGameConfigDisplayName('Durak', {
        numberOfPlayers: config.players,
        cardsInHandNum: 6,
        deckSize: config.deck,
        jokersEnabled: !!config.jokers,
        throwInPolicy: config.everyone ? 'EVERYONE' : 'NEIGHBORS_ONLY',
        passingEnabled: !!config.passing
    });
    const actionLabel = (action) => ({
        ATTACK: tr('admin.sandbox.durak.action.attack', 'attack'),
        DEFEND: tr('admin.sandbox.durak.action.defend', 'defend'),
        THROW_IN: tr('admin.sandbox.durak.action.throwIn', 'throw in'),
        PASS: tr('admin.sandbox.durak.action.pass', 'pass'),
        TAKE: tr('durak.action.take', 'take'),
        DONE: tr('durak.action.done', 'done')
    })[action] || action;

    function showStatus(message, error = false) {
        if (!status) return;
        status.textContent = message;
        status.classList.toggle('is-error', error);
    }

    function freshDeck(config) {
        const ranks = config.deck === 24
            ? [9, 10, 11, 12, 13, 14]
            : config.deck === 36 ? [6, 7, 8, 9, 10, 11, 12, 13, 14] : [2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14];
        const deck = SUITS.flatMap((cardSuit) => ranks.map((value) => card(cardSuit + value)));
        if (config.jokers) deck.push(card('JR'), card('JB'));
        // Deterministic rotation gives every reset a useful, reproducible deal.
        return deck.slice(11).concat(deck.slice(0, 11));
    }

    function buildPlayers(count) {
        const players = [{id: SELF_ID, name: SELF_NAME, hand: []}];
        for (let i = 1; i < count; i++) players.push({id: 9000 + i, name: BOT_NAMES[i - 1], hand: []});
        return players;
    }

    function reset() {
        clearTimeout(pacedAction);
        selectedCode = null;
        const config = mode();
        const players = buildPlayers(config.players);
        const deck = freshDeck(config);
        for (let round = 0; round < 6; round++)
            players.forEach((player) => player.hand.push(deck.shift()));
        const trump = deck.at(-1);
        const lead = config.passing ? players.length - 1 : 0;
        const defender = (lead + 1) % players.length;
        state = {
            players,
            deck,
            trump,
            slots: [],
            phase: 'WAITING_FOR_ATTACK',
            lead,
            defender,
            actor: lead,
            maxAttackCards: Math.min(6, players[defender].hand.length),
            done: new Set(),
            takeDeclared: false,
            bout: 1,
            revision: 1,
            ended: false
        };
        syncDeckSizeInput();
        publish('STARTED');
        showStatus(tr('admin.sandbox.durak.newDeal',
            `New local ${modeLabel(config)} Durak deal. No backend game was created.`, modeLabel(config)));
    }

    function playerKey(player) {
        return JSON.stringify(publicPlayer(player));
    }

    function gameDto() {
        const counts = {};
        state.players.forEach((player) => { counts[playerKey(player)] = player.hand.length; });
        const config = mode();
        const lead = state.players[state.lead];
        const defender = state.players[state.defender];
        const actor = state.ended ? null : state.players[state.actor];
        const eligible = config.everyone
            ? state.players.filter((_, index) => index !== state.defender)
            : [lead];
        return {
            id: GAME_ID,
            lobbyId: null,
            name: 'UI sandbox',
            playersOrder: state.players.map(publicPlayer),
            playersCardsMap: counts,
            cardsLeftInDeck: state.deck.length,
            trumpSuit: SUIT_NAMES[suit(codeOf(state.trump))],
            trumpIndicator: state.trump,
            phase: state.ended ? 'FINISHED' : state.phase,
            stateRevision: state.revision,
            boutNumber: state.bout,
            leadAttacker: publicPlayer(lead),
            defender: publicPlayer(defender),
            actionPlayer: actor ? publicPlayer(actor) : null,
            maxAttackCards: state.maxAttackCards,
            attackSlots: state.slots.map((slot) => ({
                slotId: slot.slotId,
                attacker: publicPlayer(slot.attacker),
                attackCard: slot.attackCard,
                defender: slot.defenseCard ? publicPlayer(defender) : null,
                defenseCard: slot.defenseCard
            })),
            eligibleThrowers: eligible.map(publicPlayer),
            doneThrowers: [...state.done].map((index) => publicPlayer(state.players[index])),
            takeDeclared: state.takeDeclared,
            passingEnabled: !!config.passing,
            jokersEnabled: !!config.jokers,
            throwInPolicy: config.everyone ? 'EVERYONE' : 'NEIGHBORS_ONLY',
            finishedPlayers: [],
            finishOrder: [],
            discardedCardsNum: 0,
            turnEndTime: new Date(Date.now() + 300000).toISOString(),
            turnDurationSeconds: 300
        };
    }

    function canBeat(attack, defense) {
        if (['JR', 'JB'].includes(attack)) return false;
        if (['JR', 'JB'].includes(defense)) return isRed(attack) === isRed(defense);
        if (suit(attack) === suit(defense)) return rank(defense) > rank(attack);
        return suit(defense) === suit(codeOf(state.trump));
    }

    function legalFor(player) {
        const result = {
            stateRevision: state.revision,
            allowedActionTypes: [],
            defendableSlotIds: [],
            throwableCardCodes: [],
            passableCardCodes: []
        };
        if (state.ended) return result;
        const playerIndex = state.players.indexOf(player);
        const eligible = eligibleIndices().includes(playerIndex) && !state.done.has(playerIndex);
        if (state.phase === 'WAITING_FOR_ATTACK' && playerIndex === state.actor) {
            result.allowedActionTypes.push('ATTACK');
        }
        if (state.phase === 'WAITING_FOR_DEFENSE' && playerIndex === state.defender && playerIndex === state.actor) {
            result.defendableSlotIds = state.slots
                .filter((slot) => !slot.defenseCard && player.hand.some((entry) => canBeat(codeOf(slot.attackCard), codeOf(entry))))
                .map((slot) => slot.slotId);
            if (result.defendableSlotIds.length) result.allowedActionTypes.push('DEFEND');
            if (mode().passing) {
                const ranks = new Set(state.slots.filter((slot) => !slot.defenseCard).map((slot) => rank(codeOf(slot.attackCard))));
                const next = (state.defender + 1) % state.players.length;
                if (state.slots.length + 1 <= Math.min(6, state.players[next].hand.length)) {
                    result.passableCardCodes = player.hand.filter((entry) => ranks.has(rank(codeOf(entry)))).map(codeOf);
                }
                if (result.passableCardCodes.length) result.allowedActionTypes.push('PASS');
            }
            result.allowedActionTypes.push('TAKE');
        }
        if (eligible && ['WAITING_FOR_DEFENSE', 'WAITING_FOR_THROW_IN', 'THROW_AFTER_TAKE'].includes(state.phase)) {
            const ranks = new Set(state.slots.flatMap((slot) => [slot.attackCard, slot.defenseCard]).filter(Boolean).map((entry) => rank(codeOf(entry))));
            result.throwableCardCodes = player.hand.filter((entry) => ranks.has(rank(codeOf(entry)))).map(codeOf);
            if (result.throwableCardCodes.length && state.slots.length < state.maxAttackCards) result.allowedActionTypes.push('THROW_IN');
            if (state.phase !== 'WAITING_FOR_DEFENSE') result.allowedActionTypes.push('DONE');
        }
        return result;
    }

    function eligibleIndices() {
        if (mode().everyone) return state.players.map((_, index) => index).filter((index) => index !== state.defender);
        return [state.lead];
    }

    const subscriptions = new Map();
    function emit(destination, payload) {
        const body = JSON.stringify(payload);
        queueMicrotask(() => subscriptions.get(destination)?.forEach((callback) => callback({body})));
    }

    function ownHand() {
        return state.players.find((player) => player.id === SELF_ID)?.hand || [];
    }

    function publish(event = 'UPDATED', result = null) {
        const dto = gameDto();
        window.__INITIAL_GAME__ = dto;
        window.__INITIAL_HAND__ = ownHand().slice();
        emit(`/topic/game/${GAME_ID}`, {gameEntity: dto, gameEvent: event, result});
        emit('/user/queue/game/cards', ownHand());
        emit('/user/queue/game/durak-actions', legalFor(state.players.find((player) => player.id === SELF_ID)));
        renderEditor();
    }

    function removeFromHand(player, wanted) {
        const index = player.hand.findIndex((entry) => codeOf(entry) === wanted);
        return index < 0 ? null : player.hand.splice(index, 1)[0];
    }

    function finishBout() {
        // Mirrors the server: the bout that just ended goes out on its own, table still
        // laid out, before the state that clears it.
        emit(`/topic/game/${GAME_ID}`, {gameEntity: gameDto(), gameEvent: 'UPDATED', result: null});
        const tableCards = state.slots.flatMap((slot) => [slot.attackCard, slot.defenseCard]).filter(Boolean);
        if (state.takeDeclared) state.players[state.defender].hand.push(...tableCards);
        const nextLead = state.takeDeclared ? state.lead : state.defender;
        for (let offset = 0; offset < state.players.length; offset++) {
            const player = state.players[(state.lead + offset) % state.players.length];
            while (player.hand.length < 6 && state.deck.length) player.hand.push(state.deck.shift());
        }
        state.lead = nextLead;
        state.defender = (nextLead + 1) % state.players.length;
        state.actor = state.lead;
        state.maxAttackCards = Math.min(6, state.players[state.defender].hand.length);
        state.done.clear();
        state.slots = [];
        state.phase = 'WAITING_FOR_ATTACK';
        state.takeDeclared = false;
        state.bout++;
        state.revision++;
        publish();
    }

    function act(player, type, wanted, targetSlotId) {
        const playerIndex = state.players.indexOf(player);
        // Mirrors DurakGame: a throw-in is turn-independent, and ATTACK is its alias.
        const throwing = ['THROW_IN', 'ATTACK'].includes(type) && playerIndex !== state.defender;
        const concurrent = (throwing || type === 'DONE')
            && eligibleIndices().includes(playerIndex) && !state.done.has(playerIndex)
            && state.phase !== 'WAITING_FOR_ATTACK';
        if ((playerIndex !== state.actor && !concurrent) || state.ended) return false;
        const legal = legalFor(player);
        if (throwing && concurrent) type = 'THROW_IN';
        if (!legal.allowedActionTypes.includes(type)) return false;
        if (type === 'TAKE') {
            state.takeDeclared = true;
            state.phase = 'THROW_AFTER_TAKE';
            state.actor = state.lead;
            state.done.clear();
        } else if (type === 'DONE') {
            state.done.add(playerIndex);
            if (state.phase !== 'WAITING_FOR_DEFENSE') {
                const next = eligibleIndices().find((index) => !state.done.has(index));
                if (next == null) {
                    finishBout();
                    return true;
                }
                state.actor = next;
            }
        } else if (type === 'PASS') {
            const played = removeFromHand(player, wanted);
            if (!played || !legal.passableCardCodes.includes(wanted)) {
                if (played) player.hand.push(played);
                return false;
            }
            state.slots.push({slotId: state.slots.length, attacker: player, attackCard: played, defenseCard: null});
            state.defender = (state.defender + 1) % state.players.length;
            state.actor = state.defender;
            state.maxAttackCards = Math.min(6, state.players[state.defender].hand.length);
            state.done.clear();
        } else {
            const played = removeFromHand(player, wanted);
            if (!played) return false;
            if (type === 'ATTACK' || type === 'THROW_IN') {
                if (type === 'THROW_IN' && !legal.throwableCardCodes.includes(wanted)) {
                    player.hand.push(played);
                    return false;
                }
                state.slots.push({slotId: state.slots.length, attacker: player, attackCard: played, defenseCard: null});
                state.done.clear();
                state.phase = state.takeDeclared ? 'THROW_AFTER_TAKE' : 'WAITING_FOR_DEFENSE';
                state.actor = state.takeDeclared ? state.lead : state.defender;
            } else if (type === 'DEFEND') {
                const slot = state.slots.find((entry) => entry.slotId === Number(targetSlotId) && !entry.defenseCard);
                if (!slot || !canBeat(codeOf(slot.attackCard), wanted)) {
                    player.hand.push(played);
                    return false;
                }
                slot.defenseCard = played;
                state.phase = 'WAITING_FOR_THROW_IN';
                state.actor = eligibleIndices().find((index) => !state.done.has(index)) ?? state.lead;
            }
        }
        state.revision++;
        publish();
        return true;
    }

    function autoPlay() {
        if (state.ended) return showStatus(tr('admin.sandbox.durak.resetRequired',
            'Reset the deal to keep playing.'), true);
        const player = state.players[state.actor];
        const legal = legalFor(player);
        let action;
        let played;
        let target;
        if (legal.allowedActionTypes.includes('ATTACK')) {
            action = 'ATTACK'; played = player.hand[0];
        } else if (legal.allowedActionTypes.includes('DEFEND')) {
            target = legal.defendableSlotIds[0];
            const slot = state.slots.find((entry) => entry.slotId === target);
            played = player.hand.find((entry) => canBeat(codeOf(slot.attackCard), codeOf(entry)));
            action = 'DEFEND';
        } else if (legal.allowedActionTypes.includes('TAKE')) {
            action = 'TAKE';
        } else if (legal.allowedActionTypes.includes('DONE')) {
            action = 'DONE';
        }
        if (!action || !act(player, action, codeOf(played), target)) return showStatus(
            tr('admin.sandbox.durak.noLegalAction', 'No legal local action is available.'), true);
        const playedCode = played ? codeOf(played) : '';
        showStatus(tr('admin.sandbox.durak.action', `${player.name}: ${actionLabel(action)} ${playedCode}.`,
            player.name, actionLabel(action), playedCode));
    }

    function finishCurrentBout() {
        if (state.ended) return;
        const bout = state.bout;
        autoPlay();
        if (state.bout === bout) pacedAction = setTimeout(finishCurrentBout, 380);
    }

    function selectedPlayer() {
        return state.players.find((player) => String(player.id) === playerSelect?.value) || state.players[0];
    }

    function allCodes() {
        return freshDeck(mode()).map(codeOf);
    }

    function renderEditor() {
        if (!state || !playerSelect || !handCards) return;
        const selectedPlayerId = playerSelect.value;
        playerSelect.replaceChildren(...state.players.map((player) => {
            const option = document.createElement('option');
            option.value = String(player.id);
            option.textContent = player.name;
            return option;
        }));
        if (state.players.some((player) => String(player.id) === selectedPlayerId)) playerSelect.value = selectedPlayerId;
        const player = selectedPlayer();
        if (!player.hand.some((entry) => codeOf(entry) === selectedCode)) selectedCode = null;
        handCount.textContent = tr('durak.deck.short', `${player.hand.length} cards`, player.hand.length);
        handCards.replaceChildren(...player.hand.map((entry) => {
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'sandbox-hand-card';
            button.classList.toggle('is-selected', codeOf(entry) === selectedCode);
            button.setAttribute('aria-pressed', String(codeOf(entry) === selectedCode));
            button.append(window.UltracardsGameUi.renderCardImage({card: entry, alt: codeOf(entry)}));
            button.addEventListener('click', () => {
                selectedCode = selectedCode === codeOf(entry) ? null : codeOf(entry);
                renderEditor();
            });
            return button;
        }));
        setCardButton.textContent = selectedCode
            ? tr('admin.sandbox.replaceCard', 'Replace card')
            : tr('admin.sandbox.addCard', 'Add card');
        removeCardButton.disabled = !selectedCode;
    }

    function moveCardToPlayer(wanted, player, index) {
        if (state.slots.some((slot) => [slot.attackCard, slot.defenseCard].some((entry) => codeOf(entry) === wanted))) return false;
        state.players.forEach((entry) => { entry.hand = entry.hand.filter((item) => codeOf(item) !== wanted); });
        state.deck = state.deck.filter((entry) => codeOf(entry) !== wanted);
        player.hand.splice(index, 0, card(wanted));
        state.revision++;
        publish();
        return true;
    }

    function setHandCard() {
        const player = selectedPlayer();
        const wanted = cardPicker?.value;
        if (!wanted) return;
        const index = player.hand.findIndex((entry) => codeOf(entry) === selectedCode);
        if (index >= 0) state.deck.unshift(player.hand.splice(index, 1)[0]);
        if (!moveCardToPlayer(wanted, player, index < 0 ? player.hand.length : index))
            return showStatus(tr('admin.sandbox.durak.cardOnTable',
                `${wanted} is currently on the table.`, wanted), true);
        selectedCode = null;
        showStatus(tr('admin.sandbox.durak.cardMoved',
            `${wanted} is now in ${player.name}'s hand.`, wanted, player.name));
    }

    function removeSelectedCard() {
        const player = selectedPlayer();
        const removed = removeFromHand(player, selectedCode);
        if (!removed) return;
        state.deck.unshift(removed);
        selectedCode = null;
        state.revision++;
        publish();
        showStatus(tr('admin.sandbox.durak.cardRemoved',
            `Removed ${codeOf(removed)} from ${player.name}'s hand.`, codeOf(removed), player.name));
    }

    function showResult() {
        clearTimeout(pacedAction);
        const loser = state.players.at(-1);
        state.players.forEach((player) => { player.hand = []; });
        state.deck = [];
        state.slots = [];
        state.ended = true;
        state.revision++;
        publish('RESULTED', {draw: false, loser: publicPlayer(loser), winners: state.players.slice(0, -1).map(publicPlayer)});
        showStatus(tr('admin.sandbox.durak.result',
            `Showing a local result: ${loser.name} is the durak.`, loser.name));
    }

    /**
     * The lobby's own rule controls, wired to redeal on every change. Same widgets,
     * same validation (no 24-card pack above four players, no jokers outside the 54),
     * so anything the sandbox can set is something a lobby could have created.
     */
    function renderDurakOptionControls() {
        if (!durakOptions) return;
        if (modeField) modeField.hidden = true;
        durakOptions.hidden = false;
        const players = buildDurakChoice(durakOptionId(OPTION_PREFIX, 'players'), tr('durak.players.label', 'Players'),
            [2, 3, 4, 5, 6].map((count) => [count, String(count)]), settings.numberOfPlayers);
        durakOptions.replaceChildren(players, ...buildDurakSettingsNodes(OPTION_PREFIX, settings));
        syncDurakSettingsAvailability(OPTION_PREFIX, settings.numberOfPlayers);
    }

    /** One listener on the container, bound once: the controls inside it are replaced. */
    function onDurakOptionChange() {
        const chosen = document.getElementById(durakOptionId(OPTION_PREFIX, 'players'));
        const next = sanitizeDurakConfig({
            numberOfPlayers: Number(chosen?.dataset.value) || settings.numberOfPlayers,
            ...readDurakSettings(OPTION_PREFIX)
        });
        if (durakModeKey(next) === durakModeKey(settings)) return;
        settings = next;
        const url = new URLSearchParams(window.location.search);
        url.set('type', 'durak');
        url.set('mode', durakModeKey(settings));
        history.replaceState(null, '', `?${url}`);
        cardPicker.replaceChildren();
        populateControls();
        reset();
    }

    function populateControls() {
        gameEl.dataset.gameType = 'durak';
        gameEl.dataset.gameId = GAME_ID;
        gameTypeSelect.value = 'durak';
        renderDurakOptionControls();
        cardPicker.replaceChildren(...allCodes().map((code) => {
            const option = document.createElement('option');
            option.value = code;
            option.textContent = code;
            return option;
        }));
        const layout = document.querySelector('.game-layout');
        layout?.classList.remove('treseta-game-layout');
        layout?.classList.add('durak-game-layout');
        const shell = document.querySelector('.game-shell');
        shell?.classList.remove('treseta-game-shell', 'briskula-game-shell');
        shell?.classList.add('durak-game-shell');
        const tableWrap = document.querySelector('.table-wrap');
        tableWrap?.classList.add('durak-table-area');
        const actions = document.getElementById('durak-actions');
        actions.hidden = false;
        document.getElementById('player-summary')?.append(actions);
        const hint = document.getElementById('durak-hint');
        if (hint) tableWrap?.append(hint);
        document.getElementById('sandbox-declare-actions')?.toggleAttribute('hidden', true);
        document.getElementById('sandbox-declaration-field')?.toggleAttribute('hidden', true);
        document.getElementById('sandbox-points-label')?.toggleAttribute('hidden', true);
        document.getElementById('sandbox-points-actions')?.toggleAttribute('hidden', true);
        document.getElementById('sandbox-auto-play').textContent =
            tr('admin.sandbox.durak.playAction', 'Play action');
        document.getElementById('sandbox-play-trick').textContent =
            tr('admin.sandbox.durak.finishBout', 'Finish bout');
    }

    function handleSend(destination, body) {
        if (destination !== '/app/game/durak/action') return;
        let payload;
        try { payload = JSON.parse(body); } catch (_) { return; }
        const player = state.players.find((entry) => entry.id === SELF_ID);
        if (!act(player, payload.type, codeOf(payload.card), payload.targetSlotId)) {
            emit('/user/queue/game/errors', {
                code: 'DURAK_INVALID_ACTION_FOR_PHASE',
                message: tr('durak.error.durak_invalid_action_for_phase', 'That action is not allowed right now.'),
                currentRevision: state.revision
            });
        }
    }

    window.Stomp = {
        client() {
            return {
                reconnect_delay: 0,
                debug: null,
                connect(_headers, connected) { queueMicrotask(connected); },
                subscribe(destination, callback) {
                    if (!subscriptions.has(destination)) subscriptions.set(destination, new Set());
                    subscriptions.get(destination).add(callback);
                    return {unsubscribe: () => subscriptions.get(destination)?.delete(callback)};
                },
                send(destination, _headers, body) { handleSend(destination, body); },
                disconnect(callback) { callback?.(); }
            };
        }
    };

    function on(id, event, handler) {
        document.getElementById(id)?.addEventListener(event, handler);
    }

    /** Same stock control as the trick games: drives the endgame without playing one. */
    const deckSizeInput = document.getElementById('sandbox-deck-size');

    function syncDeckSizeInput() {
        if (!deckSizeInput || !state) return;
        deckSizeInput.value = String(state.deck.length);
        deckSizeInput.max = String(freshDeck(mode()).length);
    }

    function setDeckSize(value) {
        if (!state) return;
        const pack = freshDeck(mode());
        const target = Math.max(0, Math.min(Number(value) || 0, pack.length));
        const held = new Set([
            ...state.players.flatMap((player) => player.hand.map(codeOf)),
            ...state.slots.flatMap((slot) => [slot.attackCard, slot.defenseCard]).filter(Boolean).map(codeOf)
        ]);
        while (state.deck.length > target) state.deck.shift();
        const inDeck = new Set(state.deck.map(codeOf));
        const spare = pack.filter((entry) => !held.has(codeOf(entry)) && !inDeck.has(codeOf(entry)));
        while (state.deck.length < target && spare.length) state.deck.unshift(spare.pop());
        // The trump indicator lives at the bottom of the stock; an empty stock leaves
        // it on the table as the suit marker, which is exactly the state worth testing.
        if (state.deck.length) state.trump = state.deck.at(-1);
        state.revision++;
        syncDeckSizeInput();
        publish();
        showStatus(`Deck set to ${state.deck.length} cards.`);
    }

    durakOptions?.addEventListener('change', onDurakOptionChange);
    populateControls();
    reset();
    window.__INITIAL_GAME_CHAT__ = null;
    on('sandbox-game-type', 'change', () => {
        const next = new URLSearchParams(window.location.search);
        next.set('type', gameTypeSelect.value);
        next.delete('mode');
        window.location.search = next.toString();
    });

    on('sandbox-player', 'change', () => { selectedCode = null; renderEditor(); });
    on('sandbox-deck-size', 'change', () => setDeckSize(deckSizeInput?.value));
    on('sandbox-set-card', 'click', setHandCard);
    on('sandbox-remove-card', 'click', removeSelectedCard);
    on('sandbox-auto-play', 'click', autoPlay);
    on('sandbox-play-trick', 'click', finishCurrentBout);
    on('sandbox-end', 'click', showResult);
    on('sandbox-reset', 'click', () => reset());
})();
