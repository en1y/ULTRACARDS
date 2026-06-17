(() => {
            const storageKey = 'uc-theme';
            const savedTheme = localStorage.getItem(storageKey);
            const systemDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
            const theme = savedTheme || (systemDark ? 'dark' : 'light');
            document.documentElement.setAttribute('data-theme', theme);
        })();

(function () {
        /**
         * @typedef {{ cardType: string, card: string }} GameCard
         * @typedef {{ name: string, id: (string|null), cards: (number|undefined), points: (number|undefined) }} UiPlayer
         * @typedef {{ gameWinners?: Array<{name: string}>, winnerPointsNum?: number }} GameResult
         * @typedef {{
         *   id: string,
         *   lobbyId: string,
         *   name: string,
         *   playersCardsMap: Object<string, number>,
         *   playedCards: GameCard[],
         *   cardsLeftInDeck: number,
         *   pointsPerPerson?: Object<string, number>,
         *   playersTurn?: {name: string, id: (string|null)}|null,
         *   turnEndTime?: (string|null),
         *   turnDurationSeconds?: (number|null),
         *   trumpCard?: GameCard|null,
         *   gameConfig?: Object|null
         * }} GameState
         */
        const gameEl = document.getElementById('game-container');
        if (!gameEl || !gameEl.dataset.gameId) return;

        function getTeamDisplayName(n) {
            if (n === 1) return 'Team 1';
            if (n === 2) return 'Team 2';
            return `Team ${n}`;
        }

        const gameId = gameEl.dataset.gameId;
        const currentUserId = gameEl.dataset.currentUserId ? String(gameEl.dataset.currentUserId) : null;
        const currentUsername = gameEl.dataset.username ? String(gameEl.dataset.username) : '';
        const initialGame = window.__INITIAL_GAME__ ?? null;
        const initialChat = window.__INITIAL_GAME_CHAT__ ?? null;
        const wsUrl = `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws`;
        const gameLayout = document.querySelector('.game-layout');

        const dom = {
            ring: document.getElementById('player-ring'),
            trick: document.getElementById('trick-area'),
            tableSurface: document.querySelector('.table-surface') || document.querySelector('.table-felt'),
            trump: document.getElementById('trump-card'),
            deckLeft: document.getElementById('deck-left'),
            deckTower: document.getElementById('deck-tower'),
            deckStack: document.getElementById('deck-stack'),
            dropZone: document.getElementById('drop-zone'),
            hand: document.getElementById('hand-cards'),
            connectionToast: document.getElementById('connection-toast'),
            tableTurnOverlay: document.getElementById('table-turn-overlay'),
            tableTurnMessage: document.getElementById('table-turn-message'),
            playerSummary: document.getElementById('player-summary'),
            playerSummaryPoints: document.getElementById('player-summary-points'),
            playerSummaryAvatar: document.getElementById('player-summary-avatar'),
            playerSummaryName: document.getElementById('player-summary-name')
        };

        const state = {
            game: null,
            hand: [],
            wsConnected: false,
            playing: false,
            deckLeft: null,
            pending: new Set(),
            autoPlayedPending: new Set(),
            handEls: new Map(),
            trickEls: new Map(),
            justDealtKeys: new Set(),
            finalTrumpDraw: false,
            deckExhausting: false,
            dragSession: null,
            turnIndicatorFrame: null,
            turnIndicatorTargetKey: null,
            turnIndicatorEndsAt: null,
            turnDurationMs: Number(initialGame?.turnDurationSeconds) > 0 ? Number(initialGame.turnDurationSeconds) * 1000 : 15000,
            delayedTableUpdateTimer: null,
            delayedTableCommitTimer: null,
            delayedTablePayload: null,
            delayedTablePending: false,
            endState: null,
            endRedirectTimer: null,
            endRedirectInterval: null,
            pendingRound: null,
            dealingKeys: new Set(),
            draggingEl: null,
            justPlayed: null,
            hands: {
                self: null,
                table: null
            }
        };
        const PreviousRoundStore = {
            save(id, data) {
                try { localStorage.setItem('uc-prev-round-' + id, JSON.stringify(data)); } catch (e) {}
            },
            load(id) {
                try { return JSON.parse(localStorage.getItem('uc-prev-round-' + id)); } catch (e) { return null; }
            }
        };

        const prevRoundToggle = document.getElementById('prev-round-toggle');
        const prevRoundPanel = document.getElementById('prev-round-panel');
        const prevRoundCardsEl = document.getElementById('prev-round-cards');

        if (prevRoundToggle && prevRoundPanel) {
            prevRoundToggle.addEventListener('click', () => {
                const open = prevRoundPanel.classList.toggle('is-open');
                prevRoundToggle.setAttribute('aria-expanded', String(open));
                if (open) renderPrevRoundPanel();
            });
        }

        // The "Last Round" button only appears once a completed round is stored.
        function refreshPrevRoundButton() {
            if (!prevRoundToggle) return;
            const data = PreviousRoundStore.load(gameId);
            const hasData = !!(data && Array.isArray(data.cards) && data.cards.length);
            prevRoundToggle.classList.toggle('has-data', hasData);
        }

        function renderPrevRoundPanel() {
            if (!prevRoundCardsEl) return;
            const data = PreviousRoundStore.load(gameId);
            const titleEl = prevRoundPanel?.querySelector('.prev-round-title');
            if (!data || !Array.isArray(data.cards) || !data.cards.length) {
                prevRoundCardsEl.innerHTML = '<span class="prev-round-empty">No round recorded yet</span>';
                if (titleEl) titleEl.textContent = 'Last Completed Round';
                return;
            }
            const players = Array.isArray(data.players) ? data.players : [];
            const winnerName = data.winner?.name || null;
            // Show who won the round in the panel title.
            if (titleEl) {
                titleEl.textContent = winnerName ? `Round won by ${winnerName}` : 'Last Completed Round';
            }
            prevRoundCardsEl.innerHTML = '';
            data.cards.forEach((card, i) => {
                const entry = document.createElement('div');
                entry.className = 'prev-round-entry';

                // Player name goes ABOVE the card it threw.
                const label = document.createElement('div');
                label.className = 'prev-round-player';
                // Card i was played by the player at index i (modulo player count
                // covers the 2-player/4-card config where each plays twice).
                const player = players.length ? players[i % players.length] : null;
                const playerName = player?.name || `Player ${i + 1}`;
                label.textContent = playerName;
                // Mark the winner's card(s).
                if (winnerName && playerName === winnerName) {
                    entry.classList.add('is-winner');
                    label.textContent = `${playerName} ★`;
                }
                entry.appendChild(label);

                const wrap = window.UltracardsGameUi?.renderCardImage({
                    card,
                    className: 'prev-round-card-img',
                    alt: `Card ${i + 1}`
                });
                if (wrap) {
                    entry.appendChild(wrap);
                } else {
                    const img = document.createElement('img');
                    img.src = window.UltracardsGameUi?.cardUrl(card) || '';
                    img.alt = `Card ${i + 1}`;
                    entry.appendChild(img);
                }

                prevRoundCardsEl.appendChild(entry);
            });
        }

        let refreshTimer = null;
        let refreshAttempts = 0;
        const chat = window.UltracardsChat?.create({
            initialChat,
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
            emptyText: 'Why so scared to talk?'
        });

        state.hands.self = window.UltracardsGameUi?.registerHand(dom.hand, {
            type: 'fan',
            spacingScale: 0.4,
            maxTilt: 6,
            yArc: 5,
            baseOffsetY: 2
        });
        state.hands.table = window.UltracardsGameUi?.registerHand(dom.trick, {
            type: 'center',
            spacingScale: 0.45,
            maxTilt: 4,
            yArc: 3
        });
        // Raise the card nearest the cursor so the user always grabs the card they
        // point at (cards overlap, so a card's face is covered by the next one).
        window.UltracardsGameUi?.enableHandHoverRaise(state.hands.self, {
            isActive: () => !state.playing && !state.draggingEl
        });
        // One container-level drag that always picks the RAISED (enlarged) card — never
        // a card behind it, never a copy.
        window.UltracardsGameUi?.enableHandCardDrag(state.hands.self, {
            originZone: state.hands.self,
            className: 'drag-ghost',
            isActive: () => {
                const isTurn = state.game && state.game.playersTurn && isCurrentUser(state.game.playersTurn);
                return !state.playing && !!isTurn;
            },
            onStart(session, cardEl) {
                const card = cardFromEl(cardEl);
                state.draggingEl = cardEl;
                state.dragSession = {card, originEl: cardEl, handDrag: session};
            },
            onMove(session, event) {
                syncDragTargetFromPoint(session, event);
            },
            onEnd(session, event) {
                const card = state.dragSession?.card || cardFromEl(session.el);
                finishCardDrag(card, session, event);
            }
        });
        if (dom.tableSurface) setupDropZone(dom.tableSurface);
        window.addEventListener('resize', () => positionSeats(dom.ring));

        if (initialGame != null) {
            applyGame(initialGame, 'UPDATED', null);
        }
        refreshPrevRoundButton();
        refreshHand();
        loadGame();
        connectWs();

        function connectWs() {
            if (!window.Stomp) {
                setConnectionStatus(false, 'Live connection unavailable.');
                return;
            }
            const client = Stomp.client(wsUrl);
            client.reconnect_delay = 3000;
            client.debug = null;
            client.connect({}, () => {
                state.wsConnected = true;
                setConnectionStatus(true);
                client.subscribe(`/topic/game/${gameId}`, (msg) => {
                    try {
                        const payload = JSON.parse(msg.body);
                        if (!payload || !payload.gameEntity) return;
                        applyGame(payload.gameEntity, payload.gameEvent, payload.result);
                    } catch (err) {
                        console.error('Game event error', err);
                    }
                });
                if (initialGame?.lobbyId) {
                    client.subscribe(`/topic/lobbies/${initialGame.lobbyId}/chat`, (msg) => {
                        try {
                            const payload = JSON.parse(msg.body);
                            if (!payload || !payload.message) return;
                            chat?.addMessage(payload);
                        } catch (err) {
                            console.error('Game chat event error', err);
                        }
                    });
                }
            }, () => {
                state.wsConnected = false;
                setConnectionStatus(false, 'Connection lost. Reconnecting...');
            });
        }

        function setConnectionStatus(isConnected, text) {
            if (!dom.connectionToast) return;
            dom.connectionToast.classList.toggle('is-visible', !isConnected);
            if (!isConnected && text) {
                dom.connectionToast.textContent = text;
            }
        }

        function loadGame() {
            fetch(`/api/games/${gameId}`, {credentials: 'include'})
                .then((res) => res.ok ? res.json() : Promise.reject(res.status))
                .then((game) => {
                    if (game) applyGame(game, 'UPDATED', null);
                })
        }

        function refreshHand() {
            if (!dom.hand) return;
            fetch('/api/games', {credentials: 'include'})
                .then((res) => res.ok ? res.json() : Promise.reject(res.status))
                .then((cards) => {
                    const list = Array.isArray(cards) ? cards : [];
                    const prevKeys = new Set(state.hand.map(cardKey));
                    const serverKeys = new Set(list.map(cardKey));
                    const newCards = [];
                    state.pending.forEach((key) => {
                        if (!serverKeys.has(key)) {
                            state.pending.delete(key);
                        }
                    });
                    state.autoPlayedPending.forEach((key) => {
                        if (!serverKeys.has(key)) {
                            state.autoPlayedPending.delete(key);
                        }
                    });
                    list.forEach((card) => {
                        const key = cardKey(card);
                        if (!prevKeys.has(key)) {
                            state.justDealtKeys.add(key);
                            // Mark as dealing BEFORE syncHand so the card is created
                            // face-down and excluded from the generic fade-in.
                            state.dealingKeys.add(key);
                            newCards.push(card);
                        }
                    });
                    state.hand = list.filter((card) => !state.pending.has(cardKey(card)));
                    syncHand();
                    if (newCards.length) {
                        dealCardsIntoHand(newCards, state.finalTrumpDraw);
                        state.finalTrumpDraw = false;
                    }
                    if (!state.pending.size && refreshTimer) {
                        clearTimeout(refreshTimer);
                        refreshTimer = null;
                        refreshAttempts = 0;
                    }
                })
                .catch(() => {
                    if (refreshTimer) {
                        clearTimeout(refreshTimer);
                        refreshTimer = null;
                        refreshAttempts = 0;
                    }
                });
        }

        function scheduleHandRefresh(attempts) {
            if (attempts && attempts > refreshAttempts) refreshAttempts = attempts;
            if (refreshTimer) return;
            const run = () => {
                if (refreshAttempts <= 0) {
                    refreshTimer = null;
                    return;
                }
                refreshAttempts -= 1;
                refreshHand();
                refreshTimer = setTimeout(run, 900);
            };
            refreshTimer = setTimeout(run, 350);
        }

        /**
         * @param {GameState} game
         * @param {string|null} gameEvent
         * @param {GameResult|null} result
         */
        function applyGame(game, gameEvent, result, skipTableDelay = false) {
            game.playersTurn = normalizePlayer(game.playersTurn);
            if (Number(game.turnDurationSeconds) > 0) {
                state.turnDurationMs = Number(game.turnDurationSeconds) * 1000;
            }
            const previousPlayedCards = Array.isArray(state.game?.playedCards) ? state.game.playedCards : [];
            const nextPlayedCards = Array.isArray(game.playedCards) ? game.playedCards : [];
            // Capture the in-trick player order BEFORE state.game is reassigned. Once a
            // trick completes the backend rotates playersOrder (winner first), so the
            // incoming `game` no longer matches the played-card order — this does.
            const inTrickPlayers = Array.isArray(state.game?.playersOrder)
                ? state.game.playersOrder.map((p) => parsePlayerKey(p))
                : [];
            const prevDeckLeft = state.deckLeft;
            const nextDeckLeft = game.cardsLeftInDeck ?? 0;
            const shouldDelayTableUpdate = !skipTableDelay && previousPlayedCards.length > 0 && nextPlayedCards.length === 0;
            if (shouldDelayTableUpdate) {
                state.pendingRound = {
                    cards: previousPlayedCards,
                    players: inTrickPlayers.map((p) => ({id: p.id, name: p.name})),
                    // The trick winner leads the next trick, so the incoming game's
                    // playersTurn is who won the round that just completed.
                    winner: game.playersTurn ? {id: game.playersTurn.id, name: game.playersTurn.name} : null
                };
                if (!state.delayedTablePayload || isHigherPriorityGameEvent(gameEvent, state.delayedTablePayload.gameEvent)) {
                    state.delayedTablePayload = {game, gameEvent, result};
                } else if (gameEvent === state.delayedTablePayload.gameEvent) {
                    state.delayedTablePayload = {game, gameEvent, result};
                }
                state.game = {
                    ...game,
                    playedCards: previousPlayedCards
                };
                state.deckLeft = nextDeckLeft;
                if (dom.deckLeft) dom.deckLeft.textContent = String(nextDeckLeft);
                renderDeckTower(nextDeckLeft);
                renderTrump(game.trumpCard, nextDeckLeft);
                renderPlayers(game);
                updateTurnIndicator(game);
                renderCurrentPlayer(game);
                if (prevDeckLeft != null && prevDeckLeft > nextDeckLeft) {
                    animateDeckDraw(prevDeckLeft - nextDeckLeft);
                    if (currentUserId) scheduleHandRefresh(4);
                }
                updateTurn(game.playersTurn);
                if (state.pending.size) scheduleHandRefresh(4);
                if (state.delayedTablePending) {
                    return;
                }
                state.delayedTablePending = true;
                state.delayedTableUpdateTimer = setTimeout(() => {
                    state.delayedTableUpdateTimer = null;
                    animateTrickClear();
                    state.delayedTableCommitTimer = setTimeout(() => {
                        state.delayedTableCommitTimer = null;
                        const delayedPayload = state.delayedTablePayload;
                        state.delayedTablePayload = null;
                        state.delayedTablePending = false;
                        if (delayedPayload) {
                            applyGame(delayedPayload.game, delayedPayload.gameEvent, delayedPayload.result, true);
                        }
                    }, 320);
                }, 1100);
                return;
            }
            if (state.delayedTableUpdateTimer) {
                clearTimeout(state.delayedTableUpdateTimer);
                state.delayedTableUpdateTimer = null;
            }
            if (state.delayedTableCommitTimer) {
                clearTimeout(state.delayedTableCommitTimer);
                state.delayedTableCommitTimer = null;
            }
            state.delayedTablePayload = null;
            state.delayedTablePending = false;
            state.game = game;
            state.finalTrumpDraw = prevDeckLeft != null && prevDeckLeft > nextDeckLeft && nextDeckLeft === 0;
            if (state.finalTrumpDraw) {
                state.deckExhausting = true;
            }
            if (nextDeckLeft <= 0 || gameEvent === 'RESULTED' || gameEvent === 'CLOSED') {
                state.deckExhausting = false;
            }
            state.deckLeft = nextDeckLeft;
            if (dom.deckLeft) dom.deckLeft.textContent = String(nextDeckLeft);
            renderDeckTower(nextDeckLeft);
            renderTrump(game.trumpCard, nextDeckLeft);
            const autoplayedCard = detectAutoPlayedCard(previousPlayedCards, nextPlayedCards);
            if (autoplayedCard) {
                animateAutoPlayedHandRemoval(autoplayedCard);
                scheduleHandRefresh(4);
            }
            renderTrick(nextPlayedCards, previousPlayedCards);
            renderPlayers(game);
            updateTurnIndicator(game);
            renderCurrentPlayer(game);
            if (prevDeckLeft != null && prevDeckLeft > nextDeckLeft) {
                animateDeckDraw(prevDeckLeft - nextDeckLeft);
                if (currentUserId) scheduleHandRefresh(4);
            }
            updateTurn(game.playersTurn);
            if (state.pending.size) scheduleHandRefresh(4);
            if (gameEvent === 'RESULTED' && result && Array.isArray(result.gameWinners)) {
                const winners = formatWinnerText(result.gameWinners, game);
                state.endState = {
                    title: 'Match Result',
                    winnersText: winners,
                    metaText: buildResultMetaText(result.gameWinners, game)
                };
                renderCenterResult(state.endState.title, state.endState.winnersText, state.endState.metaText);
                startLobbyReturnCountdown();
            } else if (gameEvent === 'CLOSED') {
                state.endState = {
                    title: 'Match Result',
                    winnersText: 'Game closed',
                    metaText: 'Returning to lobby'
                };
                renderCenterResult(state.endState.title, state.endState.winnersText, state.endState.metaText);
                startLobbyReturnCountdown();
            } else if (state.endState) {
                renderCenterResult(state.endState.title, state.endState.winnersText, state.endState.metaText);
            } else {
                clearCenterResult();
            }
        }

        function isHigherPriorityGameEvent(nextEvent, currentEvent) {
            const priority = {
                UPDATED: 1,
                STARTED: 1,
                CLOSED: 2,
                RESULTED: 3
            };
            return (priority[nextEvent] || 0) > (priority[currentEvent] || 0);
        }

        function renderTrump(card, deckLeft) {
            if (!dom.trump) return;
            if (!card || ((deckLeft != null && deckLeft <= 0) && !state.deckExhausting)) {
                dom.trump.style.display = 'none';
                dom.trump.dataset.cardCode = '';
                dom.trump.dataset.cardFace = 'false';
                dom.deckStack?.classList.remove('has-trump');
                return;
            }
            const frontImg = dom.trump.querySelector('.card-front');
            const url = italianCardUrl(card.card);
            if (frontImg) frontImg.src = url;
            dom.trump.dataset.cardCode = card.card || '';
            dom.trump.dataset.cardFace = 'true';
            dom.trump.style.display = '';
            dom.deckStack?.classList.add('has-trump');
            setupTrumpZoom(dom.trump);
        }

        function renderCenterResult(title, winnersText, metaText) {
            if (!dom.dropZone) return;
            dom.dropZone.classList.remove('ready');
            dom.dropZone.classList.add('is-result');
            dom.dropZone.innerHTML = `
                <div class="drop-zone-title">${title || 'Match Result'}</div>
                <div class="drop-zone-winner">${winnersText || 'No winner recorded'}</div>
                <div class="drop-zone-meta">${metaText || ''}</div>
            `;
        }

        function clearCenterResult() {
            if (!dom.dropZone || !dom.dropZone.classList.contains('is-result')) return;
            dom.dropZone.classList.remove('is-result');
            dom.dropZone.textContent = 'Drag a card to play';
            stopLobbyReturnCountdown();
        }

        function startLobbyReturnCountdown() {
            if (state.endRedirectTimer || state.endRedirectInterval) return;
            let secondsLeft = 6;
            updateLobbyReturnCountdown(secondsLeft);
            state.endRedirectInterval = setInterval(() => {
                secondsLeft -= 1;
                if (secondsLeft <= 0) {
                    stopLobbyReturnCountdown();
                    window.location.href = '/lobbies';
                    return;
                }
                updateLobbyReturnCountdown(secondsLeft);
            }, 1000);
            state.endRedirectTimer = setTimeout(() => {
                stopLobbyReturnCountdown();
                window.location.href = '/lobbies';
            }, 6000);
        }

        function stopLobbyReturnCountdown() {
            if (state.endRedirectInterval) {
                clearInterval(state.endRedirectInterval);
                state.endRedirectInterval = null;
            }
            if (state.endRedirectTimer) {
                clearTimeout(state.endRedirectTimer);
                state.endRedirectTimer = null;
            }
        }

        function updateLobbyReturnCountdown(secondsLeft) {
            if (!dom.dropZone || !dom.dropZone.classList.contains('is-result')) return;
            const meta = dom.dropZone.querySelector('.drop-zone-meta');
            if (meta) {
                meta.textContent = `Returning to lobby in ${secondsLeft}s`;
            }
        }

        /**
         * @param {GameCard[]} cards
         */
        function renderTrick(cards, previousCards = []) {
            if (!dom.trick) return;
            dom.trick.classList.remove('is-clearing');
            const list = Array.isArray(cards) ? cards : [];
            const enteringKeys = collectEnteringCardKeys(previousCards, cards);
            const nextKeys = new Set(list.map((card, idx) => `${cardKey(card)}:${idx}`));
            const ordered = [];
            const enteringCards = [];

            list.forEach((card, idx) => {
                const key = `${cardKey(card)}:${idx}`;
                let img = state.trickEls.get(key);
                if (!img) {
                    img = window.UltracardsGameUi?.renderCardImage({
                        card,
                        className: 'trick-card',
                        alt: 'Played card'
                    }) || document.createElement('img');
                    img.dataset.trickKey = key;
                    state.trickEls.set(key, img);
                    // Don't fade-in the card the local player just played: a real card is
                    // already sitting on that slot (state.justPlayed) and will be handed off.
                    if (enteringKeys.has(cardKey(card)) && cardKey(card) !== state.justPlayed?.key) {
                        enteringCards.push(img);
                    }
                }
                img.style.setProperty('--spin', `${(idx * 4) - 6}deg`);
                ordered.push(img);
            });

            Promise.resolve(window.UltracardsGameUi?.animateHandChange(state.hands.table, () => {
                state.trickEls.forEach((el, key) => {
                    if (!nextKeys.has(key)) {
                        el.remove();
                        state.trickEls.delete(key);
                    }
                });
                ordered.forEach((el) => dom.trick.appendChild(el));
            }, {
                cards: ordered,
                layout: {
                    type: 'center',
                    spacingScale: 0.45,
                    maxTilt: 4,
                    yArc: 3
                }
            })).then(() => {
                // The rendered trick card now occupies the slot — remove the flown real
                // card we kept there for a seamless handoff (no flicker / no reappear).
                if (state.justPlayed && list.some((c) => cardKey(c) === state.justPlayed.key)) {
                    state.justPlayed.el?.remove();
                    state.justPlayed = null;
                }
                enteringCards.forEach((img) => {
                    const transform = getComputedStyle(img).transform === 'none' ? '' : getComputedStyle(img).transform;
                    Promise.resolve(window.UltracardsGameUi?.animateElement(img, {
                        opacity: [0, 1],
                        transform: [`${transform} translate3d(0, 18px, 0) scale(.9)`, transform || 'scale(1)'],
                        duration: 220,
                        ease: 'out(3)'
                    })).then(() => {
                        img.style.opacity = '';
                        img.style.transform = '';
                    });
                });
            });
        }

        function animateTrickClear() {
            if (!dom.trick) return;
            const trickCards = Array.from(dom.trick.querySelectorAll('.trick-card'));
            if (!trickCards.length) return;

            // Save previous round before clearing. Prefer the captured in-trick data
            // (cards + the player order at play time, before the winner rotation).
            const pending = state.pendingRound;
            const cardsToSave = Array.isArray(pending?.cards) && pending.cards.length
                ? pending.cards
                : (Array.isArray(state.game?.playedCards) ? state.game.playedCards : []);
            if (cardsToSave.length > 0) {
                const roundData = {
                    cards: cardsToSave,
                    players: Array.isArray(pending?.players) && pending.players.length
                        ? pending.players
                        : (state.game?.playersOrder ?? []).map((p) => ({id: p.id, name: p.name})),
                    winner: pending?.winner
                        || (state.game?.playersTurn ? {id: state.game.playersTurn.id, name: state.game.playersTurn.name} : null),
                    timestamp: Date.now()
                };
                PreviousRoundStore.save(gameId, roundData);
                state.pendingRound = null;
                refreshPrevRoundButton();
                if (prevRoundPanel?.classList.contains('is-open')) renderPrevRoundPanel();
            }

            dom.trick.classList.remove('is-clearing');
            dom.trick.classList.add('is-clearing');

            const winnerPlayer = state.game?.playersTurn;
            const winnerSeatEl = winnerPlayer?.id
                ? dom.ring?.querySelector(`[data-player-id="${CSS.escape(String(winnerPlayer.id))}"]`)
                : null;

            Promise.resolve(
                window.UltracardsGameUi?.animateTrickCollect(trickCards, winnerSeatEl)
            ).then(() => {
                dom.trick.classList.remove('is-clearing');
            });
        }

        /**
         * @param {GameState} game
         */
        function renderPlayers(game) {
            if (!dom.ring) return;
            const players = buildPlayers(game);
            const teamState = resolveTeams(game);
            gameLayout?.classList.toggle('is-two-player', players.length === 2);
            gameLayout?.classList.toggle('is-four-player', players.length === 4);
            gameLayout?.classList.toggle('is-team-game', !!teamState);
            const existingSeats = new Map();
            Array.from(dom.ring.children).forEach((seat) => {
                const key = seat.dataset.playerKey;
                if (key) {
                    existingSeats.set(key, seat);
                }
            });

            players.forEach((player, index) => {
                const key = playerSeatKey(player);
                let seat = existingSeats.get(key);
                if (!seat) {
                    seat = document.createElement('div');
                    seat.className = 'player-seat';
                    seat.dataset.playerKey = key;

                    const avatar = document.createElement('div');
                    avatar.className = 'seat-avatar';
                    seat.appendChild(avatar);

                    const name = document.createElement('div');
                    name.className = 'seat-name';
                    seat.appendChild(name);

                    const badge = document.createElement('div');
                    badge.className = 'seat-team-badge';
                    badge.hidden = true;
                    seat.appendChild(badge);

                    const cards = document.createElement('div');
                    cards.className = 'seat-cards';
                    seat.appendChild(cards);

                    const points = document.createElement('div');
                    points.className = 'seat-points';
                    seat.appendChild(points);
                }
                existingSeats.delete(key);

                const isSelf = isCurrentUser(player);
                const teamNumber = getPlayerTeamNumber(teamState, player);
                const teamTone = resolvePlayerTeamTone(teamState, player);
                if (player.id) seat.dataset.playerId = String(player.id);
                seat.classList.toggle('is-turn', isSamePlayer(player, game.playersTurn));
                seat.classList.toggle('is-self', isSelf);
                seat.classList.remove('team-seat-ally', 'team-seat-enemy', 'team-seat-neutral');
                seat.classList.remove('team-seat-1', 'team-seat-2');
                seat.classList.add(`team-seat-${teamTone}`);
                if (teamNumber != null) {
                    seat.classList.add(`team-seat-${teamNumber}`);
                }
                if (isSelf) {
                    seat.dataset.isSelf = '1';
                } else {
                    delete seat.dataset.isSelf;
                }
                if (teamNumber != null) {
                    seat.dataset.teamNumber = String(teamNumber);
                } else {
                    delete seat.dataset.teamNumber;
                }
                seat.dataset.seatIndex = String(index);
                seat.dataset.seatTotal = String(players.length || 1);

                const avatar = seat.querySelector('.seat-avatar');
                if (avatar) {
                    avatar.textContent = (player.name || 'P').charAt(0).toUpperCase();
                }

                const name = seat.querySelector('.seat-name');
                if (name) {
                    name.textContent = player.name || 'Player';
                }

                const badge = seat.querySelector('.seat-team-badge');
                if (badge) {
                    const badgeText = formatSeatTeamBadge(teamState, player);
                    badge.textContent = badgeText;
                    badge.hidden = !badgeText;
                }

                const cardsCount = player.cards != null ? player.cards : (player.points ?? 0);
                const cards = seat.querySelector('.seat-cards');
                if (cards) {
                    syncSeatCards(cards, cardsCount, seat.dataset.seatSide || 'center');
                }

                const points = seat.querySelector('.seat-points');
                if (points) {
                    let bubble = points.querySelector('.seat-points-bubble');
                    if (!bubble) {
                        points.textContent = '';
                        bubble = document.createElement('span');
                        bubble.className = 'seat-points-bubble';
                        points.appendChild(bubble);
                    }
                    bubble.textContent = String(player.points ?? 0);
                }

                if (seat.parentElement !== dom.ring) {
                    dom.ring.appendChild(seat);
                }
            });

            existingSeats.forEach((seat) => seat.remove());
            positionSeats(dom.ring);
        }

        function updateTurn(playersTurn) {
            const isTurn = !playersTurn || isCurrentUser(playersTurn);
            if (dom.dropZone) {
                if (dom.dropZone.classList.contains('is-result')) return;
                dom.dropZone.textContent = isTurn ? 'Play here' : 'Wait a moment';
            }
            if (dom.tableTurnOverlay) {
                dom.tableTurnOverlay.classList.toggle('is-visible', !!playersTurn);
            }
            if (dom.tableTurnMessage) {
                dom.tableTurnMessage.textContent = isTurn
                    ? 'Your turn'
                    : `Waiting for ${playersTurn && playersTurn.name ? playersTurn.name : 'the next player'}`;
            }
            syncHand();
        }

        function syncHand() {
            if (!dom.hand) return;
            const isTurn = state.game && state.game.playersTurn
                && isCurrentUser(state.game.playersTurn);
            const visibleHand = state.hand.filter((card) => {
                const key = cardKey(card);
                return !state.pending.has(key) && !state.autoPlayedPending.has(key);
            });
            const nextKeys = new Set(visibleHand.map(cardKey));

            const ordered = [];
            visibleHand.forEach((card) => {
                const key = cardKey(card);
                let el = state.handEls.get(key);
                if (!el) {
                    el = createHandCard(card, isTurn);
                    state.handEls.set(key, el);
                    state.justDealtKeys.delete(key);
                } else {
                    updateHandCardState(el, card, isTurn);
                }
                ordered.push(el);
            });

            // The card currently being dragged lives in the overlay layer; keep it out
            // of the hand container/layout so it isn't yanked back mid-drag.
            const orderedForLayout = state.draggingEl
                ? ordered.filter((el) => el !== state.draggingEl)
                : ordered;
            window.UltracardsGameUi?.animateHandChange(state.hands.self, () => {
                state.handEls.forEach((el, key) => {
                    if (!nextKeys.has(key) && el !== state.draggingEl) {
                        el.remove();
                        state.handEls.delete(key);
                    }
                });
                orderedForLayout.forEach((el) => dom.hand.appendChild(el));
            }, {
                cards: orderedForLayout,
                layout: {
                    type: 'fan',
                    spacingScale: 0.4,
                    maxTilt: 6,
                    yArc: 5,
                    baseOffsetY: 2
                }
            });
        }

        function applyHandFan(cards) {
            window.UltracardsGameUi?.applyHandFan(cards);
        }

        function createHandCard(card, isTurn) {
            const wrap = window.UltracardsGameUi?.createCard({
                card,
                className: 'hand-card',
                alt: 'Card'
            }) || document.createElement('div');
            // Cards that are being dealt in start face-down and flip once they land
            // (see dealCardsIntoHand). The card flips itself via its own cardApi.
            if (state.dealingKeys.has(cardKey(card))) {
                wrap.classList.add('is-dealing');
                wrap.cardApi?.showBack();
            }
            updateHandCardState(wrap, card, isTurn);
            return wrap;
        }

        // Reconstruct the minimal card object from a hand card element's dataset.
        function cardFromEl(el) {
            if (!el) return null;
            return {cardType: el.dataset.cardType, card: el.dataset.cardCode};
        }

        function updateHandCardState(img, card, isTurn) {
            // Dragging is handled once at the hand-container level (enableHandCardDrag),
            // which always grabs the raised/enlarged card. Per-card we only manage the
            // disabled look and the double-click-to-play shortcut.
            img.draggable = false;
            img.classList.toggle('is-disabled', !isTurn || state.playing);
            img.ondblclick = (!isTurn || state.playing)
                ? null
                : () => playCardFromHand(card, img);
        }

        function playCardFromHand(card, sourceEl) {
            if (state.playing) return;
            const isTurn = state.game && state.game.playersTurn
                && isCurrentUser(state.game.playersTurn);
            if (state.game && state.game.playersTurn && !isTurn) {
                return;
            }
            if (!sourceEl) {
                playCard(card);
                return;
            }
            state.playing = true;
            state.pending.add(cardKey(card));
            state.handEls.delete(cardKey(card));
            // Lift the REAL card into the overlay and fly it to the trick slot with the
            // same seamless handoff as a drag-drop play (no fade, no reappear).
            const session = window.UltracardsGameUi?.startDragCard({
                sourceEl,
                originZone: state.hands.self,
                pointer: {x: 0, y: 0}
            });
            const el = (session && session.el) || sourceEl;
            const targetRect = getTableTargetRect();
            markJustPlayed(el, card);
            Promise.resolve(window.UltracardsGameUi?.flyOverlayTo(el, targetRect, {
                duration: 230,
                toScale: 0.94,
                fade: false
            })).catch(() => undefined).then(() => {
                window.UltracardsGameUi?.clearReservedSlot(state.hands.table);
                playCard(card, {alreadyLocked: true});
            });
        }

        // Track the real card flown to the table so renderTrick can hand off to the
        // drawn trick card seamlessly. A safety timer removes it even if the trick was
        // already cleared (e.g. the play completed the trick) so it can't linger.
        function markJustPlayed(el, card) {
            const entry = {el, key: cardKey(card)};
            state.justPlayed = entry;
            setTimeout(() => {
                if (state.justPlayed === entry) {
                    entry.el?.remove();
                    state.justPlayed = null;
                }
            }, 1600);
        }

        function playCard(card, options) {
            const alreadyLocked = options?.alreadyLocked === true;
            if (state.playing && !alreadyLocked) return;
            const isTurn = state.game && state.game.playersTurn
                && isCurrentUser(state.game.playersTurn);
            if (state.game && state.game.playersTurn && !isTurn) {
                if (alreadyLocked) state.playing = false;
                return;
            }
            state.playing = true;
            state.pending.add(cardKey(card));
            fetch('/api/games', {
                method: 'POST',
                credentials: 'include',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({cardType: card.cardType, card: card.card})
            })
                .then(async (res) => {
                    if (!res.ok) {
                        const text = await res.text();
                        throw new Error(text || `play failed: ${res.status}`);
                    }
                    return res.json().catch(() => null);
                })
                .then((game) => {
                    state.playing = false;
                    animateHandRemoval(cardKey(card));
                    setTimeout(() => {
                        state.hand = state.hand.filter((entry) => cardKey(entry) !== cardKey(card));
                        syncHand();
                    }, 230);
                    if (game) {
                        applyGame(game, 'UPDATED', null);
                    }
                    scheduleHandRefresh(6);
                })
                .catch((err) => {
                    state.playing = false;
                    state.pending.delete(cardKey(card));
                    // Play rejected: discard the flown real card so it doesn't linger,
                    // then re-create the card cleanly in the hand.
                    if (state.justPlayed && state.justPlayed.key === cardKey(card)) {
                        state.justPlayed.el?.remove();
                        state.justPlayed = null;
                    }
                    restoreDraggedCard(cardKey(card));
                    syncHand();
                });
        }

        function setTableDropReady(on) {
            dom.dropZone?.classList.toggle('ready', on);
            dom.tableSurface?.classList.toggle('is-drop-ready', on);
        }

        function setupDropZone(zone) {
            // Drop detection is driven by pointer position (see syncDragTargetFromPoint /
            // finishCardDrag) so the whole table accepts drops regardless of element overlap.
            zone.addEventListener('pointerdown', (evt) => evt.preventDefault());
        }

        function syncDragTargetFromPoint(session, event) {
            if (!session) return;
            const x = Number(event.clientX ?? event.pageX ?? session.lastPoint?.x ?? 0);
            const y = Number(event.clientY ?? event.pageY ?? session.lastPoint?.y ?? 0);
            const inside = isPointInTableTarget(x, y);
            if (inside === session.overTable) return;
            session.overTable = inside;
            setTableDropReady(inside);
            if (inside) {
                window.UltracardsGameUi?.reserveSlot(state.hands.table, state.trickEls.size);
            } else {
                window.UltracardsGameUi?.clearReservedSlot(state.hands.table);
            }
        }

        function finishCardDrag(card, session, event) {
            if (!session) return;
            setTableDropReady(false);
            const point = {
                x: Number(event.clientX ?? event.pageX ?? session.lastPoint?.x ?? 0),
                y: Number(event.clientY ?? event.pageY ?? session.lastPoint?.y ?? 0)
            };
            // Pointer position is authoritative: release anywhere over the table plays the card.
            const accepted = isPointInTableTarget(point.x, point.y);
            const targetRect = accepted ? getTableTargetRect() : session.originRect;
            if (accepted && targetRect) {
                const el = session.el;
                state.dragSession = null;
                state.draggingEl = null;
                state.handEls.delete(cardKey(card));
                // Exclude from the hand right away so a mid-flight syncHand can't
                // re-create a duplicate while the card flies to the table.
                state.pending.add(cardKey(card));
                // Fly the REAL card to the trick slot WITHOUT fading, and keep it there
                // until renderTrick draws the played card at the same spot — then it is
                // removed. No disappear/reappear, and it lands on the exact slot.
                markJustPlayed(el, card);
                Promise.resolve(window.UltracardsGameUi?.flyOverlayTo(el, targetRect, {
                    duration: 200,
                    toScale: 0.94,
                    fade: false
                })).then(() => {
                    window.UltracardsGameUi?.clearReservedSlot(state.hands.table);
                    playCard(card);
                });
                return;
            }
            window.UltracardsGameUi?.clearReservedSlot(state.hands.table);
            state.dragSession = null;
            state.draggingEl = null;
            // Not played: return the real card into the hand at the drop-X position,
            // flying + rotating it into its exact slot while the others open a gap.
            returnCardToHand(card, session, point);
        }

        // Drop a dragged card back into the hand at the position implied by drop X.
        // The other cards spread to make room (n+1) via their CSS transition, and the
        // returned card flies + rotates from the release point into its exact slot
        // using the same composable --deal-* vars the deal animation uses.
        function returnCardToHand(card, session, point) {
            const el = session?.el;
            if (!el || !dom.hand) return;
            const releaseRect = el.getBoundingClientRect();
            const key = cardKey(card);

            const handCards = Array.from(dom.hand.children)
                .filter((c) => c !== el && c.classList && c.classList.contains('hand-card'));
            let index = handCards.length;
            for (let i = 0; i < handCards.length; i++) {
                const r = handCards[i].getBoundingClientRect();
                if (point.x < r.left + r.width / 2) { index = i; break; }
            }

            // Reorder the model so syncHand keeps the new order.
            const others = state.hand.filter((c) => cardKey(c) !== key);
            others.splice(index, 0, card);
            state.hand = others;

            // Clean drag styles and re-insert the REAL element at the chosen index.
            el.classList.remove('drag-ghost');
            el.style.transform = '';
            el.style.opacity = '';
            el.style.width = '';
            el.style.height = '';
            const ref = handCards[index] || null;
            if (ref && ref.parentElement === dom.hand) dom.hand.insertBefore(el, ref);
            else dom.hand.appendChild(el);

            // Lay out so the other cards open a gap, then fly the returning card into
            // its exact slot FACE-UP (faceDown:false → keeps its face toward the player).
            window.UltracardsGameUi?.layoutHand(state.hands.self);
            window.UltracardsGameUi?.flyIntoSlot(el, releaseRect, {
                faceDown: false,
                spin: 10,
                fromScale: 1.04,
                duration: 300
            });
        }

        function isPointInTableTarget(x, y) {
            const target = dom.tableSurface || dom.dropZone || dom.trick;
            const rect = target?.getBoundingClientRect();
            return !!rect
                && x >= rect.left
                && x <= rect.right
                && y >= rect.top
                && y <= rect.bottom;
        }

        function getTableTargetRect() {
            const placeholder = window.UltracardsGameUi?.reserveSlot(state.hands.table, state.trickEls.size);
            return placeholder?.getBoundingClientRect() || dom.dropZone?.getBoundingClientRect() || dom.trick?.getBoundingClientRect();
        }

        /**
         * @param {GameState} game
         * @returns {UiPlayer[]}
         */
        function buildPlayers(game) {
            const players = new Map();
            const orderedKeys = [];
            const registerPlayer = (candidate, keyFallback = '') => {
                const parsed = parsePlayerKey(candidate);
                const idKey = parsed.id || parsed.name || keyFallback;
                if (!idKey) {
                    return null;
                }
                const existing = players.get(idKey) || {};
                players.set(idKey, {...existing, ...parsed});
                if (!orderedKeys.includes(idKey)) {
                    orderedKeys.push(idKey);
                }
                return idKey;
            };

            (game.playersOrder || []).forEach((player) => {
                registerPlayer(player);
            });

            const cardsMap = game.playersCardsMap || {};
            Object.keys(cardsMap).forEach((key) => {
                const idKey = registerPlayer(key, key);
                if (!idKey) {
                    return;
                }
                const existing = players.get(idKey) || {};
                players.set(idKey, {...existing, cards: cardsMap[key]});
            });
            const pointsMap = game.pointsPerPerson || {};
            Object.keys(pointsMap).forEach((key) => {
                const idKey = registerPlayer(key, key);
                if (!idKey) {
                    return;
                }
                const existing = players.get(idKey) || {};
                players.set(idKey, {...existing, points: pointsMap[key]});
            });
            const orderedPlayers = orderedKeys
                .map((key) => players.get(key))
                .filter(Boolean);
            if (!currentUserId) {
                return orderedPlayers;
            }

            const currentIndex = orderedPlayers.findIndex((player) => isCurrentUser(player));
            if (currentIndex <= 0) {
                return orderedPlayers;
            }

            return orderedPlayers
                .slice(currentIndex)
                .concat(orderedPlayers.slice(0, currentIndex));
        }

        function resolveTeams(game) {
            const config = game?.gameConfig;
            if (!config || config.teamsEnabled !== true) {
                return null;
            }

            const configuredUsers = normalizeTeamPlayers(config.orderedUsers);
            const playOrderUsers = normalizeTeamPlayers(game?.playersOrder);
            const orderedUsers = configuredUsers.length >= 4 ? configuredUsers : playOrderUsers;
            if (orderedUsers.length < 4) {
                return null;
            }
            const usePlayOrderTeams = configuredUsers.length < 4;
            const team1 = usePlayOrderTeams
                ? [orderedUsers[0], orderedUsers[2]]
                : orderedUsers.slice(0, 2);
            const team2 = usePlayOrderTeams
                ? [orderedUsers[1], orderedUsers[3]]
                : orderedUsers.slice(2, 4);
            const teamByKey = new Map();
            team1.forEach((player) => addTeamLookupKeys(teamByKey, player, 1));
            team2.forEach((player) => addTeamLookupKeys(teamByKey, player, 2));

            return {
                team1,
                team2,
                teamByKey,
                currentUserTeamNumber: getPlayerTeamNumber({teamByKey}, {id: currentUserId, name: currentUsername})
            };
        }

        function normalizeTeamPlayers(team) {
            if (!Array.isArray(team)) {
                return [];
            }
            return team
                .map((player) => parsePlayerKey(player))
                .filter((player) => player && (player.id != null || player.name));
        }

        function getPlayerTeamNumber(teamState, player) {
            if (!teamState || !player) {
                return null;
            }
            for (const key of playerLookupKeys(player)) {
                const teamNumber = teamState.teamByKey.get(key);
                if (teamNumber != null) {
                    return teamNumber;
                }
            }
            return null;
        }

        function resolvePlayerTeamTone(teamState, player) {
            if (!teamState) {
                return 'neutral';
            }
            const playerTeam = getPlayerTeamNumber(teamState, player);
            if (!playerTeam || !teamState.currentUserTeamNumber) {
                return 'neutral';
            }
            return playerTeam === teamState.currentUserTeamNumber ? 'ally' : 'enemy';
        }

        function formatSeatTeamBadge(teamState, player) {
            const teamNumber = getPlayerTeamNumber(teamState, player);
            if (!teamNumber) {
                return '';
            }
            if (isCurrentUser(player)) {
                return '';
            }
            const tone = resolvePlayerTeamTone(teamState, player);
            if (tone === 'ally') {
                return 'Teammate';
            }
            if (tone === 'enemy') {
                return 'Opponent';
            }
            return getTeamDisplayName(teamNumber);
        }

        function formatWinnerText(winners, game) {
            if (!Array.isArray(winners) || !winners.length) {
                return 'No winner recorded';
            }
            const teamState = resolveTeams(game);
            if (teamState && winners.length > 1) {
                return `Winning team: ${formatPlayerList(winners)}`;
            }
            return winners.map((winner) => winner.name).join(', ');
        }

        function buildResultMetaText(winners, game) {
            const teamState = resolveTeams(game);
            if (!teamState || !Array.isArray(winners) || !winners.length) {
                return 'Game ended.';
            }
            const currentTeamPlayers = teamState.currentUserTeamNumber === 1
                ? teamState.team1
                : (teamState.currentUserTeamNumber === 2 ? teamState.team2 : []);
            if (!currentTeamPlayers.length) {
                return 'Game ended.';
            }
            const currentTeamWon = currentTeamPlayers.length > 0
                && winners.every((winner) => currentTeamPlayers.some((player) => isSamePlayer(player, winner)));
            return currentTeamWon ? 'Your team won.' : 'Your team lost.';
        }

        function formatPlayerList(players) {
            if (!Array.isArray(players) || !players.length) {
                return 'Waiting for players';
            }
            return players.map((player) => player?.name || `User ${player?.id ?? ''}`).join(', ');
        }

        /**
         * @param {GameState} game
         */
        function renderCurrentPlayer(game) {
            if (!dom.playerSummary) return;
            const players = buildPlayers(game);
            const current = players.find((player) => isCurrentUser(player));
            if (!current) {
                dom.playerSummary.style.visibility = 'hidden';
                return;
            }
            dom.playerSummary.style.visibility = 'visible';
            if (dom.playerSummaryPoints) {
                dom.playerSummaryPoints.textContent = String(current.points ?? 0);
            }
            if (dom.playerSummaryAvatar) {
                dom.playerSummaryAvatar.textContent = (current.name || 'P').charAt(0).toUpperCase();
            }
            if (dom.playerSummaryName) {
                dom.playerSummaryName.textContent = current.name || 'Player';
            }
        }

        function positionSeats(ring) {
            if (!ring) return;
            const seats = Array.from(ring.children);
            seats.forEach((seat, index) => {
                const count = Number(seat.dataset.seatTotal) || seats.length || 1;
                const seatIndex = Number(seat.dataset.seatIndex);
                const resolvedIndex = Number.isFinite(seatIndex) ? seatIndex : index;
                const slot = getSeatSlot(resolvedIndex, count, seat.dataset.isSelf === '1');
                seat.dataset.seatSide = slot.side;
                seat.style.left = `${slot.x}%`;
                seat.style.top = `${slot.y}%`;
                seat.style.transform = `translate(-50%, -50%) translate3d(${slot.nudgeX}px, ${slot.nudgeY}px, 0)`;
                seat.style.setProperty('--hand-rotate', getSeatHandRotate(slot.side));
            });
        }

        function getSeatSlot(index, count, isSelf) {
            if (isSelf) {
                return {x: 50, y: 118, nudgeX: 0, nudgeY: 0, side: 'bottom'};
            }
            if (count <= 2) {
                return {x: 50, y: -7, nudgeX: 0, nudgeY: 0, side: 'top'};
            }
            if (count === 3) {
                const slots = [
                    {x: 50, y: 118, nudgeX: 0, nudgeY: 0, side: 'bottom'},
                    {x: 50, y: -7, nudgeX: 0, nudgeY: 0, side: 'top'},
                    {x: 108, y: 38, nudgeX: 0, nudgeY: 0, side: 'right'}
                ];
                return slots[index] || slots[1];
            }
            const slots = [
                {x: 50, y: 118, nudgeX: 0, nudgeY: 0, side: 'bottom'},
                {x: -8, y: 38, nudgeX: 0, nudgeY: 0, side: 'left'},
                {x: 50, y: -7, nudgeX: 0, nudgeY: 0, side: 'top'},
                {x: 108, y: 38, nudgeX: 0, nudgeY: 0, side: 'right'}
            ];
            return slots[index] || slots[index % slots.length];
        }

        function setupTrumpZoom(cardWrap) {
            if (!cardWrap || cardWrap.dataset.zoomReady) return;
            cardWrap.dataset.zoomReady = '1';
            let timer = null;
            let overlay = null;

            const clear = () => {
                if (timer) { clearTimeout(timer); timer = null; }
                if (overlay) { overlay.remove(); overlay = null; }
            };

            cardWrap.addEventListener('mouseenter', () => {
                clear();
                timer = setTimeout(() => {
                    const frontImg = cardWrap.querySelector('.card-front');
                    const src = frontImg?.src || '';
                    if (!src) return;
                    overlay = document.createElement('div');
                    overlay.className = 'trump-zoom';
                    const big = document.createElement('img');
                    big.src = src;
                    big.alt = 'Trump card';
                    big.addEventListener('mouseleave', clear);
                    overlay.appendChild(big);
                    overlay.addEventListener('click', clear);
                    document.body.appendChild(overlay);
                    timer = null;
                }, 1000);
            });
            cardWrap.addEventListener('mouseleave', () => {
                if (overlay) return;
                clear();
            });
        }

        function normalizePlayer(player) {
            if (!player) return null;
            if (typeof player === 'object' && player.name) {
                return {name: player.name, id: player.id != null ? String(player.id) : null};
            }
            return parsePlayerKey(player);
        }

        function cardKey(card) {
            if (!card) return '';
            return `${card.cardType || ''}:${card.card || ''}`;
        }

        function animateHandRemoval(key) {
            if (!dom.hand) return;
            const el = dom.hand.querySelector(`[data-card-key="${key}"]`);
            if (el) el.classList.add('played');
        }

        function animateAutoPlayedHandRemoval(card) {
            const key = cardKey(card);
            if (!key || state.pending.has(key) || state.autoPlayedPending.has(key)) return;
            cancelDragForCard(key);
            state.autoPlayedPending.add(key);
            animateHandRemoval(key);
            setTimeout(() => {
                syncHand();
            }, 230);
        }

        function restoreDraggedCard(key) {
            window.UltracardsGameUi?.markCardReturning(dom.hand, key, {
                className: 'returning',
                duration: 220
            });
        }

        function cancelDragForCard(key) {
            const session = state.dragSession;
            if (!session || cardKey(session.card) !== key) return;
            // The card was auto-played out from under an in-progress drag: discard the
            // real dragged element (it is gone from the hand) and reset drag state.
            const dragEl = state.draggingEl || session.handDrag?.el || session.originEl;
            if (dragEl) dragEl.remove();
            state.handEls.delete(key);
            state.draggingEl = null;
            setTableDropReady(false);
            window.UltracardsGameUi?.clearReservedSlot(state.hands.table);
            window.UltracardsGameUi?.layoutHand(state.hands.self);
            state.dragSession = null;
        }

        function collectEnteringCardKeys(previousCards, nextCards) {
            const previousCounts = new Map();
            (previousCards || []).forEach((card) => {
                const key = cardKey(card);
                previousCounts.set(key, (previousCounts.get(key) || 0) + 1);
            });
            const entering = new Set();
            (nextCards || []).forEach((card) => {
                const key = cardKey(card);
                const count = previousCounts.get(key) || 0;
                if (count > 0) {
                    previousCounts.set(key, count - 1);
                } else {
                    entering.add(key);
                }
            });
            return entering;
        }

        function detectAutoPlayedCard(previousCards, nextCards) {
            if (!nextCards || nextCards.length <= (previousCards?.length || 0)) return null;
            const enteringKeys = collectEnteringCardKeys(previousCards || [], nextCards);
            for (const card of nextCards) {
                const key = cardKey(card);
                if (!enteringKeys.has(key)) continue;
                const matchesHand = state.hand.some((handCard) => cardKey(handCard) === key);
                if (matchesHand && !state.pending.has(key)) {
                    return card;
                }
            }
            return null;
        }

        function updateTurnIndicator(game) {
            const turnPlayer = game?.playersTurn || null;
            const turnEndTimeMs = game?.turnEndTime ? Date.parse(game.turnEndTime) : NaN;
            const nextTargetKey = playerSeatKey(turnPlayer);
            const hasCountdown = turnPlayer && Number.isFinite(turnEndTimeMs);
            state.turnIndicatorTargetKey = hasCountdown ? nextTargetKey : null;
            state.turnIndicatorEndsAt = hasCountdown ? turnEndTimeMs : null;

            Array.from(dom.ring?.children || []).forEach((seat) => {
                const isTarget = hasCountdown && seat.dataset.playerKey === nextTargetKey;
                const avatar = seat.querySelector('.seat-avatar');
                const wasTarget = seat.classList.contains('has-turn-indicator');
                seat.classList.toggle('has-turn-indicator', isTarget);
                if (isTarget && !wasTarget) {
                    seat.classList.remove('turn-indicator-exit');
                    void seat.offsetWidth;
                    seat.classList.add('turn-indicator-enter');
                } else if (!isTarget && wasTarget) {
                    seat.classList.remove('turn-indicator-enter');
                    seat.classList.add('turn-indicator-exit');
                }
                if (!isTarget) {
                    seat.classList.remove('is-turn-warning');
                    avatar?.style.setProperty('--turn-progress', '0');
                }
            });

            // Mirror the indicator onto the under-hand player-summary avatar, since the
            // current user's table seat is hidden (.player-seat.is-self { display:none }).
            const selfIsTarget = hasCountdown && isCurrentUser(turnPlayer);
            state.turnIndicatorSelf = selfIsTarget;
            if (dom.playerSummary) {
                const wasSelfTarget = dom.playerSummary.classList.contains('has-turn-indicator');
                dom.playerSummary.classList.toggle('has-turn-indicator', selfIsTarget);
                if (selfIsTarget && !wasSelfTarget) {
                    dom.playerSummary.classList.remove('turn-indicator-exit');
                    void dom.playerSummary.offsetWidth;
                    dom.playerSummary.classList.add('turn-indicator-enter');
                } else if (!selfIsTarget && wasSelfTarget) {
                    dom.playerSummary.classList.remove('turn-indicator-enter');
                    dom.playerSummary.classList.add('turn-indicator-exit');
                }
                if (!selfIsTarget) {
                    dom.playerSummary.classList.remove('is-turn-warning');
                    dom.playerSummaryAvatar?.style.setProperty('--turn-progress', '0');
                }
            }

            if (!hasCountdown) {
                stopTurnIndicatorLoop();
                return;
            }
            if (!state.turnIndicatorFrame) {
                runTurnIndicatorFrame();
            }
        }

        function runTurnIndicatorFrame() {
            state.turnIndicatorFrame = null;
            const targetKey = state.turnIndicatorTargetKey;
            const endsAt = state.turnIndicatorEndsAt;
            if (!targetKey || !Number.isFinite(endsAt)) {
                stopTurnIndicatorLoop();
                return;
            }
            const seat = dom.ring?.querySelector(`[data-player-key="${CSS.escape(targetKey)}"]`);
            if (!seat) {
                state.turnIndicatorFrame = requestAnimationFrame(runTurnIndicatorFrame);
                return;
            }
            const avatar = seat.querySelector('.seat-avatar');
            if (!avatar) {
                state.turnIndicatorFrame = requestAnimationFrame(runTurnIndicatorFrame);
                return;
            }
            const remaining = Math.max(0, endsAt - Date.now());
            const progress = Math.max(0, Math.min(1, remaining / Math.max(state.turnDurationMs, 1)));
            const isWarning = remaining <= 5000;
            avatar.style.setProperty('--turn-progress', progress.toFixed(4));
            seat.classList.toggle('is-turn-warning', isWarning && remaining > 0);
            if (remaining <= 0) seat.classList.add('is-turn-warning');

            // Mirror onto the under-hand player-summary avatar for the current user.
            if (state.turnIndicatorSelf && dom.playerSummaryAvatar) {
                dom.playerSummaryAvatar.style.setProperty('--turn-progress', progress.toFixed(4));
                dom.playerSummary?.classList.toggle('is-turn-warning', isWarning && remaining > 0);
                if (remaining <= 0) dom.playerSummary?.classList.add('is-turn-warning');
            }

            if (remaining > 0) {
                state.turnIndicatorFrame = requestAnimationFrame(runTurnIndicatorFrame);
            }
        }

        function stopTurnIndicatorLoop() {
            if (state.turnIndicatorFrame) {
                cancelAnimationFrame(state.turnIndicatorFrame);
                state.turnIndicatorFrame = null;
            }
        }

        function italianCardUrl(code) {
            return window.UltracardsGameUi?.italianCardUrl(code) || '';
        }

        function italianBackUrl() {
            return window.UltracardsGameUi?.cardBackUrl('ITALIAN') || '/api/cards/italian/back';
        }

        function playerSeatKey(player) {
            if (!player) return '';
            if (player.id != null) return `id:${player.id}`;
            return `name:${player.name || ''}`;
        }

        function addTeamLookupKeys(teamByKey, player, teamNumber) {
            playerLookupKeys(player).forEach((key) => teamByKey.set(key, teamNumber));
        }

        function playerLookupKeys(player) {
            if (!player) return [];
            const keys = [];
            if (player.id != null && String(player.id) !== '') {
                keys.push(`id:${String(player.id)}`);
            }
            if (player.name) {
                const name = String(player.name);
                keys.push(`name:${name}`);
                keys.push(`name:${name.toLowerCase()}`);
            }
            return [...new Set(keys)];
        }

        function syncSeatCards(cardsEl, cardsCount, seatSide) {
            window.UltracardsGameUi?.syncBackCards(cardsEl, cardsCount, {
                cardType: 'ITALIAN',
                className: 'seat-card',
                alt: 'Card back'
            });
        }

        function getSeatHandRotate(seatSide) {
            if (seatSide !== 'bottom') return '180deg';
            return '0deg';
        }

        function renderDeckTower(cardsLeft) {
            window.UltracardsGameUi?.renderDeckTower(dom.deckTower, dom.deckStack, cardsLeft, {
                cardType: 'ITALIAN',
                featuredCard: true,
                exhausting: state.deckExhausting,
                alt: 'Deck'
            });
        }

        function animateDeckDraw(draws) {
            if (!dom.deckTower || !dom.ring || draws <= 0) return;
            const rawTowerRect = dom.deckTower.getBoundingClientRect();
            const towerRect = (rawTowerRect.width && rawTowerRect.height)
                ? rawTowerRect
                : (dom.deckStack?.getBoundingClientRect() || rawTowerRect);
            if (!towerRect.width || !towerRect.height) return;
            const seats = Array.from(dom.ring.children);
            if (!seats.length) return;
            for (let i = 0; i < draws; i++) {
                const seat = seats[i % seats.length];
                if (!seat || seat.dataset.isSelf === '1') continue;
                const delay = i * 80;
                // Source coordinates come from the deck pile; the subfunction computes
                // the destination from the target seat element.
                window.UltracardsGameUi?.animateCardBetweenZones({
                    sourceRect: towerRect,
                    toEl: seat,
                    className: 'deal-card',
                    cardType: 'ITALIAN',
                    alt: 'Card back',
                    fromRot: `${-10 + (i % 3) * 5}deg`,
                    toRot: `${-4 + (i % 5) * 2}deg`,
                    delay,
                    duration: 560
                });
            }
        }

        // Deal newly drawn cards into the hand WITHOUT clones: each real card element
        // flies from the deck to its own fanned slot by animating composable --deal-*
        // CSS vars (which add onto the slot transform), then flips itself face-up via
        // its innate cardApi. One DOM element per card — no duplicate backs, no pile.
        function dealCardsIntoHand(cards, useTrumpForLastDraw) {
            if (!dom.hand || !cards || !cards.length) return;
            const rawTowerRect = dom.deckTower?.getBoundingClientRect();
            const deckRect = (rawTowerRect && rawTowerRect.width && rawTowerRect.height)
                ? rawTowerRect
                : (dom.deckStack?.getBoundingClientRect() || rawTowerRect);
            const gsap = window.gsap;

            const finish = (el, card, isLast) => {
                if (el.dataset.dealFinished === '1') return; // idempotent
                el.dataset.dealFinished = '1';
                el.classList.remove('is-dealing');
                ['--deal-x', '--deal-y', '--deal-rot', '--deal-scale'].forEach((p) => el.style.removeProperty(p));
                state.dealingKeys.delete(cardKey(card));
                el.cardApi?.flip(card);
                if (useTrumpForLastDraw && isLast) {
                    state.deckExhausting = false;
                    renderDeckTower(0);
                    renderTrump(null, 0);
                }
            };

            cards.forEach((card, index) => {
                const key = cardKey(card);
                const el = dom.hand.querySelector(`[data-card-key="${key}"]`);
                if (!el) {
                    state.dealingKeys.delete(key);
                    return;
                }
                el.dataset.dealFinished = '';
                const isLast = index === cards.length - 1;
                const useTrumpSource = useTrumpForLastDraw && isLast && dom.trump && dom.trump.dataset.cardCode;
                const fromRect = useTrumpSource ? dom.trump.getBoundingClientRect() : deckRect;
                if (!gsap || !fromRect || !fromRect.width) {
                    finish(el, card, isLast);
                    return;
                }
                // The slot rect is the resting position (deal vars are still identity).
                const slot = el.getBoundingClientRect();
                const dx = (fromRect.left + fromRect.width / 2) - (slot.left + slot.width / 2);
                const dy = (fromRect.top + fromRect.height / 2) - (slot.top + slot.height / 2);
                gsap.set(el, {
                    '--deal-x': `${dx}px`,
                    '--deal-y': `${dy}px`,
                    '--deal-rot': `${-10 + index * 4}deg`,
                    '--deal-scale': 0.9
                });
                gsap.to(el, {
                    '--deal-x': '0px',
                    '--deal-y': '0px',
                    '--deal-rot': '0deg',
                    '--deal-scale': 1,
                    duration: 0.52,
                    delay: index * 0.08,
                    ease: 'power3.out',
                    onComplete() { finish(el, card, isLast); }
                });
                // Safety: if the tween is ever interrupted/overwritten, force-finish so
                // the card can never be left mid-deal (stuck offset, stranded).
                setTimeout(() => finish(el, card, isLast), 520 + index * 80 + 350);
            });
        }

        function createDealFlipCard(incomingCard) {
            return window.UltracardsGameUi?.createDealFlipCard(incomingCard, 'ITALIAN') || document.createElement('div');
        }

        function parsePlayerKey(key) {
            if (!key) return {name: ''};
            if (typeof key === 'object' && key.name) {
                return {name: key.name, id: key.id != null ? String(key.id) : null};
            }
            const raw = String(key);
            if (raw.startsWith('{') && raw.endsWith('}')) {
                try {
                    const obj = JSON.parse(raw);
                    if (obj && obj.name) {
                        return {name: obj.name, id: obj.id != null ? String(obj.id) : null};
                    }
                } catch (error) {
                }
            }
            const nameMatch = raw.match(/name=([^,)]+)/i);
            const idMatch = raw.match(/id=([^,)]+)/i);
            return {
                name: nameMatch ? nameMatch[1].trim() : raw,
                id: idMatch ? idMatch[1].trim() : null
            };
        }

        function isCurrentUser(player) {
            if (!player) return false;
            if (currentUserId && player.id != null && String(player.id) === currentUserId) {
                return true;
            }
            return !!currentUsername && !!player.name && String(player.name) === currentUsername;
        }

        function isSamePlayer(left, right) {
            if (!left || !right) return false;
            if (left.id != null && right.id != null) {
                return String(left.id) === String(right.id);
            }
            return !!left.name && !!right.name && String(left.name) === String(right.name);
        }

    })();
