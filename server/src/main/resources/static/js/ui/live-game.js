(function () {
    // Shared live-game controller. Game-specific surfaces are selected through
    // data-game-type and the runtime feature flags below.
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

        const gameRuntime = window.UltracardsGameRuntime;
        const gameAdapter = gameRuntime?.get(gameEl.dataset.gameType);
        if (!gameAdapter) return;

        function getTeamDisplayName(n) {
            return t('gameHistory.team', n);
        }

        function displayPoints(points) {
            return gameAdapter.displayPoints?.(points) ?? points ?? 0;
        }

        const gameId = gameEl.dataset.gameId;
        const currentUserId = gameEl.dataset.currentUserId ? String(gameEl.dataset.currentUserId) : null;
        const currentUsername = gameEl.dataset.username ? String(gameEl.dataset.username) : '';
        const initialGame = window.__INITIAL_GAME__ ?? null;
        const initialChat = window.__INITIAL_GAME_CHAT__ ?? null;
        const wsUrl = `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws`;
        const gameLayout = document.querySelector('.game-layout');

        // Fullscreen mobile table (the default phone look; 'classic' keeps the
        // old oval felt). theme-preload.js sets html[data-game-ui] before paint.
        const mobileQuery = window.matchMedia('(max-width: 900px)');
        const useNativeCardAnimations = window.CSS?.supports?.('-moz-appearance', 'none') === true;

        function isFullscreenMobile() {
            return mobileQuery.matches && document.documentElement.getAttribute('data-game-ui') !== 'classic';
        }

        // The fullscreen hand is a wide, strongly-arced fan (edge-to-edge like a
        // real held hand); classic keeps the flat desktop-ish spread.
        function handLayoutParams() {
            const layout = isFullscreenMobile()
                ? {type: 'fan', slotTotal: 3, spacingScale: 0.62, maxTilt: 13, yArc: 14, baseOffsetY: 0}
                : {type: 'fan', slotTotal: 3, spacingScale: 0.4, maxTilt: 6, yArc: 5, baseOffsetY: 2};
            return gameAdapter?.layout ? gameAdapter.layout(layout, isFullscreenMobile()) : layout;
        }

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
            playerSummaryName: document.getElementById('player-summary-name'),
            sortSuit: document.getElementById('hand-sort-suit'),
            sortRank: document.getElementById('hand-sort-rank')
        };

        const state = {
            game: null,
            hand: [],
            wsConnected: false,
            hasConnected: false,
            wsClient: null,
            wsReconnectTimer: null,
            playing: false,
            roundClearing: false,
            deckLeft: null,
            pending: new Set(),
            pendingAt: new Map(),
            autoPlayedPending: new Set(),
            handEls: new Map(),
            trickEls: new Map(),
            justDealtKeys: new Set(),
            finalTrumpDraw: false,
            deckExhausting: false,
            dragSession: null,
            turnIndicatorTimer: null,
            turnIndicatorTargetKey: null,
            turnIndicatorEndsAt: null,
            turnDurationMs: Number(initialGame?.turnDurationSeconds) > 0 ? Number(initialGame.turnDurationSeconds) * 1000 : 15000,
            delayedTableUpdateTimer: null,
            delayedTablePayload: null,
            delayedTablePending: false,
            endState: null,
            endRedirectTimer: null,
            endRedirectInterval: null,
            pendingRound: null,
            dealingKeys: new Set(),
            draggingEl: null,
            handRenderSignature: null,
            justPlayed: null,
            lastActingPlayer: null,
            trickPlayOrder: [],      // play order (leader-first) captured mid-trick, before rotation
            hands: {
                self: null,
                table: null
            },
            teammateCards: null,
            teammateAutoCloseTimer: null,
            opponentDrawReveal: null,
            opponentDrawRevealTimer: null,
            previousRound: null,
            previousRoundReplayActive: false,
            previousRoundReplayView: null,
            previousRoundAnimating: false
        };
        state.handSort = 'suit';
        dom.sortSuit?.classList.add('is-active');
        dom.sortSuit?.addEventListener('click', () => setHandSort('suit'));
        dom.sortRank?.addEventListener('click', () => setHandSort('rank'));

        function setRoundClearing(active) {
            state.roundClearing = !!active;
            gameLayout?.classList.toggle('is-round-clearing', state.roundClearing);
            refreshPrevRoundControls();
        }
        const PreviousRoundStore = {
            save(id, data) {
                try { localStorage.setItem('uc-prev-round-' + id, JSON.stringify(data)); } catch (e) {}
            },
            load(id) {
                try { return JSON.parse(localStorage.getItem('uc-prev-round-' + id)); } catch (e) { return null; }
            }
        };
        // Teammate hand only needs to survive this one game, so it's session-scoped
        // (not localStorage like the previous-round history) and erased when the game ends.
        const TeammateHandStore = {
            keyFor(id) { return 'uc-teammate-hand-' + id; },
            save(id, cards) {
                try { sessionStorage.setItem(this.keyFor(id), JSON.stringify(cards)); } catch (e) {}
            },
            load(id) {
                try { return JSON.parse(sessionStorage.getItem(this.keyFor(id))); } catch (e) { return null; }
            },
            clear(id) {
                try { sessionStorage.removeItem(this.keyFor(id)); } catch (e) {}
            },
            // Drop any leftover entries from other games in this tab's session.
            clearStale(currentId) {
                try {
                    const keep = this.keyFor(currentId);
                    Array.from({length: sessionStorage.length}, (_, i) => sessionStorage.key(i))
                        .filter((k) => k && k.startsWith('uc-teammate-hand-') && k !== keep)
                        .forEach((k) => sessionStorage.removeItem(k));
                } catch (e) {}
            }
        };

        const prevRoundBack = document.getElementById('prev-round-back');
        const prevRoundForward = document.getElementById('prev-round-forward');

        const teammateHandToggle = document.getElementById('teammate-hand-toggle');
        const teammateHandPanel = document.getElementById('teammate-hand-panel');
        const teammateHandCardsEl = document.getElementById('teammate-hand-cards');

        if (teammateHandToggle && teammateHandPanel) {
            // Predictable toggle: any tap cancels the auto-close preview and flips the
            // panel open/closed, so the button always visibly responds.
            teammateHandToggle.addEventListener('click', () => {
                if (state.teammateAutoCloseTimer) {
                    clearTimeout(state.teammateAutoCloseTimer);
                    state.teammateAutoCloseTimer = null;
                }
                if (teammateHandPanel.classList.contains('is-open')) {
                    closeTeammateHandPanel();
                } else {
                    openTeammateHandPanel(false);
                }
            });
        }

        // The teammate-hand button only appears once the server has revealed it.
        function refreshTeammateHandButton() {
            const hasData = Array.isArray(state.teammateCards) && state.teammateCards.length > 0;
            teammateHandToggle?.classList.toggle('has-data', hasData);
        }

        function clearTeammateHand() {
            state.teammateCards = null;
            TeammateHandStore.clear(gameId);
            refreshTeammateHandButton();
            closeTeammateHandPanel();
        }

        function closeTeammateHandPanel() {
            if (state.teammateAutoCloseTimer) {
                clearTimeout(state.teammateAutoCloseTimer);
                state.teammateAutoCloseTimer = null;
            }
            teammateHandPanel?.classList.remove('is-open');
            teammateHandToggle?.setAttribute('aria-expanded', 'false');
        }

        // autoClose: true when triggered by the WS reveal itself (self-closes after a
        // couple seconds unless the player clicks the button, which pins it open).
        function openTeammateHandPanel(autoClose) {
            if (!teammateHandPanel || !teammateHandToggle) return;
            teammateHandPanel.classList.add('is-open');
            teammateHandToggle.setAttribute('aria-expanded', 'true');
            renderTeammateHandPanel();
            if (state.teammateAutoCloseTimer) {
                clearTimeout(state.teammateAutoCloseTimer);
                state.teammateAutoCloseTimer = null;
            }
            if (autoClose) {
                state.teammateAutoCloseTimer = setTimeout(() => {
                    state.teammateAutoCloseTimer = null;
                    closeTeammateHandPanel();
                }, 2500);
            }
        }

        function renderTeammateHandPanel() {
            const el = teammateHandCardsEl;
            if (!el) return;
            const cards = Array.isArray(state.teammateCards) ? state.teammateCards : [];
            el.innerHTML = '';
            if (!cards.length) {
                el.innerHTML = '<span class="teammate-hand-empty">No cards to show</span>';
                return;
            }
            cards.forEach((card, i) => {
                const wrap = window.UltracardsGameUi?.renderCardImage({
                    card,
                    className: 'teammate-hand-card-img',
                    alt: t('briskula.teammateCardN.alt', i + 1)
                });
                if (wrap) {
                    el.appendChild(wrap);
                } else {
                    const img = document.createElement('img');
                    window.UltracardsGameUi?.applyCardImage(img, window.UltracardsGameUi?.cardUrl(card) || '');
                    img.alt = t('briskula.teammateCardN.alt', i + 1);
                    el.appendChild(img);
                }
            });
        }

        function previousRoundData() {
            const data = state.previousRound || PreviousRoundStore.load(gameId);
            if (data && Array.isArray(data.cards) && data.cards.length) {
                state.previousRound = data;
                return data;
            }
            return null;
        }

        function refreshPrevRoundControls() {
            if (!prevRoundBack || !prevRoundForward) return;
            const data = previousRoundData();
            const disabled = !data || state.roundClearing || state.previousRoundAnimating;
            const view = state.previousRoundReplayView;
            prevRoundBack.hidden = false;
            prevRoundForward.hidden = false;
            prevRoundBack.disabled = disabled || (view?.phase === 'previous' && view.previousCount <= 1);
            prevRoundForward.disabled = disabled || !view
                || (view.phase === 'current' && view.currentCount >= view.currentCards.length);
        }

        function resetPreviousRoundReplay() {
            state.previousRoundReplayActive = false;
            state.previousRoundReplayView = null;
            state.previousRoundAnimating = false;
            syncHand();
            refreshPrevRoundControls();
        }

        function stopPreviousRoundReplay() {
            if (!state.previousRoundReplayActive) return;
            const renderedCards = state.previousRoundReplayView?.renderedCards || [];
            state.previousRoundReplayActive = false;
            state.previousRoundReplayView = null;
            renderTrick(state.game?.playedCards || [], renderedCards);
            clearPreviousRoundLabels();
            syncHand();
        }

        function renderPreviousRoundReplay(data) {
            const view = state.previousRoundReplayView;
            if (!view) return;
            const cards = view.phase === 'current'
                ? view.currentCards.slice(0, view.currentCount)
                : data.cards.slice(0, view.previousCount);
            renderTrick(cards, view.renderedCards);
            view.renderedCards = cards;
            if (view.phase === 'previous') {
                renderPreviousRoundLabels(data, view.previousCount);
                dom.trick?.classList.add('is-previous-round-replay');
            } else {
                clearPreviousRoundLabels();
            }
        }

        function replayPreviousRound(direction) {
            const data = previousRoundData();
            if (!data || state.previousRoundAnimating) return;
            if (!state.previousRoundReplayActive) {
                const currentCards = (state.game?.playedCards || []).slice();
                state.previousRoundReplayActive = true;
                state.previousRoundReplayView = {
                    phase: 'current',
                    currentCards,
                    currentCount: currentCards.length,
                    previousCount: data.cards.length,
                    renderedCards: currentCards
                };
            }

            const view = state.previousRoundReplayView;
            if (direction === 'back') {
                if (view.phase === 'current' && view.currentCount > 1) {
                    view.currentCount--;
                } else if (view.phase === 'current') {
                    view.phase = 'previous';
                    view.previousCount = data.cards.length;
                } else if (view.previousCount > 1) {
                    view.previousCount--;
                } else {
                    return;
                }
            } else if (view.phase === 'previous' && view.previousCount < data.cards.length) {
                view.previousCount++;
            } else if (view.phase === 'previous') {
                view.phase = 'current';
                view.currentCount = view.currentCards.length ? 1 : 0;
            } else if (view.currentCount < view.currentCards.length) {
                view.currentCount++;
            } else {
                return;
            }

            const returnedToLiveTrick = direction === 'forward'
                && view.phase === 'current'
                && view.currentCount >= view.currentCards.length;
            if (returnedToLiveTrick) {
                stopPreviousRoundReplay();
                refreshPrevRoundControls();
                return;
            }

            state.previousRoundAnimating = true;
            renderPreviousRoundReplay(data);
            syncHand();
            refreshPrevRoundControls();
            window.setTimeout(() => {
                state.previousRoundAnimating = false;
                refreshPrevRoundControls();
            }, 420);
        }

        prevRoundBack?.addEventListener('click', () => replayPreviousRound('back'));
        prevRoundForward?.addEventListener('click', () => replayPreviousRound('forward'));

        function clearPreviousRoundLabels() {
            dom.trick?.classList.remove('is-previous-round-replay');
            dom.trick?.querySelectorAll('.replay-card-player').forEach((label) => label.remove());
            state.trickEls.forEach((card) => card.classList.remove('is-previous-round-replay'));
        }

        function renderPreviousRoundLabels(data, playCount) {
            clearPreviousRoundLabels();
            const players = Array.isArray(data.players) ? data.players : [];
            data.cards.slice(0, playCount).forEach((card, index) => {
                const trickCard = state.trickEls.get(`${cardKey(card)}:${index}`);
                if (!trickCard || !players.length || !trickCard.appendChild) return;
                const label = document.createElement('span');
                label.className = 'replay-card-player';
                label.textContent = players[index % players.length]?.name || `Player ${index + 1}`;
                trickCard.classList.add('is-previous-round-replay');
                trickCard.appendChild(label);
            });
        }

        function refreshPrevRoundButton() {
            resetPreviousRoundReplay();
        }

        function refreshPreviousRoundControlsAfterGameUpdate() {
            const view = state.previousRoundReplayView;
            if (view) {
                view.currentCards = (state.game?.playedCards || []).slice();
                if (view.phase === 'current') {
                    view.currentCount = Math.min(view.currentCount + 1, view.currentCards.length);
                }
                const data = previousRoundData();
                if (data) renderPreviousRoundReplay(data);
            }
            refreshPrevRoundControls();
        }

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
            emptyText: t('chat.gameEmpty')
        });

        state.hands.self = window.UltracardsGameUi?.registerHand(dom.hand, handLayoutParams());
        state.hands.table = window.UltracardsGameUi?.registerHand(dom.trick, {
            type: 'center',
            spacingScale: 0.45,
            maxTilt: 4,
            yArc: 3
        });
        // Raise the card nearest the cursor so the user always grabs the card they
        // point at (cards overlap, so a card's face is covered by the next one).
        window.UltracardsGameUi?.enableHandHoverRaise(state.hands.self, {
            isActive: () => {
                const isTurn = state.game?.playersTurn && isCurrentUser(state.game.playersTurn);
                return !state.playing && !state.roundClearing && !state.previousRoundReplayActive && !state.draggingEl && !!isTurn;
            },
            isCardActive: (cardEl) => isPlayableCard(cardFromEl(cardEl))
        });
        // One container-level drag that always picks the RAISED (enlarged) card — never
        // a card behind it, never a copy.
        window.UltracardsGameUi?.enableHandCardDrag(state.hands.self, {
            originZone: state.hands.self,
            className: 'drag-ghost',
            isActive: () => {
                const isTurn = state.game && state.game.playersTurn && isCurrentUser(state.game.playersTurn);
                return !state.playing && !state.roundClearing && !state.previousRoundReplayActive && !!isTurn;
            },
            isCardActive: (cardEl) => isPlayableCard(cardFromEl(cardEl)),
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

        // Seats + hand fan depend on the viewport size.
        function refreshLayoutMode() {
            positionSeats(dom.ring);
            if (state.hands.self) window.UltracardsGameUi?.layoutZone(state.hands.self, undefined, handLayoutParams());
            if (state.game) renderPlayers(state.game);
        }

        window.addEventListener('resize', refreshLayoutMode);

        if (initialGame != null) {
            applyGame(initialGame, 'UPDATED', null);
        }
        refreshPrevRoundButton();
        TeammateHandStore.clearStale(gameId);
        state.teammateCards = TeammateHandStore.load(gameId);
        // The server reveals teammate hands only once the deck is exhausted, so a
        // cached hand while the deck still has cards is stale — erase it.
        if (state.teammateCards && Number(state.deckLeft) > 0) {
            state.teammateCards = null;
            TeammateHandStore.clear(gameId);
        }
        refreshTeammateHandButton();
        // The initial applyGame ran before the cached reveal was loaded — re-render
        // the seats so the teammate's face-up cards show right away.
        if (state.game && Array.isArray(state.teammateCards) && state.teammateCards.length) {
            renderPlayers(state.game);
        }
        applyHand(window.__INITIAL_HAND__ || []);
        connectWs();

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
            // Disable Stomp's built-in single retry — we manage reconnection ourselves
            // (every ~1s) so a dropped connection is always recovered.
            client.reconnect_delay = 0;
            client.debug = null;
            state.wsClient = client;
            client.connect({}, () => {
                state.wsConnected = true;
                state.hasConnected = true;
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
                client.subscribe('/user/queue/game/cards', (msg) => {
                    try {
                        applyHand(JSON.parse(msg.body));
                    } catch (err) {
                        console.error('Hand event error', err);
                    }
                });
                client.subscribe('/user/queue/game/errors', (msg) => {
                    try {
                        const error = JSON.parse(msg.body);
                        window.alert(error?.message || 'Invalid move');
                    } catch (err) {
                        console.error('Game error event error', err);
                    }
                    window.location.reload();
                });
                client.subscribe('/user/queue/game/teammate-cards', (msg) => {
                    try {
                        state.teammateCards = JSON.parse(msg.body) || [];
                        TeammateHandStore.save(gameId, state.teammateCards);
                        refreshTeammateHandButton();
                        // Fullscreen mobile mirrors the reveal onto the teammate's
                        // seat as face-up cards (renderPlayers picks it up).
                        if (state.game) renderPlayers(state.game);
                        openTeammateHandPanel(true);
                    } catch (err) {
                        console.error('Teammate hand event error', err);
                    }
                });
                client.subscribe('/user/queue/game/opponent-drawn-cards', (msg) => {
                    try {
                        const drawn = JSON.parse(msg.body) || [];
                        if (!Array.isArray(drawn) || !drawn.length || !state.game) return;
                        if (buildPlayers(state.game).length !== 2) return;
                        if (state.opponentDrawRevealTimer) {
                            clearTimeout(state.opponentDrawRevealTimer);
                        }
                        const token = (state.opponentDrawReveal?.token || 0) + 1;
                        // The private draw event is published before the public game
                        // snapshot. Reserve the new slots now so syncBackCards can
                        // create them and fly their backs from the deck immediately.
                        const opponent = buildPlayers(state.game).find((player) => !isCurrentUser(player));
                        const opponentSeat = opponent?.id != null
                            ? dom.ring?.querySelector(`[data-player-id="${CSS.escape(String(opponent.id))}"]`)
                            : null;
                        const currentCount = Number(opponent?.cards) || 0;
                        const renderedCount = opponentSeat?.querySelector('.seat-cards')?.children.length || 0;
                        const targetCount = Math.max(currentCount, renderedCount);
                        state.opponentDrawReveal = {cards: drawn, token, targetCount};
                        renderPlayers(state.game);
                        state.opponentDrawRevealTimer = setTimeout(() => {
                            if (state.opponentDrawReveal?.token !== token) return;
                            hideOpponentDrawnCards().finally(() => {
                                if (state.opponentDrawReveal?.token !== token) return;
                                state.opponentDrawReveal = null;
                                state.opponentDrawRevealTimer = null;
                                if (state.game) renderPlayers(state.game);
                            });
                        }, 3600);
                    } catch (err) {
                        console.error('Opponent drawn-card event error', err);
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
                const hadConnection = state.hasConnected;
                state.wsConnected = false;
                if (hadConnection) {
                    setConnectionStatus(false, t('game.connectionLost'));
                }
                scheduleWsReconnect();
            });
        }

        function scheduleWsReconnect() {
            if (state.wsReconnectTimer) return;   // already scheduled — single timer
            state.wsReconnectTimer = setTimeout(() => {
                state.wsReconnectTimer = null;
                connectWs();
            }, 1000);
        }

        function setConnectionStatus(isConnected, text) {
            if (!dom.connectionToast) return;
            dom.connectionToast.classList.toggle('is-visible', !isConnected);
            if (!isConnected && text) {
                dom.connectionToast.textContent = text;
            }
        }

        function applyHand(cards) {
            if (!dom.hand) return;
            const list = Array.isArray(cards) ? cards : [];
            const prevKeys = new Set(state.hand.map(cardKey));
            const serverKeys = new Set(list.map(cardKey));
            const newCards = [];
            const returnedCards = [];
            const returningKeys = new Set();
            const turnMovedAway = !!state.game?.playersTurn && !isCurrentUser(state.game.playersTurn);
            state.pending.forEach((key) => {
                if (!serverKeys.has(key)) {
                    // Server no longer lists the card: the play was accepted.
                    state.pending.delete(key);
                    state.pendingAt.delete(key);
                } else if (turnMovedAway || Date.now() - (state.pendingAt.get(key) || 0) > 2500) {
                    // Server STILL lists the card well past the play round-trip:
                    // the play never landed (usually because the turn expired). The
                    // incoming update wins over the local cache — return the real
                    // card to the hand instead of treating it as a new deal.
                    state.pending.delete(key);
                    state.pendingAt.delete(key);
                    returningKeys.add(key);
                    const handoff = state.justPlayed?.key === key ? state.justPlayed : null;
                    const fromRect = handoff?.el?.isConnected ? handoff.el.getBoundingClientRect() : null;
                    handoff?.el?.remove();
                    if (state.justPlayed === handoff) state.justPlayed = null;
                    returnedCards.push({key, fromRect});
                }
            });
            if (returnedCards.length) {
                state.playing = false;
                state.dragSession = null;
                state.draggingEl = null;
                setTableDropReady(false);
                window.UltracardsGameUi?.clearReservedSlot(state.hands.table);
            }
            state.autoPlayedPending.forEach((key) => {
                if (!serverKeys.has(key)) {
                    state.autoPlayedPending.delete(key);
                }
            });
            list.forEach((card) => {
                const key = cardKey(card);
                if (!prevKeys.has(key)) {
                    if (!returningKeys.has(key)) {
                        state.justDealtKeys.add(key);
                        // Mark as dealing BEFORE syncHand so the card is created
                        // face-down and excluded from the generic fade-in.
                        state.dealingKeys.add(key);
                        newCards.push(card);
                    }
                }
            });
            state.hand = preserveHandOrder(list.filter((card) => !state.pending.has(cardKey(card))));
            syncHand();
            returnedCards.forEach(({key, fromRect}) => {
                const el = state.handEls.get(key);
                if (!el || !fromRect?.width) return;
                window.UltracardsGameUi?.flyIntoSlot(el, fromRect, {
                    faceDown: false,
                    spin: 0,
                    fromScale: 1.04,
                    duration: 220
                });
            });
            if (newCards.length) {
                dealCardsIntoHand(newCards, state.finalTrumpDraw);
                state.finalTrumpDraw = false;
            }
        }

        /**
         * @param {GameState} game
         * @param {string|null} gameEvent
         * @param {GameResult|null} result
         */
        function applyGame(game, gameEvent, result, skipTableDelay = false) {
            clearPreviousRoundLabels();
            game.playersTurn = normalizePlayer(game.playersTurn);
            if (Number(game.turnDurationSeconds) > 0) {
                state.turnDurationMs = Number(game.turnDurationSeconds) * 1000;
            }
            const previousPlayedCards = Array.isArray(state.game?.playedCards) ? state.game.playedCards : [];
            const nextPlayedCards = Array.isArray(game.playedCards) ? game.playedCards : [];
            // A revealed teammate card showing up on the table was just played by
            // the teammate (deck cards are unique) — drop it from the reveal so the
            // mirrored hand and panel stay accurate for the remaining tricks.
            if (Array.isArray(state.teammateCards) && state.teammateCards.length && nextPlayedCards.length) {
                const playedKeys = new Set(nextPlayedCards.map(cardKey));
                const remaining = state.teammateCards.filter((card) => !playedKeys.has(cardKey(card)));
                if (remaining.length !== state.teammateCards.length) {
                    state.teammateCards = remaining;
                    TeammateHandStore.save(gameId, remaining);
                    refreshTeammateHandButton();
                    if (teammateHandPanel?.classList.contains('is-open')) renderTeammateHandPanel();
                }
            }
            // Capture the in-trick player order BEFORE state.game is reassigned. Once a
            // trick completes the backend rotates playersOrder (winner first), so the
            // incoming `game` no longer matches the played-card order — this does.
            const inTrickPlayers = Array.isArray(state.game?.playersOrder)
                ? state.game.playersOrder.map((p) => parsePlayerKey(p))
                : [];
            // While a trick is still in progress the backend has NOT yet rotated
            // playersOrder, so playersOrder[0] is the leader and the whole list is the
            // play order (leader-first). The rotation only happens once the trick
            // completes, so capture the order now and reuse it when saving the round.
            const playersCount = buildPlayers(game).length || inTrickPlayers.length || 2;
            if (nextPlayedCards.length >= 1 && nextPlayedCards.length < playersCount) {
                state.trickPlayOrder = (Array.isArray(game.playersOrder) ? game.playersOrder : [])
                    .map((p) => parsePlayerKey(p))
                    .map((p) => ({id: p.id, name: p.name}));
            }
            // The player whose turn it was BEFORE this update is the one who just acted
            // (played a card) — used as the source hand for the cross-hand fly-in.
            state.lastActingPlayer = state.game?.playersTurn || null;
            const prevDeckLeft = state.deckLeft;
            const nextDeckLeft = game.cardsLeftInDeck ?? 0;
            const shouldDelayTableUpdate = !skipTableDelay && previousPlayedCards.length > 0 && nextPlayedCards.length === 0;
            if (shouldDelayTableUpdate) {
                // Keep the hand locked until the old trick has fully exited and the
                // incoming round state has been applied. Otherwise the new turn can
                // become draggable while the previous cards are still disappearing.
                state.playing = true;
                setRoundClearing(true);
                // The trick winner leads the next trick, so the incoming game's
                // playersTurn is who won the round that just completed.
                const roundWinner = game.playersTurn ? {id: game.playersTurn.id, name: game.playersTurn.name} : null;
                // Play order captured mid-trick (correct); fall back to the rotated
                // order only if we never saw an in-progress frame (e.g. reconnect).
                const orderedPlayers = state.trickPlayOrder.length
                    ? state.trickPlayOrder
                    : inTrickPlayers.map((p) => ({id: p.id, name: p.name}));
                state.pendingRound = {
                    cards: previousPlayedCards,
                    players: orderedPlayers,
                    winner: roundWinner,
                    // Whole winning team (both teammates in 2v2; just the winner in 1v1).
                    winners: resolveWinningTeam(state.game, roundWinner, inTrickPlayers)
                };
                if (!state.delayedTablePayload || isHigherPriorityGameEvent(gameEvent, state.delayedTablePayload.gameEvent)) {
                    state.delayedTablePayload = {game, gameEvent, result};
                } else if (gameEvent === state.delayedTablePayload.gameEvent) {
                    state.delayedTablePayload = {game, gameEvent, result};
                }
                // Use the incoming empty trick for legality immediately. Keeping the
                // previous trick here caused Treseta cards to be classified twice:
                // once against the old round and again after the table was cleared.
                state.game = game;
                state.deckLeft = nextDeckLeft;
                if (dom.deckLeft) dom.deckLeft.textContent = String(nextDeckLeft);
                renderDeckTower(nextDeckLeft);
                renderTrump(game.trumpCard, nextDeckLeft);
                renderPlayers(game);
                updateTurnIndicator(game);
                renderCurrentPlayer(game);
                updateTurn(game.playersTurn);
                if (state.delayedTablePending) {
                    return;
                }
                state.delayedTablePending = true;
                state.delayedTableUpdateTimer = setTimeout(() => {
                    state.delayedTableUpdateTimer = null;
                    Promise.resolve(animateTrickClear()).finally(() => {
                        const delayedPayload = state.delayedTablePayload;
                        state.delayedTablePayload = null;
                        state.delayedTablePending = false;
                        if (delayedPayload) {
                            applyGame(delayedPayload.game, delayedPayload.gameEvent, delayedPayload.result, true);
                        }
                        setRoundClearing(false);
                        state.playing = false;
                        // The round state was rendered while the clear lock was
                        // active, so refresh the card classes after unlocking or
                        // they can remain visually disabled for the new round.
                        syncHand();
                    });
                }, 1100);
                return;
            }
            if (state.delayedTableUpdateTimer) {
                clearTimeout(state.delayedTableUpdateTimer);
                state.delayedTableUpdateTimer = null;
                setRoundClearing(false);
                state.playing = false;
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
            }
            renderTrick(nextPlayedCards, previousPlayedCards);
            refreshPreviousRoundControlsAfterGameUpdate();
            renderPlayers(game);
            updateTurnIndicator(game);
            renderCurrentPlayer(game);
            updateTurn(game.playersTurn);
            if (gameEvent === 'RESULTED' && result && Array.isArray(result.gameWinners)) {
                const winners = formatWinnerText(result.gameWinners, game);
                state.endState = {
                    title: t('briskula.matchResult'),
                    winnersText: winners,
                    metaText: buildResultMetaText(result.gameWinners, game)
                };
                renderCenterResult(state.endState.title, state.endState.winnersText, state.endState.metaText);
                startLobbyReturnCountdown();
                clearTeammateHand();
            } else if (gameEvent === 'CLOSED') {
                state.endState = {
                    title: t('briskula.matchResult'),
                    winnersText: t('briskula.gameClosed'),
                    metaText: t('briskula.returningToLobby')
                };
                renderCenterResult(state.endState.title, state.endState.winnersText, state.endState.metaText);
                startLobbyReturnCountdown();
                clearTeammateHand();
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
            const slot = dom.trump.parentElement;
            if (!card || ((deckLeft != null && deckLeft <= 0) && !state.deckExhausting)) {
                if (slot) slot.style.display = 'none';
                dom.trump.style.display = 'none';
                dom.trump.dataset.cardCode = '';
                dom.trump.dataset.cardFace = 'false';
                dom.deckStack?.classList.remove('has-trump');
                return;
            }
            const frontImg = dom.trump.querySelector('.card-front');
            const url = italianCardUrl(card.card);
            if (frontImg) window.UltracardsGameUi?.applyCardImage(frontImg, url);
            dom.trump.dataset.cardCode = card.card || '';
            dom.trump.dataset.cardFace = 'true';
            if (slot) slot.style.display = '';
            dom.trump.style.display = '';
            dom.deckStack?.classList.add('has-trump');
            setupTrumpZoom(dom.trump);
        }

        function renderCenterResult(title, winnersText, metaText) {
            if (!dom.dropZone) return;
            const entering = !dom.dropZone.classList.contains('is-result');
            dom.dropZone.classList.remove('ready');
            if (entering) void dom.dropZone.offsetWidth;
            dom.dropZone.classList.add('is-result');
            dom.dropZone.innerHTML = `
                <div class="drop-zone-title">${title || t('briskula.matchResult')}</div>
                <div class="drop-zone-winner">${winnersText || t('history.noWinner')}</div>
                <div class="drop-zone-meta">${metaText || ''}</div>
            `;
        }

        function clearCenterResult() {
            if (!dom.dropZone || !dom.dropZone.classList.contains('is-result')) return;
            dom.dropZone.classList.remove('is-result');
            dom.dropZone.textContent = t('game.dragToPlay');
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
                meta.textContent = t('briskula.returningIn', secondsLeft);
            }
        }

        // Rect of a player's hand area (their seat-cards) — the source for a card
        // flying from that player's hand into the trick.
        function playerSeatRect(player) {
            if (!dom.ring || !player) return null;
            let seat = null;
            if (player.id != null && String(player.id) !== '') {
                seat = dom.ring.querySelector(`[data-player-id="${CSS.escape(String(player.id))}"]`);
            }
            if (!seat && player.name) {
                const wanted = String(player.name).toLowerCase();
                seat = Array.from(dom.ring.children).find((s) => {
                    const nm = s.querySelector('.seat-name')?.textContent;
                    return nm && nm.toLowerCase() === wanted;
                }) || null;
            }
            return seatHandRect(seat);
        }

        function anyOpponentSeatRect() {
            if (!dom.ring) return null;
            const opp = Array.from(dom.ring.children).find((s) => s.dataset.isSelf !== '1');
            return seatHandRect(opp);
        }

        function seatHandRect(seat) {
            if (!seat) return null;
            const cardsEl = seat.querySelector('.seat-cards');
            const target = (cardsEl && cardsEl.getBoundingClientRect().width) ? cardsEl : seat;
            const rect = target.getBoundingClientRect();
            return rect && rect.width ? rect : null;
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
            const localHandoffCards = [];

            list.forEach((card, idx) => {
                const key = `${cardKey(card)}:${idx}`;
                let img = state.trickEls.get(key);
                if (!img) {
                    img = window.UltracardsGameUi?.renderCardImage({
                        card,
                        className: 'trick-card',
                        alt: t('briskula.playedCard.alt')
                    }) || document.createElement('img');
                    img.dataset.trickKey = key;
                    state.trickEls.set(key, img);
                    // Don't fade-in the card the local player just played: a real card is
                    // already sitting on that slot (state.justPlayed) and will be handed off.
                    if (enteringKeys.has(cardKey(card))) {
                        if (cardKey(card) === state.justPlayed?.key) {
                            img.classList.add('is-handoff-hidden');
                            localHandoffCards.push(img);
                        } else {
                            enteringCards.push(img);
                        }
                    }
                }
                img.style.setProperty('--spin', `${(idx * 4) - 6}deg`);
                ordered.push(img);
            });

            // A confirmed local play can arrive before its flight ends. Remove the
            // reserved target now so the hidden confirmed card occupies that same slot
            // instead of being laid out beside it and moving a second time on cleanup.
            if (localHandoffCards.length) {
                window.UltracardsGameUi?.clearReservedSlot(state.hands.table);
            }
            // Fixed-slot row: cards occupy stable slots and never re-center.
            setTrickSlotTotal(list.length);
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
                    yArc: 3,
                    slotTotal: trickSlotTotal(list.length)
                }
            })).then(() => {
                // The rendered trick card now occupies the slot — remove the flown real
                // card we kept there for a seamless handoff (no flicker / no reappear).
                localHandoffCards.forEach((img) => finishLocalPlayedHandoff(img));
                // Opponent cards (entering, not the local play) fly in from the HAND of
                // the player who played them (their seat), straight to the fixed slot.
                const rawDeck = dom.deckTower?.getBoundingClientRect();
                const deckRect = (rawDeck && rawDeck.width && rawDeck.height)
                    ? rawDeck
                    : dom.deckStack?.getBoundingClientRect();
                const sourceRect = playerSeatRect(state.lastActingPlayer)
                    || anyOpponentSeatRect()
                    || deckRect;
                enteringCards.forEach((img, i) => {
                    if (sourceRect && sourceRect.width) {
                        window.UltracardsGameUi?.crossHandTransfer({
                            cardEl: img,
                            fromRect: sourceRect,
                            faceDown: false,
                            spin: -6 + i * 4,
                            fromScale: 0.9,
                            duration: 380
                        });
                    } else {
                        Promise.resolve(window.UltracardsGameUi?.animateElement(img, {
                            opacity: [0, 1],
                            duration: 200,
                            ease: 'out(3)'
                        })).then(() => { img.style.opacity = ''; });
                    }
                });
            });
        }

        function finishLocalPlayedHandoff(renderedCard) {
            const handoff = state.justPlayed;
            if (!renderedCard || !handoff) return;
            const adoptPlayedCard = () => {
                if (!handoff.flightDone) {
                    handoff.revealWhenReady = adoptPlayedCard;
                    return;
                }
                if (state.justPlayed !== handoff) return;
                const movingCard = handoff.el;
                if (!movingCard?.isConnected || !renderedCard.isConnected) {
                    renderedCard.classList.remove('is-handoff-hidden');
                    movingCard?.remove();
                    state.justPlayed = null;
                    return;
                }

                // Keep the actual card that flew from the hand. Copy the already-laid-out
                // trick card's classes/styles and replace its hidden placeholder in one
                // synchronous step, so there is no frame with different geometry or shadow.
                const trickKey = renderedCard.dataset.trickKey;
                movingCard.className = Array.from(renderedCard.classList)
                    .filter((className) => className !== 'is-handoff-hidden')
                    .join(' ');
                movingCard.style.cssText = renderedCard.style.cssText;
                movingCard.dataset.trickKey = trickKey;
                movingCard.ondblclick = null;
                renderedCard.replaceWith(movingCard);
                state.trickEls.set(trickKey, movingCard);
                state.justPlayed = null;
            };
            adoptPlayedCard();
        }

        function animateTrickClear() {
            if (!dom.trick) return Promise.resolve();
            const trickCards = Array.from(dom.trick.querySelectorAll('.trick-card'));
            if (!trickCards.length) return Promise.resolve();

            // Save previous round before clearing. Prefer the captured in-trick data
            // (cards + the player order at play time, before the winner rotation).
            const pending = state.pendingRound;
            const cardsToSave = Array.isArray(pending?.cards) && pending.cards.length
                ? pending.cards
                : (Array.isArray(state.game?.playedCards) ? state.game.playedCards : []);
            if (cardsToSave.length > 0) {
                const fallbackWinner = pending?.winner
                    || (state.game?.playersTurn ? {id: state.game.playersTurn.id, name: state.game.playersTurn.name} : null);
                const roundData = {
                    cards: cardsToSave,
                    // Play order (leader-first) captured mid-trick; card i ← players[i % n].
                    players: Array.isArray(pending?.players) && pending.players.length
                        ? pending.players
                        : (state.game?.playersOrder ?? []).map((p) => ({id: p.id, name: p.name})),
                    winner: fallbackWinner,
                    // All players on the winning team (both teammates in 2v2).
                    winners: Array.isArray(pending?.winners) && pending.winners.length
                        ? pending.winners
                        : (fallbackWinner ? [fallbackWinner] : []),
                    timestamp: Date.now()
                };
                state.previousRound = roundData;
                PreviousRoundStore.save(gameId, roundData);
                state.pendingRound = null;
                refreshPrevRoundButton();
            }

            dom.trick.classList.remove('is-clearing');
            dom.trick.classList.add('is-clearing');

            const winnerPlayer = state.game?.playersTurn;
            const winnerSeatEl = winnerPlayer?.id
                ? dom.ring?.querySelector(`[data-player-id="${CSS.escape(String(winnerPlayer.id))}"]`)
                : null;

            return Promise.resolve(
                window.UltracardsGameUi?.animateTrickCollect(trickCards, winnerSeatEl)
            ).finally(() => {
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
            gameLayout?.classList.toggle('is-three-player', players.length === 3);
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
                // The teammate-hand toggle lives inside the teammate's seat, right
                // above the avatar (moved, not cloned — listeners stay attached).
                if (teamTone === 'ally' && !isSelf && teammateHandToggle && teammateHandToggle.parentElement !== seat) {
                    seat.insertBefore(teammateHandToggle, seat.firstChild);
                }
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
                    // 2v2 endgame reveal (fullscreen mobile): the teammate's seat
                    // shows their actual cards face-up instead of anonymous backs.
                    const revealed = teamTone === 'ally' && !isSelf && isFullscreenMobile()
                        && Array.isArray(state.teammateCards) && state.teammateCards.length
                        ? state.teammateCards
                        : null;
                    if (revealed) {
                        renderTeammateSeatCards(cards, revealed);
                    } else if (players.length === 2 && !isSelf && state.opponentDrawReveal?.cards?.length) {
                        renderOpponentDrawnSeatCards(
                            cards,
                            cardsCount,
                            seat.dataset.seatSide || 'center',
                            state.opponentDrawReveal.cards,
                            state.opponentDrawReveal.targetCount
                        );
                    } else {
                        if (cards.dataset.opponentRevealSig) {
                            cards.querySelectorAll('.opponent-drawn-card').forEach((card) => {
                                card.cardApi?.showBack();
                                card.classList.remove('opponent-drawn-card');
                            });
                            delete cards.dataset.opponentRevealSig;
                        }
                        syncSeatCards(cards, cardsCount, seat.dataset.seatSide || 'center');
                    }
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
                    bubble.textContent = String(displayPoints(player.points));
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
            if (isTurn) stopPreviousRoundReplay();
            refreshPrevRoundControls();
            if (dom.dropZone) {
                if (dom.dropZone.classList.contains('is-result')) return;
                dom.dropZone.textContent = isTurn ? t('briskula.playHere') : t('briskula.waitMoment');
            }
            if (dom.tableTurnOverlay) {
                dom.tableTurnOverlay.classList.toggle('is-visible', !!playersTurn);
            }
            if (dom.tableTurnMessage) {
                dom.tableTurnMessage.textContent = isTurn
                    ? t('briskula.yourTurn')
                    : t('briskula.waitingFor', playersTurn && playersTurn.name ? playersTurn.name : t('briskula.nextPlayer'));
            }
            syncHand();
        }

        function syncHand() {
            if (!dom.hand) return;
            const isTurn = !state.previousRoundReplayActive && state.game && state.game.playersTurn
                && isCurrentUser(state.game.playersTurn);
            const visibleHand = state.hand.filter((card) => {
                const key = cardKey(card);
                return !state.pending.has(key) && !state.autoPlayedPending.has(key);
            }).sort(compareHandCards);
            const renderSignature = [
                isTurn ? 'turn' : 'wait',
                state.playing ? 'locked' : 'ready',
                state.roundClearing ? 'clearing' : 'steady',
                state.draggingEl?.dataset.cardKey || '',
                visibleHand.map((card) => `${cardKey(card)}:${isPlayableCard(card) ? 1 : 0}`).join(',')
            ].join('|');
            if (renderSignature === state.handRenderSignature) return;
            state.handRenderSignature = renderSignature;
            const nextKeys = new Set(visibleHand.map(cardKey));
            const cardsByKey = new Map(visibleHand.map((card) => [cardKey(card), card]));

            const ordered = [];
            visibleHand.forEach((card) => {
                const key = cardKey(card);
                let el = state.handEls.get(key);
                if (!el) {
                    el = createHandCard(card, isTurn);
                    state.handEls.set(key, el);
                    state.justDealtKeys.delete(key);
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
                // Apply legality after the old layout has been captured by the
                // hand reflow. This gives the legal/illegal lift a real start
                // position to animate from after a play or round transition.
                ordered.forEach((el) => {
                    const card = cardsByKey.get(el.dataset.cardKey);
                    if (card) updateHandCardState(el, card, isTurn);
                });
                orderedForLayout.forEach((el) => dom.hand.appendChild(el));
            }, {
                cards: orderedForLayout,
                layout: handLayoutParams()
            });
        }

        function createHandCard(card, isTurn) {
            const wrap = window.UltracardsGameUi?.createCard({
                card,
                className: 'hand-card',
                alt: 'Card',
                flippable: state.dealingKeys.has(cardKey(card))
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
            const playable = isTurn && !state.playing && !state.roundClearing && isPlayableCard(card);
            img.classList.toggle('is-playable', playable);
            img.classList.toggle('is-illegal', isTurn && !state.playing && !playable);
            img.classList.toggle('is-disabled', !playable);
            img.ondblclick = !playable
                ? null
                : () => playCardFromHand(card, img);
        }

        function isPlayableCard(card) {
            return gameAdapter.canPlayCard?.(card, state.game, state.hand) ?? true;
        }

        function setHandSort(sort) {
            state.handSort = sort;
            dom.sortSuit?.classList.toggle('is-active', sort === 'suit');
            dom.sortRank?.classList.toggle('is-active', sort === 'rank');
            syncHand();
        }

        function disableHandSort() {
            state.handSort = 'manual';
            dom.sortSuit?.classList.remove('is-active');
            dom.sortRank?.classList.remove('is-active');
        }

        function preserveHandOrder(cards) {
            if (state.handSort !== 'manual') return cards;
            const byKey = new Map(cards.map((card) => [cardKey(card), card]));
            const ordered = state.hand.map((card) => byKey.get(cardKey(card))).filter(Boolean);
            cards.forEach((card) => {
                if (!ordered.some((entry) => cardKey(entry) === cardKey(card))) ordered.push(card);
            });
            return ordered;
        }

        function compareHandCards(left, right) {
            const leftSuit = suitOrder(left);
            const rightSuit = suitOrder(right);
            const leftRank = rankOrder(left);
            const rightRank = rankOrder(right);
            return state.handSort === 'manual' ? 0 : state.handSort === 'rank'
                ? leftRank - rightRank || leftSuit - rightSuit
                : leftSuit - rightSuit || leftRank - rightRank;
        }

        function suitOrder(card) {
            return ({C: 0, D: 1, S: 2, B: 3})[card?.card?.charAt(0)] ?? 4;
        }

        function rankOrder(card) {
            const value = String(card?.card || '').slice(1);
            return gameAdapter.cardStrength?.[value] ?? 10;
        }

        function playCardFromHand(card, sourceEl) {
            if (state.playing || state.roundClearing || !isPlayableCard(card)) return;
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
            state.pendingAt.set(cardKey(card), Date.now());
            state.handEls.delete(cardKey(card));
            // Lift the REAL card into the overlay and fly it to the trick slot with the
            // same seamless handoff as a drag-drop play (no fade, no reappear).
            const sourceRect = sourceEl.getBoundingClientRect();
            const session = window.UltracardsGameUi?.startDragCard({
                sourceEl,
                originZone: state.hands.self,
                pointer: {x: sourceRect.left + sourceRect.width / 2, y: sourceRect.top + sourceRect.height / 2}
            });
            const el = (session && session.el) || sourceEl;
            const targetRect = getTableTargetRect();
            markJustPlayed(el, card);
            // Use the UNSCALED width: getBoundingClientRect would include the drag
            // scale(1.04) and land the card ~4% under the real slot size (snap on handoff).
            const flownW = el.offsetWidth || el.getBoundingClientRect().width;
            const toScale = (targetRect.width && flownW) ? (targetRect.width / flownW) : 0.94;
            if (!playCard(card, {alreadyLocked: true, keepLocked: true})) {
                window.UltracardsGameUi?.clearReservedSlot(state.hands.table);
                return;
            }
            Promise.resolve(window.UltracardsGameUi?.flyOverlayTo(el, targetRect, {
                duration: 230,
                toScale,
                toRot: `${trickSlotRotation(state.trickEls.size)}deg`,
                fade: false
            })).catch(() => undefined).then(() => {
                completeJustPlayedFlight(cardKey(card));
                state.playing = false;
                window.UltracardsGameUi?.clearReservedSlot(state.hands.table);
            });
        }

        // Track the real card flown to the table so renderTrick can hand off to the
        // drawn trick card seamlessly. A safety timer removes it even if the trick was
        // already cleared (e.g. the play completed the trick) so it can't linger.
        function markJustPlayed(el, card) {
            const entry = {el, key: cardKey(card), flightDone: false};
            state.justPlayed = entry;
            setTimeout(() => {
                if (state.justPlayed === entry) {
                    entry.el?.remove();
                    state.justPlayed = null;
                }
            }, 1600);
        }

        function completeJustPlayedFlight(cardKeyValue) {
            const handoff = state.justPlayed;
            if (!handoff || handoff.key !== cardKeyValue) return;
            handoff.flightDone = true;
            handoff.revealWhenReady?.();
        }

        function playCard(card, options) {
            const alreadyLocked = options?.alreadyLocked === true;
            if (state.roundClearing || state.previousRoundReplayActive) return false;
            if (state.playing && !alreadyLocked) return false;
            if (!isPlayableCard(card)) return false;
            const isTurn = state.game && state.game.playersTurn
                && isCurrentUser(state.game.playersTurn);
            if (state.game && state.game.playersTurn && !isTurn) {
                if (alreadyLocked) state.playing = false;
                return false;
            }
            state.playing = true;
            state.pending.add(cardKey(card));
            state.pendingAt.set(cardKey(card), Date.now());
            if (!state.wsClient || !state.wsConnected) {
                state.playing = false;
                state.pending.delete(cardKey(card));
                // No live connection: discard the flown real card so it doesn't linger,
                // then re-create the card cleanly in the hand.
                if (state.justPlayed && state.justPlayed.key === cardKey(card)) {
                    state.justPlayed.el?.remove();
                    state.justPlayed = null;
                }
                restoreDraggedCard(cardKey(card));
                syncHand();
                return false;
            }
            state.wsClient.send('/app/game/play', {}, JSON.stringify({cardType: card.cardType, card: card.card}));
            if (!options?.keepLocked) state.playing = false;
            animateHandRemoval(cardKey(card));
            setTimeout(() => {
                state.hand = state.hand.filter((entry) => cardKey(entry) !== cardKey(card));
                syncHand();
            }, 230);
            return true;
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
            const inside = isPointInTableTarget(x, y, session);
            if (inside === session.overTable) return;
            session.overTable = inside;
            setTableDropReady(inside);
            if (inside) {
                setTrickSlotTotal(state.trickEls.size + 1);
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
            const accepted = isPointInTableTarget(point.x, point.y, session);
            const targetRect = accepted ? getTableTargetRect() : session.originRect;
            if (accepted && targetRect) {
                const el = session.el;
                state.dragSession = null;
                state.draggingEl = null;
                // Lock the play for the whole handoff. Without this, a second
                // pointer gesture can start while the first card is still flying.
                state.playing = true;
                state.handEls.delete(cardKey(card));
                // Exclude from the hand right away so a mid-flight syncHand can't
                // re-create a duplicate while the card flies to the table.
                state.pending.add(cardKey(card));
                state.pendingAt.set(cardKey(card), Date.now());
                // Fly the REAL card to the trick slot WITHOUT fading, and keep it there
                // until renderTrick draws the played card at the same spot — then it is
                // removed. No disappear/reappear, and it lands on the exact slot.
                markJustPlayed(el, card);
                // Fly to the trick slot at the EXACT final size + rotation so there's no
                // sudden shrink or late rotation when the trick card takes over. Use the
                // UNSCALED width so the drag scale(1.04) isn't double-counted.
                const flownW = el.offsetWidth || el.getBoundingClientRect().width;
                const toScale = (targetRect.width && flownW) ? (targetRect.width / flownW) : 0.94;
                if (!playCard(card, {alreadyLocked: true, keepLocked: true})) {
                    window.UltracardsGameUi?.clearReservedSlot(state.hands.table);
                    return;
                }
                Promise.resolve(window.UltracardsGameUi?.flyOverlayTo(el, targetRect, {
                    duration: 220,
                    toScale,
                    toRot: `${trickSlotRotation(state.trickEls.size)}deg`,
                    fade: false
                })).catch(() => undefined).then(() => {
                    completeJustPlayedFlight(cardKey(card));
                    state.playing = false;
                    window.UltracardsGameUi?.clearReservedSlot(state.hands.table);
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
            const others = handCards.map(cardFromEl).filter(Boolean);
            others.splice(index, 0, card);
            state.hand = others;
            disableHandSort();

            // Clean drag styles and re-insert the REAL element at the chosen index.
            el.classList.remove('drag-ghost');
            el.style.transform = '';
            el.style.opacity = '';
            el.style.width = '';
            el.style.height = '';
            const ref = handCards[index] || null;
            const insert = () => {
                if (ref && ref.parentElement === dom.hand) dom.hand.insertBefore(el, ref);
                else dom.hand.appendChild(el);
            };

            // Lay out so the other cards open a gap, then fly the returning card into
            // its exact slot FACE-UP (faceDown:false → keeps its face toward the player).
            window.UltracardsGameUi?.animateHandChange(state.hands.self, insert);
            window.UltracardsGameUi?.flyIntoSlot(el, releaseRect, {
                faceDown: false,
                spin: 10,
                fromScale: 1.04,
                duration: 300
            });
        }

        // `cache` (a drag session) memoizes the rects for the drag's lifetime —
        // nothing the hit-test reads moves mid-drag, and reading layout on every
        // touch move right after the drag transform write forces a layout flush
        // per input event (a real stutter source on mobile Gecko).
        function isPointInTableTarget(x, y, cache) {
            let rects = cache?.tableHitRects;
            if (!rects) {
                const target = dom.tableSurface || dom.dropZone || dom.trick;
                rects = {
                    table: target?.getBoundingClientRect() || null,
                    hand: dom.hand?.closest('.hand-row')?.getBoundingClientRect() || null
                };
                if (cache) cache.tableHitRects = rects;
            }
            const rect = rects.table;
            const inside = !!rect
                && x >= rect.left
                && x <= rect.right
                && y >= rect.top
                && y <= rect.bottom;
            if (!inside) return false;
            // Fullscreen mobile: the felt extends under the hand strip — releasing
            // a card back over the hand must NOT play it. (Classic layouts keep the
            // hand below the felt, so this never triggers there.)
            const handRect = rects.hand;
            return !(handRect && handRect.height > 0 && y >= handRect.top);
        }

        // Stable number of fixed slots for the trick row = the trick size (players for
        // standard play). Cards occupy fixed slots so they never re-center.
        function trickSlotTotal(extra) {
            const playersCount = state.game ? buildPlayers(state.game).length : 2;
            return Math.max(2, playersCount, Number(extra) || 0, state.trickEls.size);
        }

        function setTrickSlotTotal(extra) {
            if (state.hands.table?.options) {
                state.hands.table.options.slotTotal = trickSlotTotal(extra);
            }
        }

        // The final rotation (slot tilt + spin) of the trick card at a given play index,
        // matching renderTrick's layout (maxTilt 4) and per-card --spin. Used so a played
        // card flies to its exact final rotation (no late rotate after it lands).
        function trickSlotRotation(index) {
            const total = trickSlotTotal(index + 1);
            const half = (total - 1) / 2;
            const normalized = half > 0 ? (index - half) / half : 0;
            return normalized * 4 + (index * 4 - 6);
        }

        function getTableTargetRect() {
            // Reserve the new card's FIXED slot so the play flies straight there.
            setTrickSlotTotal(state.trickEls.size + 1);
            const placeholder = window.UltracardsGameUi?.reserveSlot(state.hands.table, state.trickEls.size);
            if (!placeholder) {
                return dom.dropZone?.getBoundingClientRect() || dom.trick?.getBoundingClientRect();
            }
            const visualRect = placeholder.getBoundingClientRect();
            const width = placeholder.offsetWidth || visualRect.width;
            const height = placeholder.offsetHeight || visualRect.height;
            const centerX = visualRect.left + visualRect.width / 2;
            const centerY = visualRect.top + visualRect.height / 2;
            return {
                left: centerX - width / 2,
                top: centerY - height / 2,
                right: centerX + width / 2,
                bottom: centerY + height / 2,
                width,
                height
            };
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

        // The full set of players that share the round winner's team. In 2v2 this is
        // both teammates; in a non-team game it's just the winner.
        function resolveWinningTeam(game, winner, allPlayers) {
            if (!winner) return [];
            const teamState = resolveTeams(game);
            if (!teamState) return [winner];
            const winnerTeam = getPlayerTeamNumber(teamState, winner);
            if (winnerTeam == null) return [winner];
            const players = Array.isArray(allPlayers) ? allPlayers : [];
            const teamMembers = players
                .filter((p) => getPlayerTeamNumber(teamState, p) === winnerTeam)
                .map((p) => ({id: p.id, name: p.name}));
            if (!teamMembers.some((p) => (winner.id != null && p.id === winner.id) || p.name === winner.name)) {
                teamMembers.push(winner);
            }
            return teamMembers.length ? teamMembers : [winner];
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
                return t('briskula.teammate');
            }
            if (tone === 'enemy') {
                return t('briskula.opponent');
            }
            return getTeamDisplayName(teamNumber);
        }

        function formatWinnerText(winners, game) {
            if (!Array.isArray(winners) || !winners.length) {
                return t('history.noWinner');
            }
            const winnerLabel = winners.length === 1 ? t('history.winner') : t('history.winners');
            return `${winnerLabel}: ${winners.map((winner) => winner.name).join(', ')}`;
        }

        function buildResultMetaText(winners, game) {
            const teamState = resolveTeams(game);
            if (!teamState || !Array.isArray(winners) || !winners.length) {
                return t('briskula.gameEnded');
            }
            const currentTeamPlayers = teamState.currentUserTeamNumber === 1
                ? teamState.team1
                : (teamState.currentUserTeamNumber === 2 ? teamState.team2 : []);
            if (!currentTeamPlayers.length) {
                return t('briskula.gameEnded');
            }
            const currentTeamWon = currentTeamPlayers.length > 0
                && winners.every((winner) => currentTeamPlayers.some((player) => isSamePlayer(player, winner)));
            return currentTeamWon ? t('briskula.teamWon') : t('briskula.teamLost');
        }

        function formatPlayerList(players) {
            if (!Array.isArray(players) || !players.length) {
                return t('lobbyPage.waitingShort');
            }
            return players.map((player) => player?.name || t('lobby.userFallback', player?.id ?? '')).join(', ');
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
                dom.playerSummaryPoints.textContent = String(displayPoints(current.points));
            }
            if (dom.playerSummaryAvatar) {
                dom.playerSummaryAvatar.textContent = (current.name || 'P').charAt(0).toUpperCase();
            }
            if (dom.playerSummaryName) {
                dom.playerSummaryName.textContent = current.name || 'Player';
            }
            separateFullscreenAvatars(dom.ring);
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
                seat.style.left = formatSeatCoordinate(slot.x);
                seat.dataset.seatTop = formatSeatCoordinate(slot.y);
                seat.style.top = seat.dataset.seatTop;
                const nudgeX = typeof slot.nudgeX === 'number' ? `${slot.nudgeX}px` : (slot.nudgeX || '0px');
                const nudgeY = typeof slot.nudgeY === 'number' ? `${slot.nudgeY}px` : (slot.nudgeY || '0px');
                seat.style.transform = `translate(-50%, -50%) translate3d(${nudgeX}, ${nudgeY}, 0)`;
                seat.style.setProperty('--hand-rotate', getSeatHandRotate(slot.side));
            });
            separateFullscreenAvatars(ring);
        }

        function formatSeatCoordinate(value) {
            return typeof value === 'number' ? `${value}%` : value;
        }

        function separateFullscreenAvatars(ring) {
            if (!ring || !isFullscreenMobile()) return;
            const gap = 8;
            const occupied = [];
            const selfAvatar = dom.playerSummaryAvatar;
            if (selfAvatar && getComputedStyle(selfAvatar).visibility !== 'hidden') {
                const rect = selfAvatar.getBoundingClientRect();
                if (rect.width && rect.height) occupied.push(rect);
            }
            Array.from(ring.children).forEach((seat) => {
                if (seat.dataset.isSelf === '1') return;
                const avatar = seat.querySelector('.seat-avatar');
                if (!avatar) return;
                let rect = avatar.getBoundingClientRect();
                let offset = 0;
                occupied.forEach((other) => {
                    if (rect.right + gap <= other.left || rect.left >= other.right + gap
                        || rect.bottom + gap <= other.top || rect.top >= other.bottom + gap) return;
                    offset += Math.ceil(other.bottom + gap - rect.top);
                    seat.style.top = `calc(${seat.dataset.seatTop} + ${offset}px)`;
                    rect = avatar.getBoundingClientRect();
                });
                occupied.push(rect);
            });
        }

        function getSeatSlot(index, count, isSelf) {
            if (isSelf) {
                return {x: 50, y: 112, nudgeX: 0, nudgeY: 0, side: 'bottom'};
            }
            if (isFullscreenMobile()) {
                // Full-bleed felt: the classic ring hangs side seats at -3%/103%,
                // which would put them off screen — keep everyone fully visible.
                if (count <= 2) {
                    return {x: 50, y: 24, nudgeX: 0, nudgeY: 0, side: 'top'};
                }
                if (count === 3) {
                    const slots = [
                        {x: 50, y: 112, nudgeX: 0, nudgeY: 0, side: 'bottom'},
                        {x: 26, y: 24, nudgeX: 0, nudgeY: 0, side: 'top'},
                        {x: 74, y: 24, nudgeX: 0, nudgeY: 0, side: 'top'}
                    ];
                    return slots[index] || slots[1];
                }
                const slots = [
                    {x: 50, y: 112, nudgeX: 0, nudgeY: 0, side: 'bottom'},
                    {x: 14, y: 30, nudgeX: 0, nudgeY: 0, side: 'left'},
                    {x: 50, y: 24, nudgeX: 0, nudgeY: 0, side: 'top'},
                    {x: 86, y: 30, nudgeX: 0, nudgeY: 0, side: 'right'}
                ];
                return slots[index] || slots[index % slots.length];
            }
            if (count <= 2) {
                return {x: 50, y: 10, nudgeX: 0, nudgeY: 'var(--seat-top-nudge-y)', side: 'top'};
            }
            if (count === 3) {
                const slots = [
                    {x: 50, y: 112, nudgeX: 0, nudgeY: 0, side: 'bottom'},
                    {x: 34, y: 10, nudgeX: 0, nudgeY: 'var(--seat-top-nudge-y)', side: 'top'},
                    {x: 66, y: 10, nudgeX: 0, nudgeY: 'var(--seat-top-nudge-y)', side: 'top'}
                ];
                return slots[index] || slots[1];
            }
            const slots = [
                {x: 50, y: 112, nudgeX: 0, nudgeY: 0, side: 'bottom'},
                {x: -3, y: 42, nudgeX: 0, nudgeY: 0, side: 'left'},
                {x: 50, y: 10, nudgeX: 0, nudgeY: 'var(--seat-top-nudge-y)', side: 'top'},
                {x: 103, y: 42, nudgeX: 0, nudgeY: 0, side: 'right'}
            ];
            return slots[index] || slots[index % slots.length];
        }

        function setupTrumpZoom(cardWrap) {
            if (!cardWrap) return;
            const hoverTarget = cardWrap.parentElement;
            if (!hoverTarget || hoverTarget.dataset.zoomReady) return;
            hoverTarget.dataset.zoomReady = '1';
            let timer = null;
            let overlay = null;

            const clear = () => {
                if (timer) { clearTimeout(timer); timer = null; }
                if (overlay) { overlay.remove(); overlay = null; }
            };

            hoverTarget.addEventListener('mouseenter', () => {
                clear();
                timer = setTimeout(() => {
                    const frontImg = cardWrap.querySelector('.card-front');
                    const src = frontImg?.src || '';
                    if (!src) return;
                    overlay = document.createElement('div');
                    overlay.className = 'trump-zoom';
                    const big = document.createElement('img');
                    big.src = src;
                    big.alt = t('briskula.trumpCard.alt');
                    big.addEventListener('mouseleave', clear);
                    overlay.appendChild(big);
                    overlay.addEventListener('click', clear);
                    document.body.appendChild(overlay);
                    timer = null;
                }, 1000);
            });
            hoverTarget.addEventListener('mouseleave', () => {
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
            if (!state.turnIndicatorTimer) {
                runTurnIndicatorFrame();
            }
        }

        function runTurnIndicatorFrame() {
            state.turnIndicatorTimer = null;
            const targetKey = state.turnIndicatorTargetKey;
            const endsAt = state.turnIndicatorEndsAt;
            if (!targetKey || !Number.isFinite(endsAt)) {
                stopTurnIndicatorLoop();
                return;
            }
            const seat = dom.ring?.querySelector(`[data-player-key="${CSS.escape(targetKey)}"]`);
            if (!seat) {
                state.turnIndicatorTimer = window.setTimeout(runTurnIndicatorFrame, 100);
                return;
            }
            const avatar = seat.querySelector('.seat-avatar');
            if (!avatar) {
                state.turnIndicatorTimer = window.setTimeout(runTurnIndicatorFrame, 100);
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
                state.turnIndicatorTimer = window.setTimeout(runTurnIndicatorFrame, 100);
            }
        }

        function stopTurnIndicatorLoop() {
            if (state.turnIndicatorTimer) {
                clearTimeout(state.turnIndicatorTimer);
                state.turnIndicatorTimer = null;
            }
        }

        function italianCardUrl(code) {
            return window.UltracardsGameUi?.italianCardUrl(code) || '';
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

        // Creative 2v2 endgame reveal: replace the teammate's anonymous backs with
        // their actual cards, fanned face-up in the seat with a staggered pop-in.
        function renderTeammateSeatCards(cardsEl, cards) {
            const sig = cards.map(cardKey).join('|');
            if (cardsEl.dataset.revealSig === sig) return;
            cardsEl.dataset.revealSig = sig;
            cardsEl.replaceChildren();
            const total = cards.length;
            cards.forEach((card, i) => {
                const el = window.UltracardsGameUi?.renderCardImage({
                    card,
                    className: 'seat-card teammate-open-card',
                    alt: t('briskula.teammateCard.alt')
                });
                if (!el) return;
                const centered = i - (total - 1) / 2;
                el.style.transform = `translateY(${(Math.abs(centered) * 1.5).toFixed(1)}px) rotate(${(centered * 6).toFixed(1)}deg)`;
                el.style.zIndex = String(i + 1);
                el.style.setProperty('--reveal-i', String(i));
                cardsEl.appendChild(el);
            });
        }

        function renderOpponentDrawnSeatCards(cardsEl, cardsCount, seatSide, drawnCards, targetCount) {
            const drawn = Array.isArray(drawnCards) ? drawnCards : [];
            const count = Math.max(Number(cardsCount) || 0, Number(targetCount) || 0, drawn.length);
            const sig = `${count}:${drawn.map(cardKey).join('|')}`;
            if (cardsEl.dataset.opponentRevealSig === sig) return;
            if (cardsEl.dataset.opponentRevealSig) {
                // A second draw arrived before the first reveal expired. Turn the
                // previous face-up cards back without rebuilding the whole hand, so
                // only the newly added back cards fly in from the deck.
                cardsEl.querySelectorAll('.opponent-drawn-card').forEach((card) => {
                    card.cardApi?.showBack();
                    card.classList.remove('opponent-drawn-card');
                });
            }
            cardsEl.dataset.opponentRevealSig = sig;
            // Keep the normal anonymous backs and replace only the newly drawn
            // positions with face-up cards. The existing fan/slot geometry remains
            // identical, so the reveal does not move the opponent's whole hand.
            syncSeatCards(cardsEl, count, seatSide, {forceDealCount: drawn.length});
            const children = Array.from(cardsEl.children);
            const start = Math.max(0, children.length - drawn.length);
            drawn.forEach((card, index) => {
                const back = children[start + index];
                if (!back?.cardApi) return;
                back.classList.add('opponent-drawn-card');
                window.setTimeout(() => {
                    if (cardsEl.dataset.opponentRevealSig !== sig || !back.isConnected) return;
                    Promise.resolve(back.cardApi.flip(card)).then(() => {
                        if (cardsEl.dataset.opponentRevealSig !== sig || !back.isConnected) return;
                        // Keep the landed card readable at a larger size before the
                        // hide animation shrinks it and turns it back face-down.
                        const inner = back.querySelector('.card-inner');
                        if (!inner) return;
                        const scale = opponentRevealScale(back);
                        if (useNativeCardAnimations && window.UltracardsGameUi?.animateElement) {
                            window.UltracardsGameUi.animateElement(inner, {
                                transform: ['rotateY(180deg) scale(1)', `rotateY(180deg) scale(${scale})`],
                                duration: 120
                            });
                        } else if (window.gsap) {
                            window.gsap.to(inner, {
                                scale,
                                duration: 0.12,
                                ease: 'power2.out'
                            });
                        }
                    });
                }, 560 + index * 45);
            });
        }

        function opponentRevealScale(card) {
            const handCard = dom.hand?.querySelector('.hand-card');
            const handWidth = handCard?.offsetWidth
                || Number.parseFloat(getComputedStyle(gameLayout || document.documentElement)
                    .getPropertyValue('--hand-card-width'));
            const seatWidth = card?.offsetWidth || 0;
            if (!handWidth || !seatWidth) return 1.65;
            return Math.max(1, handWidth / seatWidth);
        }

        function hideOpponentDrawnCards() {
            const cards = Array.from(document.querySelectorAll('.opponent-drawn-card'));
            if (!cards.length) return Promise.resolve();
            if (useNativeCardAnimations && window.UltracardsGameUi?.animateElement) {
                return Promise.all(cards.map(async (card) => {
                    const inner = card.querySelector('.card-inner');
                    if (!inner) {
                        card.cardApi?.showBack();
                        return;
                    }
                    await window.UltracardsGameUi.animateElement(inner, {
                        transform: [
                            `rotateY(180deg) scale(${opponentRevealScale(card)})`,
                            'rotateY(180deg) scale(0.96)'
                        ],
                        duration: 180
                    });
                    await window.UltracardsGameUi.animateElement(inner, {
                        transform: ['rotateY(180deg) scale(0.96)', 'rotateY(90deg) scale(0.96)'],
                        duration: 140
                    });
                    card.cardApi?.showBack();
                    await window.UltracardsGameUi.animateElement(inner, {
                        transform: ['rotateY(0deg) scale(0.96)', 'rotateY(0deg) scale(1)'],
                        duration: 160
                    });
                    inner.style.transform = '';
                }));
            }
            if (!window.gsap) {
                cards.forEach((card) => card.cardApi?.showBack());
                return Promise.resolve();
            }
            return Promise.all(cards.map((card) => new Promise((resolve) => {
                const inner = card.querySelector('.card-inner');
                if (!inner) {
                    card.cardApi?.showBack();
                    resolve();
                    return;
                }
                const timeline = window.gsap.timeline({onComplete: resolve});
                window.gsap.killTweensOf(inner);
                // Shrink the face layer as it turns edge-on, leaving the seat slot
                // transform untouched so the card cannot jump between edges.
                timeline.to(inner, {
                    scale: 0.96,
                    duration: 0.18,
                    ease: 'power2.inOut'
                }, 0);
                timeline.to(inner, {
                    rotateY: 90,
                    duration: 0.14,
                    ease: 'power2.in'
                }, 0.18);
                timeline.call(() => card.cardApi?.showBack(), null, 0.32);
                timeline.to(inner, {
                    scale: 1,
                    duration: 0.16,
                    ease: 'power2.out'
                }, 0.32);
            })));
        }

        function syncSeatCards(cardsEl, cardsCount, seatSide, animationOptions) {
            // Returning from a teammate reveal (mode switched / reveal cleared):
            // the face-up cards can't be reused as backs — rebuild from scratch.
            if (cardsEl.dataset.revealSig) {
                delete cardsEl.dataset.revealSig;
                cardsEl.replaceChildren();
            }
            // Newly drawn seat cards fly in from the deck to their slot (generic deal
            // animation in syncBackCards) — same fly-in the main hand uses.
            const rawDeck = dom.deckTower?.getBoundingClientRect();
            const deckRect = (rawDeck && rawDeck.width && rawDeck.height)
                ? rawDeck
                : dom.deckStack?.getBoundingClientRect();
            window.UltracardsGameUi?.syncBackCards(cardsEl, cardsCount, {
                cardType: gameAdapter?.cardType || 'ITALIAN',
                className: 'seat-card',
                alt: t('game.cardBack.alt'),
                flippable: gameLayout?.classList.contains('is-two-player') === true,
                spread: gameAdapter?.opponentCardSpread,
                lift: gameAdapter?.opponentCardLift,
                fromRect: deckRect,
                ...animationOptions
            });
        }

        function getSeatHandRotate(seatSide) {
            if (gameAdapter?.opponentHandRotation === false) return '0deg';
            if (seatSide !== 'bottom') return '180deg';
            return '0deg';
        }

        function renderDeckTower(cardsLeft) {
            window.UltracardsGameUi?.renderDeckTower(dom.deckTower, dom.deckStack, cardsLeft, {
                cardType: gameAdapter?.cardType || 'ITALIAN',
                featuredCard: gameAdapter?.features?.trump === true,
                exhausting: state.deckExhausting,
                alt: 'Deck'
            });
        }

        // Opponent draws are dealt by syncBackCards (renderPlayers): newly added seat
        // cards fly in from the deck to their own slot — generic, in game.js, so it works
        // for every hand and every game mode (no per-game deck-draw animation needed).

        // Deal newly drawn cards into the hand WITHOUT clones: each real card element
        // flies from the deck to its own fanned slot through the shared transfer, then
        // flips itself face-up via its innate cardApi. One element per card.
        function dealCardsIntoHand(cards, useTrumpForLastDraw) {
            const rawTowerRect = dom.deckTower?.getBoundingClientRect();
            const deckRect = (rawTowerRect && rawTowerRect.width && rawTowerRect.height)
                ? rawTowerRect
                : (dom.deckStack?.getBoundingClientRect() || rawTowerRect);
            window.UltracardsGameUi?.dealCardsIntoHand(dom.hand, cards, {
                fromRect: deckRect,
                fromFeaturedLast: useTrumpForLastDraw && !!dom.trump?.dataset.cardCode,
                featuredRect: dom.trump?.getBoundingClientRect(),
                onFinish(el, card, isLast) {
                    state.dealingKeys.delete(cardKey(card));
                    if (useTrumpForLastDraw && isLast) {
                        state.deckExhausting = false;
                        renderDeckTower(0);
                        renderTrump(null, 0);
                    }
                }
            });
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
