(() => {
    const stage = document.querySelector('.history-replay-stage');
    const gameId = window.__HISTORY_GAME_ID__ || stage?.dataset.gameId;
    const dom = {
        title: document.getElementById('replay-title'),
        meta: document.getElementById('replay-meta'),
        ring: document.getElementById('replay-player-ring'),
        trick: document.getElementById('replay-trick-area'),
        stateLabel: document.getElementById('replay-state-label'),
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

    const state = {
        game: null,
        teamState: null,
        steps: [],
        stepIndex: 0,
        seats: new Map(),
        animating: false
    };

    const escapeHtml = (value) => String(value ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');

    const parsePlayer = (value) => {
        if (!value) return {name: 'Unknown player', id: null};
        if (typeof value === 'object') return value;

        const raw = String(value);
        try {
            return JSON.parse(raw);
        } catch {
            const nameMatch = raw.match(/name=([^,\)]*)/i);
            const idMatch = raw.match(/id=([^,\)]*)/i) || raw.match(/(\d+)/);
            return {
                name: nameMatch ? nameMatch[1].trim() : raw,
                id: idMatch ? Number(idMatch[1]) : null
            };
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
        return new Intl.DateTimeFormat(undefined, {
            dateStyle: 'medium',
            timeStyle: 'short',
            hour12: false
        }).format(date);
    };

    const settingsText = (config = {}) => {
        const players = config.numberOfPlayers || '?';
        const cards = config.cardsInHandNum || '?';
        const mode = config.teamsEnabled ? 'teams' : 'solo';
        return `${players} players - ${cards} cards - ${mode}`;
    };

    const cardKey = (card) => `${card?.cardType || ''}:${card?.card || ''}`;

    const italianCardUrl = (code) => {
        if (!code) return '';
        const suitLetter = code.charAt(0).toUpperCase();
        const valueNum = code.slice(1);
        const suitMap = {C: 'COPPE', D: 'DENARI', S: 'SPADE', B: 'BASTONI'};
        const valueMap = {
            '1': 'ACE', '2': 'TWO', '3': 'THREE', '4': 'FOUR', '5': 'FIVE', '6': 'SIX',
            '7': 'SEVEN', '11': 'JACK', '12': 'KNIGHT', '13': 'KING'
        };
        const suit = suitMap[suitLetter];
        const value = valueMap[valueNum];
        if (!suit || !value) return '';
        return `/api/cards/italian/${suit}/${value}`;
    };

    const italianBackUrl = () => '/api/cards/italian/back';

    const normalizePlayerMap = (mapValue = {}) => {
        const entries = [];
        Object.entries(mapValue || {}).forEach(([key, value]) => {
            entries.push({player: parsePlayer(key), value});
        });
        return entries;
    };

    const pointsMap = (mapValue = {}) => {
        const map = new Map();
        normalizePlayerMap(mapValue).forEach(({player, value}) => {
            map.set(playerKey(player), Number(value) || 0);
        });
        return map;
    };

    const handsMap = (mapValue = {}) => {
        const map = new Map();
        normalizePlayerMap(mapValue).forEach(({player, value}) => {
            map.set(playerKey(player), Array.isArray(value) ? value.slice() : []);
        });
        return map;
    };

    const initialPoints = () => {
        const map = new Map();
        (state.game?.playersOrder || []).forEach((player) => {
            map.set(playerKey(player), 0);
        });
        return map;
    };

    const getStep = () => state.steps[state.stepIndex] || {roundIndex: 0, playCount: 0};
    const getRound = () => state.game?.rounds?.[getStep().roundIndex] || null;

    const buildSteps = (game) => {
        const steps = [];
        (game.rounds || []).forEach((round, roundIndex) => {
            const plays = round.plays || [];
            steps.push({roundIndex, playCount: 0});
            plays.forEach((play, playIndex) => {
                steps.push({roundIndex, playCount: playIndex + 1});
            });
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
        const visiblePlays = (round?.plays || []).slice(0, playCount);
        visiblePlays.forEach((play) => {
            const key = playerKey(play.player);
            const hand = map.get(key) || [];
            const index = hand.findIndex((card) => cardKey(card) === cardKey(play.card));
            if (index >= 0) {
                hand.splice(index, 1);
            }
            map.set(key, hand);
        });
        return map;
    };

    const totalCardCount = () => Number(state.game?.gameConfig?.numberOfPlayers) === 3 ? 39 : 40;

    const visibleCardsCount = () => {
        const step = getStep();
        const round = getRound();
        const visibleHands = buildVisibleHands(round, step.playCount);
        let handsCount = 0;
        visibleHands.forEach((cards) => {
            handsCount += Array.isArray(cards) ? cards.length : 0;
        });
        return completedCardsBefore(step.roundIndex) + handsCount + step.playCount;
    };

    const completedCardsBefore = (roundIndex) => (state.game?.rounds || [])
        .slice(0, Math.max(roundIndex || 0, 0))
        .reduce((total, round) => total + ((round.plays || []).length), 0);

    const currentDeckLeft = () => Math.max(totalCardCount() - visibleCardsCount(), 0);

    const currentPoints = () => {
        const step = getStep();
        const round = state.game?.rounds?.[step.roundIndex];
        const plays = round?.plays || [];
        if (round && step.playCount >= plays.length && plays.length > 0) {
            return pointsMap(round.pointsAfterRound);
        }
        if (step.roundIndex > 0) {
            return pointsMap(state.game.rounds[step.roundIndex - 1]?.pointsAfterRound);
        }
        return initialPoints();
    };

    const renderTrump = () => {
        if (!dom.trump || !state.game?.trumpCard) return;
        dom.trump.src = italianCardUrl(state.game.trumpCard.card);
        dom.trump.hidden = !dom.trump.src;
        dom.deckStack?.classList.add('has-trump');
    };

    const renderDeckTower = (cardsLeft = currentDeckLeft()) => {
        if (!dom.deckTower) return;
        dom.deckTower.innerHTML = '';
        const visibleCount = Math.min(Math.max(cardsLeft - 1, 0), 12);
        dom.deckStack?.classList.toggle('is-empty', cardsLeft <= 0);
        if (dom.deckLeft) dom.deckLeft.textContent = String(cardsLeft);
        if (visibleCount <= 0) return;
        for (let i = 0; i < visibleCount; i++) {
            const img = document.createElement('img');
            img.alt = 'Deck';
            img.src = italianBackUrl();
            img.style.setProperty('--deck-offset-x', String((i % 4) * 1.5));
            img.style.setProperty('--deck-offset-y', String((i % 5) * -0.9));
            img.style.setProperty('--deck-rot', String((i % 5) - 2));
            dom.deckTower.appendChild(img);
        }
    };

    const initializeSeats = () => {
        if (!dom.ring || !state.game) return;
        dom.ring.innerHTML = '';
        state.seats.clear();
        (state.game.playersOrder || []).forEach((player, index) => {
            const key = playerKey(player);
            const teamNumber = getTeamNumber(player);
            const seat = document.createElement('div');
            seat.className = 'player-seat team-seat-neutral';
            if (teamNumber != null) {
                seat.classList.add(`team-seat-${teamNumber}`);
            }
            seat.dataset.playerKey = key;
            seat.dataset.seatIndex = String(index);
            seat.dataset.seatTotal = String(state.game.playersOrder.length || 1);

            const avatar = document.createElement('div');
            avatar.className = 'seat-avatar';
            avatar.textContent = playerName(player).charAt(0).toUpperCase();
            seat.appendChild(avatar);

            const name = document.createElement('div');
            name.className = 'seat-name';
            name.textContent = playerName(player);
            seat.appendChild(name);

            const badge = document.createElement('div');
            badge.className = 'seat-team-badge';
            badge.textContent = teamNumber ? `Team ${teamNumber}` : '';
            badge.hidden = !teamNumber;
            seat.appendChild(badge);

            const hand = document.createElement('div');
            hand.className = 'seat-cards';
            seat.appendChild(hand);

            const points = document.createElement('div');
            points.className = 'seat-points';
            seat.appendChild(points);

            dom.ring.appendChild(seat);
            state.seats.set(key, seat);
        });
        positionSeats();
    };

    const renderSeats = () => {
        if (!dom.ring || !state.game) return;
        const step = getStep();
        const round = getRound();
        const visibleHands = buildVisibleHands(round, step.playCount);
        const visiblePlays = (round?.plays || []).slice(0, step.playCount);
        const currentPlay = round?.plays?.[step.playCount] || null;
        const scores = currentPoints();

        (state.game.playersOrder || []).forEach((player) => {
            const key = playerKey(player);
            const seat = state.seats.get(key);
            if (!seat) return;
            seat.classList.toggle('is-turn', currentPlay && samePlayer(player, currentPlay.player));
            renderSeatHand(seat, player, visibleHands.get(key) || []);
            const points = seat.querySelector('.seat-points');
            if (points) points.textContent = `Points: ${scores.get(key) || 0}`;
        });

        renderTrick(visiblePlays);
    };

    const renderSeatHand = (seat, player, cards, options = {}) => {
        const hand = seat.querySelector('.seat-cards');
        if (!hand) return;
        hand.innerHTML = '';
        cards.forEach((card, cardIndex) => {
            const img = document.createElement('img');
            img.className = 'seat-card replay-seat-card';
            if (options.hiddenCardKey === cardKey(card)) {
                img.classList.add('is-replay-hidden');
            }
            img.dataset.cardKey = cardKey(card);
            img.alt = `${playerName(player)} card`;
            img.src = italianCardUrl(card.card);
            const centeredIndex = cardIndex - ((cards.length - 1) / 2);
            img.style.transform = `translateY(${Math.abs(centeredIndex) * 1.4}px) rotate(${centeredIndex * 5.5}deg)`;
            hand.appendChild(img);
        });
    };

    const renderTrick = (plays, options = {}) => {
        if (!dom.trick) return;
        dom.trick.innerHTML = '';
        (plays || []).forEach((play, index) => {
            const img = document.createElement('img');
            img.className = 'trick-card';
            if (options.hiddenPlayIndex === index) {
                img.classList.add('is-replay-hidden');
            }
            img.dataset.playIndex = String(index);
            img.alt = `${playerName(play.player)} played ${play.card?.card || 'card'}`;
            img.title = img.alt;
            img.src = italianCardUrl(play.card?.card);
            img.style.setProperty('--spin', `${(index * 4) - 6}deg`);
            dom.trick.appendChild(img);
        });
    };

    const positionSeats = () => {
        if (!dom.ring) return;
        const seats = Array.from(dom.ring.children);
        const base = Math.min(dom.ring.clientWidth, dom.ring.clientHeight) / 2;
        const radius = Math.max(base - 6, 0);
        seats.forEach((seat, index) => {
            const count = Number(seat.dataset.seatTotal) || seats.length || 1;
            const seatIndex = Number(seat.dataset.seatIndex) || index;
            const angle = (Math.PI * 2 * seatIndex) / count + Math.PI / 2;
            const x = Math.cos(angle) * radius;
            let y = Math.sin(angle) * radius;
            const seatSide = x < -8 ? 'left' : (x > 8 ? 'right' : (y < 0 ? 'top' : 'bottom'));
            if (count === 4 && seatSide === 'top') y += 20;
            seat.style.left = '50%';
            seat.style.top = '50%';
            seat.style.transform = `translate(-50%, -50%) translate(${x}px, ${y}px)`;
            seat.style.setProperty('--hand-rotate', '0deg');
        });
    };

    const renderScores = () => {
        if (!dom.scores || !state.game) return;
        const scores = currentPoints();
        dom.scores.innerHTML = (state.game.playersOrder || []).map((player) => {
            const teamNumber = getTeamNumber(player);
            return `
        <div class="history-replay-score ${teamNumber ? `team-score-${teamNumber}` : ''}">
          <span>${escapeHtml(playerName(player))}</span>
          <strong>${scores.get(playerKey(player)) || 0}</strong>
        </div>
      `;
        }).join('');
    };

    const renderTeams = () => {
        if (!dom.teams) return;
        if (!state.teamState) {
            dom.teams.innerHTML = '';
            return;
        }
        dom.teams.innerHTML = [state.teamState.team1, state.teamState.team2].map((team, index) => `
      <div class="history-replay-team team-score-${index + 1}">
        <strong>Team ${index + 1}</strong>
        <span>${team.map((player) => escapeHtml(playerName(player))).join(', ')}</span>
      </div>
    `).join('');
    };

    const renderStepText = () => {
        const step = getStep();
        const round = getRound();
        const plays = round?.plays || [];
        const roundNumber = Number(round?.roundNumber ?? step.roundIndex) + 1;
        if (dom.stepTitle) dom.stepTitle.textContent = `Round ${roundNumber}`;
        if (dom.stateLabel) {
            dom.stateLabel.textContent = step.playCount === 0
                ? `Round ${roundNumber}`
                : `${step.playCount}/${plays.length}`;
        }
    };

    const renderControls = () => {
        const max = Math.max(state.steps.length - 1, 0);
        if (dom.range) {
            dom.range.max = String(max);
            dom.range.value = String(state.stepIndex);
        }
        if (dom.prev) dom.prev.disabled = state.animating || state.stepIndex <= 0;
        if (dom.next) dom.next.disabled = state.animating || state.stepIndex >= max;
        if (dom.range) dom.range.disabled = state.animating;
    };

    const render = () => {
        renderSeats();
        renderDeckTower();
        renderScores();
        renderTeams();
        renderStepText();
        renderControls();
    };

    const setStep = (index) => {
        if (state.animating) return;
        const max = Math.max(state.steps.length - 1, 0);
        state.stepIndex = Math.min(Math.max(index, 0), max);
        render();
    };

    const setStepWithAnimation = async (nextIndex) => {
        if (state.animating) return;
        const max = Math.max(state.steps.length - 1, 0);
        const boundedIndex = Math.min(Math.max(nextIndex, 0), max);
        if (boundedIndex === state.stepIndex) return;
        const fromStep = getStep();
        const toStep = state.steps[boundedIndex];
        const dealAnimation = resolveDealAnimation(fromStep, toStep);
        const animation = resolveStepAnimation(fromStep, toStep);

        state.animating = true;
        renderControls();
        let cleanupAnimation = null;
        let hideAnimationTarget = false;
        if (dealAnimation) {
            await animateDealCards(dealAnimation);
        } else if (animation) {
            const flight = animation.direction === 'backward'
                ? prepareReturningCardFlight(animation)
                : preparePlayedCardFlight(animation);
            if (flight) {
                prepareHandTransition(animation);
                prepareTrickTransition(animation);
                const flightResult = await flight.start();
                if (typeof flightResult === 'function') {
                    cleanupAnimation = flightResult;
                    hideAnimationTarget = true;
                } else if (flightResult?.cleanup) {
                    cleanupAnimation = flightResult.cleanup;
                    hideAnimationTarget = flightResult.hideTargetOnRender !== false;
                }
            }
        }
        state.stepIndex = boundedIndex;
        state.animating = false;
        render();
        if (cleanupAnimation) {
            const targetEl = findAnimationTarget(animation);
            if (hideAnimationTarget) targetEl?.classList.add('is-replay-hidden');
            requestAnimationFrame(() => {
                requestAnimationFrame(() => window.setTimeout(() => cleanupAnimation(targetEl), 45));
            });
        }
    };

    const resolveDealAnimation = (fromStep, toStep) => {
        if (!fromStep || !toStep || toStep.playCount !== 0 || toStep.roundIndex !== fromStep.roundIndex + 1) {
            return null;
        }

        const fromRound = state.game?.rounds?.[fromStep.roundIndex];
        const toRound = state.game?.rounds?.[toStep.roundIndex];
        if (!fromRound || !toRound || fromStep.playCount < (fromRound.plays || []).length) {
            return null;
        }

        const currentHands = buildVisibleHands(fromRound, fromStep.playCount);
        const targetHands = handsMap(toRound.playerHands);
        const drawsByPlayer = new Map();

        (state.game.playersOrder || []).forEach((player) => {
            const key = playerKey(player);
            const currentCards = currentHands.get(key) || [];
            const targetCards = targetHands.get(key) || [];
            const currentCounts = new Map();
            currentCards.forEach((card) => {
                const key = cardKey(card);
                currentCounts.set(key, (currentCounts.get(key) || 0) + 1);
            });

            const drawnCards = [];
            targetCards.forEach((card) => {
                const key = cardKey(card);
                const count = currentCounts.get(key) || 0;
                if (count > 0) {
                    currentCounts.set(key, count - 1);
                } else {
                    drawnCards.push(card);
                }
            });
            drawsByPlayer.set(key, drawnCards);
        });

        const drawQueue = [];
        const maxDraws = Math.max(0, ...Array.from(drawsByPlayer.values()).map((cards) => cards.length));
        for (let drawIndex = 0; drawIndex < maxDraws; drawIndex++) {
            (state.game.playersOrder || []).forEach((player) => {
                const card = drawsByPlayer.get(playerKey(player))?.[drawIndex];
                if (card) drawQueue.push({player, card});
            });
        }

        return drawQueue.length ? {fromStep, toStep, currentHands, drawQueue} : null;
    };

    const resolveStepAnimation = (fromStep, toStep) => {
        if (!fromStep || !toStep || fromStep.roundIndex !== toStep.roundIndex) {
            return null;
        }
        const round = state.game?.rounds?.[fromStep.roundIndex];
        const plays = round?.plays || [];
        if (toStep.playCount === fromStep.playCount + 1) {
            const play = plays[toStep.playCount - 1];
            return play ? {direction: 'forward', play, playIndex: toStep.playCount - 1, fromStep, toStep} : null;
        }
        if (toStep.playCount === fromStep.playCount - 1) {
            const play = plays[fromStep.playCount - 1];
            return play ? {direction: 'backward', play, playIndex: fromStep.playCount - 1, fromStep, toStep} : null;
        }
        return null;
    };

    const preparePlayedCardFlight = (animation) => {
        const cardCode = animation.play?.card?.card;
        const src = italianCardUrl(cardCode);
        if (!src) return null;

        const playerSeat = state.seats.get(playerKey(animation.play.player));
        const handCard = findSeatCard(playerSeat, animation.play.card);
        const trickCard = dom.trick?.querySelector(`[data-play-index="${animation.playIndex}"]`);
        const sourceHandRect = handCard?.getBoundingClientRect()
            || measureHandCardRect(animation.play.player, animation.play.card, animation.fromStep)
            || playerSeat?.querySelector('.seat-cards')?.getBoundingClientRect()
            || playerSeat?.getBoundingClientRect();
        const sourceTrickRect = trickCard?.getBoundingClientRect()
            || measureTrickCardRect(animation.playIndex, animation.fromStep)
            || dom.trick?.getBoundingClientRect();
        const targetTrickRect = measureTrickCardRect(animation.playIndex, animation.toStep)
            || sourceTrickRect
            || dom.trick?.getBoundingClientRect();
        const targetHandRect = measureHandCardRect(animation.play.player, animation.play.card, animation.toStep)
            || sourceHandRect
            || playerSeat?.getBoundingClientRect();

        const fromRect = animation.direction === 'forward' ? sourceHandRect : sourceTrickRect;
        const toRect = animation.direction === 'forward' ? targetTrickRect : targetHandRect;
        if (!fromRect || !toRect) return null;

        const sourceEl = animation.direction === 'forward' ? handCard : trickCard;
        sourceEl?.classList.add('is-replay-hidden');

        const fromX = fromRect.left + fromRect.width / 2;
        const fromY = fromRect.top + fromRect.height / 2;
        const toX = toRect.left + toRect.width / 2;
        const toY = toRect.top + toRect.height / 2;
        const fromWidth = animation.direction === 'forward'
            ? cardVisualWidth(handCard, fromRect)
            : cardVisualWidth(trickCard, fromRect);
        const toWidth = animation.direction === 'forward'
            ? cardVisualWidth(null, targetTrickRect)
            : cardVisualWidth(null, targetHandRect);
        const fromRot = sourceRotation(animation);
        const toRot = targetRotation(animation);
        const peakScale = animation.direction === 'forward' ? 1.04 : 0.94;
        const fromTransform = `translate3d(${fromX}px, ${fromY}px, 0) translate(-50%, -50%) rotate(${fromRot}) scale(1)`;
        const toTransform = `translate3d(${toX}px, ${toY}px, 0) translate(-50%, -50%) rotate(${toRot}) scale(1)`;

        const img = document.createElement('img');
        img.className = `history-replay-fly-card ${animation.direction === 'backward' ? 'is-reverse' : ''}`;
        img.alt = 'Replay card animation';
        img.src = src;
        img.style.left = '0';
        img.style.top = '0';
        img.style.width = `${fromWidth}px`;
        img.style.transform = fromTransform;
        document.body.appendChild(img);

        return {
            start: () => new Promise((resolve) => {
                let done = false;
                const keyframes = [
                    {
                        width: `${fromWidth}px`,
                        transform: fromTransform,
                        opacity: 0.98
                    },
                    {
                        offset: 0.72,
                        width: `${toWidth}px`,
                        transform: `translate3d(${toX}px, ${toY}px, 0) translate(-50%, -50%) rotate(${toRot}) scale(${peakScale})`,
                        opacity: 1
                    },
                    {
                        width: `${toWidth}px`,
                        transform: toTransform,
                        opacity: 1
                    }
                ];
                const motion = img.animate(keyframes, {
                    duration: 420,
                    easing: 'cubic-bezier(.18, .84, .24, 1)',
                    fill: 'forwards'
                });
                const finish = () => {
                    if (done) return;
                    done = true;
                    img.style.width = `${toWidth}px`;
                    img.style.transform = toTransform;
                    resolve((targetEl) => {
                        motion.cancel();
                        sourceEl?.classList.remove('is-replay-hidden');
                        targetEl?.classList.remove('is-replay-hidden');
                        img.remove();
                    });
                };
                motion.finished.then(finish).catch(finish);
                window.setTimeout(finish, 520);
            })
        };
    };

    const prepareReturningCardFlight = (animation) => {
        const trickCard = dom.trick?.querySelector(`[data-play-index="${animation.playIndex}"]`);
        const sourceRect = trickCard?.getBoundingClientRect()
            || measureTrickCardRect(animation.playIndex, animation.fromStep)
            || dom.trick?.getBoundingClientRect();
        if (!sourceRect) return null;

        trickCard?.classList.add('is-replay-hidden');
        const sourceWidth = cardVisualWidth(trickCard, sourceRect);
        const sourceRot = rotationDegrees(sourceRotation(animation));
        const targetRot = rotationDegrees(targetRotation(animation));

        return {
            start: () => new Promise((resolve) => {
                const targetCard = findAnimationTarget(animation);
                if (!targetCard) {
                    trickCard?.classList.remove('is-replay-hidden');
                    resolve(null);
                    return;
                }

                const targetRect = targetCard.getBoundingClientRect();
                const targetWidth = cardVisualWidth(targetCard, targetRect);
                const sourceX = sourceRect.left + sourceRect.width / 2;
                const sourceY = sourceRect.top + sourceRect.height / 2;
                const targetX = targetRect.left + targetRect.width / 2;
                const targetY = targetRect.top + targetRect.height / 2;
                const deltaX = sourceX - targetX;
                const deltaY = sourceY - targetY;
                const scale = Math.max(sourceWidth / targetWidth, 0.1);
                const baseTransform = targetCard.style.transform || '';
                const rotateDelta = sourceRot - targetRot;
                let done = false;

                targetCard.classList.add('is-replay-moving-card');
                targetCard.style.transition = 'none';
                targetCard.style.transform = `translate(${deltaX}px, ${deltaY}px) rotate(${rotateDelta}deg) scale(${scale}) ${baseTransform}`;
                targetCard.classList.remove('is-replay-hidden');
                targetCard.getBoundingClientRect();

                requestAnimationFrame(() => {
                    targetCard.style.transition = 'transform 420ms cubic-bezier(.18, .84, .24, 1), box-shadow 150ms ease';
                    targetCard.style.transform = baseTransform;
                });

                const finish = () => {
                    if (done) return;
                    done = true;
                    trickCard?.classList.remove('is-replay-hidden');
                    targetCard.classList.remove('is-replay-moving-card');
                    targetCard.style.transition = '';
                    targetCard.style.transform = baseTransform;
                    resolve(null);
                };
                targetCard.addEventListener('transitionend', finish, {once: true});
                window.setTimeout(finish, 520);
            })
        };
    };

    const animateDealCards = async (animation) => {
        const currentHands = new Map();
        animation.currentHands.forEach((cards, key) => currentHands.set(key, cards.slice()));
        let deckLeft = currentDeckLeft();

        renderTrick([]);
        if (dom.stateLabel) dom.stateLabel.textContent = 'Dealing';

        for (const draw of animation.drawQueue) {
            const key = playerKey(draw.player);
            const seat = state.seats.get(key);
            const hand = seat?.querySelector('.seat-cards');
            if (!seat || !hand) continue;

            const nextCards = [...(currentHands.get(key) || []), draw.card];
            prepareDealHandTransition(draw.player, nextCards, cardKey(draw.card));
            const targetRect = findSeatCard(seat, draw.card)?.getBoundingClientRect()
                || measureHandCardRect(draw.player, draw.card, animation.toStep)
                || hand.getBoundingClientRect();

            const deal = animateDealCard(draw.card, targetRect);
            deckLeft = Math.max(deckLeft - 1, 0);
            renderDeckTower(deckLeft);
            await deal;

            const targetCard = findSeatCard(seat, draw.card);
            targetCard?.classList.remove('is-replay-hidden');
            currentHands.set(key, nextCards);
            await wait(80);
        }
    };

    const prepareDealHandTransition = (player, targetCards, hiddenCardKey) => {
        const seat = state.seats.get(playerKey(player));
        const hand = seat?.querySelector('.seat-cards');
        if (!hand) return;

        const oldRects = new Map();
        Array.from(hand.querySelectorAll('.replay-seat-card')).forEach((card, index) => {
            oldRects.set(`${card.dataset.cardKey}:${index}`, card.getBoundingClientRect());
        });

        renderSeatHand(seat, player, targetCards, {hiddenCardKey});
        const oldCardQueues = new Map();
        oldRects.forEach((rect, key) => {
            const cardKeyOnly = key.slice(0, key.lastIndexOf(':'));
            const queue = oldCardQueues.get(cardKeyOnly) || [];
            queue.push(rect);
            oldCardQueues.set(cardKeyOnly, queue);
        });

        Array.from(hand.querySelectorAll('.replay-seat-card')).forEach((card) => {
            if (card.dataset.cardKey === hiddenCardKey) return;
            const queue = oldCardQueues.get(card.dataset.cardKey) || [];
            const oldRect = queue.shift();
            if (!oldRect) return;
            animateCardElementFromRect(card, oldRect);
        });
    };

    const animateDealCard = (card, targetRect) => {
        const sourceRect = deckSourceRect();
        if (!sourceRect || !targetRect) return Promise.resolve();

        const dealCard = document.createElement('div');
        dealCard.className = 'history-replay-deal-card';
        dealCard.style.setProperty('--from-x', `${sourceRect.left + sourceRect.width / 2}px`);
        dealCard.style.setProperty('--from-y', `${sourceRect.top + sourceRect.height / 2}px`);
        dealCard.style.setProperty('--from-width', `${Math.max(sourceRect.width, 42)}px`);
        dealCard.style.setProperty('--to-x', `${targetRect.left + targetRect.width / 2}px`);
        dealCard.style.setProperty('--to-y', `${targetRect.top + targetRect.height / 2}px`);
        dealCard.style.setProperty('--to-width', `${Math.max(targetRect.width, 42)}px`);

        const inner = document.createElement('div');
        inner.className = 'history-replay-deal-inner';

        const back = document.createElement('div');
        back.className = 'history-replay-deal-face history-replay-deal-back';
        const backImg = document.createElement('img');
        backImg.alt = 'Deck card';
        backImg.src = italianBackUrl();
        back.appendChild(backImg);

        const front = document.createElement('div');
        front.className = 'history-replay-deal-face history-replay-deal-front';
        const frontImg = document.createElement('img');
        frontImg.alt = 'Drawn card';
        frontImg.src = italianCardUrl(card.card);
        front.appendChild(frontImg);

        inner.append(back, front);
        dealCard.appendChild(inner);
        document.body.appendChild(dealCard);

        return new Promise((resolve) => {
            let done = false;
            const finish = () => {
                if (done) return;
                done = true;
                dealCard.remove();
                resolve();
            };
            dealCard.addEventListener('animationend', finish, {once: true});
            window.setTimeout(finish, 520);
        });
    };

    const deckSourceRect = () => {
        const cards = dom.deckTower ? Array.from(dom.deckTower.querySelectorAll('img')) : [];
        return cards.at(-1)?.getBoundingClientRect()
            || dom.deckTower?.getBoundingClientRect()
            || dom.deckStack?.getBoundingClientRect();
    };

    const findSeatCard = (seat, card) => {
        if (!seat || !card) return null;
        const wantedKey = cardKey(card);
        return Array.from(seat.querySelectorAll('.replay-seat-card'))
            .find((img) => img.dataset.cardKey === wantedKey) || null;
    };

    const findAnimationTarget = (animation) => {
        if (!animation) return null;
        if (animation.direction === 'forward') {
            return dom.trick?.querySelector(`[data-play-index="${animation.playIndex}"]`) || null;
        }
        const seat = state.seats.get(playerKey(animation.play.player));
        return findSeatCard(seat, animation.play.card);
    };

    const cardVisualWidth = (element, rect) => {
        const styleWidth = element ? Number.parseFloat(window.getComputedStyle(element).width) : 0;
        return Math.max(styleWidth || rect?.cardWidth || rect?.width || 42, 42);
    };

    const rotationDegrees = (value) => Number.parseFloat(String(value || '0')) || 0;

    const rectWithCardWidth = (element) => {
        const rect = element.getBoundingClientRect();
        return {
            left: rect.left,
            top: rect.top,
            right: rect.right,
            bottom: rect.bottom,
            width: rect.width,
            height: rect.height,
            cardWidth: cardVisualWidth(element, rect)
        };
    };

    const prepareTrickTransition = (animation) => {
        if (!dom.trick) return;
        const oldRects = new Map();
        Array.from(dom.trick.querySelectorAll('.trick-card')).forEach((card) => {
            oldRects.set(card.dataset.playIndex, card.getBoundingClientRect());
        });

        const targetRound = state.game?.rounds?.[animation.toStep.roundIndex];
        const targetPlays = (targetRound?.plays || []).slice(0, animation.toStep.playCount);
        renderTrick(targetPlays, {
            hiddenPlayIndex: animation.direction === 'forward' ? animation.playIndex : null
        });

        Array.from(dom.trick.querySelectorAll('.trick-card')).forEach((card) => {
            const oldRect = oldRects.get(card.dataset.playIndex);
            if (!oldRect) return;
            const newRect = card.getBoundingClientRect();
            const deltaX = oldRect.left + oldRect.width / 2 - (newRect.left + newRect.width / 2);
            const deltaY = oldRect.top + oldRect.height / 2 - (newRect.top + newRect.height / 2);
            if (Math.abs(deltaX) < 0.5 && Math.abs(deltaY) < 0.5) return;

            card.style.transition = 'none';
            card.style.transform = `translate(${deltaX}px, ${deltaY}px) rotate(var(--spin, 0deg))`;
            card.getBoundingClientRect();
            requestAnimationFrame(() => {
                card.style.transition = 'transform 260ms cubic-bezier(.18, .84, .24, 1), margin-left 180ms ease';
                card.style.transform = 'translate(0, 0) rotate(var(--spin, 0deg))';
            });
            card.addEventListener('transitionend', () => {
                card.style.transition = '';
                card.style.transform = '';
            }, {once: true});
        });
    };

    const prepareHandTransition = (animation) => {
        const player = animation.play.player;
        const seat = state.seats.get(playerKey(player));
        const hand = seat?.querySelector('.seat-cards');
        if (!hand) return;

        const oldRects = new Map();
        Array.from(hand.querySelectorAll('.replay-seat-card')).forEach((card, index) => {
            oldRects.set(`${card.dataset.cardKey}:${index}`, card.getBoundingClientRect());
        });

        const targetRound = state.game?.rounds?.[animation.toStep.roundIndex];
        const targetCards = buildVisibleHands(targetRound, animation.toStep.playCount).get(playerKey(player)) || [];
        renderSeatHand(seat, player, targetCards, {
            hiddenCardKey: animation.direction === 'backward' ? cardKey(animation.play.card) : null
        });

        const oldCardQueues = new Map();
        oldRects.forEach((rect, key) => {
            const cardKeyOnly = key.slice(0, key.lastIndexOf(':'));
            const queue = oldCardQueues.get(cardKeyOnly) || [];
            queue.push(rect);
            oldCardQueues.set(cardKeyOnly, queue);
        });

        Array.from(hand.querySelectorAll('.replay-seat-card')).forEach((card) => {
            if (animation.direction === 'backward' && card.dataset.cardKey === cardKey(animation.play.card)) {
                return;
            }

            const queue = oldCardQueues.get(card.dataset.cardKey) || [];
            const oldRect = queue.shift();
            if (!oldRect) return;
            const newRect = card.getBoundingClientRect();
            const deltaX = oldRect.left + oldRect.width / 2 - (newRect.left + newRect.width / 2);
            const deltaY = oldRect.top + oldRect.height / 2 - (newRect.top + newRect.height / 2);
            if (Math.abs(deltaX) < 0.5 && Math.abs(deltaY) < 0.5) return;

            const baseTransform = card.style.transform || '';
            animateCardElementFromRect(card, oldRect, baseTransform);
        });
    };

    const animateCardElementFromRect = (card, oldRect, baseTransform = card.style.transform || '') => {
        const newRect = card.getBoundingClientRect();
        const deltaX = oldRect.left + oldRect.width / 2 - (newRect.left + newRect.width / 2);
        const deltaY = oldRect.top + oldRect.height / 2 - (newRect.top + newRect.height / 2);
        if (Math.abs(deltaX) < 0.5 && Math.abs(deltaY) < 0.5) return;

        card.style.transition = 'none';
        card.style.transform = `translate(${deltaX}px, ${deltaY}px) ${baseTransform}`;
        card.getBoundingClientRect();
        requestAnimationFrame(() => {
            card.style.transition = 'transform 260ms cubic-bezier(.18, .84, .24, 1), box-shadow 150ms ease, filter 150ms ease';
            card.style.transform = baseTransform;
        });
        card.addEventListener('transitionend', () => {
            card.style.transition = '';
        }, {once: true});
    };

    const wait = (ms) => new Promise((resolve) => window.setTimeout(resolve, ms));

    const sourceRotation = (animation) => animation.direction === 'forward'
        ? handCardRotation(animation.play.player, animation.play.card, animation.fromStep)
        : trickCardRotation(animation.playIndex);

    const targetRotation = (animation) => animation.direction === 'forward'
        ? trickCardRotation(animation.playIndex)
        : handCardRotation(animation.play.player, animation.play.card, animation.toStep);

    const trickCardRotation = (playIndex) => `${(playIndex * 4) - 6}deg`;

    const handCardRotation = (player, card, step) => {
        const round = state.game?.rounds?.[step?.roundIndex];
        if (!round) return '0deg';
        const cards = buildVisibleHands(round, step?.playCount || 0).get(playerKey(player)) || [];
        const index = cards.findIndex((handCard) => cardKey(handCard) === cardKey(card));
        if (index < 0) return '0deg';
        const centeredIndex = index - ((cards.length - 1) / 2);
        return `${centeredIndex * 5.5}deg`;
    };

    const measureTrickCardRect = (playIndex, step) => {
        const round = state.game?.rounds?.[step?.roundIndex];
        const plays = (round?.plays || []).slice(0, step?.playCount || 0);
        const trickRect = dom.trick?.getBoundingClientRect();
        if (!trickRect || !plays.length) return null;

        const measure = document.createElement('div');
        measure.className = 'history-replay-measure trick-area';
        measure.style.left = `${trickRect.left}px`;
        measure.style.top = `${trickRect.top}px`;
        measure.style.width = `${trickRect.width}px`;
        measure.style.height = `${trickRect.height}px`;

        plays.forEach((play, index) => {
            const img = document.createElement('img');
            img.className = 'trick-card';
            img.dataset.playIndex = String(index);
            img.alt = '';
            img.src = italianCardUrl(play.card?.card);
            img.style.setProperty('--spin', `${(index * 4) - 6}deg`);
            measure.appendChild(img);
        });

        document.body.appendChild(measure);
        const card = measure.querySelector(`[data-play-index="${playIndex}"]`);
        const cardRect = card ? rectWithCardWidth(card) : null;
        measure.remove();
        return cardRect;
    };

    const measureHandCardRect = (player, card, step) => {
        const seat = state.seats.get(playerKey(player));
        const handRect = seat?.querySelector('.seat-cards')?.getBoundingClientRect();
        const round = state.game?.rounds?.[step?.roundIndex];
        if (!handRect || !round) return null;

        const handCards = buildVisibleHands(round, step?.playCount || 0).get(playerKey(player)) || [];
        const wantedKey = cardKey(card);
        const measure = document.createElement('div');
        measure.className = 'history-replay-measure seat-cards';
        measure.style.left = `${handRect.left}px`;
        measure.style.top = `${handRect.top}px`;
        measure.style.width = `${handRect.width}px`;
        measure.style.height = `${handRect.height}px`;

        handCards.forEach((handCard, cardIndex) => {
            const img = document.createElement('img');
            img.className = 'seat-card replay-seat-card';
            img.dataset.cardKey = cardKey(handCard);
            img.alt = '';
            img.src = italianCardUrl(handCard.card);
            const centeredIndex = cardIndex - ((handCards.length - 1) / 2);
            img.style.transform = `translateY(${Math.abs(centeredIndex) * 1.4}px) rotate(${centeredIndex * 5.5}deg)`;
            measure.appendChild(img);
        });

        document.body.appendChild(measure);
        const measuredCard = Array.from(measure.querySelectorAll('.replay-seat-card'))
            .find((img) => img.dataset.cardKey === wantedKey);
        const cardRect = measuredCard ? rectWithCardWidth(measuredCard) : null;
        measure.remove();
        return cardRect;
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
            dom.meta.innerHTML = `
        <p>${escapeHtml(formatDate(game.endedAt || game.createdAt))}</p>
        <p>${escapeHtml(settingsText(game.gameConfig))}</p>
      `;
        }
        renderTrump();
        renderDeckTower();
        initializeSeats();
        render();
    };

    dom.prev?.addEventListener('click', () => setStepWithAnimation(state.stepIndex - 1));
    dom.next?.addEventListener('click', () => setStepWithAnimation(state.stepIndex + 1));
    dom.range?.addEventListener('input', () => setStep(Number(dom.range.value) || 0));
    window.addEventListener('resize', positionSeats);

    loadReplay().catch(() => {
        if (dom.title) dom.title.textContent = 'Replay unavailable';
        if (dom.meta) dom.meta.innerHTML = '<p>The game history could not be loaded.</p>';
        if (dom.stateLabel) dom.stateLabel.textContent = 'Error';
    });
})();
