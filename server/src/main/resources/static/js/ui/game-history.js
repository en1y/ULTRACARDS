(() => {
    const ui = window.UltracardsGameUi;
    const layout = document.querySelector('.game-layout');
    const gameId = window.__HISTORY_GAME_ID__ || layout?.dataset.gameId;
    const dom = {
        title: document.getElementById('replay-title'),
        meta: document.getElementById('replay-meta'),
        seatRegions: {
            top: document.getElementById('replay-seats-top'),
            left: document.getElementById('replay-seats-left'),
            right: document.getElementById('replay-seats-right'),
            bottom: document.getElementById('replay-seats-bottom')
        },
        trick: document.getElementById('replay-trick-area'),
        stateLabel: document.getElementById('replay-drop-zone'),
        trump: document.getElementById('replay-trump-card'),
        deckStack: document.getElementById('replay-deck-stack'),
        deckTower: document.getElementById('replay-deck-tower'),
        deckLeft: document.getElementById('replay-deck-left'),
        stepTitle: document.getElementById('replay-step-title'),
        prev: document.getElementById('replay-prev'),
        next: document.getElementById('replay-next'),
        range: document.getElementById('replay-step-range'),
        scores: document.getElementById('replay-score-list'),
        teams: document.getElementById('replay-team-list')
    };

    const state = {game: null, teamState: null, steps: [], stepIndex: 0, seats: new Map(), trickZone: null, trickEls: new Map(), animating: false};

    // ---- data model helpers (unchanged from the original replay) ----
    const escapeHtml = (value) => String(value ?? '')
        .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;').replaceAll("'", '&#39;');

    const parsePlayer = (value) => {
        if (!value) return {name: 'Unknown player', id: null};
        if (typeof value === 'object') return value;
        const raw = String(value);
        try {
            return JSON.parse(raw);
        } catch {
            const nameMatch = raw.match(/name=([^,\)]*)/i);
            const idMatch = raw.match(/id=([^,\)]*)/i) || raw.match(/(\d+)/);
            return {name: nameMatch ? nameMatch[1].trim() : raw, id: idMatch ? Number(idMatch[1]) : null};
        }
    };
    const playerName = (player) => parsePlayer(player).name || 'Unknown player';
    const playerKey = (player) => {
        const parsed = parsePlayer(player);
        if (parsed.id != null && String(parsed.id) !== '') return `id:${parsed.id}`;
        return `name:${String(parsed.name || '').toLowerCase()}`;
    };
    const playerLookupKeys = (player) => {
        const parsed = parsePlayer(player);
        const keys = [];
        if (parsed.id != null && String(parsed.id) !== '') keys.push(`id:${parsed.id}`);
        if (parsed.name) {
            keys.push(`name:${parsed.name}`);
            keys.push(`name:${String(parsed.name).toLowerCase()}`);
        }
        return [...new Set(keys)];
    };
    const samePlayer = (a, b) => {
        const bKeys = new Set(playerLookupKeys(b));
        return playerLookupKeys(a).some((key) => bKeys.has(key));
    };
    const formatDate = (value) => {
        if (!value) return 'Unknown time';
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return 'Unknown time';
        return new Intl.DateTimeFormat(undefined, {dateStyle: 'medium', timeStyle: 'short', hour12: false}).format(date);
    };
    const settingsText = (config = {}) => {
        const players = config.numberOfPlayers || '?';
        const cards = config.cardsInHandNum || '?';
        const mode = config.teamsEnabled ? 'teams' : 'solo';
        return `${players} players - ${cards} cards - ${mode}`;
    };
    // Card identity, matching what game.js renderCardImage writes to data-card-key
    // (it forces ITALIAN). Keep both sides identical so snapshots line up.
    const ckey = (card) => `ITALIAN:${card?.card || ''}`;

    const normalizePlayerMap = (mapValue = {}) => Object.entries(mapValue || {})
        .map(([key, value]) => ({player: parsePlayer(key), value}));
    const pointsMap = (mapValue = {}) => {
        const map = new Map();
        normalizePlayerMap(mapValue).forEach(({player, value}) => map.set(playerKey(player), Number(value) || 0));
        return map;
    };
    const handsMap = (mapValue = {}) => {
        const map = new Map();
        normalizePlayerMap(mapValue).forEach(({player, value}) => map.set(playerKey(player), Array.isArray(value) ? value.slice() : []));
        return map;
    };
    const initialPoints = () => {
        const map = new Map();
        (state.game?.playersOrder || []).forEach((player) => map.set(playerKey(player), 0));
        return map;
    };
    const getStep = () => state.steps[state.stepIndex] || {roundIndex: 0, playCount: 0};
    const getRound = () => state.game?.rounds?.[getStep().roundIndex] || null;
    const buildSteps = (game) => {
        const steps = [];
        (game.rounds || []).forEach((round, roundIndex) => {
            steps.push({roundIndex, playCount: 0});
            (round.plays || []).forEach((play, playIndex) => steps.push({roundIndex, playCount: playIndex + 1}));
        });
        return steps.length ? steps : [{roundIndex: 0, playCount: 0}];
    };
    const resolveTeams = (game) => {
        if (!game?.gameConfig?.teamsEnabled) return null;
        const explicitTeams = Array.isArray(game.teams) ? game.teams : [];
        const team1 = explicitTeams[0] || [];
        const team2 = explicitTeams[1] || [];
        if (team1.length < 1 || team2.length < 1) return null;
        const teamByKey = new Map();
        team1.forEach((player) => playerLookupKeys(player).forEach((key) => teamByKey.set(key, 1)));
        team2.forEach((player) => playerLookupKeys(player).forEach((key) => teamByKey.set(key, 2)));
        return {team1, team2, teamByKey};
    };
    const getTeamNumber = (player) => {
        if (!state.teamState) return null;
        for (const key of playerLookupKeys(player)) {
            const teamNumber = state.teamState.teamByKey.get(key);
            if (teamNumber != null) return teamNumber;
        }
        return null;
    };
    const buildVisibleHands = (round, playCount) => {
        const map = handsMap(round?.playerHands || {});
        (round?.plays || []).slice(0, playCount).forEach((play) => {
            const key = playerKey(play.player);
            const hand = map.get(key) || [];
            const index = hand.findIndex((card) => ckey(card) === ckey(play.card));
            if (index >= 0) hand.splice(index, 1);
            map.set(key, hand);
        });
        return map;
    };
    const totalCardCount = () => Number(state.game?.gameConfig?.numberOfPlayers) === 3 ? 39 : 40;
    const completedCardsBefore = (roundIndex) => (state.game?.rounds || [])
        .slice(0, Math.max(roundIndex || 0, 0))
        .reduce((total, round) => total + ((round.plays || []).length), 0);
    const visibleCardsCount = () => {
        const step = getStep();
        const visibleHands = buildVisibleHands(getRound(), step.playCount);
        let handsCount = 0;
        visibleHands.forEach((cards) => {
            handsCount += Array.isArray(cards) ? cards.length : 0;
        });
        return completedCardsBefore(step.roundIndex) + handsCount + step.playCount;
    };
    const currentDeckLeft = () => Math.max(totalCardCount() - visibleCardsCount(), 0);
    const currentPoints = () => {
        const step = getStep();
        const round = state.game?.rounds?.[step.roundIndex];
        const plays = round?.plays || [];
        if (round && step.playCount >= plays.length && plays.length > 0) return pointsMap(round.pointsAfterRound);
        if (step.roundIndex > 0) return pointsMap(state.game.rounds[step.roundIndex - 1]?.pointsAfterRound);
        return initialPoints();
    };

    const players = () => state.game?.playersOrder || [];
    const playerCount = () => players().length || 2;

    // ---- rendering, via the game.js (briskula) pipeline ----
    const renderDeck = (deckLeft) => {
        ui?.renderDeckTower(dom.deckTower, dom.deckStack, deckLeft, {cardType: 'ITALIAN', featuredCard: true, alt: 'Deck'});
        if (dom.deckLeft) dom.deckLeft.textContent = String(deckLeft);
    };
    const renderTrump = (deckLeft) => {
        const code = state.game?.trumpCard?.card;
        const show = !!code && deckLeft > 0;
        if (!dom.trump) return;
        if (dom.trump.parentElement) dom.trump.parentElement.style.display = show ? '' : 'none';
        dom.trump.style.display = show ? '' : 'none';
        dom.deckStack?.classList.toggle('has-trump', show);
        if (show) ui?.revealCardFace(dom.trump, {cardType: 'ITALIAN', card: code});
    };

    // Which flow region each player sits in (no absolute positioning). One per region
    // for 2-4 players, so everyone stays on screen.
    const seatRegion = (index, count) => {
        const order = count >= 4 ? ['bottom', 'left', 'top', 'right']
            : count === 3 ? ['bottom', 'left', 'right']
                : ['bottom', 'top'];
        return dom.seatRegions[order[index] || 'top'] || dom.seatRegions.top;
    };

    const initSeats = () => {
        Object.values(dom.seatRegions).forEach((region) => region && (region.innerHTML = ''));
        state.seats.clear();
        const count = playerCount();
        players().forEach((player, index) => {
            const key = playerKey(player);
            const teamNumber = getTeamNumber(player);
            const seat = document.createElement('div');
            seat.className = `player-seat team-seat-neutral${teamNumber != null ? ` team-seat-${teamNumber}` : ''}`;
            seat.dataset.playerKey = key;
            seat.innerHTML = `
                <div class="seat-avatar">${escapeHtml(playerName(player).charAt(0).toUpperCase())}</div>
                <div class="seat-name">${escapeHtml(playerName(player))}</div>
                <div class="seat-team-badge"${teamNumber ? '' : ' hidden'}>${teamNumber ? `Team ${teamNumber}` : ''}</div>
                <div class="seat-cards"></div>
                <div class="seat-points"></div>`;
            seatRegion(index, count).appendChild(seat);
            state.seats.set(key, seat);
        });
    };

    // Big floating preview above (bottom hands) / below (top hands) a hovered card.
    // pointer-events:none keeps the small cards as the hover targets so you can glide.
    const showPreview = (cardEl, code) => {
        const r = cardEl.getBoundingClientRect();
        const img = document.createElement('img');
        img.className = 'replay-card-preview';
        img.src = ui.cardUrl({cardType: 'ITALIAN', card: code});
        const up = r.top > window.innerHeight / 2;
        img.style.left = `${r.left + r.width / 2}px`;
        img.style.top = `${up ? r.top - 4 : r.bottom + 4}px`;
        img.style.transform = up ? 'translate(-50%, -100%)' : 'translate(-50%, 0)';
        document.body.appendChild(img);
        return img;
    };

    // Face-up seat hand (replay is omniscient). Same fan transform the live seats use.
    const renderSeatHand = (seat, player, cards) => {
        const hand = seat.querySelector('.seat-cards');
        if (!hand) return;
        hand.innerHTML = '';
        const total = cards.length;
        cards.forEach((card, i) => {
            const el = ui.renderCardImage({card: {cardType: 'ITALIAN', card: card.card}, className: 'seat-card', alt: `${playerName(player)} card`});
            const centered = i - (total - 1) / 2;
            el.style.transform = `translateY(${Math.abs(centered) * 1.4}px) rotate(${centered * 5.5}deg)`;
            el.style.zIndex = String(i + 1);
            el.addEventListener('mouseenter', () => { el._prev = showPreview(el, card.card); });
            el.addEventListener('mouseleave', () => { el._prev?.remove(); el._prev = null; });
            hand.appendChild(el);
        });
    };

    const renderTrick = (plays) => {
        if (!dom.trick) return;
        const list = plays || [];
        const wanted = new Set(list.map((play) => ckey(play.card)));
        // Reuse existing trick cards so already-placed cards keep their slot instead of
        // re-tweening from centre every step (the "rearrange" glitch).
        state.trickEls.forEach((el, key) => {
            if (!wanted.has(key)) {
                el.remove();
                state.trickEls.delete(key);
            }
        });
        const fresh = [];
        const cards = list.map((play, idx) => {
            const key = ckey(play.card);
            let el = state.trickEls.get(key);
            if (!el) {
                el = ui.renderCardImage({card: {cardType: 'ITALIAN', card: play.card?.card}, className: 'trick-card', alt: `${playerName(play.player)} played`});
                el.style.transition = 'none';   // land in place, no centre→slot tween
                state.trickEls.set(key, el);
                dom.trick.appendChild(el);
                fresh.push(el);
            }
            el.style.setProperty('--spin', `${(idx * 4) - 6}deg`);
            return el;
        });
        if (state.trickZone) {
            // Fixed slot count = the full trick size, so slots don't re-centre as cards land.
            state.trickZone.options.slotTotal = Math.max(2, (getRound()?.plays?.length) || playerCount());
            ui.layoutZone(state.trickZone, cards);
        }
        if (fresh.length) requestAnimationFrame(() => fresh.forEach((el) => { el.style.transition = ''; }));
    };

    const renderScores = () => {
        if (!dom.scores) return;
        const scores = currentPoints();
        dom.scores.innerHTML = players().map((player) => {
            const teamNumber = getTeamNumber(player);
            return `<div class="history-replay-score ${teamNumber ? `team-score-${teamNumber}` : ''}">
                <span>${escapeHtml(playerName(player))}</span><strong>${scores.get(playerKey(player)) || 0}</strong></div>`;
        }).join('');
    };
    const renderTeams = () => {
        if (!dom.teams) return;
        if (!state.teamState) {
            dom.teams.innerHTML = '';
            return;
        }
        dom.teams.innerHTML = [state.teamState.team1, state.teamState.team2].map((team, index) => `
            <div class="history-replay-team team-score-${index + 1}"><strong>Team ${index + 1}</strong>
            <span>${team.map((player) => escapeHtml(playerName(player))).join(', ')}</span></div>`).join('');
    };
    const renderStepText = () => {
        const step = getStep();
        const round = getRound();
        const plays = round?.plays || [];
        const roundNumber = Number(round?.roundNumber ?? step.roundIndex) + 1;
        if (dom.stepTitle) dom.stepTitle.textContent = `Round ${roundNumber}`;
        if (dom.stateLabel) dom.stateLabel.textContent = step.playCount === 0 ? `Round ${roundNumber}` : `${step.playCount}/${plays.length}`;
    };
    const renderControls = () => {
        const max = Math.max(state.steps.length - 1, 0);
        if (dom.range) {
            dom.range.max = String(max);
            dom.range.value = String(state.stepIndex);
            dom.range.disabled = state.animating;
        }
        if (dom.prev) dom.prev.disabled = state.animating || state.stepIndex <= 0;
        if (dom.next) dom.next.disabled = state.animating || state.stepIndex >= max;
    };

    const render = () => {
        // Seat cards are rebuilt each step, so a preview whose card vanished must go.
        document.querySelectorAll('.replay-card-preview').forEach((p) => p.remove());
        const step = getStep();
        const round = getRound();
        const visibleHands = buildVisibleHands(round, step.playCount);
        const currentPlay = round?.plays?.[step.playCount] || null;
        const scores = currentPoints();
        const deckLeft = currentDeckLeft();
        renderDeck(deckLeft);
        renderTrump(deckLeft);
        players().forEach((player) => {
            const seat = state.seats.get(playerKey(player));
            if (!seat) return;
            seat.classList.toggle('is-turn', !!currentPlay && samePlayer(player, currentPlay.player));
            renderSeatHand(seat, player, visibleHands.get(playerKey(player)) || []);
            const pts = seat.querySelector('.seat-points');
            if (pts) pts.innerHTML = `<span class="seat-points-bubble">${scores.get(playerKey(player)) || 0}</span>`;
        });
        renderTrick((round?.plays || []).slice(0, step.playCount));
        renderScores();
        renderTeams();
        renderStepText();
        renderControls();
    };

    // ---- step transitions ----
    const snapshotRects = () => {
        const map = new Map();
        const add = (el) => {
            if (el.dataset.cardKey) map.set(el.dataset.cardKey, el.getBoundingClientRect());
        };
        state.seats.forEach((seat) => seat.querySelectorAll('.seat-cards .card-wrap').forEach(add));
        dom.trick?.querySelectorAll('.trick-card').forEach(add);
        return map;
    };
    const findTrickCard = (key) => dom.trick?.querySelector(`.trick-card[data-card-key="${CSS.escape(key)}"]`) || null;
    const findSeatCard = (key) => {
        for (const seat of state.seats.values()) {
            const el = seat.querySelector(`.seat-cards .card-wrap[data-card-key="${CSS.escape(key)}"]`);
            if (el) return el;
        }
        return null;
    };
    const deckRect = () => dom.deckTower?.getBoundingClientRect() || dom.deckStack?.getBoundingClientRect();

    // Fly a seat card (plain inline-transform fan) from a rect to its slot, keeping the fan.
    // Starts at the source's visual size (e.g. trick/deck) and shrinks into the small hand
    // so the motion is actually visible going back, not a near-invisible 44px nudge.
    const flySeatCard = (el, fromRect) => {
        if (!el || !fromRect) return Promise.resolve();
        const next = el.getBoundingClientRect();
        const slotT = el.style.transform || 'translate3d(0,0,0)';
        const dx = (fromRect.left + fromRect.width / 2) - (next.left + next.width / 2);
        const dy = (fromRect.top + fromRect.height / 2) - (next.top + next.height / 2);
        const scale = Math.min(Math.max((fromRect.width / (next.width || 1)) || 1, 0.6), 3);
        return Promise.resolve(ui.animateElement(el, {
            transform: [`translate3d(${dx}px, ${dy}px, 0) ${slotT} scale(${scale})`, slotT],
            opacity: [0.4, 1],
            duration: 360,
            ease: 'out(3)'
        }));
    };

    const classify = (from, to) => {
        const rounds = state.game?.rounds || [];
        if (to.roundIndex === from.roundIndex) {
            if (to.playCount === from.playCount + 1) return 'forward';
            if (to.playCount === from.playCount - 1) return 'backward';
        }
        if (to.roundIndex === from.roundIndex + 1 && to.playCount === 0
            && from.playCount === (rounds[from.roundIndex]?.plays?.length || 0)) return 'deal';
        return 'snap';
    };

    const setStep = (index) => {
        if (state.animating) return;
        const max = Math.max(state.steps.length - 1, 0);
        state.stepIndex = Math.min(Math.max(index, 0), max);
        render();
    };

    const stepAnimated = async (nextIndex) => {
        if (state.animating) return;
        const max = Math.max(state.steps.length - 1, 0);
        const to = Math.min(Math.max(nextIndex, 0), max);
        if (to === state.stepIndex) return;

        const fromStep = getStep();
        const toStep = state.steps[to];
        const kind = classify(fromStep, toStep);
        if (kind === 'snap') {
            setStep(to);
            return;
        }

        const plays = state.game?.rounds?.[fromStep.roundIndex]?.plays || [];
        const before = snapshotRects();
        state.animating = true;
        state.stepIndex = to;
        render();          // target state is on screen; flies pull cards from their old spot

        try {
            if (kind === 'forward') {
                const key = ckey(plays[fromStep.playCount]?.card);
                const card = findTrickCard(key);
                if (card && before.has(key)) {
                    await Promise.resolve(ui.crossHandTransfer({cardEl: card, fromRect: before.get(key), faceDown: false, duration: 320}));
                }
            } else if (kind === 'backward') {
                const key = ckey(plays[fromStep.playCount - 1]?.card);
                const card = findSeatCard(key);
                if (card && before.has(key)) await flySeatCard(card, before.get(key));
            } else if (kind === 'deal') {
                const from = deckRect();
                const fresh = [];
                state.seats.forEach((seat) => seat.querySelectorAll('.seat-cards .card-wrap').forEach((el) => {
                    if (!before.has(el.dataset.cardKey)) {
                        el.style.opacity = '0';
                        fresh.push(el);
                    }
                }));
                await Promise.all(fresh.map((el, i) => new Promise((resolve) => setTimeout(
                    () => flySeatCard(el, from).then(() => {
                        el.style.opacity = '';
                        resolve();
                    }), i * 55))));
            }
        } catch (_) { /* render already shows the correct state; ignore fly errors */ }

        state.animating = false;
        renderControls();
    };

    const loadReplay = async () => {
        if (!gameId) throw new Error('Missing game id');
        const response = await fetch(`/api/games/history/${encodeURIComponent(gameId)}`, {credentials: 'include'});
        if (!response.ok) throw new Error('Could not load replay');
        const game = await response.json();
        state.game = game;
        state.teamState = resolveTeams(game);
        state.steps = buildSteps(game);
        state.stepIndex = 0;

        if (dom.title) dom.title.textContent = game.name || 'Briskula';
        if (dom.meta) {
            dom.meta.innerHTML = `<p>${escapeHtml(formatDate(game.endedAt || game.createdAt))}</p>
                <p>${escapeHtml(settingsText(game.gameConfig))}</p>`;
        }
        layout?.style.setProperty('--max-hand', String(game.gameConfig?.cardsInHandNum || 4));
        layout?.classList.toggle('is-two-player', playerCount() === 2);
        layout?.classList.toggle('is-four-player', playerCount() === 4);
        initSeats();
        state.trickZone = ui?.registerHand(dom.trick, {type: 'center', spacingScale: 0.45, maxTilt: 4, yArc: 3});
        render();
    };

    dom.prev?.addEventListener('click', () => stepAnimated(state.stepIndex - 1));
    dom.next?.addEventListener('click', () => stepAnimated(state.stepIndex + 1));
    dom.range?.addEventListener('input', () => setStep(Number(dom.range.value) || 0));

    loadReplay().catch(() => {
        if (dom.title) dom.title.textContent = 'Replay unavailable';
        if (dom.meta) dom.meta.innerHTML = '<p>The game history could not be loaded.</p>';
        if (dom.stateLabel) dom.stateLabel.textContent = 'Error';
    });
})();
