/*
 * Guided Durak lessons for the public how-to-play page.
 *
 * Like the trick-game guide (guide-game.js) this feeds the REAL Durak controller
 * (js/ui/games/durak.js) through an in-page STOMP-compatible transport, so the
 * board, the gestures and the animations are the ones a player will meet in a
 * real match. Unlike a full game it is a series of tiny, hand-picked deals — one
 * concept each — and the coach waits for you to actually perform the move.
 *
 * The rules engine here is deliberately small: it only has to be right about the
 * few situations the lessons set up, and every one of them is fixed in advance.
 */
(() => {
    const gameEl = document.getElementById('game-container');
    if (!gameEl || gameEl.dataset.guide !== 'durak') return;

    const GAME_ID = 'ui-guide';
    const SELF_ID = Number(gameEl.dataset.currentUserId) || 1;
    const SELF_NAME = gameEl.dataset.username || 'You';
    const BOT = ['Bot Gojko', 'Bot Ivo'];
    const T = (key, ...args) => (window.t ? window.t(key, ...args) : key);

    const card = (code) => ({cardType: 'POKER', card: code});
    const codeOf = (value) => String(value?.card || value || '');
    const rank = (code) => (['JR', 'JB'].includes(code) ? 15 : Number(String(code).slice(1)));
    const suit = (code) => String(code).charAt(0);
    const SUIT_NAMES = {H: 'HEARTS', D: 'DIAMONDS', C: 'CLUBS', S: 'SPADES'};

    /* --------------------------------- lessons --------------------------------- */

    /**
     * Each lesson is a fixed deal plus a script. A step either says something and
     * waits for "Next", or says something and waits for a specific move from the
     * player; bot replies are scripted so every lesson plays out the same way.
     */
    const LESSONS = [
        {
            id: 'attack',
            players: 2, trump: 'S6', passing: false, everyone: false,
            hands: [['H10', 'H7', 'C12'], ['H13', 'D8', 'C7']],
            deck: [],
            lead: 0,
            steps: [
                {say: 'guides.durak.step.attack.intro'},
                {say: 'guides.durak.step.attack.trump', target: '.trump-card-slot'},
                {say: 'guides.durak.step.attack.play', await: {type: 'ATTACK'}},
                {say: 'guides.durak.step.attack.beaten', bot: {type: 'DEFEND'}},
                {say: 'guides.durak.step.attack.done', await: {type: 'DONE'}, target: '#durak-actions'}
            ]
        },
        {
            id: 'defend',
            players: 2, trump: 'S6', passing: false, everyone: false,
            hands: [['H12', 'S7', 'C9'], ['H9', 'D12', 'C8']],
            deck: [],
            lead: 1,
            steps: [
                {say: 'guides.durak.step.defend.intro', bot: {type: 'ATTACK', card: 'H9'}},
                {say: 'guides.durak.step.defend.higher', await: {type: 'DEFEND'}},
                // A throw-in has to match a rank already on the table — the twelve the
                // reader just played with — and they hold no diamond to answer it.
                {say: 'guides.durak.step.defend.trumped', bot: {type: 'ATTACK', card: 'D12'}},
                {say: 'guides.durak.step.defend.useTrump', await: {type: 'DEFEND'}},
                {say: 'guides.durak.step.defend.recap'}
            ]
        },
        {
            id: 'take',
            players: 2, trump: 'S6', passing: false, everyone: false,
            hands: [['C7', 'D7', 'H8'], ['H14', 'S13', 'C9']],
            deck: [],
            lead: 1,
            steps: [
                {say: 'guides.durak.step.take.intro', bot: {type: 'ATTACK', card: 'H14'}},
                {say: 'guides.durak.step.take.stuck', target: '#durak-actions'},
                {say: 'guides.durak.step.take.press', await: {type: 'TAKE'}, target: '#durak-actions'},
                {say: 'guides.durak.step.take.recap'}
            ]
        },
        {
            id: 'throwIn',
            players: 3, trump: 'S6', passing: false, everyone: true,
            hands: [['H9', 'D9', 'C12'], ['H13', 'D13', 'C10'], ['S9', 'S12', 'C13']],
            deck: [],
            lead: 0,
            steps: [
                {say: 'guides.durak.step.throwIn.intro'},
                {say: 'guides.durak.step.throwIn.open', await: {type: 'ATTACK', card: 'H9'}, hint: true},
                {say: 'guides.durak.step.throwIn.beaten', bot: {type: 'DEFEND'}},
                {say: 'guides.durak.step.throwIn.match', await: {type: 'ATTACK', card: 'D9'}, hint: true},
                {say: 'guides.durak.step.throwIn.recap'}
            ]
        },
        {
            id: 'rotate',
            players: 3, trump: 'S6', passing: true, everyone: true,
            // Seat 2 opens so that seat 0 — the reader — is the defender, and seat 1
            // holds enough cards to be handed the attack.
            hands: [['C9', 'D12', 'H14'], ['H13', 'D10', 'C10'], ['H9', 'S13', 'S12']],
            deck: [],
            lead: 2,
            steps: [
                {say: 'guides.durak.step.rotate.intro', bot: {type: 'ATTACK', card: 'H9', by: 2}},
                {say: 'guides.durak.step.rotate.target', target: '.durak-rotate-slot'},
                {say: 'guides.durak.step.rotate.play', await: {type: 'PASS', card: 'C9'}, hint: true},
                {say: 'guides.durak.step.rotate.recap'}
            ]
        }
    ];

    /* -------------------------------- simulator -------------------------------- */

    let lesson = LESSONS[0];
    let state = null;
    let stepIndex = 0;
    let awaiting = null;

    const players = () => state.players;
    const self = () => state.players[0];
    const publicPlayer = (player) => ({id: player.id, name: player.name});
    const playerKey = (player) => JSON.stringify(publicPlayer(player));

    function buildState() {
        const list = [{id: SELF_ID, name: SELF_NAME, hand: lesson.hands[0].map(card)}];
        for (let i = 1; i < lesson.players; i++) {
            list.push({id: 9000 + i, name: BOT[i - 1], hand: (lesson.hands[i] || []).map(card)});
        }
        const defender = (lesson.lead + 1) % lesson.players;
        state = {
            players: list,
            deck: (lesson.deck || []).map(card),
            trump: card(lesson.trump),
            slots: [],
            phase: 'WAITING_FOR_ATTACK',
            lead: lesson.lead,
            defender,
            actor: lesson.lead,
            done: new Set(),
            takeDeclared: false,
            bout: 1,
            revision: 1,
            finished: false
        };
    }

    function eligibleIndices() {
        return lesson.everyone
            ? state.players.map((_, index) => index).filter((index) => index !== state.defender)
            : [state.lead];
    }

    function gameDto() {
        const counts = {};
        state.players.forEach((player) => { counts[playerKey(player)] = player.hand.length; });
        const defender = state.players[state.defender];
        return {
            id: GAME_ID, lobbyId: null, name: 'Durak guide',
            playersOrder: state.players.map(publicPlayer),
            playersCardsMap: counts,
            cardsLeftInDeck: state.deck.length,
            trumpSuit: SUIT_NAMES[suit(codeOf(state.trump))],
            trumpIndicator: state.trump,
            phase: state.finished ? 'FINISHED' : state.phase,
            stateRevision: state.revision,
            boutNumber: state.bout,
            leadAttacker: publicPlayer(state.players[state.lead]),
            defender: publicPlayer(defender),
            actionPlayer: publicPlayer(state.players[state.actor]),
            maxAttackCards: Math.min(6, defender.hand.length + state.slots.length),
            attackSlots: state.slots.map((slot) => ({
                slotId: slot.slotId,
                attacker: publicPlayer(slot.attacker),
                attackCard: slot.attackCard,
                defender: slot.defenseCard ? publicPlayer(defender) : null,
                defenseCard: slot.defenseCard
            })),
            eligibleThrowers: eligibleIndices().map((index) => publicPlayer(state.players[index])),
            doneThrowers: [...state.done].map((index) => publicPlayer(state.players[index])),
            takeDeclared: state.takeDeclared,
            passingEnabled: !!lesson.passing,
            jokersEnabled: false,
            throwInPolicy: lesson.everyone ? 'EVERYONE' : 'NEIGHBORS_ONLY',
            finishedPlayers: [], finishOrder: [], discardedCardsNum: 0,
            turnEndTime: new Date(Date.now() + 600000).toISOString(),
            turnDurationSeconds: 600
        };
    }

    function canBeat(attack, defense) {
        const trump = suit(codeOf(state.trump));
        if (suit(attack) === suit(defense)) return rank(defense) > rank(attack);
        return suit(defense) === trump;
    }

    function legalFor(player) {
        const index = state.players.indexOf(player);
        const result = {
            stateRevision: state.revision, allowedActionTypes: [],
            defendableSlotIds: [], throwableCardCodes: [], passableCardCodes: []
        };
        if (state.finished) return result;
        if (state.phase === 'WAITING_FOR_ATTACK' && index === state.actor) {
            result.allowedActionTypes.push('ATTACK');
            return result;
        }
        if (state.phase === 'WAITING_FOR_DEFENSE' && index === state.defender) {
            result.defendableSlotIds = state.slots
                .filter((slot) => !slot.defenseCard
                    && player.hand.some((entry) => canBeat(codeOf(slot.attackCard), codeOf(entry))))
                .map((slot) => slot.slotId);
            if (result.defendableSlotIds.length) result.allowedActionTypes.push('DEFEND');
            if (lesson.passing && !state.slots.some((slot) => slot.defenseCard)) {
                const ranks = new Set(state.slots.map((slot) => rank(codeOf(slot.attackCard))));
                const next = state.players[(state.defender + 1) % state.players.length];
                if (state.slots.length + 1 <= Math.min(6, next.hand.length)) {
                    result.passableCardCodes = player.hand
                        .filter((entry) => ranks.has(rank(codeOf(entry)))).map(codeOf);
                }
                if (result.passableCardCodes.length) result.allowedActionTypes.push('PASS');
            }
            result.allowedActionTypes.push('TAKE');
        }
        const throwing = eligibleIndices().includes(index) && !state.done.has(index)
            && state.phase !== 'WAITING_FOR_ATTACK';
        if (throwing) {
            const ranks = new Set(state.slots
                .flatMap((slot) => [slot.attackCard, slot.defenseCard]).filter(Boolean)
                .map((entry) => rank(codeOf(entry))));
            result.throwableCardCodes = player.hand
                .filter((entry) => ranks.has(rank(codeOf(entry)))).map(codeOf);
            if (result.throwableCardCodes.length) result.allowedActionTypes.push('THROW_IN');
            if (state.phase !== 'WAITING_FOR_DEFENSE') result.allowedActionTypes.push('DONE');
        }
        return result;
    }

    const subscriptions = new Map();
    function emit(destination, payload) {
        const body = JSON.stringify(payload);
        queueMicrotask(() => subscriptions.get(destination)?.forEach((callback) => callback({body})));
    }

    function publish(event = 'UPDATED', result = null) {
        const dto = gameDto();
        window.__INITIAL_GAME__ = dto;
        window.__INITIAL_HAND__ = self().hand.slice();
        emit(`/topic/game/${GAME_ID}`, {gameEntity: dto, gameEvent: event, result});
        emit('/user/queue/game/cards', self().hand.slice());
        emit('/user/queue/game/durak-actions', legalFor(self()));
    }

    function reject(message) {
        emit('/user/queue/game/errors', {
            code: 'DURAK_INVALID_ACTION_FOR_PHASE',
            message: message || T('durak.error.durak_invalid_action_for_phase'),
            currentRevision: state.revision
        });
    }

    function removeFromHand(player, wanted) {
        const index = player.hand.findIndex((entry) => codeOf(entry) === wanted);
        return index < 0 ? null : player.hand.splice(index, 1)[0];
    }

    /** Applies one action for one player. Returns the action name when it stuck. */
    function apply(index, type, wanted, targetSlotId) {
        const player = state.players[index];
        const legal = legalFor(player);
        const kind = type === 'ATTACK' && state.phase !== 'WAITING_FOR_ATTACK' ? 'THROW_IN' : type;
        if (!legal.allowedActionTypes.includes(kind)) return null;

        if (kind === 'TAKE') {
            state.takeDeclared = true;
            state.phase = 'THROW_AFTER_TAKE';
            state.actor = state.lead;
            state.done.clear();
        } else if (kind === 'DONE') {
            state.done.add(index);
            const next = eligibleIndices().find((seat) => !state.done.has(seat));
            if (next == null) {
                resolveBout();
                return kind;
            }
            state.actor = next;
        } else if (kind === 'PASS') {
            const played = removeFromHand(player, wanted);
            if (!played || !legal.passableCardCodes.includes(wanted)) {
                if (played) player.hand.push(played);
                return null;
            }
            state.slots.push({slotId: state.slots.length, attacker: player, attackCard: played, defenseCard: null});
            state.defender = (state.defender + 1) % state.players.length;
            state.actor = state.defender;
            state.done.clear();
        } else if (kind === 'DEFEND') {
            const slot = state.slots.find((entry) => entry.slotId === Number(targetSlotId) && !entry.defenseCard);
            if (!slot || !canBeat(codeOf(slot.attackCard), wanted)) return null;
            const played = removeFromHand(player, wanted);
            if (!played) return null;
            slot.defenseCard = played;
            state.phase = state.slots.every((entry) => entry.defenseCard)
                ? 'WAITING_FOR_THROW_IN' : 'WAITING_FOR_DEFENSE';
            state.actor = state.phase === 'WAITING_FOR_THROW_IN' ? state.lead : state.defender;
        } else {
            if (kind === 'THROW_IN' && !legal.throwableCardCodes.includes(wanted)) return null;
            const played = removeFromHand(player, wanted);
            if (!played) return null;
            state.slots.push({slotId: state.slots.length, attacker: player, attackCard: played, defenseCard: null});
            state.done.clear();
            state.phase = state.takeDeclared ? 'THROW_AFTER_TAKE' : 'WAITING_FOR_DEFENSE';
            state.actor = state.takeDeclared ? state.lead : state.defender;
        }
        state.revision++;
        publish();
        return kind;
    }

    function resolveBout() {
        // Same two-step as the server: the finished bout first, then the state that clears it.
        publish();
        const table = state.slots.flatMap((slot) => [slot.attackCard, slot.defenseCard]).filter(Boolean);
        if (state.takeDeclared) state.players[state.defender].hand.push(...table);
        const nextLead = state.takeDeclared ? state.lead : state.defender;
        state.players.forEach((player) => {
            while (player.hand.length < 6 && state.deck.length) player.hand.push(state.deck.shift());
        });
        state.lead = nextLead;
        state.defender = (nextLead + 1) % state.players.length;
        state.actor = state.lead;
        state.slots = [];
        state.done.clear();
        state.takeDeclared = false;
        state.phase = 'WAITING_FOR_ATTACK';
        state.bout++;
        state.revision++;
        publish();
    }

    /* ---------------------------------- coach ---------------------------------- */

    const bubble = document.getElementById('guide-coach-bubble');
    const coachText = document.getElementById('guide-coach');
    const scrim = document.getElementById('guide-coach-scrim');
    const puck = document.getElementById('guide-coach-puck');
    const primary = document.getElementById('guide-primary');
    const previous = document.getElementById('guide-previous');
    const hideBtn = document.getElementById('guide-hide');
    const hintEl = document.getElementById('guide-hint');
    let lit = null;
    let modal = true;

    function light(selector) {
        lit?.classList.remove('guide-coach-target');
        lit = null;
        if (!selector) return;
        // One frame late: the board has just been repainted from the published state,
        // so the card this step points at may not exist yet.
        requestAnimationFrame(() => {
            lit = document.querySelector(selector);
            lit?.classList.add('guide-coach-target');
        });
    }

    /**
     * The bubble sits over the board while it is talking and steps aside — down to
     * the mascot puck — while it is your move, so it never covers the cards it just
     * asked you to play.
     */
    function paint(html, {waiting, target, hint}) {
        modal = !waiting;
        if (coachText) coachText.innerHTML = html;
        bubble?.classList.add('is-visible', 'tail-none');
        bubble?.classList.toggle('is-waiting', !!waiting);
        if (scrim) scrim.hidden = !modal;
        if (puck) puck.hidden = true;
        if (primary) primary.hidden = !!waiting;
        if (previous) previous.hidden = stepIndex === 0;
        if (hideBtn) hideBtn.hidden = !!waiting;
        if (hintEl) hintEl.hidden = !hint;
        light(target || null);
        placeCoach(!!waiting);
    }

    /**
     * The coach owns the middle of the board while it is talking and slides to the
     * top of it once it is your move, so it never sits on the cards it just asked
     * you to play. Both positions are clamped to the part of the board that is
     * actually on screen, so the bubble can never end up outside the viewport.
     */
    function placeCoach(waiting) {
        const board = document.querySelector('.game-layout');
        if (!bubble || !board) return;
        const gap = 12;
        const area = board.getBoundingClientRect();
        const size = bubble.getBoundingClientRect();
        // Pinned to the viewport on a phone: the browser keeps it in view for free,
        // which is worth more than following the board — and costs no scroll handler.
        coachIsFixed = getComputedStyle(bubble).position === 'fixed';
        const origin = coachIsFixed ? {left: 0, top: 0} : area;
        // The sticky header can hide itself while scrolling down, so reserve its full
        // height rather than where it happens to sit right now.
        const headerH = document.querySelector('.uc-header')?.offsetHeight || 0;
        // Visible band of the board, in viewport coordinates.
        const bandTop = Math.max(area.top, headerH + gap);
        const bandBottom = Math.min(area.bottom, window.innerHeight - gap);
        const room = bandBottom - bandTop - size.height;
        // Wide screens leave empty board either side of the felt: park the coach in
        // that gutter, where it covers nothing at all.
        const felt = board.querySelector('.table-surface')?.getBoundingClientRect();
        const gutter = felt && felt.left - area.left >= size.width + gap * 2;
        const top = room < 0
            ? Math.max(gap, Math.min(bandTop, window.innerHeight - size.height - gap))
            : bandTop + (waiting && !gutter ? 0 : room / 2);
        const left = gutter
            ? felt.left - size.width - gap
            : Math.max(
                Math.min(area.left, gap),
                Math.min(area.left + (area.width - size.width) / 2, window.innerWidth - size.width - gap));
        bubble.style.setProperty('--coach-x', `${Math.round(left - origin.left)}px`);
        bubble.style.setProperty('--coach-y', `${Math.round(top - origin.top)}px`);
        requestAnimationFrame(() => bubble.classList.add('can-fly'));
    }

    let placing = 0;
    let coachIsFixed = false;
    // Scrolling snaps the bubble into place instead of flying it: a transition would
    // chase the viewport and leave it outside the band it was just clamped to.
    const reflow = () => {
        cancelAnimationFrame(placing);
        placing = requestAnimationFrame(() => {
            bubble?.classList.remove('can-fly');
            placeCoach(!modal);
        });
    };
    window.addEventListener('scroll', () => {
        if (!coachIsFixed) reflow();
    }, {passive: true});
    window.addEventListener('resize', reflow);

    // A viewport-pinned coach would hang over the rules once the board scrolls away,
    // so it steps out while the board is not on screen. An observer costs nothing per
    // scroll, unlike measuring the board on every scroll event.
    const boardEl = document.querySelector('.game-layout');
    if (boardEl && 'IntersectionObserver' in window) {
        new IntersectionObserver(([entry]) => {
            bubble?.classList.toggle('is-away', coachIsFixed && entry.intersectionRatio < 0.3);
        }, {threshold: [0, 0.3, 0.6]}).observe(boardEl);
    }

    /** Brings the board under the header so a lesson never starts off screen. */
    function revealBoard() {
        const board = document.querySelector('.game-layout');
        if (!board) return;
        const area = board.getBoundingClientRect();
        // The board is sized to the space under the header, so that is exactly where
        // it goes — any slack pushes its bottom edge off the screen.
        const headerH = document.querySelector('.uc-header')?.offsetHeight || 0;
        if (Math.abs(area.top - headerH) < 2) return;
        window.scrollTo({top: area.top + window.scrollY - headerH, behavior: 'smooth'});
    }

    /* ---------------------------------- script --------------------------------- */

    function currentStep() {
        return lesson.steps[stepIndex] || null;
    }

    function runStep() {
        const step = currentStep();
        if (!step) return finishLesson();
        // A scripted bot move happens first, so the coach describes the board the
        // reader is actually looking at.
        if (step.bot) {
            const by = step.bot.by ?? nextBotIndex();
            botMove(by, step.bot);
        }
        awaiting = step.await || null;
        // A step that names a card points at it in the hand, which is what the
        // "play the highlighted card" hint promises.
        const target = step.target
            || (step.await?.card ? `.hand-cards [data-card-code="${step.await.card}"]` : null);
        paint(`<p>${T(step.say)}</p>`, {waiting: !!awaiting, target, hint: step.hint});
    }

    function nextBotIndex() {
        return state.players.findIndex((_, index) => index !== 0 && index === state.actor) >= 0
            ? state.actor : 1;
    }

    function botMove(index, move) {
        if (move.type === 'DEFEND') {
            const open = state.slots.find((slot) => !slot.defenseCard);
            const player = state.players[index] || state.players[state.defender];
            const seat = state.players.indexOf(player);
            const beater = player.hand.find((entry) => canBeat(codeOf(open.attackCard), codeOf(entry)));
            if (open && beater) apply(seat, 'DEFEND', codeOf(beater), open.slotId);
            return;
        }
        apply(index, move.type, move.card, move.targetSlotId);
    }

    function advance() {
        stepIndex += 1;
        runStep();
    }

    function stepBack() {
        if (stepIndex === 0) return;
        startLesson(LESSONS.indexOf(lesson), Math.max(0, stepIndex - 1));
    }

    function finishLesson() {
        const index = LESSONS.indexOf(lesson);
        const last = index === LESSONS.length - 1;
        awaiting = null;
        paint(`<p class="guide-coach-result">${T(last ? 'guides.durak.step.allDone' : 'guides.durak.step.lessonDone')}</p>`,
            {waiting: false});
        if (primary) primary.textContent = T(last ? 'guides.durak.restart' : 'guides.durak.nextLesson');
        primary.onclick = () => startLesson(last ? 0 : index + 1);
    }

    /**
     * Replays the lesson from the start up to `upTo`, applying every scripted bot
     * move and every move the script expects from the player. That keeps "Previous"
     * honest without having to snapshot the table on every step.
     */
    function startLesson(index, upTo = 0, reveal = true) {
        lesson = LESSONS[index] || LESSONS[0];
        if (reveal) revealBoard();
        if (lessonSelect) lessonSelect.value = lesson.id;
        buildState();
        stepIndex = 0;
        awaiting = null;
        publish('STARTED');
        for (let i = 0; i < upTo; i++) {
            const step = lesson.steps[i];
            if (step.bot) botMove(step.bot.by ?? state.actor, step.bot);
            if (step.await) autoPlayExpected(step.await);
            stepIndex = i + 1;
        }
        if (primary) {
            primary.textContent = T('guides.coach.next');
            primary.onclick = advance;
        }
        runStep();
    }

    /** Performs, on the player's behalf, exactly the move a replayed step asked for. */
    function autoPlayExpected(expected) {
        const hand = self().hand.map(codeOf);
        if (expected.type === 'DEFEND') {
            const open = state.slots.find((slot) => !slot.defenseCard);
            const beater = hand.find((code) => open && canBeat(codeOf(open.attackCard), code));
            if (open && beater) apply(0, 'DEFEND', beater, open.slotId);
            return;
        }
        if (expected.type === 'TAKE' || expected.type === 'DONE') {
            apply(0, expected.type);
            return;
        }
        apply(0, expected.type, expected.card || hand[0]);
    }

    /* ------------------------------- transport -------------------------------- */

    function handleSend(destination, body) {
        if (destination !== '/app/game/durak/action') return;
        let payload;
        try { payload = JSON.parse(body); } catch (_) { return; }
        const wanted = codeOf(payload.card);
        // Off-script moves are refused with the game's own error channel, so the
        // reader sees the normal in-game explanation rather than a silent no-op.
        if (awaiting) {
            const typeOk = awaiting.type === payload.type
                || (awaiting.type === 'ATTACK' && payload.type === 'ATTACK');
            const cardOk = !awaiting.card || awaiting.card === wanted;
            if (!typeOk || !cardOk) {
                reject(T('guides.durak.step.notThisOne'));
                return;
            }
        }
        const applied = apply(0, payload.type, wanted, payload.targetSlotId);
        if (!applied) {
            reject();
            return;
        }
        if (awaiting) {
            awaiting = null;
            setTimeout(advance, 650);
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

    /* --------------------------------- start ---------------------------------- */

    const lessonSelect = document.getElementById('guide-lesson');
    if (lessonSelect) {
        lessonSelect.replaceChildren(...LESSONS.map((entry) => {
            const option = document.createElement('option');
            option.value = entry.id;
            option.textContent = T(`guides.durak.lesson.${entry.id}`);
            return option;
        }));
        lessonSelect.addEventListener('change', () =>
            startLesson(LESSONS.findIndex((entry) => entry.id === lessonSelect.value)));
    }
    previous?.addEventListener('click', stepBack);
    hideBtn?.addEventListener('click', () => {
        bubble?.classList.remove('is-visible');
        if (scrim) scrim.hidden = true;
        if (puck) puck.hidden = false;
    });
    puck?.addEventListener('click', () => {
        bubble?.classList.add('is-visible');
        if (scrim) scrim.hidden = !modal;
        if (puck) puck.hidden = true;
    });

    window.__INITIAL_GAME_CHAT__ = null;
    buildState();
    window.__INITIAL_GAME__ = gameDto();
    window.__INITIAL_HAND__ = self().hand.slice();
    // durak.js reads the globals above on load, so the first lesson only has to be
    // started once its controller is listening.
    queueMicrotask(() => startLesson(0, 0, false));
})();
