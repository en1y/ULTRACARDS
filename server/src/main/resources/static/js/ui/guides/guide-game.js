/*
 * Guided fake-game simulator for the public how-to-play pages. Like the admin
 * sandbox (js/ui/sandbox.js) it feeds the REAL live-game renderer through an
 * in-page STOMP-compatible transport, so the board, gestures and animations are
 * identical to a live match. Here the game is played against bots: a coach
 * bubble flies around the table narrating every phase and pointing at whatever
 * it explains (including the card to play), and a mode selector lets you deal
 * and explore each supported game mode with the same preset hands.
 *
 * All coach text is resolved through window.t, so it follows the site
 * language. Only a shortened demo hand is dealt (deck of a few cards) so drawing,
 * the trump, and the end-game all fit in a couple of minutes.
 */
(() => {
    const gameEl = document.getElementById('game-container');
    if (!gameEl || gameEl.dataset.guide !== '1') return;

    const type = gameEl.dataset.gameType === 'briskula' ? 'briskula' : 'treseta';
    const GAME_ID = 'ui-guide';
    const SELF_ID = String(gameEl.dataset.currentUserId || '1');
    const SELF_NAME = gameEl.dataset.username || 'You';
    const BOT_NAMES = ['Bot Ana', 'Bot Ivo', 'Bot Mia'];

    const T = (key, ...args) => (window.t ? window.t(key, ...args) : key);
    const modeSelect = document.getElementById('guide-mode');
    if (!modeSelect?.options.length) return;

    // Options are rendered from the real Java enums by GuideUIController.
    const MODES = Object.fromEntries(Array.from(modeSelect.options, (option) => [option.value, {
        players: Number(option.dataset.players),
        hand: Number(option.dataset.hand),
        teams: option.dataset.teams === 'true',
        declarations: option.dataset.declarations === 'true'
    }]));
    const DEFAULT_MODE = type === 'treseta' && MODES.TWO_PLAYERS_WITH_DECLARATIONS
        ? 'TWO_PLAYERS_WITH_DECLARATIONS'
        : 'TWO_PLAYERS';
    let modeName = MODES[DEFAULT_MODE] ? DEFAULT_MODE : Object.keys(MODES)[0];
    const mode = () => MODES[modeName];

    // Keep the on-screen demo short: briskula deals its real (small) hand; treseta
    // deals a trimmed 4-card hand. The fixed deals teach trumping, following suit,
    // card strength and declarations; the tiny deck shows drawing and the end-game.
    const PRESET_HANDS = {
        briskula: [
            ['D1', 'B6', 'C2', 'S3'],
            ['S13', 'C7', 'B4', 'S2'],
            ['C13', 'S7', 'B2', 'C4'],
            ['B13', 'D7', 'C6', 'S4']
        ],
        treseta: [
            ['S2', 'S3', 'S1', 'C4'],
            ['S13', 'C7', 'D4', 'B6'],
            ['C3', 'D7', 'B4', 'S6'],
            ['D3', 'B7', 'C5', 'S4']
        ]
    };
    const PRESET_DRAWS = {
        briskula: ['C1', 'D3', 'B1', 'C5', 'D5', 'B5', 'D2', 'D4'],
        treseta: ['C1', 'D1', 'B1', 'S5']
    };
    const demoHand = () => (type === 'briskula' ? mode().hand : 4);
    const drawsCards = () => type === 'briskula' || mode().players === 2;
    const demoDeck = () => (drawsCards() ? 2 * mode().players : 0);

    const card = (code) => ({ cardType: 'ITALIAN', card: code });
    const publicPlayer = (p) => ({ name: p.name, id: p.id });
    const playerKey = (p) => JSON.stringify({ name: p.name, id: p.id });

    let state;
    let phase = 'intro';        // intro | play | final
    let generation = 0;
    let pacedAction = null;
    let pendingResult = null;   // narration to prepend after a trick resolves
    let pendingNotice = '';
    let bootstrapped = false;   // live-game reads the first hand from globals; don't also emit it
    let introStep = 0;
    let undoState = null;       // snapshot taken when it becomes your turn, for "Previous"

    const coachEl = document.getElementById('guide-coach');
    const primaryBtn = document.getElementById('guide-primary');
    const previousBtn = document.getElementById('guide-previous');
    const hintEl = document.getElementById('guide-hint');
    const hideBtn = document.getElementById('guide-hide');

    function showHint(on) { if (hintEl) hintEl.hidden = !on; }

    // ---- Floating coach ----
    // Absolutely positioned inside .game-layout (which is position: relative), so
    // the browser scrolls it with the board and we never re-solve on scroll. It
    // owns the table behind a scrim while it has something to say, and disappears
    // completely while you play — the mascot button calls it back.
    const bubbleEl = document.getElementById('guide-coach-bubble');
    const scrimEl = document.getElementById('guide-coach-scrim');
    const puckEl = document.getElementById('guide-coach-puck');
    const boardEl = document.querySelector('.game-layout');
    const SIDES = ['tail-top', 'tail-bottom', 'tail-left', 'tail-right', 'tail-none'];
    const GAP = 16;   // bubble-to-target breathing room
    const EDGE = 10;  // keep this far inside the board's edges
    let coachTarget = null;
    let coachFrame = null;
    let litElement = null;
    let modal = true;      // a message is up: centre stage, board locked until Next
    let dismissed = false; // ...but the reader pressed Hide on this one
    let puckOpen = false;  // mid-play, the reader tapped the mascot to catch up
    let flown = false;     // the very first placement must not fly in from a corner

    function setCoach(html) {
        if (!coachEl) return;
        coachEl.innerHTML = html;
        bubbleEl?.classList.remove('is-speaking');
        void bubbleEl?.offsetWidth;             // restart the pop on every new line
        bubbleEl?.classList.add('is-speaking');
        if (!modal && !puckOpen) puckEl?.classList.add('has-news');
        scheduleCoach();
    }

    // Message that has to be acknowledged (intro step, round recap, final score)
    // versus play, where the coach gets out of the way.
    function setModal(on) {
        modal = on;
        dismissed = false;
        puckOpen = false;
        scheduleCoach();
    }

    function pointAt(selector) {
        coachTarget = selector;
        scheduleCoach();
    }

    function scheduleCoach() {
        if (coachFrame) return;
        coachFrame = requestAnimationFrame(() => {
            coachFrame = null;
            positionCoach();
        });
    }

    function light(el) {
        if (litElement === el) return;
        litElement?.classList.remove('guide-coach-target');
        litElement = el;
        el?.classList.add('guide-coach-target');
    }

    function place(side, x, y, tailX, tailY) {
        SIDES.forEach((name) => bubbleEl.classList.toggle(name, name === side));
        bubbleEl.style.setProperty('--coach-x', Math.round(x) + 'px');
        bubbleEl.style.setProperty('--coach-y', Math.round(y) + 'px');
        bubbleEl.style.setProperty('--tail-x', Math.round(tailX) + 'px');
        bubbleEl.style.setProperty('--tail-y', Math.round(tailY) + 'px');
        bubbleEl.classList.add('is-visible');
        // Flights are for moving between things it explains, never for the entrance.
        if (!flown) {
            flown = true;
            requestAnimationFrame(() => bubbleEl.classList.add('can-fly'));
        }
    }

    function positionCoach() {
        if (!bubbleEl || !boardEl) return;
        const speaking = modal && !dismissed;
        const showing = speaking || puckOpen;
        // Only dim once the demo is actually running — there is nothing to lock
        // away during the intro walkthrough.
        if (scrimEl) scrimEl.hidden = !speaking || phase === 'intro';
        if (puckEl) {
            puckEl.hidden = speaking;
            puckEl.classList.toggle('is-open', puckOpen);
            if (puckOpen) puckEl.classList.remove('has-news');
        }
        bubbleEl.classList.toggle('is-visible', showing);
        const found = coachTarget ? document.querySelector(coachTarget) : null;
        const hit = found?.getBoundingClientRect();
        light(hit?.width ? found : null);   // the highlight stays on even when hidden

        // Everything below is in board coordinates: the board is the coach's world.
        const board = boardEl.getBoundingClientRect();
        const fit = (v, size, limit) => Math.min(Math.max(v, EDGE), Math.max(EDGE, limit - size - EDGE));

        // Sit the mascot on the table's top-left corner. The board's own corner is
        // dead space well away from the felt, where it just reads as hidden.
        const felt = document.querySelector('.table-felt, .table-surface')?.getBoundingClientRect();
        if (puckEl && !puckEl.hidden && felt?.width) {
            const pw = puckEl.offsetWidth;
            const ph = puckEl.offsetHeight;
            puckEl.style.setProperty('--puck-x', fit(felt.left - board.left - pw / 2, pw, board.width) + 'px');
            puckEl.style.setProperty('--puck-y', fit(felt.top - board.top - ph / 2, ph, board.height) + 'px');
        }
        if (!showing) return;

        const w = bubbleEl.offsetWidth;
        const h = bubbleEl.offsetHeight;
        if (!hit?.width) {
            // Nothing to point at. A message with the table empty belongs in the
            // middle of it; the puck-opened bubble tucks under the mascot instead.
            const x = speaking ? (board.width - w) / 2 : EDGE;
            const y = speaking ? (board.height - h) / 2 : puckEl.offsetHeight + 2 * EDGE;
            place('tail-none', fit(x, w, board.width), fit(y, h, board.height), 0, 0);
            return;
        }
        const midX = hit.left + hit.width / 2 - board.left;
        const midY = hit.top + hit.height / 2 - board.top;
        const left = hit.left - board.left;
        const top = hit.top - board.top;
        // Only the axis that separates the bubble from the target has to fit — the
        // other is slid back inside the board, which is what keeps a bubble nearly
        // as wide as a phone from rejecting every side.
        const options = [
            ['tail-top', midX - w / 2, top + hit.height + GAP, 'y'],
            ['tail-bottom', midX - w / 2, top - GAP - h, 'y'],
            ['tail-left', left + hit.width + GAP, midY - h / 2, 'x'],
            ['tail-right', left - GAP - w, midY - h / 2, 'x']
        ];
        const fits = ([, x, y, axis]) => (axis === 'y'
            ? y >= EDGE && y + h <= board.height - EDGE
            : x >= EDGE && x + w <= board.width - EDGE);
        const [side, wantX, wantY] = options.find(fits) || options[0];
        const x = fit(wantX, w, board.width);
        const y = fit(wantY, h, board.height);
        // Tail offsets come off the final position, so sliding the bubble back in
        // slides the tail the other way and it keeps pointing home.
        place(side, x, y,
            Math.min(Math.max(midX - x, 18), w - 18),
            Math.min(Math.max(midY - y, 18), h - 18));
    }

    puckEl?.addEventListener('click', () => {
        puckOpen = !puckOpen;
        if (puckOpen) dismissed = false;
        scheduleCoach();
    });

    hideBtn?.addEventListener('click', () => {
        dismissed = true;
        puckOpen = false;
        scheduleCoach();
    });

    new MutationObserver(scheduleCoach).observe(gameEl, { childList: true, subtree: true });
    window.addEventListener('resize', scheduleCoach);

    // ---- Build + publish ----
    function buildState() {
        const config = mode();
        const self = { id: SELF_ID, name: SELF_NAME, hand: [], points: 0 };
        const seats = [self];
        for (let i = 1; i < config.players; i++) {
            seats.push({ id: String(-100 - i), name: BOT_NAMES[i - 1], hand: [], points: 0 });
        }
        seats.forEach((player, index) => {
            player.hand = PRESET_HANDS[type][index].slice(0, demoHand()).map(card);
        });
        const lobbyOrder = seats.slice();
        // Teams sit across from each other (0&2 vs 1&3); a bot leads the first trick.
        const players = config.teams ? [seats[0], seats[2], seats[1], seats[3]] : seats.slice();
        const ordered = players.slice(1).concat(players.slice(0, 1)); // bot leads

        const deckSize = demoDeck();
        let deckCodes = PRESET_DRAWS[type].slice(0, deckSize);
        if (type === 'briskula' && deckSize) {
            deckCodes = PRESET_DRAWS.briskula.slice(0, deckSize - 1).concat('D4');
        }
        const keep = deckCodes.map(card);
        const discarded = type === 'treseta' && config.players === 3 ? card('D1') : null;
        const trump = type === 'briskula' ? keep[keep.length - 1] : null;

        return {
            players: ordered, lobbyOrder, deck: keep, discarded, trump,
            played: [], declarations: [],
            canDeclare: new Set(config.declarations ? ordered.map((player) => player.id) : []),
            ended: false, clearing: false, deckStarted: keep.length
        };
    }

    function currentPlayer() { return state.players[state.played.length % state.players.length]; }

    function gameDto() {
        const cards = {}, points = {};
        state.players.forEach((p) => { cards[playerKey(p)] = p.hand.length; points[playerKey(p)] = p.points; });
        const current = state.clearing || state.ended ? null : currentPlayer();
        const config = mode();
        return {
            id: GAME_ID, lobbyId: null, name: 'How to play',
            playersOrder: state.players.map(publicPlayer),
            playersCardsMap: cards,
            playedCards: state.played.map((e) => e.card),
            cardsLeftInDeck: state.deck.length,
            pointsPerPerson: points,
            playersTurn: current ? publicPlayer(current) : null,
            turnEndTime: new Date(Date.now() + 300000).toISOString(),
            turnDurationSeconds: 300,
            trumpCard: state.trump,
            gameConfig: {
                numberOfPlayers: config.players, cardsInHandNum: demoHand(),
                teamsEnabled: !!config.teams, declarationsEnabled: !!config.declarations,
                orderedUsers: state.lobbyOrder.map(publicPlayer)
            },
            declarations: state.declarations,
            canDeclareUserIds: current && state.canDeclare.has(current.id) ? [current.id] : []
        };
    }

    function ownHand() { return state.players.find((p) => p.id === SELF_ID)?.hand || []; }

    const subscriptions = new Map();
    function emit(destination, payload) {
        const body = JSON.stringify(payload);
        setTimeout(() => subscriptions.get(destination)?.forEach((cb) => cb({ body })), 0);
    }

    function publishState(event = 'UPDATED', result = null) {
        if (!state) return;
        const dto = gameDto();
        window.__INITIAL_GAME__ = dto;
        window.__INITIAL_HAND__ = ownHand().slice();
        // On the very first publish, live-game bootstraps from the globals above at
        // its own init — emitting the same state again would re-render the freshly
        // dealt cards mid-flip and leave them stuck face-down. Skip that one emit.
        if (bootstrapped) {
            emit('/topic/game/' + GAME_ID, { gameEntity: dto, gameEvent: event, result });
            emit('/user/queue/game/cards', ownHand());
        }
        bootstrapped = true;
        scheduleNext();
    }

    // ---- Rules ----
    function leadSuit() { return state.played[0]?.card?.card?.charAt(0) || null; }
    function legalCards(player) {
        if (type !== 'treseta' || !leadSuit()) return player.hand;
        const matching = player.hand.filter((e) => e.card.charAt(0) === leadSuit());
        return matching.length ? matching : player.hand;
    }
    function strength(entry) {
        const value = Number(entry.card.slice(1));
        const order = type === 'treseta' ? [3, 2, 1, 13, 12, 11, 7, 6, 5, 4] : [1, 3, 13, 12, 11, 7, 6, 5, 4, 2];
        return order.indexOf(value);
    }
    function trickWinner(played) {
        const lead = played[0].card.card.charAt(0);
        const trump = state.trump?.card?.charAt(0);
        let candidates = played;
        if (type === 'briskula' && candidates.some((e) => e.card.card.charAt(0) === trump)) {
            candidates = candidates.filter((e) => e.card.card.charAt(0) === trump);
        } else {
            candidates = candidates.filter((e) => e.card.card.charAt(0) === lead);
        }
        return candidates.reduce((best, e) => (strength(e.card) < strength(best.card) ? e : best));
    }
    function cardPoints(entry) {
        const value = Number(entry.card.slice(1));
        if (type === 'briskula') return ({ 1: 11, 3: 10, 13: 4, 12: 3, 11: 2 })[value] || 0;
        return ({ 1: 3, 2: 1, 3: 1, 13: 1, 12: 1, 11: 1 })[value] || 0;
    }
    function coachPoints(points) {
        if (type !== 'treseta') return points;
        return Math.floor((Number(points) || 0) / 3 * 10) / 10;
    }
    function addPoints(player, pts) {
        player.points += pts;
        if (mode().teams) state.players[(state.players.indexOf(player) + 2) % 4].points += pts;
    }

    // A simple, teachable heuristic used for both the bot and the player's arrow.
    function suggestCard(player) {
        const legal = legalCards(player);
        const byWeak = legal.slice().sort((a, b) => strength(b) - strength(a)); // weakest first
        if (!state.played.length) {
            // Leading: lead a low-value card, keep the strong ones.
            const zeros = byWeak.filter((e) => cardPoints(e) === 0);
            return (zeros[0] || byWeak[0]).card;
        }
        const trickPts = state.played.reduce((s, e) => s + cardPoints(e.card), 0);
        const winners = legal.filter((e) => trickWinner(state.played.concat([{ player, card: e }])).player === player);
        if (winners.length && (trickPts > 0 || player.hand.length <= 2)) {
            // Win as cheaply as possible when the trick is worth taking.
            return winners.sort((a, b) => cardPoints(a) - cardPoints(b) || strength(b) - strength(a))[0].card;
        }
        // Otherwise dump the weakest, cheapest card.
        return byWeak.sort((a, b) => cardPoints(a) - cardPoints(b))[0].card;
    }

    function play(player, playedCard) {
        if (!playedCard || state.ended || state.clearing || player !== currentPlayer()) return false;
        if (!legalCards(player).some((e) => e.card === playedCard.card)) return false;
        state.canDeclare.delete(player.id);
        player.hand = player.hand.filter((e) => e.card !== playedCard.card);
        state.played.push({ player, card: playedCard });

        if (state.played.length < state.players.length) { publishState(); return true; }

        const winnerEntry = trickWinner(state.played);
        const winner = winnerEntry.player;
        const wonByTrump = type === 'briskula' && winnerEntry.card.card.charAt(0) === state.trump?.card?.charAt(0)
            && state.played.some((e) => e.card.card.charAt(0) !== state.trump?.card?.charAt(0));
        const trickPts = state.played.reduce((s, e) => s + cardPoints(e.card), 0);
        addPoints(winner, trickPts);
        const finalTrick = state.players.every((p) => p.hand.length === 0);
        const lastTrickPoints = finalTrick && type === 'treseta' ? 3 : 0;
        if (lastTrickPoints) addPoints(winner, lastTrickPoints); // +1 point, stored in thirds

        pendingResult = { self: winner.id === SELF_ID, byTrump: wonByTrump, pts: trickPts + lastTrickPoints,
            winnerName: winner.name, lastTrick: finalTrick };
        state.clearing = true;
        publishState();

        const run = generation;
        pacedAction = setTimeout(() => {
            if (run !== generation) return;
            const wi = state.players.indexOf(winner);
            state.players = state.players.slice(wi).concat(state.players.slice(0, wi));
            const before = state.deck.length;
            if (drawsCards() && state.deck.length) {
                for (const p of state.players) { const d = state.deck.shift(); if (d) p.hand.push(d); }
            }
            if (before > 0 && state.deck.length === 0) pendingResult.trumpGone = true;
            state.played = [];
            state.clearing = false;
            if (finalTrick) state.ended = true;
            publishState();
        }, 1250);
        return true;
    }

    function botPlay() {
        if (state.ended || state.clearing) return;
        const player = currentPlayer();
        if (player.id === SELF_ID) return;
        play(player, card(suggestCard(player)));
    }

    // ---- Flow / narration ----
    function botDelay() { return 1300; }

    function resultLine() {
        if (!pendingResult) return '';
        const r = pendingResult;
        let msg;
        const points = coachPoints(r.pts);
        if (r.self) msg = r.byTrump ? T('guides.play.wonTrump', points) : T('guides.play.wonTrick', points);
        else msg = T('guides.play.lostTrick', r.winnerName);
        if (r.trumpGone) {
            msg += ' ' + T(type === 'briskula' ? 'guides.play.trumpGone' : 'guides.play.deckEmpty');
        }
        return '<p class="guide-coach-result">' + msg + '</p>';
    }

    function turnLine() {
        const player = currentPlayer();
        if (player.id !== SELF_ID) return '<p>' + T('guides.play.botTurn', player.name) + '</p>';
        if (type === 'treseta' && state.canDeclare.has(SELF_ID)) {
            return '<p>' + T('guides.play.declareNow') + '</p>';
        }
        if (!state.played.length) return '<p>' + T('guides.play.lead') + '</p>';
        if (type === 'treseta') {
            const hasSuit = ownHand().some((e) => e.card.charAt(0) === leadSuit());
            return '<p>' + T(hasSuit ? 'guides.play.follow' : 'guides.play.followFree') + '</p>';
        }
        return '<p>' + T('guides.play.followBriskula') + '</p>';
    }

    function scheduleNext() {
        clearTimeout(pacedAction);
        pacedAction = null;
        pointAt(null);
        showHint(false);
        if (phase !== 'play') return;
        if (state.clearing) return;
        if (state.ended || state.players.every((p) => p.hand.length === 0)) return showFinal();

        // A round just resolved: hold the recap on screen until Next is pressed, so
        // the coach never talks over its own result. Re-entrant on purpose —
        // pendingResult is only cleared by the Next click.
        if (pendingResult) {
            setModal(true);
            setCoach(resultLine() + pendingNotice);
            setControls('guides.coach.next');
            return;
        }

        const lastTrick = state.players.reduce((s, p) => s + p.hand.length, 0) === state.players.length && !state.deck.length;
        const banner = lastTrick ? '<p class="guide-coach-result">' + T('guides.play.lastTrick') + '</p>' : '';
        setModal(false);
        setCoach(pendingNotice + banner + turnLine());
        pendingNotice = '';

        if (currentPlayer().id === SELF_ID) {
            undoState = structuredClone(state); // "Previous" replays from here
            if (!state.canDeclare.has(SELF_ID)) {
                pointAt('.hand-cards [data-card-code="' + suggestCard(currentPlayer()) + '"]');
                showHint(true);
            }
        } else {
            const run = generation;
            pacedAction = setTimeout(() => { if (run === generation && phase === 'play') botPlay(); }, botDelay());
        }
        setControls(null);
    }

    // Primary button shows a label or hides; Previous is always there and only
    // toggles between "step back in the intro" and "replay your last move".
    function setControls(primaryKey) {
        if (!primaryBtn) return;
        primaryBtn.hidden = !primaryKey;
        if (primaryKey) primaryBtn.textContent = T(primaryKey);
        previousBtn.hidden = !(phase === 'intro' ? introStep > 0 : undoState);
    }

    function setPhase(next) {
        phase = next;
        if (next === 'final') setControls('guides.interactive.restart');
        else if (next === 'play') setControls(null);
    }

    function forgetDeclareSkip() {
        if (type !== 'treseta') return;
        try { localStorage.removeItem('treseta-declare-skip:' + GAME_ID); } catch (_) {}
    }

    // Rewind to the start of your last turn and play it again.
    function replayMove() {
        if (!undoState) return;
        generation++;                      // cancel queued bot moves / trick clearing
        clearTimeout(pacedAction);
        pacedAction = null;
        pendingResult = null;
        pendingNotice = '';
        forgetDeclareSkip();
        state = structuredClone(undoState);
        setPhase('play');
        publishState();
    }

    function winnersText() {
        const config = mode();
        const me = state.players.find((player) => player.id === SELF_ID);
        const myPoints = coachPoints(me?.points || 0);
        if (config.teams) {
            const a = state.players[0].points, b = state.players[1].points;
            if (a === b) return { tie: true, pts: myPoints };
            const meWins = (state.players[0].id === SELF_ID || state.players[2].id === SELF_ID) ? a > b : b > a;
            const winner = a > b ? state.players[0] : state.players[1];
            return { self: meWins, pts: myPoints, name: winner.name };
        }
        const high = Math.max(...state.players.map((p) => p.points));
        const leaders = state.players.filter((p) => p.points === high);
        if (leaders.length > 1) return { tie: true, pts: myPoints };
        return { self: leaders[0].id === SELF_ID, pts: myPoints, name: leaders[0].name };
    }

    function showFinal() {
        setModal(true);
        pointAt(null);
        showHint(false);
        const r = winnersText();
        let msg;
        if (r.tie) msg = T('guides.play.resultTie', r.pts);
        else if (r.self) msg = T('guides.play.resultWin', r.pts);
        else msg = T('guides.play.resultLose', r.name || currentPlayer().name, r.pts);
        setCoach(resultLine() + '<p>' + msg + '</p>');
        pendingResult = null;
        setPhase('final');
    }

    function introSteps() {
        const presetKey = type === 'briskula'
            ? 'guides.play.briskulaPreset'
            : (mode().declarations ? 'guides.play.declarationPreset' : 'guides.play.tresetaPreset');
        const steps = [
            {html: '<p>' + T('guides.' + type + '.play.intro') + '</p>'},
            {html: '<p class="guide-mode-explain">' + T('guides.mode.' + type + '.' + modeName) + '</p>'}
        ];
        if (type === 'briskula') {
            steps.push({
                html: '<p>' + T('guides.play.trumpExplain') + '</p>',
                target: '#trump-card'
            });
        } else if (mode().declarations) {
            steps.push({html: '<p>' + T('guides.play.declarationWhat') + '</p>'});
            steps.push({html: '<p>' + T('guides.play.declarationHow') + '</p>'});
        }
        steps.push({
            html: '<p>' + T('guides.controls.explainer') + '</p>',
            target: '#player-summary'
        });
        steps.push({html: '<p class="guide-coach-result">' + T(presetKey) + '</p>'});
        steps.push({html: '<p class="guide-demo-note">' + T('guides.play.demoNote') + '</p>'});
        return steps;
    }

    function showIntroStep() {
        setModal(true);
        const steps = introSteps();
        const step = steps[introStep];
        setCoach(step.html);
        setControls(introStep === steps.length - 1 ? 'guides.coach.start' : 'guides.coach.next');
        pointAt(step.target || null);
    }

    function focusBoard() {
        document.querySelector('.game-layout')?.scrollIntoView({ block: 'start' });
    }

    function reset() {
        generation++;
        clearTimeout(pacedAction);
        pacedAction = null;
        pendingResult = null;
        pendingNotice = '';
        undoState = null;
        pointAt(null);
        showHint(false);
        forgetDeclareSkip();
        state = buildState();
        phase = 'intro';
        introStep = 0;
        showIntroStep();
        publishState('STARTED');
    }

    function advanceIntro() {
        if (phase !== 'intro') return;
        const steps = introSteps();
        if (introStep < steps.length - 1) {
            introStep++;
            showIntroStep();
            return;
        }
        setPhase('play');
        focusBoard();
        scheduleNext();
    }

    function previousIntro() {
        if (phase !== 'intro' || introStep === 0) return;
        introStep--;
        showIntroStep();
    }

    // ---- Mode selector ----
    function modeLabel(name) {
        const c = MODES[name];
        const parts = [T('guides.modeLabel.players', c.players)];
        if (c.teams) parts.push(T('guides.modeLabel.teams'));
        if (c.hand === 4 && type === 'briskula') parts.push(T('guides.modeLabel.fourCards'));
        if (c.declarations) parts.push(T('guides.modeLabel.declarations'));
        return parts.join(' · ');
    }
    function populateModes() {
        Array.from(modeSelect.options).forEach((option) => {
            option.textContent = modeLabel(option.value);
            option.selected = option.value === modeName;
        });
    }

    // ---- STOMP shim ----
    function handleSend(destination, body) {
        let payload = {};
        try { payload = JSON.parse(body || '{}'); } catch (_) {}
        if (destination === '/app/game/play') {
            const player = currentPlayer();
            if (player?.id === SELF_ID) play(player, payload);
            return;
        }
        if (destination === '/app/game/declare'
                && type === 'treseta'
                && mode().declarations
                && currentPlayer()?.id === SELF_ID
                && state.canDeclare.has(SELF_ID)) {
            const codes = (payload.cards || []).map((entry) => entry.card).sort();
            if (codes.join(',') !== 'S1,S2,S3') return;
            const player = state.players.find((entry) => entry.id === SELF_ID);
            state.declarations.push({
                player: publicPlayer(player),
                type: 'NAPOLITANA',
                suits: ['SPADE'],
                points: 9
            });
            state.canDeclare.delete(SELF_ID);
            addPoints(player, 9);
            pendingNotice = '<p class="guide-coach-result">' + T('guides.play.declared') + '</p>';
            publishState();
        }
    }
    window.Stomp = {
        client() {
            return {
                reconnect_delay: 0, debug: null,
                connect(_h, connected) { queueMicrotask(connected); },
                subscribe(destination, callback) {
                    if (!subscriptions.has(destination)) subscriptions.set(destination, new Set());
                    subscriptions.get(destination).add(callback);
                    return { unsubscribe: () => subscriptions.get(destination)?.delete(callback) };
                },
                send(destination, _h, body) { handleSend(destination, body); },
                disconnect(cb) { cb?.(); }
            };
        }
    };

    primaryBtn?.addEventListener('click', () => {
        if (phase === 'final') return reset();
        if (phase === 'intro') return advanceIntro();
        pendingResult = null;   // acknowledge the round recap and carry on
        pendingNotice = '';
        scheduleNext();
    });
    previousBtn?.addEventListener('click', () => (phase === 'intro' ? previousIntro() : replayMove()));
    modeSelect?.addEventListener('change', () => {
        if (MODES[modeSelect.value]) { modeName = modeSelect.value; reset(); }
    });

    populateModes();
    reset();
    if (!window.location.hash) requestAnimationFrame(() => requestAnimationFrame(focusBoard));
})();
