/* Browser-only admin game sandbox. It feeds the real live-game controller
 * through a tiny in-page STOMP-compatible transport, so rendering, gestures,
 * declarations, and animation stay identical without creating backend games. */
(() => {
    const gameEl = document.getElementById('game-container');
    if (!gameEl?.dataset.sandbox) return;

    const SUITS = ['C', 'D', 'S', 'B'];
    const VALUES = [1, 2, 3, 4, 5, 6, 7, 11, 12, 13];
    const SUIT_NAMES = {C: 'COPPE', D: 'DENARI', S: 'SPADE', B: 'BASTONI'};
    const SUIT_LABELS = {C: 'Cups', D: 'Coins', S: 'Swords', B: 'Clubs'};
    const VALUE_LABELS = {1: 'Ace', 2: 'Two', 3: 'Three', 4: 'Four', 5: 'Five', 6: 'Six', 7: 'Seven', 11: 'Jack', 12: 'Knight', 13: 'King'};
    const BOT_NAMES = ['Bot Ana', 'Bot Ivo', 'Bot Mia'];
    const GAME_ID = 'ui-sandbox';
    const SELF_ID = String(gameEl.dataset.currentUserId || '1');
    const SELF_NAME = gameEl.dataset.username || 'Admin';

    const MODES = {
        briskula: {
            TWO_PLAYERS: {label: '2 players', players: 2, hand: 3},
            TWO_PLAYERS_FOUR_CARDS_IN_HAND_EACH: {label: '2 players · 4 cards', players: 2, hand: 4, trick: 4},
            THREE_PLAYERS: {label: '3 players', players: 3, hand: 3},
            FOUR_PLAYERS_NO_TEAMS: {label: '4 players · no teams', players: 4, hand: 3},
            FOUR_PLAYERS_WITH_TEAMS: {label: '4 players · teams', players: 4, hand: 3, teams: true}
        },
        treseta: {
            TWO_PLAYERS: {label: '2 players', players: 2, hand: 10},
            THREE_PLAYERS: {label: '3 players', players: 3, hand: 13},
            FOUR_PLAYERS_WITH_TEAMS: {label: '4 players · teams', players: 4, hand: 10, teams: true},
            FOUR_PLAYERS_NO_TEAMS: {label: '4 players · no teams', players: 4, hand: 10},
            TWO_PLAYERS_WITH_DECLARATIONS: {label: '2 players · declarations', players: 2, hand: 10, declarations: true},
            THREE_PLAYERS_WITH_DECLARATIONS: {label: '3 players · declarations', players: 3, hand: 13, declarations: true},
            FOUR_PLAYERS_WITH_TEAMS_WITH_DECLARATIONS: {label: '4 players · teams + declarations', players: 4, hand: 10, teams: true, declarations: true},
            FOUR_PLAYERS_NO_TEAMS_WITH_DECLARATIONS: {label: '4 players · no teams + declarations', players: 4, hand: 10, declarations: true}
        }
    };

    const params = new URLSearchParams(window.location.search);
    const type = params.get('type') === 'briskula' ? 'briskula' : 'treseta';
    const defaultMode = type === 'briskula' ? 'TWO_PLAYERS' : 'TWO_PLAYERS_WITH_DECLARATIONS';
    let modeName = MODES[type][params.get('mode')] ? params.get('mode') : defaultMode;
    let state;
    let generation = 0;
    let pacedAction = null;
    let selectedHandCardCode = null;

    const status = document.getElementById('sandbox-status');
    const defaultStatus = status?.textContent || '';
    const gameTypeSelect = document.getElementById('sandbox-game-type');
    const modeSelect = document.getElementById('sandbox-mode');
    const playerSelect = document.getElementById('sandbox-player');
    const declarationSelect = document.getElementById('sandbox-declaration');
    const cardPicker = document.getElementById('sandbox-card-picker');
    const handCards = document.getElementById('sandbox-hand-cards');
    const handCount = document.getElementById('sandbox-hand-count');
    const setCardButton = document.getElementById('sandbox-set-card');
    const removeCardButton = document.getElementById('sandbox-remove-card');

    function showStatus(message, isError = false) {
        if (!status) return;
        status.textContent = message || defaultStatus;
        status.classList.toggle('is-error', isError);
    }

    function card(code) {
        return {cardType: 'ITALIAN', card: code};
    }

    function cardCode(suit, value) {
        return suit + value;
    }

    function freshDeck() {
        const deck = [];
        SUITS.forEach((suit) => VALUES.forEach((value) => deck.push(card(cardCode(suit, value)))));
        for (let i = deck.length - 1; i > 0; i--) {
            const j = Math.floor(Math.random() * (i + 1));
            [deck[i], deck[j]] = [deck[j], deck[i]];
        }
        return deck;
    }

    function playerKey(player) {
        return JSON.stringify({name: player.name, id: player.id});
    }

    function publicPlayer(player) {
        return {name: player.name, id: player.id};
    }

    function mode() {
        return MODES[type][modeName];
    }

    function buildPlayers(config) {
        const lobbyOrder = [{id: SELF_ID, name: SELF_NAME, hand: [], points: 0, declarations: [], canDeclare: true}];
        for (let i = 1; i < config.players; i++) {
            lobbyOrder.push({id: String(-100 - i), name: BOT_NAMES[i - 1], hand: [], points: 0, declarations: [], canDeclare: true});
        }
        if (!config.teams) return {players: lobbyOrder, lobbyOrder};
        return {players: [lobbyOrder[0], lobbyOrder[2], lobbyOrder[1], lobbyOrder[3]], lobbyOrder};
    }

    function reset(nextMode = modeName) {
        if (!MODES[type][nextMode]) return;
        modeName = nextMode;
        generation++;
        selectedHandCardCode = null;
        clearTimeout(pacedAction);
        pacedAction = null;
        try { localStorage.removeItem('treseta-declare-skip:' + GAME_ID); } catch (_) {}

        const config = mode();
        const built = buildPlayers(config);
        const deck = freshDeck();
        const discarded = type === 'treseta' && config.players === 3 ? deck.shift() : null;
        const trump = type === 'briskula' ? deck.at(-1) : null;
        for (const player of built.players) {
            for (let i = 0; i < config.hand; i++) player.hand.push(deck.shift());
        }
        state = {
            players: built.players,
            lobbyOrder: built.lobbyOrder,
            deck,
            discarded,
            trump,
            played: [],
            ended: false,
            clearing: false
        };
        updateDeclarationControls();
        updatePlayerOptions();
        renderDiscarded();
        publishState('STARTED');
        showStatus('New local ' + config.label + ' deal. No backend game was created.');
    }

    function gameDto() {
        const cards = {};
        const points = {};
        state.players.forEach((player) => {
            cards[playerKey(player)] = player.hand.length;
            points[playerKey(player)] = player.points;
        });
        const current = state.clearing || state.ended ? null : state.players[state.played.length % state.players.length];
        const config = mode();
        return {
            id: GAME_ID,
            lobbyId: null,
            name: 'UI sandbox',
            playersOrder: state.players.map(publicPlayer),
            playersCardsMap: cards,
            playedCards: state.played.map((entry) => entry.card),
            cardsLeftInDeck: state.deck.length,
            pointsPerPerson: points,
            playersTurn: current ? publicPlayer(current) : null,
            turnEndTime: new Date(Date.now() + 300000).toISOString(),
            turnDurationSeconds: 300,
            trumpCard: state.trump,
            gameConfig: {
                numberOfPlayers: config.players,
                cardsInHandNum: config.hand,
                teamsEnabled: !!config.teams,
                declarationsEnabled: !!config.declarations,
                orderedUsers: state.lobbyOrder.map(publicPlayer)
            },
            declarations: state.players.flatMap((player) => player.declarations),
            canDeclareUserIds: config.declarations && current?.canDeclare ? [current.id] : []
        };
    }

    function ownHand() {
        return state.players.find((player) => player.id === SELF_ID)?.hand || [];
    }

    const subscriptions = new Map();
    function emit(destination, payload) {
        const body = JSON.stringify(payload);
        setTimeout(() => subscriptions.get(destination)?.forEach((callback) => callback({body})), 0);
    }

    function publishState(event = 'UPDATED', result = null) {
        if (!state) return;
        const dto = gameDto();
        window.__INITIAL_GAME__ = dto;
        window.__INITIAL_HAND__ = ownHand().slice();
        emit('/topic/game/' + GAME_ID, {gameEntity: dto, gameEvent: event, result});
        emit('/user/queue/game/cards', ownHand());
        updatePlayerOptions();
        renderHandEditor();
    }

    function currentPlayer() {
        return state.players[state.played.length % state.players.length];
    }

    function leadSuit() {
        return state.played[0]?.card?.card?.charAt(0) || null;
    }

    function legalCards(player) {
        if (type !== 'treseta' || !leadSuit()) return player.hand;
        const matching = player.hand.filter((entry) => entry.card.charAt(0) === leadSuit());
        return matching.length ? matching : player.hand;
    }

    function strength(entry) {
        const value = Number(entry.card.slice(1));
        const order = type === 'treseta'
            ? [3, 2, 1, 13, 12, 11, 7, 6, 5, 4]
            : [1, 3, 13, 12, 11, 7, 6, 5, 4, 2];
        return order.indexOf(value);
    }

    function roundWinner() {
        const lead = state.played[0].card.card.charAt(0);
        const trump = state.trump?.card?.charAt(0);
        let candidates = state.played;
        if (type === 'briskula' && candidates.some((entry) => entry.card.card.charAt(0) === trump)) {
            candidates = candidates.filter((entry) => entry.card.card.charAt(0) === trump);
        } else {
            candidates = candidates.filter((entry) => entry.card.card.charAt(0) === lead);
        }
        return candidates.reduce((best, entry) => strength(entry.card) < strength(best.card) ? entry : best).player;
    }

    function cardPoints(entry) {
        const value = Number(entry.card.slice(1));
        if (type === 'briskula') return ({1: 11, 3: 10, 13: 4, 12: 3, 11: 2})[value] || 0;
        return ({1: 3, 2: 1, 3: 1, 13: 1, 12: 1, 11: 1})[value] || 0;
    }

    function addTeamPoints(player, points) {
        player.points += points;
        if (!mode().teams) return;
        state.players[(state.players.indexOf(player) + 2) % 4].points += points;
    }

    function play(player, playedCard) {
        if (!playedCard || state.ended || state.clearing || player !== currentPlayer()) return false;
        if (!legalCards(player).some((entry) => entry.card === playedCard.card)) return false;
        player.hand = player.hand.filter((entry) => entry.card !== playedCard.card);
        player.canDeclare = false;
        state.played.push({player, card: playedCard});

        const trickSize = mode().trick || state.players.length;
        if (state.played.length < trickSize) {
            publishState();
            return true;
        }

        const winner = roundWinner();
        addTeamPoints(winner, state.played.reduce((sum, entry) => sum + cardPoints(entry.card), 0));
        state.clearing = true;
        publishState();
        const run = generation;
        pacedAction = setTimeout(() => {
            if (run !== generation) return;
            const winnerIndex = state.players.indexOf(winner);
            state.players = state.players.slice(winnerIndex).concat(state.players.slice(0, winnerIndex));
            const opponentDrawn = [];
            const drawsPerPlayer = type === 'briskula' && mode().trick === 4 ? 2 : 1;
            const shouldDraw = type === 'briskula' || (type === 'treseta' && mode().players === 2);
            if (shouldDraw) {
                for (let draw = 0; draw < drawsPerPlayer; draw++) {
                    for (const target of state.players) {
                        const drawn = state.deck.shift();
                        if (!drawn) continue;
                        target.hand.push(drawn);
                        if (type === 'treseta' && mode().players === 2 && target.id !== SELF_ID) opponentDrawn.push(drawn);
                    }
                }
            }
            state.played = [];
            state.clearing = false;
            if (opponentDrawn.length) emit('/user/queue/game/opponent-drawn-cards', opponentDrawn.slice(-1));
            publishState();
        }, 1250);
        return true;
    }

    function autoPlay() {
        if (state.ended) return showStatus('Reset the deal to keep playing.', true);
        if (state.clearing) return showStatus('The trick is being collected.', true);
        const player = currentPlayer();
        const candidates = legalCards(player);
        if (!candidates.length) return showStatus('The current player has no cards.', true);
        play(player, candidates[0]);
        showStatus(player.name + ' played ' + candidates[0].card + '.');
    }

    function finishTrick() {
        if (state.clearing || state.ended) return;
        autoPlay();
        if (!state.clearing && state.played.length) pacedAction = setTimeout(finishTrick, 620);
    }

    function selectedPlayer() {
        return state.players.find((player) => player.id === playerSelect?.value) || state.players[0];
    }

    function takeCard(code, target, index = target.hand.length) {
        if (target.hand.some((entry) => entry.card === code)) return true;
        if (state.played.some((entry) => entry.card.card === code)) return false;
        state.players.forEach((player) => {
            player.hand = player.hand.filter((entry) => entry.card !== code);
        });
        state.deck = state.deck.filter((entry) => entry.card !== code);
        if (state.discarded?.card === code) state.discarded = null;
        target.hand.splice(index, 0, card(code));
        if (type === 'briskula') state.trump = state.deck.at(-1) || null;
        renderDiscarded();
        return true;
    }

    function declarationSpec(value = declarationSelect?.value, target = selectedPlayer()) {
        if (String(value).startsWith('NAPOLITANA_')) {
            const suit = String(value).slice(-1);
            return {type: 'NAPOLITANA', codes: [1, 2, 3].map((rank) => cardCode(suit, rank)), points: 9};
        }
        const [type, countText] = String(value || 'ACES_3').split('_');
        const rank = {ACES: 1, TWOS: 2, THREES: 3}[type];
        const count = Number(countText) || 3;
        const codes = target.hand
                .filter((entry) => Number(entry.card.slice(1)) === rank)
                .map((entry) => entry.card)
                .slice(0, count);
        for (const suit of SUITS) {
            const code = cardCode(suit, rank);
            if (codes.length < count && !codes.includes(code)) codes.push(code);
        }
        return {type, codes, points: count === 4 ? 12 : 9};
    }

    function rigDeclaration() {
        if (type !== 'treseta') return;
        const target = selectedPlayer();
        const spec = declarationSpec();
        if (spec.codes.some((code) => state.played.some((entry) => entry.card.card === code))) {
            return showStatus('Finish the current trick before moving one of its cards.', true);
        }
        spec.codes.forEach((code) => takeCard(code, target));
        target.canDeclare = true;
        publishState();
        showStatus(target.name + ' now holds ' + declarationSelect.selectedOptions[0].text + '.');
    }

    function addDeclaration(target, spec) {
        if (!spec.codes.every((code) => target.hand.some((entry) => entry.card === code))) return false;
        const suits = [...new Set(spec.codes.map((code) => SUIT_NAMES[code.charAt(0)]))];
        const signature = spec.type + ':' + suits.slice().sort().join(',');
        if (target.declarations.some((entry) => entry.signature === signature)) return false;
        const replaced = target.declarations.filter((entry) => entry.type === spec.type && entry.suits.length === 3);
        if (suits.length === 3 && target.declarations.some((entry) => entry.type === spec.type && entry.suits.length === 4))
            return false;
        const upgrading = suits.length === 4 && replaced.length > 0;
        if (upgrading)
            target.declarations = target.declarations.filter((entry) => !replaced.includes(entry));
        target.declarations.push({
            player: publicPlayer(target),
            type: spec.type,
            suits,
            points: spec.points,
            signature
        });
        const replacedPoints = upgrading ? replaced.reduce((sum, entry) => sum + entry.points, 0) : 0;
        addTeamPoints(target, spec.points - replacedPoints);
        return true;
    }

    function updateDeclarationOptions() {
        if (!declarationSelect || type !== 'treseta') return;
        const declarations = selectedPlayer().declarations;
        for (const option of declarationSelect.options) {
            const [declarationType, count] = option.value.split('_');
            option.disabled = count === '3'
                && declarations.some((entry) => entry.type === declarationType && entry.suits.length === 4);
        }
        if (declarationSelect.selectedOptions[0]?.disabled)
            declarationSelect.value = Array.from(declarationSelect.options).find((option) => !option.disabled)?.value || '';
    }

    function declareSelected() {
        if (type !== 'treseta' || !mode().declarations) return showStatus('Choose a declaration-enabled Treseta mode.', true);
        const target = selectedPlayer();
        const spec = declarationSpec();
        if (!addDeclaration(target, spec)) return showStatus('Rig that declaration first, or choose one not already shown.', true);
        publishState();
        showStatus(target.name + ' declared ' + declarationSelect.selectedOptions[0].text + '.');
    }

    function declareFromMessage(payload) {
        const target = state.players.find((player) => player.id === SELF_ID);
        const cards = Array.isArray(payload?.cards) ? payload.cards : [];
        const values = new Set(cards.map((entry) => Number(entry.card.slice(1))));
        const suits = new Set(cards.map((entry) => entry.card.charAt(0)));
        let spec = null;
        if (values.size === 1 && cards.length >= 3 && cards.length <= 4) {
            const typeName = {1: 'ACES', 2: 'TWOS', 3: 'THREES'}[[...values][0]];
            if (typeName) spec = {type: typeName, codes: cards.map((entry) => entry.card), points: cards.length === 4 ? 12 : 9};
        } else if (cards.length === 3 && suits.size === 1 && [1, 2, 3].every((value) => values.has(value))) {
            spec = {type: 'NAPOLITANA', codes: cards.map((entry) => entry.card), points: 9};
        }
        if (spec && addDeclaration(target, spec)) publishState();
    }

    function renderHandEditor() {
        if (!handCards || !state) return;
        const target = selectedPlayer();
        updateDeclarationOptions();
        if (!target.hand.some((entry) => entry.card === selectedHandCardCode)) selectedHandCardCode = null;
        handCount.textContent = target.hand.length + ' cards';
        handCards.replaceChildren(...target.hand.map((entry) => {
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'sandbox-hand-card';
            button.classList.toggle('is-selected', entry.card === selectedHandCardCode);
            button.dataset.cardCode = entry.card;
            button.title = (entry.card === selectedHandCardCode ? 'Deselect ' : 'Select ') + entry.card;
            button.setAttribute('aria-pressed', String(entry.card === selectedHandCardCode));
            const image = window.UltracardsGameUi?.renderCardImage({card: entry, alt: entry.card});
            if (image) button.appendChild(image);
            button.addEventListener('click', () => {
                selectedHandCardCode = selectedHandCardCode === entry.card ? null : entry.card;
                renderHandEditor();
            });
            return button;
        }));
        setCardButton.textContent = selectedHandCardCode ? 'Replace card' : 'Add card';
        removeCardButton.disabled = !selectedHandCardCode;
    }

    function setHandCard() {
        const target = selectedPlayer();
        const code = cardPicker?.value;
        if (!code) return;
        if (state.played.some((entry) => entry.card.card === code)) {
            return showStatus(code + ' is currently on the table.', true);
        }
        const selectedIndex = target.hand.findIndex((entry) => entry.card === selectedHandCardCode);
        const existingIndex = target.hand.findIndex((entry) => entry.card === code);
        if (existingIndex >= 0 && existingIndex !== selectedIndex) {
            return showStatus(target.name + ' already has ' + code + '.', true);
        }
        if (selectedIndex >= 0 && target.hand[selectedIndex].card === code) {
            return showStatus(code + ' is already selected.', true);
        }
        if (selectedIndex >= 0) state.deck.unshift(target.hand.splice(selectedIndex, 1)[0]);
        if (!takeCard(code, target, selectedIndex >= 0 ? selectedIndex : target.hand.length)) return;
        selectedHandCardCode = null;
        publishState();
        showStatus((selectedIndex >= 0 ? 'Replaced a card in ' : 'Added ' + code + ' to ') + target.name + "'s hand.");
    }

    function removeSelectedCard() {
        const target = selectedPlayer();
        const index = target.hand.findIndex((entry) => entry.card === selectedHandCardCode);
        if (index < 0) return;
        const [removed] = target.hand.splice(index, 1);
        state.deck.unshift(removed);
        if (type === 'briskula') state.trump = state.deck.at(-1) || null;
        selectedHandCardCode = null;
        publishState();
        showStatus('Removed ' + removed.card + ' from ' + target.name + "'s hand.");
    }

    function changePoints(direction) {
        const target = selectedPlayer();
        const step = type === 'treseta' ? 3 : 10;
        target.points = Math.max(0, target.points + direction * step);
        publishState();
        showStatus('Updated ' + target.name + "'s score.");
    }

    function winners() {
        if (mode().teams) {
            const first = state.players[0].points;
            const second = state.players[1].points;
            if (first === second) return state.players;
            return first > second ? [state.players[0], state.players[2]] : [state.players[1], state.players[3]];
        }
        const high = Math.max(...state.players.map((player) => player.points));
        return state.players.filter((player) => player.points === high);
    }

    function showResult() {
        generation++;
        clearTimeout(pacedAction);
        pacedAction = null;
        if (state.players.every((player) => player.points === 0)) addTeamPoints(state.players[0], type === 'treseta' ? 33 : 70);
        state.players.forEach((player) => { player.hand = []; });
        state.deck = [];
        state.played = [];
        state.ended = true;
        const winningPlayers = winners().map(publicPlayer);
        publishState('RESULTED', {gameWinners: winningPlayers, winnerPointsNum: Math.max(...state.players.map((player) => player.points))});
        showStatus('Showing the local result state. New deal resets it.');
    }

    function renderDiscarded() {
        document.querySelector('.discarded-card-display')?.remove();
        if (!state?.discarded) return;
        const display = document.createElement('div');
        display.className = 'discarded-card-display';
        display.setAttribute('role', 'note');
        const rendered = window.UltracardsGameUi?.renderCardImage({card: state.discarded, className: 'discarded-card', alt: 'Card out of play'});
        if (rendered) display.appendChild(rendered);
        const label = document.createElement('span');
        label.className = 'discarded-card-label';
        label.textContent = 'Card out of play';
        display.appendChild(label);
        document.querySelector('.table-surface')?.appendChild(display);
    }

    function updatePlayerOptions() {
        if (!playerSelect || !state) return;
        const selected = playerSelect.value;
        playerSelect.replaceChildren(...state.players.map((player) => {
            const option = document.createElement('option');
            option.value = player.id;
            option.textContent = player.name;
            return option;
        }));
        if (state.players.some((player) => player.id === selected)) playerSelect.value = selected;
    }

    function populateCardPicker() {
        if (!cardPicker) return;
        cardPicker.replaceChildren(...SUITS.map((suit) => {
            const group = document.createElement('optgroup');
            group.label = SUIT_LABELS[suit];
            group.append(...VALUES.map((value) => {
                const option = document.createElement('option');
                option.value = cardCode(suit, value);
                option.textContent = VALUE_LABELS[value] + ' of ' + SUIT_LABELS[suit].toLowerCase();
                return option;
            }));
            return group;
        }));
    }

    function populateControls() {
        gameEl.dataset.gameType = type;
        gameEl.dataset.gameId = GAME_ID;
        gameTypeSelect.value = type;
        modeSelect.replaceChildren(...Object.entries(MODES[type]).map(([name, config]) => {
            const option = document.createElement('option');
            option.value = name;
            option.textContent = config.label;
            option.selected = name === modeName;
            return option;
        }));
        const isTreseta = type === 'treseta';
        document.querySelector('.game-layout')?.classList.toggle('treseta-game-layout', isTreseta);
        const shell = document.querySelector('.game-shell');
        shell?.classList.toggle('treseta-game-shell', isTreseta);
        shell?.classList.toggle('briskula-game-shell', !isTreseta);
        updateDeclarationControls();
    }

    function updateDeclarationControls() {
        const enabled = type === 'treseta' && !!mode().declarations;
        document.getElementById('sandbox-declare-actions')?.toggleAttribute('hidden', !enabled);
        document.getElementById('sandbox-declaration-field')?.toggleAttribute('hidden', !enabled);
    }

    function handleSend(destination, body) {
        let payload = {};
        try { payload = JSON.parse(body || '{}'); } catch (_) {}
        if (destination === '/app/game/play') {
            const player = currentPlayer();
            if (player?.id === SELF_ID) play(player, payload);
        } else if (destination === '/app/game/declare') {
            declareFromMessage(payload);
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

    populateControls();
    populateCardPicker();
    reset();
    window.__INITIAL_GAME_CHAT__ = null;

    on('sandbox-game-type', 'change', () => {
        const next = new URLSearchParams(window.location.search);
        next.set('type', gameTypeSelect.value);
        next.delete('mode');
        window.location.search = next.toString();
    });
    on('sandbox-mode', 'change', () => {
        const next = new URLSearchParams(window.location.search);
        next.set('type', type);
        next.set('mode', modeSelect.value);
        window.history.replaceState(null, '', '?' + next.toString());
        reset(modeSelect.value);
    });
    on('sandbox-auto-play', 'click', autoPlay);
    on('sandbox-play-trick', 'click', finishTrick);
    on('sandbox-player', 'change', () => {
        selectedHandCardCode = null;
        renderHandEditor();
    });
    on('sandbox-set-card', 'click', setHandCard);
    on('sandbox-remove-card', 'click', removeSelectedCard);
    on('sandbox-bot-declare', 'click', declareSelected);
    on('sandbox-rig', 'click', rigDeclaration);
    on('sandbox-points-add', 'click', () => changePoints(1));
    on('sandbox-points-remove', 'click', () => changePoints(-1));
    on('sandbox-end', 'click', showResult);
    on('sandbox-reset', 'click', () => reset());
})();
