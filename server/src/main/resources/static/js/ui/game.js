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
         *   trumpCard?: GameCard|null
         * }} GameState
         */
        const gameEl = document.getElementById('game-container');
        if (!gameEl || !gameEl.dataset.gameId) return;

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
            endRedirectInterval: null
        };
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

        if (dom.dropZone) setupDropZone(dom.dropZone);
        window.addEventListener('resize', () => positionSeats(dom.ring));

        if (initialGame != null) {
            applyGame(initialGame, 'UPDATED', null);
        }
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
                            newCards.push(card);
                        }
                    });
                    state.hand = list.filter((card) => !state.pending.has(cardKey(card)));
                    syncHand();
                    if (newCards.length) {
                        animateIncomingHandCards(newCards, state.finalTrumpDraw);
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
            const prevDeckLeft = state.deckLeft;
            const nextDeckLeft = game.cardsLeftInDeck ?? 0;
            const shouldDelayTableUpdate = !skipTableDelay && previousPlayedCards.length > 0 && nextPlayedCards.length === 0;
            if (shouldDelayTableUpdate) {
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
                const winners = result.gameWinners.map((winner) => winner.name).join(', ');
                state.endState = {
                    title: 'Match Result',
                    winnersText: winners,
                    metaText: 'Game ended.'
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
                dom.trump.removeAttribute('src');
                dom.trump.style.display = 'none';
                dom.deckStack?.classList.remove('has-trump');
                return;
            }
            dom.trump.src = italianCardUrl(card.card);
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
            dom.trick.innerHTML = '';
            if (!cards || !cards.length) return;
            const enteringKeys = collectEnteringCardKeys(previousCards, cards);
            cards.forEach((card, idx) => {
                const img = document.createElement('img');
                img.className = 'trick-card';
                img.style.setProperty('--spin', `${(idx * 4) - 6}deg`);
                img.alt = 'Played card';
                img.src = italianCardUrl(card.card);
                if (enteringKeys.has(cardKey(card))) {
                    img.classList.add('is-entering');
                }
                dom.trick.appendChild(img);
            });
        }

        function animateTrickClear() {
            if (!dom.trick) return;
            const cards = dom.trick.querySelectorAll('.trick-card');
            cards.forEach((card) => {
                card.classList.remove('is-clearing');
                void card.offsetWidth;
                card.classList.add('is-clearing');
            });
        }

        /**
         * @param {GameState} game
         */
        function renderPlayers(game) {
            if (!dom.ring) return;
            const players = buildPlayers(game);
            gameLayout?.classList.toggle('is-two-player', players.length === 2);
            gameLayout?.classList.toggle('is-four-player', players.length === 4);
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

                    const cards = document.createElement('div');
                    cards.className = 'seat-cards';
                    seat.appendChild(cards);

                    const points = document.createElement('div');
                    points.className = 'seat-points';
                    seat.appendChild(points);
                }
                existingSeats.delete(key);

                const isSelf = isCurrentUser(player);
                if (player.id) seat.dataset.playerId = String(player.id);
                seat.classList.toggle('is-turn', isSamePlayer(player, game.playersTurn));
                seat.classList.toggle('is-self', isSelf);
                if (isSelf) {
                    seat.dataset.isSelf = '1';
                } else {
                    delete seat.dataset.isSelf;
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

                const cardsCount = player.cards != null ? player.cards : (player.points ?? 0);
                const cards = seat.querySelector('.seat-cards');
                if (cards) {
                    syncSeatCards(cards, cardsCount, seat.dataset.seatSide || 'center');
                }

                const points = seat.querySelector('.seat-points');
                if (points) {
                    points.textContent = `Points: ${player.points ?? 0}`;
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

            state.handEls.forEach((el, key) => {
                if (!nextKeys.has(key)) {
                    el.remove();
                    state.handEls.delete(key);
                }
            });

            const ordered = [];
            visibleHand.forEach((card) => {
                const key = cardKey(card);
                let el = state.handEls.get(key);
                if (!el) {
                    el = createHandCard(card, isTurn);
                    state.handEls.set(key, el);
                    if (state.justDealtKeys.has(key)) {
                        el.classList.add('is-entering');
                        el.addEventListener('animationend', () => {
                            el.classList.remove('is-entering');
                        }, {once: true});
                        state.justDealtKeys.delete(key);
                    }
                } else {
                    updateHandCardState(el, card, isTurn);
                }
                ordered.push(el);
            });

            applyHandFan(ordered);
            ordered.forEach((el) => dom.hand.appendChild(el));
        }

        function applyHandFan(cards) {
            const total = cards.length;
            if (!total) return;
            const spread = Math.min(6, total <= 2 ? 4 : total <= 4 ? 5 : 6);
            cards.forEach((el, index) => {
                const centeredIndex = index - ((total - 1) / 2);
                el.style.setProperty('--tilt', `${centeredIndex * spread}deg`);
            });
        }

        function createHandCard(card, isTurn) {
            const img = document.createElement('img');
            img.className = 'hand-card';
            img.dataset.cardKey = cardKey(card);
            img.alt = 'Card';
            img.src = italianCardUrl(card.card);
            updateHandCardState(img, card, isTurn);
            return img;
        }

        function updateHandCardState(img, card, isTurn) {
            img.draggable = false;
            img.onpointerdown = null;
            img.ondblclick = null;
            img.classList.toggle('is-disabled', !isTurn || state.playing);
            if (!isTurn || state.playing) {
                return;
            }
            img.onpointerdown = (evt) => {
                evt.preventDefault();
                armDrag(card, img, evt);
            };
            img.ondblclick = () => {
                playCard(card);
            };
        }

        function playCard(card) {
            if (state.playing) return;
            const isTurn = state.game && state.game.playersTurn
                && isCurrentUser(state.game.playersTurn);
            if (state.game && state.game.playersTurn && !isTurn) {
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
                    restoreDraggedCard(cardKey(card));
                    syncHand();
                });
        }

        function setupDropZone(zone) {
            zone.addEventListener('pointerdown', (evt) => evt.preventDefault());
        }

        function armDrag(card, originEl, evt) {
            if (state.dragSession || state.playing) return;
            const isTurn = state.game && state.game.playersTurn
                && isCurrentUser(state.game.playersTurn);
            if (state.game && state.game.playersTurn && !isTurn) {
                return;
            }
            const pointerId = evt.pointerId;
            const rect = originEl.getBoundingClientRect();
            const start = {x: evt.clientX, y: evt.clientY};
            originEl.classList.add('is-pressed');
            state.dragSession = {
                card,
                originEl,
                pointerId,
                rect,
                start,
                dragging: false,
                ghost: null
            };

            const onMove = (moveEvt) => {
                if (!state.dragSession || state.dragSession.pointerId !== moveEvt.pointerId) return;
                const deltaX = moveEvt.clientX - state.dragSession.start.x;
                const deltaY = moveEvt.clientY - state.dragSession.start.y;
                const distance = Math.hypot(deltaX, deltaY);
                if (!state.dragSession.dragging && distance < 10) {
                    return;
                }
                if (!state.dragSession.dragging) {
                    startDrag(state.dragSession);
                }
                moveGhost(state.dragSession.ghost, moveEvt.clientX, moveEvt.clientY);
            };

            const finish = (upEvt, cancelled) => {
                if (!state.dragSession || state.dragSession.pointerId !== upEvt.pointerId) return;
                const session = state.dragSession;
                cleanupDragListeners();
                state.dragSession = null;
                session.originEl.classList.remove('is-pressed');

                if (!session.dragging || cancelled) {
                    if (session.ghost) session.ghost.remove();
                    return;
                }

                dom.dropZone?.classList.remove('ready');
                const targetRect = dom.dropZone ? dom.dropZone.getBoundingClientRect() : null;
                const inside = targetRect
                    && upEvt.clientX >= targetRect.left
                    && upEvt.clientX <= targetRect.right
                    && upEvt.clientY >= targetRect.top
                    && upEvt.clientY <= targetRect.bottom;
                if (inside && dom.dropZone) {
                    const centerX = targetRect.left + targetRect.width / 2;
                    const centerY = targetRect.top + targetRect.height / 2;
                    session.ghost.classList.add('snap');
                    session.ghost.style.transition = 'transform 160ms ease, left 160ms ease, top 160ms ease';
                    session.ghost.style.left = `${centerX}px`;
                    session.ghost.style.top = `${centerY}px`;
                    setTimeout(() => {
                        session.ghost.remove();
                        playCard(session.card);
                    }, 170);
                } else {
                    session.ghost.style.transition = 'transform 210ms cubic-bezier(.22, .9, .3, 1), left 210ms cubic-bezier(.22, .9, .3, 1), top 210ms cubic-bezier(.22, .9, .3, 1), opacity 210ms ease';
                    const returnX = session.rect.left + session.rect.width / 2;
                    const returnY = session.rect.top + session.rect.height / 2;
                    session.ghost.style.left = `${returnX}px`;
                    session.ghost.style.top = `${returnY}px`;
                    setTimeout(() => {
                        session.ghost.remove();
                        session.originEl.classList.remove('is-dragging');
                    }, 210);
                }
            };

            const cleanupDragListeners = () => {
                window.removeEventListener('pointermove', onMove);
                window.removeEventListener('pointerup', onUp);
                window.removeEventListener('pointercancel', onCancel);
            };

            const onUp = (upEvt) => finish(upEvt, false);
            const onCancel = (cancelEvt) => finish(cancelEvt, true);

            window.addEventListener('pointermove', onMove);
            window.addEventListener('pointerup', onUp);
            window.addEventListener('pointercancel', onCancel);
        }

        function startDrag(session) {
            if (state.playing) return;
            const ghost = document.createElement('img');
            ghost.src = session.originEl.src;
            ghost.className = 'drag-ghost';
            ghost.style.width = `${session.rect.width}px`;
            document.body.appendChild(ghost);
            session.ghost = ghost;
            session.dragging = true;
            session.originEl.classList.remove('is-pressed');
            session.originEl.classList.add('is-dragging');
            moveGhost(ghost, session.start.x, session.start.y);
            dom.dropZone?.classList.add('ready');
        }

        function moveGhost(ghost, x, y) {
            ghost.style.left = `${x}px`;
            ghost.style.top = `${y}px`;
        }

        /**
         * @param {GameState} game
         * @returns {UiPlayer[]}
         */
        function buildPlayers(game) {
            const players = new Map();
            const cardsMap = game.playersCardsMap || {};
            Object.keys(cardsMap).forEach((key) => {
                const parsed = parsePlayerKey(key);
                const idKey = parsed.id || parsed.name || key;
                players.set(idKey, {...parsed, cards: cardsMap[key]});
            });
            const pointsMap = game.pointsPerPerson || {};
            Object.keys(pointsMap).forEach((key) => {
                const parsed = parsePlayerKey(key);
                const idKey = parsed.id || parsed.name || key;
                const existing = players.get(idKey) || parsed;
                players.set(idKey, {...existing, points: pointsMap[key]});
            });
            const list = Array.from(players.values());
            list.sort((a, b) => {
                const aNum = Number(a.id);
                const bNum = Number(b.id);
                if (!Number.isNaN(aNum) && !Number.isNaN(bNum)) return aNum - bNum;
                return String(a.name).localeCompare(String(b.name));
            });
            if (currentUserId) {
                const idx = list.findIndex((player) => isCurrentUser(player));
                if (idx > 0) {
                    return list.slice(idx).concat(list.slice(0, idx));
                }
            }
            return list;
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
                dom.playerSummaryPoints.textContent = `Points: ${current.points ?? 0}`;
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
            const base = Math.min(ring.clientWidth, ring.clientHeight) / 2;
            const radius = base - 2;
            seats.forEach((seat, index) => {
                const count = Number(seat.dataset.seatTotal) || seats.length || 1;
                const seatIndex = Number(seat.dataset.seatIndex);
                const resolvedIndex = Number.isFinite(seatIndex) ? seatIndex : index;
                const angle = (Math.PI * 2 * resolvedIndex) / count + Math.PI / 2;
                const x = Math.cos(angle) * radius;
                const y = Math.sin(angle) * radius;
                const selfTopOffset = seat.dataset.isSelf === '1' ? 130 : 0;
                const seatSide = x < -8 ? 'left' : (x > 8 ? 'right' : (y < 0 ? 'top' : 'bottom'));
                seat.dataset.seatSide = seatSide;
                seat.style.transform = `translate(-50%, -50%) translate(${x}px, ${y}px)`;
                seat.style.left = '50%';
                seat.style.top = `calc(50% + ${selfTopOffset}px)`;
                seat.style.setProperty('--hand-rotate', getSeatHandRotate(seatSide));
            });
        }

        function setupTrumpZoom(img) {
            if (!img || img.dataset.zoomReady) return;
            img.dataset.zoomReady = '1';
            let timer = null;
            let overlay = null;

            const clear = () => {
                if (timer) {
                    clearTimeout(timer);
                    timer = null;
                }
                if (overlay) {
                    overlay.remove();
                    overlay = null;
                }
            };

            img.addEventListener('mouseenter', () => {
                clear();
                timer = setTimeout(() => {
                    overlay = document.createElement('div');
                    overlay.className = 'trump-zoom';
                    const big = document.createElement('img');
                    big.src = img.src;
                    big.alt = 'Trump card';
                    big.addEventListener('mouseleave', clear);
                    overlay.appendChild(big);
                    overlay.addEventListener('click', clear);
                    document.body.appendChild(overlay);
                    timer = null;
                }, 1000);
            });
            img.addEventListener('mouseleave', () => {
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
            if (!dom.hand) return;
            const el = dom.hand.querySelector(`[data-card-key="${key}"]`);
            if (!el) return;
            el.classList.remove('is-dragging');
            el.classList.add('returning');
            setTimeout(() => el.classList.remove('returning'), 220);
        }

        function cancelDragForCard(key) {
            const session = state.dragSession;
            if (!session || cardKey(session.card) !== key) return;
            session.originEl.classList.remove('is-pressed', 'is-dragging');
            session.ghost?.remove();
            dom.dropZone?.classList.remove('ready');
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
                seat.classList.toggle('has-turn-indicator', isTarget);
                if (!isTarget) {
                    seat.classList.remove('is-turn-warning');
                    avatar?.style.setProperty('--turn-progress', '0');
                }
            });

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
            avatar.style.setProperty('--turn-progress', progress.toFixed(4));
            seat.classList.toggle('is-turn-warning', remaining <= 5000 && remaining > 0);

            if (remaining > 0) {
                state.turnIndicatorFrame = requestAnimationFrame(runTurnIndicatorFrame);
            } else {
                seat.classList.add('is-turn-warning');
            }
        }

        function stopTurnIndicatorLoop() {
            if (state.turnIndicatorFrame) {
                cancelAnimationFrame(state.turnIndicatorFrame);
                state.turnIndicatorFrame = null;
            }
        }

        function italianCardUrl(code) {
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
        }

        function italianBackUrl() {
            return '/api/cards/italian/back';
        }

        function playerSeatKey(player) {
            if (!player) return '';
            if (player.id != null) return `id:${player.id}`;
            return `name:${player.name || ''}`;
        }

        function syncSeatCards(cardsEl, cardsCount, seatSide) {
            const existingCards = Array.from(cardsEl.children);
            while (existingCards.length > cardsCount) {
                const card = existingCards.pop();
                card?.remove();
            }
            while (existingCards.length < cardsCount) {
                const img = document.createElement('img');
                img.className = 'seat-card';
                img.alt = 'Card back';
                img.src = italianBackUrl();
                cardsEl.appendChild(img);
                existingCards.push(img);
            }
            existingCards.forEach((img, i) => {
                const centeredIndex = i - ((cardsCount - 1) / 2);
                const rotate = centeredIndex * 6.5;
                const lift = Math.abs(centeredIndex) * 1.6;
                img.style.transform = `translateY(${lift}px) rotate(${rotate}deg)`;
            });
        }

        function getSeatHandRotate(seatSide) {
            if (seatSide !== 'bottom') return '180deg';
            return '0deg';
        }

        function renderDeckTower(cardsLeft) {
            if (!dom.deckTower) return;
            dom.deckTower.innerHTML = '';
            const deckTowerCount = Math.max((cardsLeft ?? 0) - 1, 0);
            if (dom.deckStack) {
                dom.deckStack.classList.toggle('is-empty', deckTowerCount <= 0 && !state.deckExhausting);
            }
            if (deckTowerCount <= 0 && !state.deckExhausting) return;
            const count = state.deckExhausting ? 1 : deckTowerCount;
            for (let i = 0; i < count; i++) {
                const img = document.createElement('img');
                img.alt = 'Deck';
                img.src = italianBackUrl();
                const depth = count - i - 1;
                const maxSpread = Math.min(count - 1, 20);
                const ratio = maxSpread > 0 ? Math.min(depth, maxSpread) / maxSpread : 0;
                const xOffset = Math.round(ratio * 9);
                const yOffset = Math.round(-ratio * 16);
                const rotation = ((depth % 5) - 2) * 0.8;
                img.style.setProperty('--deck-offset-x', String(xOffset));
                img.style.setProperty('--deck-offset-y', String(yOffset));
                img.style.setProperty('--deck-rot', String(rotation));
                img.style.zIndex = String(i + 1);
                dom.deckTower.appendChild(img);
            }
        }

        function animateDeckDraw(draws) {
            if (!dom.deckTower || !dom.ring || draws <= 0) return;
            const towerRect = dom.deckTower.getBoundingClientRect();
            if (!towerRect.width || !towerRect.height) return;
            const seats = Array.from(dom.ring.children);
            if (!seats.length) return;
            const fromX = towerRect.left + towerRect.width / 2;
            const fromY = towerRect.top + towerRect.height / 2;
            const targets = seats.map((seat) => ({
                isSelf: seat.dataset.isSelf === '1',
                rect: seat.dataset.isSelf === '1' && dom.hand
                    ? dom.hand.getBoundingClientRect()
                    : seat.getBoundingClientRect()
            }));
            for (let i = 0; i < draws; i++) {
                const target = targets[i % targets.length];
                if (!target) continue;
                if (target.isSelf) continue;
                const toX = target.rect.left + target.rect.width / 2;
                const toY = target.rect.top + target.rect.height / 2;
                const card = document.createElement('img');
                card.className = 'deal-card';
                card.src = italianBackUrl();
                card.style.setProperty('--from-x', `${fromX}px`);
                card.style.setProperty('--from-y', `${fromY}px`);
                card.style.setProperty('--to-x', `${toX}px`);
                card.style.setProperty('--to-y', `${toY}px`);
                card.style.setProperty('--from-rot', `${-10 + (i % 3) * 5}deg`);
                card.style.setProperty('--to-rot', `${-4 + (i % 5) * 2}deg`);
                document.body.appendChild(card);
                const delay = i * 80;
                card.style.animationDelay = `${delay}ms`;
                setTimeout(() => card.remove(), 640 + delay);
            }
        }

        function animateIncomingHandCards(cards, useTrumpForLastDraw) {
            if (!dom.deckTower || !dom.hand || !cards || !cards.length) return;
            const towerRect = dom.deckTower.getBoundingClientRect();
            if (!towerRect.width || !towerRect.height) return;
            cards.forEach((card, index) => {
                const key = cardKey(card);
                const targetEl = dom.hand.querySelector(`[data-card-key="${key}"]`);
                if (!targetEl) return;
                const targetRect = targetEl.getBoundingClientRect();
                const animationCard = createDealFlipCard(card);
                const useTrumpSource = useTrumpForLastDraw && index === cards.length - 1 && dom.trump && dom.trump.getAttribute('src');
                const sourceRect = useTrumpSource
                    ? dom.trump.getBoundingClientRect()
                    : towerRect;
                targetEl.classList.add('is-dealing');
                animationCard.className = 'deal-card to-self';
                animationCard.style.setProperty('--from-x', `${sourceRect.left + sourceRect.width / 2}px`);
                animationCard.style.setProperty('--from-y', `${sourceRect.top + sourceRect.height / 2}px`);
                animationCard.style.setProperty('--to-x', `${targetRect.left + targetRect.width / 2}px`);
                animationCard.style.setProperty('--to-y', `${targetRect.top + targetRect.height / 2}px`);
                animationCard.style.setProperty('--from-rot', `${-12 + index * 3}deg`);
                animationCard.style.setProperty('--to-rot', `${-6 + index * 4}deg`);
                document.body.appendChild(animationCard);
                const delay = index * 90;
                animationCard.style.animationDelay = `${delay}ms`;
                setTimeout(() => {
                    targetEl.classList.remove('is-dealing');
                    animationCard.remove();
                    if (useTrumpForLastDraw && index === cards.length - 1) {
                        state.deckExhausting = false;
                        renderDeckTower(0);
                        renderTrump(null, 0);
                    }
                }, 780 + delay);
            });
        }

        function createDealFlipCard(incomingCard) {
            const card = document.createElement('div');
            const back = document.createElement('div');
            back.className = 'deal-card-face deal-card-back';
            const backImg = document.createElement('img');
            backImg.src = italianBackUrl();
            backImg.alt = 'Card back';
            back.appendChild(backImg);

            const front = document.createElement('div');
            front.className = 'deal-card-face deal-card-front';
            const frontImg = document.createElement('img');
            frontImg.src = incomingCard ? italianCardUrl(incomingCard.card) : italianBackUrl();
            frontImg.alt = 'Card';
            front.appendChild(frontImg);

            card.appendChild(back);
            card.appendChild(front);
            return card;
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
