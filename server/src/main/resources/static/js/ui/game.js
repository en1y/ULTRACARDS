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

        const gameId = gameEl.dataset.gameId;
        const currentUserId = gameEl.dataset.currentUserId ? String(gameEl.dataset.currentUserId) : null;
        const currentUsername = gameEl.dataset.username ? String(gameEl.dataset.username) : '';
        const initialGame = window.__INITIAL_GAME__ ?? null;
        const initialHand = Array.isArray(window.__INITIAL_HAND__) ? window.__INITIAL_HAND__ : [];
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
            selfSeatRow: document.getElementById('self-seat-row'),
            turnOrder: document.getElementById('turn-order'),
            connectionToast: document.getElementById('connection-toast'),
            statusToast: document.getElementById('game-status-toast'),
            statusToastTitle: document.getElementById('game-status-toast-title'),
            statusToastText: document.getElementById('game-status-toast-text'),
            tableTurnOverlay: document.getElementById('table-turn-overlay'),
            tableTurnMessage: document.getElementById('table-turn-message')
        };

        const state = {
            game: null,
            hand: [],
            wsConnected: false,
            playing: false,
            deckLeft: null,
            pending: new Set(),
            autoPlayedPending: new Set(),
            dragHidden: new Set(),
            playFlightSources: new Map(),
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
        let statusToastTimer = null;
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
        if (initialHand.length) {
            state.hand = initialHand;
            syncHand();
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

        function showStatusToast(title, text) {
            if (!dom.statusToast) return;
            if (statusToastTimer) {
                clearTimeout(statusToastTimer);
            }
            if (dom.statusToastTitle) {
                dom.statusToastTitle.textContent = title || 'Game update';
            }
            if (dom.statusToastText) {
                dom.statusToastText.textContent = text || 'Unable to update the game.';
            }
            dom.statusToast.hidden = false;
            dom.statusToast.classList.add('is-visible');
            statusToastTimer = setTimeout(() => {
                dom.statusToast.classList.remove('is-visible');
                statusToastTimer = setTimeout(() => {
                    dom.statusToast.hidden = true;
                    statusToastTimer = null;
                }, 180);
            }, 3200);
        }

        function loadGame() {
            fetch(`/api/games/${gameId}`, {credentials: 'include'})
                .then((res) => res.ok ? res.json() : Promise.reject(res.status))
                .then((game) => {
                    if (game) applyGame(game, 'UPDATED', null);
                })
                .catch(() => {
                    showStatusToast('Game refresh failed', 'Unable to load the latest game state.');
                });
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
                        if (state.finalTrumpDraw) {
                            state.deckExhausting = false;
                            renderDeckTower(0);
                            renderTrump(null, 0);
                        }
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
                    showStatusToast('Hand refresh failed', 'Unable to load your current hand.');
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
                if (prevDeckLeft != null && prevDeckLeft > nextDeckLeft) {
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
                removeAutoPlayedHandCard(autoplayedCard);
                scheduleHandRefresh(4);
            }
            renderTrick(nextPlayedCards, previousPlayedCards);
            renderPlayers(game);
            updateTurnIndicator(game);
            if (prevDeckLeft != null && prevDeckLeft > nextDeckLeft) {
                if (currentUserId) scheduleHandRefresh(4);
            }
            updateTurn(game.playersTurn);
            if (state.pending.size) scheduleHandRefresh(4);
            if (gameEvent === 'RESULTED' && result && Array.isArray(result.gameWinners)) {
                const winners = formatWinnerText(result.gameWinners, game);
                state.endState = {
                    title: 'Match Result',
                    winnersText: winners,
                    metaText: buildResultMetaText(result.gameWinners, game, result)
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
            dom.trick.classList.remove('is-clearing');
            dom.trick.innerHTML = '';
            if (!cards || !cards.length) return;
            cards.forEach((card, idx) => {
                const img = document.createElement('img');
                img.className = 'trick-card';
                img.style.setProperty('--spin', `${(idx * 4) - 6}deg`);
                img.dataset.cardKey = cardKey(card);
                img.dataset.playIndex = String(idx);
                img.alt = 'Played card';
                img.src = italianCardUrl(card.card);
                dom.trick.appendChild(img);
            });
        }

        function renderTrickWithAnimation(cards, previousCards = []) {
            renderTrick(cards, previousCards);
        }

        function collectAddedCards(previousCards, nextCards) {
            const previousCounts = new Map();
            (previousCards || []).forEach((card) => {
                const key = cardKey(card);
                previousCounts.set(key, (previousCounts.get(key) || 0) + 1);
            });
            return (nextCards || []).filter((card) => {
                const key = cardKey(card);
                const count = previousCounts.get(key) || 0;
                if (count > 0) {
                    previousCounts.set(key, count - 1);
                    return false;
                }
                return true;
            });
        }

        function animateTrickClear() {
            if (!dom.trick) return;
            if (!dom.trick.querySelector('.trick-card')) return;
            dom.trick.classList.remove('is-clearing');
            void dom.trick.offsetWidth;
            requestAnimationFrame(() => {
                dom.trick.classList.add('is-clearing');
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
            Array.from(dom.ring.children).concat(Array.from(dom.selfSeatRow?.children || [])).forEach((seat) => {
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
                    cards.hidden = isSelf;
                    syncSeatCards(cards, cardsCount, seat.dataset.seatSide || 'center');
                }

                const points = seat.querySelector('.seat-points');
                if (points) {
                    points.textContent = `Points: ${player.points ?? 0}`;
                }

                const targetParent = isSelf && dom.selfSeatRow ? dom.selfSeatRow : dom.ring;
                if (seat.parentElement !== targetParent) {
                    targetParent.appendChild(seat);
                }
            });

            existingSeats.forEach((seat) => seat.remove());
            positionSeats(dom.ring);
            renderTurnOrder(players, game.playersTurn);
        }

        function renderTurnOrder(players, playersTurn) {
            if (!dom.turnOrder) return;
            dom.turnOrder.innerHTML = '';
            players.forEach((player, index) => {
                const item = document.createElement('div');
                item.className = 'turn-order-player';
                item.classList.toggle('is-active', isSamePlayer(player, playersTurn));
                item.textContent = `${index + 1}. ${player.name || 'Player'}`;
                dom.turnOrder.appendChild(item);
            });
        }

        function updateTurn(playersTurn) {
            const isTurn = !playersTurn || isCurrentUser(playersTurn);
            if (dom.dropZone) {
                if (dom.dropZone.classList.contains('is-result')) return;
                dom.dropZone.textContent = isTurn ? 'Play here' : 'Wait a moment';
            }
            if (dom.tableTurnOverlay) {
                dom.tableTurnOverlay.classList.add('is-visible');
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
                return !state.pending.has(key) && !state.autoPlayedPending.has(key) && !state.dragHidden.has(key);
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
                    state.justDealtKeys.delete(key);
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
                const tilt = centeredIndex * spread;
                el.style.setProperty('--tilt', `${tilt}deg`);
                el.dataset.tilt = `${tilt}deg`;
            });
        }

        function createHandCard(card, isTurn) {
            const img = document.createElement('img');
            img.className = 'hand-card';
            img.dataset.cardKey = cardKey(card);
            img.alt = formatCardLabel(card);
            img.setAttribute('aria-label', `Playable ${formatCardLabel(card)}`);
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
            const localSource = state.handEls.get(cardKey(card));
            if (!state.playFlightSources.has(cardKey(card)) && localSource) {
                storePlayFlightSource(card, localSource);
            }
            state.playing = true;
            state.pending.add(cardKey(card));
            syncHand();
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
                    state.hand = state.hand.filter((entry) => cardKey(entry) !== cardKey(card));
                    syncHand();
                    if (game) {
                        applyGame(game, 'UPDATED', null);
                    }
                    scheduleHandRefresh(6);
                })
                .catch((err) => {
                    state.playing = false;
                    state.pending.delete(cardKey(card));
                    state.dragHidden.delete(cardKey(card));
                    state.playFlightSources.delete(cardKey(card));
                    syncHand();
                    restoreDraggedCard(cardKey(card));
                    showStatusToast('Play failed', 'Unable to play that card.');
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
                last: {x: evt.clientX, y: evt.clientY, time: performance.now()},
                velocity: {x: 0, y: 0},
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
                updateDragVelocity(state.dragSession, moveEvt);
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
                    session.originEl.classList.remove('is-dragging');
                    state.dragHidden.delete(cardKey(session.card));
                    syncHand();
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
                    storePlayFlightSource(session.card, session.ghost);
                    session.ghost.remove();
                    playCard(session.card);
                } else {
                    state.dragHidden.delete(cardKey(session.card));
                    syncHand();
                    session.ghost.remove();
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
            moveGhost(ghost, session.start.x, session.start.y);
            state.dragHidden.add(cardKey(session.card));
            syncHand();
            dom.dropZone?.classList.add('ready');
        }

        function moveGhost(ghost, x, y) {
            if (!ghost) return;
            ghost.style.left = `${x}px`;
            ghost.style.top = `${y}px`;
        }

        function updateDragVelocity(session, evt) {
            const now = performance.now();
            const elapsed = Math.max(now - session.last.time, 1);
            session.velocity = {
                x: (evt.clientX - session.last.x) / elapsed,
                y: (evt.clientY - session.last.y) / elapsed
            };
            session.last = {x: evt.clientX, y: evt.clientY, time: now};
        }

        function animateGhostTo(ghost, x, y, options = {}) {
            if (!ghost) return Promise.resolve();
            moveGhost(ghost, x, y);
            if (options.width) ghost.style.width = `${options.width}px`;
            ghost.style.opacity = String(options.opacity ?? 1);
            return Promise.resolve();
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

        function buildResultMetaText(winners, game, result) {
            const pointsText = Number.isFinite(Number(result?.winnerPointsNum))
                ? `Winner points: ${Number(result.winnerPointsNum)}.`
                : '';
            const teamState = resolveTeams(game);
            if (!teamState || !Array.isArray(winners) || !winners.length) {
                return pointsText || 'Game ended.';
            }
            const currentTeamPlayers = teamState.currentUserTeamNumber === 1
                ? teamState.team1
                : (teamState.currentUserTeamNumber === 2 ? teamState.team2 : []);
            if (!currentTeamPlayers.length) {
                return pointsText || 'Game ended.';
            }
            const currentTeamWon = currentTeamPlayers.length > 0
                && winners.every((winner) => currentTeamPlayers.some((player) => isSamePlayer(player, winner)));
            const outcome = currentTeamWon ? 'Your team won.' : 'Your team lost.';
            return pointsText ? `${outcome} ${pointsText}` : outcome;
        }

        function formatPlayerList(players) {
            if (!Array.isArray(players) || !players.length) {
                return 'Waiting for players';
            }
            return players.map((player) => player?.name || `User ${player?.id ?? ''}`).join(', ');
        }

        function getTeamDisplayName(teamNumber) {
            return teamNumber === 1 ? 'Team 1' : (teamNumber === 2 ? 'Team 2' : `Team ${teamNumber}`);
        }

        function positionSeats(ring) {
            if (!ring) return;
            const seats = Array.from(ring.children);
            seats.forEach((seat, index) => {
                const count = Number(seat.dataset.seatTotal) || seats.length || 1;
                const seatIndex = Number(seat.dataset.seatIndex);
                const resolvedIndex = Number.isFinite(seatIndex) ? seatIndex : index;
                const seatSide = resolvedIndex === 0 && count > 1 ? 'left' : 'top';
                seat.dataset.seatSide = seatSide;
                seat.style.transform = '';
                seat.style.left = '';
                seat.style.top = '';
                seat.style.setProperty('--hand-rotate', '0deg');
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
        }

        function removeAutoPlayedHandCard(card) {
            const key = cardKey(card);
            if (!key || state.pending.has(key) || state.autoPlayedPending.has(key)) return;
            cancelDragForCard(key);
            state.autoPlayedPending.add(key);
            syncHand();
        }

        function restoreDraggedCard(key) {
            if (!dom.hand) return;
            const el = dom.hand.querySelector(`[data-card-key="${key}"]`);
            if (!el) return;
            el.classList.remove('is-dragging');
        }

        function storePlayFlightSource(card, sourceEl) {
            const key = cardKey(card);
            if (!key || !sourceEl) return;
            const rect = sourceEl.getBoundingClientRect();
            if (!rect.width || !rect.height) return;
            state.playFlightSources.set(key, {
                rect: {
                    left: rect.left,
                    top: rect.top,
                    width: rect.width,
                    height: rect.height
                },
                src: sourceEl.currentSrc || sourceEl.src || italianCardUrl(card.card)
            });
        }

        function resolvePlayedCardSource(card) {
            const key = cardKey(card);
            const stored = state.playFlightSources.get(key);
            if (stored) {
                state.playFlightSources.delete(key);
                return stored;
            }
            const handSource = dom.hand?.querySelector(`[data-card-key="${CSS.escape(key)}"]`);
            if (handSource) {
                return {
                    rect: handSource.getBoundingClientRect(),
                    src: handSource.currentSrc || handSource.src || italianCardUrl(card.card)
                };
            }
            const activeSeat = getSeatElements().find((seat) => seat.classList.contains('is-turn'));
            const seatSource = activeSeat?.querySelector('.seat-card') || activeSeat;
            if (!seatSource) {
                return null;
            }
            return {
                rect: seatSource.getBoundingClientRect(),
                src: italianBackUrl()
            };
        }

        function animatePlayedCardFlight(card, source, target) {
            target?.classList.remove('is-flight-target');
        }

        function cancelDragForCard(key) {
            const session = state.dragSession;
            if (!session || cardKey(session.card) !== key) return;
            session.originEl.classList.remove('is-pressed', 'is-dragging');
            session.ghost?.remove();
            dom.dropZone?.classList.remove('ready');
            state.dragHidden.delete(key);
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

            getSeatElements().forEach((seat) => {
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
            const seat = getSeatElements().find((candidate) => candidate.dataset.playerKey === targetKey);
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

        function getSeatElements() {
            return Array.from(dom.ring?.children || []).concat(Array.from(dom.selfSeatRow?.children || []));
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

        function formatCardLabel(card) {
            const code = card?.card ? String(card.card).toUpperCase() : '';
            const suitLetter = code.charAt(0);
            const valueCode = code.slice(1);
            const suitMap = {C: 'Coppe', D: 'Denari', S: 'Spade', B: 'Bastoni'};
            const valueMap = {
                '1': 'Ace', '2': 'Two', '3': 'Three', '4': 'Four', '5': 'Five', '6': 'Six',
                '7': 'Seven', '11': 'Jack', '12': 'Knight', '13': 'King'
            };
            const suit = suitMap[suitLetter];
            const value = valueMap[valueCode];
            return suit && value ? `${value} of ${suit}` : 'card';
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
        }

        function animateIncomingHandCards(cards, useTrumpForLastDraw) {
            if (useTrumpForLastDraw) {
                state.deckExhausting = false;
                renderDeckTower(0);
                renderTrump(null, 0);
            }
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
