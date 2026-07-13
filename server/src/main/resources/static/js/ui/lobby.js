(() => {
        const lobbyPage = document.getElementById('lobby-page');
        const initialLobby = window.__INITIAL_LOBBY__ ?? null;
        const initialChat = window.__INITIAL_LOBBY_CHAT__ ?? null;
        const username = lobbyPage?.dataset.username || '';
        const lobbyClosedNoticeKey = 'uc-lobby-closed-notice';
        const reconnectDisconnectDelayMs = 20000;
        if (!lobbyPage || !initialLobby || !initialLobby.id) {
            return;
        }

        const state = {
            lobby: initialLobby,
            currentUser: null,
            wsConnected: false,
            redirectingToGame: false,
            disconnectingForReconnectTimeout: false,
            closeWarningVisible: false,
            teamModeActive: false,
            dragSession: null,
            pendingPlayerAreaTransition: null,
            preservePlayersAreaUntil: 0
        };

        const dom = {
            name: document.getElementById('lobby-name'),
            gameType: document.getElementById('lobby-type-chip'),
            playerChip: document.getElementById('lobby-player-chip'),
            host: document.getElementById('lobby-host'),
            config: document.getElementById('lobby-config'),
            visibilityValue: document.getElementById('lobby-visibility-value'),
            visibilityHelp: document.getElementById('lobby-visibility-help'),
            visibilityControl: document.getElementById('lobby-visibility-control'),
            publicToggle: document.getElementById('lobby-public-toggle'),
            publicToggleLabel: document.getElementById('lobby-public-toggle-label'),
            lobbyCodeTile: document.getElementById('lobby-code-tile'),
            lobbyCode: document.getElementById('lobby-code'),
            configEditor: document.getElementById('lobby-config-editor'),
            configSelect: document.getElementById('lobby-config-select'),
            teamsSection: document.getElementById('lobby-teams-section'),
            teamsSummary: document.getElementById('lobby-teams-summary'),
            randomize: document.getElementById('lobby-randomize'),
            status: document.getElementById('lobby-status'),
            closeWarning: document.getElementById('lobby-close-warning'),
            closeWarningText: document.getElementById('lobby-close-warning-text'),
            toast: document.getElementById('lobby-toast'),
            toastTitle: document.getElementById('lobby-toast-title'),
            toastText: document.getElementById('lobby-toast-text'),
            players: document.getElementById('players-list'),
            start: document.getElementById('start-button'),
            leave: document.getElementById('leave-button'),
            delete: document.getElementById('delete-button')
        };
        let toastHideTimer = null;
        let toastCleanupTimer = null;
        let connectionLostNotified = false;
        let closeWarningTimer = null;
        let reconnectDisconnectTimer = null;
        let previousLobbySnapshot = state.lobby;
        const chat = window.UltracardsChat?.create({
            initialChat,
            currentUsername: username,
            messagesId: 'chat-messages',
            formId: 'chat-form',
            inputId: 'chat-input',
            sendId: 'chat-send',
            messageClass: 'chat-message',
            metaClass: 'chat-meta',
            bubbleClass: 'chat-bubble',
            timeClass: 'chat-time',
            emptyClass: 'chat-empty',
            emptyText: t('chat.lobbyEmpty')
        });

        renderLobby(state.lobby);
        bindActions();
        loadProfile();
        connectLobbyWs();

        function loadProfile() {
            fetch('/api/profile', { credentials: 'include' })
                .then((response) => response.ok ? response.json() : Promise.reject(response.status))
                .then((profile) => {
                    state.currentUser = profile;
                    renderLobby(state.lobby);
                    chat?.setIdentity({
                        id: profile?.id,
                        username: profile?.username || username
                    });
                })
                .catch(() => {
                    updateStatus(t('lobbyPage.profileLoadFailed'));
                });
        }

        function connectLobbyWs() {
            if (!window.Stomp) {
                showToast(t('lobbyPage.toast.connUnavailable.title'), t('lobbyPage.toast.connUnavailable.body'));
                return;
            }

            const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
            const wsUrl = `${wsProtocol}//${window.location.host}/ws`;
            const client = Stomp.client(wsUrl);
            client.reconnect_delay = 3000;
            client.debug = null;

            client.connect({}, () => {
                state.wsConnected = true;
                clearReconnectDisconnectTimer();
                if (connectionLostNotified) {
                    connectionLostNotified = false;
                    showToast(t('lobbyPage.toast.connRestored.title'), t('lobbyPage.toast.connRestored.body'));
                }
                client.subscribe(`/topic/lobbies/${state.lobby.id}`, (message) => {
                    try {
                        const payload = JSON.parse(message.body);
                        if (payload && payload.gameId) {
                            updateStatus(t('lobbyPage.gameStartedId', payload.gameId));
                            redirectToGame(payload.gameId);
                            return;
                        }
                        if (!payload || !payload.type) {
                            return;
                        }
                        if (payload.type === 'DELETED') {
                            persistLobbyClosedNotice(payload.lobbyDto || state.lobby);
                            window.location.href = '/';
                            return;
                        }
                        if (payload.lobbyDto) {
                            if (state.dragSession) {
                                cancelTeamDrag();
                            }
                            const previousLobby = previousLobbySnapshot;
                            const previousRects = capturePlayerCardRects();
                            state.lobby = payload.lobbyDto;
                            previousLobbySnapshot = payload.lobbyDto;
                            const preserveTeamBoard = shouldPreserveTeamBoard(state.lobby);
                            renderLobby(state.lobby, {
                                preserveTeamBoard,
                                ...buildPlayerAreaRenderOptions(previousLobby, payload.lobbyDto)
                            });
                            if (!preserveTeamBoard && shouldAnimateObservedTeams(previousLobby, payload.lobbyDto)) {
                                animateObservedTeamBoard(previousRects);
                            }
                            notifyLobbyEvent(payload.type, previousLobby, payload.lobbyDto);
                            if (payload.type === 'STARTED') {
                                showToast(t('lobbyPage.toast.gameStarted.title'), t('lobbyPage.toast.gameStarted.body'));
                                redirectToResolvedGame();
                            }
                        }
                    } catch (error) {
                        console.error('Lobby websocket parse error', error);
                    }
                });
                client.subscribe(`/topic/lobbies/${state.lobby.id}/chat`, (message) => {
                    try {
                        const payload = JSON.parse(message.body);
                        if (!payload || !payload.message) {
                            return;
                        }
                        chat?.addMessage(payload);
                    } catch (error) {
                        console.error('Lobby chat websocket parse error', error);
                    }
                });
            }, () => {
                state.wsConnected = false;
                if (!connectionLostNotified) {
                    connectionLostNotified = true;
                    showToast(t('lobbyPage.toast.connLost.title'), t('lobbyPage.toast.connLost.body'));
                }
                startReconnectDisconnectTimer();
            });
        }

        function bindActions() {
            dom.lobbyCodeTile?.addEventListener('click', async () => {
                const lobbyCode = String(state.lobby?.lobbyCode || '').trim();
                if (!lobbyCode) {
                    showToast(t('lobbyPage.toast.copyFailed.title'), t('lobbyPage.toast.copyFailed.noCode'));
                    return;
                }

                try {
                    await copyToClipboard(lobbyCode);
                    showToast(t('lobbyPage.toast.codeCopied.title'), t('lobbyPage.toast.codeCopied.body', lobbyCode));
                } catch (error) {
                    showToast(t('lobbyPage.toast.copyFailed.title'), t('lobbyPage.toast.copyFailed.body'));
                }
            });

            dom.start?.addEventListener('click', async () => {
                if (!isLobbyReadyToStart(state.lobby)) {
                    const message = buildStartBlockedMessage(state.lobby);
                    updateStatus(message);
                    showToast(t('lobbyPage.toast.notReady.title'), message);
                    return;
                }

                try {
                    const response = await fetch('/api/lobby/start', {
                        method: 'POST',
                        credentials: 'include'
                    });
                    if (!response.ok) {
                        throw new Error('Failed to start lobby');
                    }
                    updateStatus(t('lobbyPage.startingGame'));
                    redirectToResolvedGame();
                } catch (error) {
                    updateStatus(t('lobbyPage.startFailed'));
                }
            });

            dom.leave?.addEventListener('click', async () => {
                try {
                    await leaveCurrentLobby();
                    window.location.href = '/';
                } catch (error) {
                    updateStatus(t('lobbyPage.leaveFailed'));
                }
            });

            dom.delete?.addEventListener('click', async () => {
                try {
                    const response = await fetch('/api/lobby/delete', {
                        method: 'DELETE',
                        credentials: 'include'
                    });
                    if (!response.ok) {
                        throw new Error('Failed to delete lobby');
                    }
                    window.location.href = '/';
                } catch (error) {
                    updateStatus(t('lobbyPage.deleteFailed'));
                }
            });

            dom.players?.addEventListener('click', async (event) => {
                const button = event.target.closest('[data-kick-player]');
                if (!button) {
                    return;
                }

                const playerId = Number(button.dataset.kickPlayer);
                if (!Number.isFinite(playerId)) {
                    return;
                }

                try {
                    const response = await fetch('/api/lobby/kick-player', {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json'
                        },
                        credentials: 'include',
                        body: JSON.stringify(playerId)
                    });
                    if (!response.ok) {
                        throw new Error('Failed to kick player');
                    }
                } catch (error) {
                    updateStatus(t('lobbyPage.kickFailed'));
                }
            });

            dom.configSelect?.addEventListener('change', async () => {
                if (!state.currentUser || !state.lobby?.host || String(state.currentUser.id) !== String(state.lobby.host.id)) {
                    return;
                }

                try {
                    await updateLobbyConfiguration(dom.configSelect.value);
                } catch (error) {
                    renderConfigEditor(state.lobby, true);
                    showToast(t('lobbyPage.toast.updateFailed.title'), t('lobbyPage.toast.updateFailed.config'));
                }
            });

            dom.publicToggle?.addEventListener('change', async () => {
                if (!isCurrentUserHost()) {
                    syncLobbyVisibility(state.lobby, false);
                    return;
                }

                const nextPublic = !!dom.publicToggle.checked;
                const previousLobby = state.lobby;
                try {
                    await updateLobbyVisibility(nextPublic);
                } catch (error) {
                    state.lobby = previousLobby;
                    syncLobbyVisibility(state.lobby, true);
                    showToast(t('lobbyPage.toast.updateFailed.title'), t('lobbyPage.toast.updateFailed.visibility'));
                }
            });

            dom.randomize?.addEventListener('click', async () => {
                if (!isCurrentUserHost()) {
                    return;
                }

                try {
                    await randomizeLobbyOrder();
                } catch (error) {
                    renderLobby(state.lobby);
                    showToast(t('lobbyPage.toast.updateFailed.title'), t('lobbyPage.toast.updateFailed.randomize'));
                }
            });

            dom.players?.addEventListener('pointerdown', (event) => {
                if (event.button !== 0 || !isCurrentUserHost() || !isBriskulaOrderReorderable(state.lobby)) {
                    return;
                }

                if (event.target.closest('[data-kick-player]')) {
                    return;
                }

                const card = event.target.closest('[data-player-card]');
                if (!card) {
                    return;
                }

                const teamState = resolveTeams(state.lobby);
                const orderedPlayers = resolveOrderedLobbyPlayers(state.lobby);
                if (teamState) {
                    event.preventDefault();
                    startTeamDrag(event, card, teamState);
                    return;
                }

                if (!orderedPlayers.length) {
                    return;
                }
                event.preventDefault();
                startPlayerListDrag(event, card, orderedPlayers);
            });

            window.addEventListener('pointermove', handleTeamDragMove);
            window.addEventListener('pointerup', handleTeamDragEnd);
            window.addEventListener('pointercancel', cancelTeamDrag);
        }

        function renderLobby(lobby, options = {}) {
            const players = resolveOrderedLobbyPlayers(lobby);
            const teamState = resolveTeams(lobby);
            const leavingTeamMode = !teamState && state.teamModeActive;
            const teamExitTransition = options.playerAreaTransitionSnapshot || (
                !options.preserveTeamBoard && leavingTeamMode
                    ? captureTeamModeTransitionSnapshot()
                    : null
            );
            const enteringTeamMode = !!teamState && !state.teamModeActive;
            const teamEntryTransition = options.playerAreaEntrySnapshot || (
                !options.preserveTeamBoard && enteringTeamMode
                    ? capturePlayerCardRects()
                    : null
            );
            state.teamModeActive = !!teamState;
            const currentUserId = state.currentUser?.id != null ? String(state.currentUser.id) : null;
            const isHost = currentUserId && lobby.host && String(lobby.host.id) === currentUserId;
            const isPlayer = currentUserId
                ? players.some((player) => String(player.id) === currentUserId)
                : true;

            if (!isPlayer) {
                window.location.href = '/';
                return;
            }

            if (dom.name) dom.name.textContent = lobby.name || 'ULTRAlobby';
            if (dom.gameType) dom.gameType.textContent = getGameTypeDisplayName(lobby.gameType) || t('game.unknown');
            if (dom.playerChip) dom.playerChip.textContent = formatLobbyPlayerCounter(lobby, players.length);
            if (dom.host) dom.host.textContent = lobby.host?.name || 'Unknown';
            if (dom.config) dom.config.textContent = describeConfig(lobby);
            if (dom.lobbyCode) dom.lobbyCode.textContent = lobby.lobbyCode || '------';
            if (dom.status) dom.status.textContent = buildLobbyStatus(lobby, players.length);
            syncLobbyVisibility(lobby, !!isHost);
            renderConfigEditor(lobby, !!isHost);
            renderTeams(lobby);
            syncCloseWarning(lobby);

            if (dom.start) {
                dom.start.hidden = !isHost;
                const readyToStart = isLobbyReadyToStart(lobby, players.length);
                dom.start.disabled = false;
                dom.start.setAttribute('aria-disabled', String(!readyToStart));
                dom.start.classList.toggle('is-start-blocked', !readyToStart);
                dom.start.title = readyToStart ? '' : buildStartBlockedMessage(lobby, players.length);
            }
            if (dom.delete) {
                dom.delete.hidden = !isHost;
            }
            if (dom.leave) {
                dom.leave.hidden = !!isHost;
            }

            if (options.preservePlayersArea) {
                return;
            }

            if (!options.preserveTeamBoard) {
                renderPlayers(
                    players,
                    isHost,
                    currentUserId,
                    lobby.host,
                    teamState,
                    enteringTeamMode,
                    teamExitTransition,
                    teamEntryTransition
                );
            } else {
                updateRenderedTeamState(teamState);
            }
        }

        function renderPlayers(
            players,
            isHost,
            currentUserId,
            host,
            teamState,
            enteringTeamMode = false,
            transitionSnapshot = null,
            teamEntryTransition = null
        ) {
            if (!dom.players) {
                return;
            }

            if (teamState) {
                renderTeamPlayers(teamState, isHost, currentUserId, host, enteringTeamMode, teamEntryTransition);
                return;
            }

            if (!players.length) {
                dom.players.innerHTML = `<div class="lobby-empty">${t('lobby.noPlayers')}</div>`;
                return;
            }

            const helper = isHost && isBriskulaOrderReorderable(state.lobby)
                ? `<p class="team-board-helper">${t('lobbyPage.dragHint')}</p>`
                : '';
            dom.players.innerHTML = helper + players.map((player) => {
                const playerId = String(player.id);
                const owner = host && String(host.id) === playerId;
                const current = currentUserId && currentUserId === playerId;
                const canKick = isHost && !owner;
                const initial = (player.name || 'U').charAt(0).toUpperCase();
                const role = owner ? t('lobby.host') : t('common.player');
                const reorderable = isHost && isBriskulaOrderReorderable(state.lobby) ? ' player-row-reorderable' : '';

                return `
                    <div class="player-row${reorderable}" data-player-card="${player.id}">
                        <div class="player-main">
                            <div class="player-avatar">${escapeHtml(initial)}</div>
                            <div>
                                <div class="player-name">
                                    <span>${escapeHtml(player.name || t('lobby.userFallback', player.id))}</span>
                                    ${current ? `<span class="player-self-tag">${t('lobby.you')}</span>` : ''}
                                </div>
                                <div class="player-role">${escapeHtml(role)}</div>
                            </div>
                        </div>
                        ${renderKickAction(canKick, player.id)}
                    </div>
                `;
            }).join('');
            startPlayerLayoutTransition(transitionSnapshot);
        }

        function renderTeamPlayers(teamState, isHost, currentUserId, host, enteringTeamMode, transitionSnapshot = null) {
            dom.players.innerHTML = `
                <div class="team-board${enteringTeamMode ? ' is-entering' : ''}" data-team-board>
                    <div class="team-board-toolbar">
                        <p class="team-board-helper">
                            ${isHost
                                ? t('lobbyPage.dragHintTeams')
                                : t('lobbyPage.askHost')}
                        </p>
                    </div>
                    ${renderTeamPanel(teamState, 1, currentUserId)}
                    ${renderTeamPanel(teamState, 2, currentUserId)}
                </div>
            `;
            updateRenderedTeamState(teamState);
            startTeamLayoutTransition(transitionSnapshot);
        }

        function renderTeamPanel(teamState, teamNumber, currentUserId) {
            const team = teamNumber === 1 ? teamState.team1 : teamState.team2;
            const capacity = teamNumber === 1 ? teamState.team1Capacity : teamState.team2Capacity;
            const tone = resolveTeamTone(teamState, currentUserId, teamNumber);
            const cards = team.map((player, index) => renderTeamPlayerCard(player, teamNumber, tone, index, teamState)).join('');
            return `
                <section class="team-panel team-panel-${tone}" data-team-panel-shell="${teamNumber}">
                    <div class="team-panel-header">
                        <div>
                            <div class="team-panel-title">${escapeHtml(getTeamDisplayName(teamNumber))}</div>
                            <div class="team-panel-meta" data-team-panel-meta="${teamNumber}">${t('lobby.playerCount', team.length, capacity)}</div>
                        </div>
                    </div>
                    <div class="team-panel-body" data-team-panel="${teamNumber}">
                        ${cards || `<div class="team-drop-hint">${t('lobbyPage.waitingShort')}</div>`}
                    </div>
                </section>
            `;
        }

        function renderTeamPlayerCard(player, teamNumber, tone, index, teamState) {
            const playerId = String(player.id);
            const owner = state.lobby.host && String(state.lobby.host.id) === playerId;
            const current = state.currentUser?.id != null && String(state.currentUser.id) === playerId;
            const canKick = isCurrentUserHost() && !owner;
            const initial = (player.name || 'U').charAt(0).toUpperCase();
            const role = owner ? t('lobby.host') : t('common.player');
            const splitIndex = teamState.team1Capacity;
            const orderIndex = teamNumber === 1 ? index : splitIndex + index;

            return `
                <div class="player-row team-player-card team-player-card-${tone}${owner ? ' team-player-card-owner' : ''}" data-team-player-card="${player.id}" data-player-card="${player.id}" data-team-number="${teamNumber}" data-team-order-index="${orderIndex}">
                    <div class="player-main">
                        <div class="player-avatar">${escapeHtml(initial)}</div>
                        <div>
                            <div class="player-name">
                                <span>${escapeHtml(player.name || t('lobby.userFallback', player.id))}</span>
                                ${current ? `<span class="player-self-tag">${t('lobby.you')}</span>` : ''}
                            </div>
                            <div class="player-role">
                                ${escapeHtml(role)}
                            </div>
                        </div>
                    </div>
                    ${renderKickAction(canKick, player.id)}
                </div>
            `;
        }

        function renderKickAction(canKick, playerId) {
            if (canKick) {
                return `<button class="btn danger" type="button" data-kick-player="${playerId}">${t('lobbyPage.kick')}</button>`;
            }
            return '';
        }

        function sortPlayers(players, host) {
            return [...players].sort((left, right) => {
                const leftHost = host && left && String(left.id) === String(host.id);
                const rightHost = host && right && String(right.id) === String(host.id);
                if (leftHost && !rightHost) {
                    return -1;
                }
                if (!leftHost && rightHost) {
                    return 1;
                }
                return String(left?.name || '').localeCompare(String(right?.name || ''));
            });
        }

        function describeConfig(lobby) {
            if (!lobby?.gameConfig) {
                return t('lobby.config.standard');
            }

            if (typeof getGameConfigDisplayName === 'function') {
                return getGameConfigDisplayName(lobby.gameType, lobby.gameConfig);
            }

            const config = lobby.gameConfig;
            const parts = [];
            if (config.numberOfPlayers != null) {
                parts.push(t('history.playersCount', config.numberOfPlayers));
            }
            if (config.cardsInHandNum != null) {
                parts.push(t('lobbyPage.cardsEach', config.cardsInHandNum));
            }
            if (config.numberOfPlayers === 4) {
                parts.push(config.teamsEnabled ? t('lobbyPage.teamsEnabled') : t('lobbyPage.noTeams'));
            }
            return parts.join(' • ') || t('lobby.config.standard');
        }

        function isLobbyPublic(lobby) {
            return lobby?.isPublic !== false;
        }

        function setAnimatedVisibilityText(element, text) {
            if (!element || element.textContent === text) {
                return;
            }

            element.textContent = text;
            element.classList.remove('lobby-visibility-text-swap');
            element.getBoundingClientRect();
            element.classList.add('lobby-visibility-text-swap');
        }

        function syncLobbyVisibility(lobby, isHost) {
            const publicLobby = isLobbyPublic(lobby);
            setAnimatedVisibilityText(dom.visibilityValue, publicLobby ? t('createLobby.visibility.publicLobby') : t('createLobby.visibility.privateLobby'));
            setAnimatedVisibilityText(
                dom.visibilityHelp,
                publicLobby
                    ? t('lobbyPage.publicHelp')
                    : t('lobbyPage.privateHelp')
            );
            if (dom.visibilityControl) {
                dom.visibilityControl.hidden = !isHost;
            }
            if (dom.publicToggle) {
                dom.publicToggle.checked = publicLobby;
                dom.publicToggle.disabled = !isHost;
            }
            setAnimatedVisibilityText(dom.publicToggleLabel, publicLobby ? 'Public' : 'Private');
        }

        function renderConfigEditor(lobby, isHost) {
            if (!dom.configEditor || !dom.configSelect) {
                return;
            }

            const settings = getGameTypeSettings(String(lobby.gameType || '').toLowerCase());
            if (!isHost || !settings || !Object.keys(settings).length) {
                dom.configEditor.hidden = true;
                dom.configSelect.innerHTML = '';
                return;
            }

            const selectedKey = resolveGameSettingKey(lobby);
            dom.configSelect.innerHTML = '';
            for (const [key, value] of Object.entries(settings)) {
                const option = document.createElement('option');
                option.value = key;
                option.textContent = value.ui_text;
                option.selected = key === selectedKey;
                dom.configSelect.appendChild(option);
            }
            dom.configEditor.hidden = false;
        }

        function renderTeams(lobby) {
            if (!dom.teamsSection || !dom.teamsSummary) {
                return;
            }

            const teamState = resolveTeams(lobby);
            dom.teamsSection.hidden = !teamState;
            if (teamState) {
                dom.teamsSummary.textContent = formatTeamsSummary(teamState);
            }
            if (dom.randomize) {
                dom.randomize.hidden = !(isBriskulaOrderReorderable(lobby) && isCurrentUserHost());
            }
        }

        function resolveGameSettingKey(lobby) {
            return typeof resolveLobbyGameSettingKey === 'function' ? resolveLobbyGameSettingKey(lobby) : '';
        }

        async function updateLobbyConfiguration(settingKey) {
            const gameTypeKey = String(state.lobby.gameType || '').toLowerCase();
            if (!supportsLobbyCreation(gameTypeKey, settingKey)) {
                throw new Error('Unsupported game configuration');
            }

            const teamModeActive = !!resolveTeams(state.lobby);
            const teamModeNext = settingKey === 'p4teams';
            if (teamModeActive && !teamModeNext) {
                state.pendingPlayerAreaTransition = {
                    kind: 'exit-team',
                    ...captureTeamModeTransitionSnapshot()
                };
            } else if (!teamModeActive && teamModeNext) {
                state.pendingPlayerAreaTransition = {
                    kind: 'enter-team',
                    cardRects: capturePlayerCardRects()
                };
            } else {
                state.pendingPlayerAreaTransition = null;
            }

            const updatedLobby = JSON.parse(buildLobbyCreatePayload(gameTypeKey, settingKey, state.lobby.name));
            updatedLobby.id = state.lobby.id;
            updatedLobby.name = state.lobby.name;
            updatedLobby.players = state.lobby.players;
            updatedLobby.host = state.lobby.host;
            updatedLobby.isPublic = isLobbyPublic(state.lobby);
            if (updatedLobby.gameConfig) {
                updatedLobby.gameConfig.orderedUsers = resolveOrderedLobbyPlayers(state.lobby);
            }

            const response = await fetch('/api/lobby/update', {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                credentials: 'include',
                body: JSON.stringify(updatedLobby)
            });

            if (!response.ok) {
                throw new Error('Failed to update lobby');
            }

            const lobby = await response.json();
            if (lobby) {
                const previousLobby = previousLobbySnapshot;
                state.lobby = lobby;
                previousLobbySnapshot = lobby;
                renderLobby(state.lobby, buildPlayerAreaRenderOptions(previousLobby, lobby));
            }
        }

        async function updateLobbyVisibility(nextPublic) {
            const response = await fetch('/api/lobby/update', {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                credentials: 'include',
                body: JSON.stringify({
                    ...state.lobby,
                    isPublic: nextPublic
                })
            });

            if (!response.ok) {
                throw new Error('Failed to update lobby visibility');
            }

            const lobby = await response.json();
            if (lobby) {
                const previousLobby = previousLobbySnapshot;
                state.lobby = lobby;
                previousLobbySnapshot = lobby;
                renderLobby(state.lobby, buildPlayerAreaRenderOptions(previousLobby, lobby));
            }
        }

        async function randomizeLobbyOrder() {
            const teamState = resolveTeams(state.lobby);
            const rollbackLobby = JSON.parse(JSON.stringify(state.lobby));
            const orderedPlayers = teamState
                ? getOrderedTeamPlayers(teamState)
                : resolveOrderedLobbyPlayers(state.lobby);
            const shuffledIds = orderedPlayers.map((player) => String(player.id));
            for (let index = shuffledIds.length - 1; index > 0; index -= 1) {
                const randomIndex = Math.floor(Math.random() * (index + 1));
                [shuffledIds[index], shuffledIds[randomIndex]] = [shuffledIds[randomIndex], shuffledIds[index]];
            }

            applyOrderedPlayersToLobbyState(shuffledIds);
            renderLobby(state.lobby);
            try {
                await saveLobbyPlayerOrder(shuffledIds, { preserveTeamBoard: false });
            } catch (error) {
                state.lobby = rollbackLobby;
                previousLobbySnapshot = rollbackLobby;
                renderLobby(state.lobby);
                throw error;
            }
        }

        async function saveLobbyPlayerOrder(orderedPlayerIds, options = {}) {
            const currentOrderedPlayers = resolveOrderedLobbyPlayers(state.lobby);
            const playersById = new Map(currentOrderedPlayers.map((player) => [String(player.id), player]));
            const orderedPlayers = orderedPlayerIds
                .map((playerId) => playersById.get(String(playerId)))
                .filter(Boolean);

            const response = await fetch('/api/lobby/update', {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                credentials: 'include',
                body: JSON.stringify({
                    ...state.lobby,
                    gameConfig: {
                        ...state.lobby.gameConfig,
                        orderedUsers: orderedPlayers
                    }
                })
            });

            if (!response.ok) {
                throw new Error('Failed to update lobby teams');
            }

            const lobby = await response.json();
            if (lobby) {
                state.lobby = lobby;
                previousLobbySnapshot = lobby;
                renderLobby(state.lobby, { preserveTeamBoard: !!options.preserveTeamBoard });
            }
        }

        function applyOrderedPlayersToLobbyState(orderedPlayerIds) {
            const orderedLobbyPlayers = resolveOrderedLobbyPlayers(state.lobby);
            if (!orderedLobbyPlayers.length) {
                return;
            }

            const playersById = new Map(orderedLobbyPlayers.map((player) => [String(player.id), player]));
            const orderedPlayers = orderedPlayerIds
                .map((playerId) => playersById.get(String(playerId)))
                .filter(Boolean);

            state.lobby = {
                ...state.lobby,
                gameConfig: {
                    ...state.lobby.gameConfig,
                    orderedUsers: orderedPlayers
                }
            };
            renderTeams(state.lobby);
        }

        function updateRenderedTeamState(teamState = resolveTeams(state.lobby)) {
            if (!teamState || !dom.players) {
                return;
            }

            syncTeamPanel(teamState, 1);
            syncTeamPanel(teamState, 2);
            renderTeams(state.lobby);
        }

        function syncTeamPanel(teamState, teamNumber) {
            const shell = dom.players.querySelector(`[data-team-panel-shell="${teamNumber}"]`);
            const body = dom.players.querySelector(`[data-team-panel="${teamNumber}"]`);
            const meta = dom.players.querySelector(`[data-team-panel-meta="${teamNumber}"]`);
            if (!shell || !body || !meta) {
                return;
            }

            const currentUserId = state.currentUser?.id != null ? String(state.currentUser.id) : null;
            const tone = resolveTeamTone(teamState, currentUserId, teamNumber);
            const count = teamNumber === 1 ? teamState.team1.length : teamState.team2.length;
            const capacity = teamNumber === 1 ? teamState.team1Capacity : teamState.team2Capacity;

            shell.classList.remove('team-panel-ally', 'team-panel-enemy', 'team-panel-neutral');
            shell.classList.add(`team-panel-${tone}`);
            meta.textContent = t('lobby.playerCount', count, capacity);
            const badge = shell.querySelector('.team-panel-badge');
            if (badge) {
                badge.textContent = tone === 'ally' ? t('lobbyPage.yourSide') : (tone === 'enemy' ? t('lobbyPage.opposition') : t('lobbyPage.team'));
            }

            body.querySelectorAll('[data-team-player-card]').forEach((card, index) => {
                card.dataset.teamNumber = String(teamNumber);
                card.dataset.teamOrderIndex = String(teamNumber === 1 ? index : teamState.team1Capacity + index);
                card.classList.remove('team-player-card-ally', 'team-player-card-enemy', 'team-player-card-neutral');
                card.classList.add(`team-player-card-${tone}`);
            });
        }

        function resolveTeams(lobby) {
            const config = lobby?.gameConfig;
            if (!lobby || !['Briskula', 'Treseta'].includes(lobby.gameType) || !config || config.numberOfPlayers !== 4 || !config.teamsEnabled) {
                return null;
            }

            const players = resolveOrderedLobbyPlayers(lobby);
            const teamSize = 2;
            const team1Capacity = teamSize;
            const team2Capacity = teamSize;
            const team1 = players.slice(0, team1Capacity);
            const team2 = players.slice(team1Capacity, team1Capacity + team2Capacity);

            return {
                players,
                team1,
                team2,
                team1Capacity,
                team2Capacity,
                totalCapacity: team1Capacity + team2Capacity,
                orderedPlayers: [...team1, ...team2]
            };
        }

        function resolveOrderedLobbyPlayers(lobby) {
            const players = sortPlayers(Array.isArray(lobby?.players) ? lobby.players : [], lobby?.host);
            if (!lobby || !['Briskula', 'Treseta'].includes(lobby.gameType) || !lobby.gameConfig) {
                return players;
            }

            const playersById = new Map(players.map((player) => [String(player.id), player]));
            const ordered = [];
            for (const candidate of lobby.gameConfig.orderedUsers || []) {
                const player = playersById.get(String(candidate?.id));
                if (player && !ordered.some((existing) => String(existing.id) === String(player.id))) {
                    ordered.push(player);
                }
            }

            for (const player of players) {
                if (!ordered.some((existing) => String(existing.id) === String(player.id))) {
                    ordered.push(player);
                }
            }

            return ordered;
        }

        function getPlayerTeamNumber(teamState, playerId) {
            if (teamState.team1.some((player) => String(player.id) === String(playerId))) {
                return 1;
            }
            if (teamState.team2.some((player) => String(player.id) === String(playerId))) {
                return 2;
            }
            return 0;
        }

        function resolveTeamTone(teamState, currentUserId, teamNumber) {
            if (!currentUserId) {
                return 'neutral';
            }

            const currentUserTeam = getPlayerTeamNumber(teamState, currentUserId);
            if (!currentUserTeam) {
                return 'neutral';
            }
            return currentUserTeam === teamNumber ? 'ally' : 'enemy';
        }

        function getOrderedTeamPlayers(teamState) {
            return [...teamState.orderedPlayers];
        }

        function isBriskulaOrderReorderable(lobby) {
            return !!(lobby && ['Briskula', 'Treseta'].includes(lobby.gameType) && lobby.gameConfig);
        }

        function startTeamDrag(event, card, teamState) {
            cancelTeamDrag();

            const rect = card.getBoundingClientRect();
            const containerRect = dom.players?.getBoundingClientRect();
            const proxy = card.cloneNode(true);
            proxy.classList.add('team-drag-proxy');
            proxy.style.width = `${rect.width}px`;
            proxy.style.height = `${rect.height}px`;
            document.body.appendChild(proxy);

            card.classList.add('is-source-hidden');
            state.dragSession = {
                pointerId: event.pointerId,
                playerId: card.dataset.teamPlayerCard,
                sourceCard: card,
                proxy,
                splitIndex: teamState.team1Capacity,
                previewOrderIds: getOrderedTeamPlayers(teamState).map((player) => String(player.id)),
                startSignature: buildLobbyTeamSignature(state.lobby),
                previewSignature: buildLobbyTeamSignature(state.lobby),
                pendingDragPoint: null,
                moveFrame: null,
                offsetX: event.clientX - rect.left,
                offsetY: event.clientY - rect.top,
                containerBounds: containerRect ? {
                    left: containerRect.left,
                    top: containerRect.top,
                    right: containerRect.right,
                    bottom: containerRect.bottom
                } : null,
                proxyWidth: rect.width,
                proxyHeight: rect.height
            };

            positionTeamDragProxy(event.clientX, event.clientY);
            document.body.classList.add('is-team-dragging');
        }

        function startPlayerListDrag(event, card, orderedPlayers) {
            cancelTeamDrag();

            const rect = card.getBoundingClientRect();
            const containerRect = dom.players?.getBoundingClientRect();
            const proxy = card.cloneNode(true);
            proxy.classList.add('team-drag-proxy');
            proxy.style.width = `${rect.width}px`;
            proxy.style.height = `${rect.height}px`;
            document.body.appendChild(proxy);

            card.classList.add('is-source-hidden');
            state.dragSession = {
                mode: 'list',
                pointerId: event.pointerId,
                playerId: card.dataset.playerCard,
                sourceCard: card,
                proxy,
                previewOrderIds: orderedPlayers.map((player) => String(player.id)),
                startSignature: buildLobbyOrderSignature(state.lobby),
                previewSignature: buildLobbyOrderSignature(state.lobby),
                pendingDragPoint: null,
                moveFrame: null,
                offsetX: event.clientX - rect.left,
                offsetY: event.clientY - rect.top,
                containerBounds: containerRect ? {
                    left: containerRect.left,
                    top: containerRect.top,
                    right: containerRect.right,
                    bottom: containerRect.bottom
                } : null,
                proxyWidth: rect.width,
                proxyHeight: rect.height
            };

            positionTeamDragProxy(event.clientX, event.clientY);
            document.body.classList.add('is-team-dragging');
        }

        function handleTeamDragMove(event) {
            if (!state.dragSession || state.dragSession.pointerId !== event.pointerId) {
                return;
            }

            const dragPoint = resolveConstrainedDragPoint(event.clientX, event.clientY);
            state.dragSession.pendingDragPoint = dragPoint;
            if (state.dragSession.moveFrame) {
                return;
            }

            state.dragSession.moveFrame = window.requestAnimationFrame(processDragMoveFrame);
        }

        function processDragMoveFrame() {
            const dragSession = state.dragSession;
            if (!dragSession) {
                return;
            }

            dragSession.moveFrame = null;
            const dragPoint = dragSession.pendingDragPoint;
            dragSession.pendingDragPoint = null;
            if (!dragPoint) {
                return;
            }

            positionTeamDragProxy(dragPoint.x, dragPoint.y);
            if (dragSession.mode === 'list') {
                handlePlayerListDragMove(dragPoint.x, dragPoint.y);
                return;
            }

            handleTeamBoardDragMove(dragPoint.x, dragPoint.y);
        }

        function handleTeamBoardDragMove(clientX, clientY) {
            const insertionIndex = resolvePreviewInsertionIndex(clientX, clientY);
            if (insertionIndex == null) {
                return;
            }

            const nextOrderIds = buildPreviewOrder(
                state.dragSession.previewOrderIds,
                state.dragSession.playerId,
                insertionIndex
            );
            const nextSignature = buildTeamBoardSignature(nextOrderIds, state.dragSession.splitIndex);
            if (nextSignature === state.dragSession.previewSignature) {
                return;
            }

            state.dragSession.previewOrderIds = nextOrderIds;
            state.dragSession.previewSignature = nextSignature;
            applyOrderedPlayersToTeamBoard(nextOrderIds, state.dragSession.splitIndex, {
                animate: true,
                skipPlayerId: state.dragSession.playerId
            });
        }

        function handlePlayerListDragMove(clientX, clientY) {
            const insertionIndex = resolvePlayerListInsertionIndex(clientY);
            if (insertionIndex == null) {
                return;
            }

            const nextOrderIds = buildPreviewOrder(
                state.dragSession.previewOrderIds,
                state.dragSession.playerId,
                insertionIndex
            );
            const nextSignature = buildPlayerListSignature(nextOrderIds);
            if (nextSignature === state.dragSession.previewSignature) {
                return;
            }

            state.dragSession.previewOrderIds = nextOrderIds;
            state.dragSession.previewSignature = nextSignature;
            applyOrderedPlayersToPlayerList(nextOrderIds, {
                animate: true,
                skipPlayerId: state.dragSession.playerId
            });
        }

        async function handleTeamDragEnd() {
            if (!state.dragSession) {
                return;
            }

            flushPendingDragMove();
            if (state.dragSession.mode === 'list') {
                await handlePlayerListDragEnd();
                return;
            }

            const dragSession = state.dragSession;
            const finalOrderIds = [...dragSession.previewOrderIds];
            finishTeamDragVisuals();

            const nextSignature = buildTeamBoardSignature(finalOrderIds, dragSession.splitIndex);
            if (nextSignature === dragSession.startSignature) {
                state.dragSession = null;
                return;
            }

            const rollbackLobby = JSON.parse(JSON.stringify(state.lobby));
            applyOrderedPlayersToLobbyState(finalOrderIds);
            state.dragSession = null;

            try {
                await saveLobbyPlayerOrder(finalOrderIds, { preserveTeamBoard: true });
            } catch (error) {
                state.lobby = rollbackLobby;
                previousLobbySnapshot = rollbackLobby;
                renderLobby(state.lobby);
                showToast(t('lobbyPage.toast.updateFailed.title'), t('lobbyPage.toast.updateFailed.teams'));
            }
        }

        async function handlePlayerListDragEnd() {
            const dragSession = state.dragSession;
            const finalOrderIds = [...dragSession.previewOrderIds];
            finishTeamDragVisuals();

            const nextSignature = buildPlayerListSignature(finalOrderIds);
            if (nextSignature === dragSession.startSignature) {
                state.dragSession = null;
                return;
            }

            const rollbackLobby = JSON.parse(JSON.stringify(state.lobby));
            applyOrderedPlayersToLobbyState(finalOrderIds);
            state.dragSession = null;

            try {
                await saveLobbyPlayerOrder(finalOrderIds);
            } catch (error) {
                state.lobby = rollbackLobby;
                previousLobbySnapshot = rollbackLobby;
                renderLobby(state.lobby);
                showToast(t('lobbyPage.toast.updateFailed.title'), t('lobbyPage.toast.updateFailed.order'));
            }
        }

        function cancelTeamDrag() {
            if (!state.dragSession) {
                return;
            }

            finishTeamDragVisuals();
            state.dragSession = null;
            renderLobby(state.lobby);
        }

        function finishTeamDragVisuals() {
            if (!state.dragSession) {
                return;
            }

            if (state.dragSession.moveFrame) {
                window.cancelAnimationFrame(state.dragSession.moveFrame);
                state.dragSession.moveFrame = null;
            }
            state.dragSession.sourceCard?.classList.remove('is-source-hidden');
            state.dragSession.proxy?.remove();
            document.body.classList.remove('is-team-dragging');
        }

        function flushPendingDragMove() {
            const dragSession = state.dragSession;
            if (!dragSession) {
                return;
            }

            if (dragSession.moveFrame) {
                window.cancelAnimationFrame(dragSession.moveFrame);
                dragSession.moveFrame = null;
            }
            processDragMoveFrame();
        }

        function positionTeamDragProxy(clientX, clientY) {
            if (!state.dragSession?.proxy) {
                return;
            }

            const { left, top } = resolveConstrainedProxyPosition(clientX, clientY);
            state.dragSession.proxy.style.transform = `translate3d(${left}px, ${top}px, 0)`;
        }

        function resolvePreviewInsertionIndex(clientX, clientY) {
            const panels = [...(dom.players?.querySelectorAll('[data-team-panel]') || [])]
                .map((panel) => ({
                    panel,
                    rect: panel.getBoundingClientRect()
                }))
                .sort((left, right) => left.rect.top - right.rect.top);
            if (!panels.length || !state.dragSession) {
                return null;
            }

            let targetPanel = panels.find(({ rect }) => pointInRect(clientX, clientY, rect));
            if (!targetPanel) {
                targetPanel = resolvePanelFromGap(panels, clientY);
            }
            if (!targetPanel) {
                targetPanel = panels.reduce((closest, candidate) => {
                    if (!closest) {
                        return candidate;
                    }
                    const currentDistance = distanceToRect(clientX, clientY, candidate.rect);
                    const closestDistance = distanceToRect(clientX, clientY, closest.rect);
                    return currentDistance < closestDistance ? candidate : closest;
                }, null);
            }

            if (!targetPanel) {
                return null;
            }

            const teamNumber = Number(targetPanel.panel.dataset.teamPanel);
            const cards = [...targetPanel.panel.querySelectorAll('[data-team-player-card]')]
                .filter((card) => card.dataset.teamPlayerCard !== state.dragSession.playerId);
            const localIndex = resolveTeamPanelInsertionIndex(cards, clientX, clientY);

            if (teamNumber === 1) {
                return Math.max(0, Math.min(localIndex, state.dragSession.splitIndex));
            }

            return Math.max(
                state.dragSession.splitIndex,
                Math.min(state.dragSession.splitIndex + localIndex, state.dragSession.previewOrderIds.length - 1)
            );
        }

        function resolvePlayerListInsertionIndex(clientY) {
            const cards = [...(dom.players?.querySelectorAll('[data-player-card]') || [])]
                .filter((card) => card.dataset.playerCard !== state.dragSession?.playerId);
            if (!cards.length) {
                return 0;
            }

            for (let index = 0; index < cards.length; index += 1) {
                const rect = cards[index].getBoundingClientRect();
                if (clientY <= rect.top + rect.height / 2) {
                    return index;
                }
            }

            return cards.length;
        }

        function resolveTeamPanelInsertionIndex(cards, clientX, clientY) {
            if (!cards.length) {
                return 0;
            }

            const rows = [];
            cards.forEach((card) => {
                const rect = card.getBoundingClientRect();
                let row = rows.find((candidate) => Math.abs(candidate.top - rect.top) < 12);
                if (!row) {
                    row = {
                        top: rect.top,
                        bottom: rect.bottom,
                        cards: []
                    };
                    rows.push(row);
                }
                row.bottom = Math.max(row.bottom, rect.bottom);
                row.cards.push({ card, rect });
            });

            rows.sort((left, right) => left.top - right.top);
            rows.forEach((row) => {
                row.cards.sort((left, right) => left.rect.left - right.rect.left);
            });

            let offset = 0;
            for (let index = 0; index < rows.length; index += 1) {
                const row = rows[index];
                const nextRow = rows[index + 1];
                const boundary = nextRow
                    ? row.bottom + Math.max(0, nextRow.top - row.bottom) / 2
                    : row.bottom;

                if (clientY <= boundary) {
                    for (let cardIndex = 0; cardIndex < row.cards.length; cardIndex += 1) {
                        const { rect } = row.cards[cardIndex];
                        if (clientX <= rect.left + rect.width / 2) {
                            return offset + cardIndex;
                        }
                    }
                    return offset + row.cards.length;
                }

                offset += row.cards.length;
            }

            return cards.length;
        }

        function resolveConstrainedDragPoint(clientX, clientY) {
            const bounds = state.dragSession?.containerBounds;
            if (!bounds) {
                return { x: clientX, y: clientY };
            }

            return {
                x: clamp(clientX, bounds.left, bounds.right),
                y: clamp(clientY, bounds.top, bounds.bottom)
            };
        }

        function resolveConstrainedProxyPosition(clientX, clientY) {
            const dragSession = state.dragSession;
            const preferredLeft = clientX - dragSession.offsetX;
            const preferredTop = clientY - dragSession.offsetY;
            const bounds = dragSession.containerBounds;
            if (!bounds) {
                return {
                    left: preferredLeft,
                    top: preferredTop
                };
            }

            const minLeft = bounds.left;
            const maxLeft = Math.max(bounds.left, bounds.right - dragSession.proxyWidth);
            const minTop = bounds.top;
            const maxTop = Math.max(bounds.top, bounds.bottom - dragSession.proxyHeight);

            return {
                left: clamp(preferredLeft, minLeft, maxLeft),
                top: clamp(preferredTop, minTop, maxTop)
            };
        }

        function resolvePanelFromGap(panels, clientY) {
            for (let index = 0; index < panels.length - 1; index += 1) {
                const current = panels[index];
                const next = panels[index + 1];
                if (clientY >= current.rect.bottom && clientY <= next.rect.top) {
                    const midpoint = current.rect.bottom + (next.rect.top - current.rect.bottom) / 2;
                    return clientY <= midpoint ? current : next;
                }
            }
            return null;
        }

        function buildPreviewOrder(currentOrderIds, playerId, insertionIndex) {
            const remainingIds = currentOrderIds.filter((candidate) => candidate !== playerId);
            remainingIds.splice(Math.max(0, Math.min(insertionIndex, remainingIds.length)), 0, playerId);
            return remainingIds;
        }

        function applyOrderedPlayersToTeamBoard(orderedPlayerIds, splitIndex, options = {}) {
            const team1Body = dom.players?.querySelector('[data-team-panel="1"]');
            const team2Body = dom.players?.querySelector('[data-team-panel="2"]');
            if (!team1Body || !team2Body) {
                return;
            }

            const previousRects = options.animate ? captureTeamCardRects() : null;
            const cardsById = new Map(
                [...dom.players.querySelectorAll('[data-team-player-card]')].map((card) => [card.dataset.teamPlayerCard, card])
            );

            for (const playerId of orderedPlayerIds.slice(0, splitIndex)) {
                const card = cardsById.get(String(playerId));
                if (card) {
                    team1Body.appendChild(card);
                }
            }

            for (const playerId of orderedPlayerIds.slice(splitIndex)) {
                const card = cardsById.get(String(playerId));
                if (card) {
                    team2Body.appendChild(card);
                }
            }

            updateRenderedTeamState();
            if (previousRects) {
                animateTeamCardsFromRects(previousRects, options.skipPlayerId);
            }
        }

        function applyOrderedPlayersToPlayerList(orderedPlayerIds, options = {}) {
            if (!dom.players) {
                return;
            }

            const previousRects = options.animate ? capturePlayerCardRects() : null;
            const cardsById = new Map(
                [...dom.players.querySelectorAll('[data-player-card]')].map((card) => [card.dataset.playerCard, card])
            );

            for (const playerId of orderedPlayerIds) {
                const card = cardsById.get(String(playerId));
                if (card) {
                    dom.players.appendChild(card);
                }
            }

            if (previousRects) {
                animatePlayerCardsFromRects(previousRects);
            }
        }

        function buildPlayerListSignature(orderedPlayersOrIds) {
            return orderedPlayersOrIds
                .map((player) => typeof player === 'object' ? String(player.id) : String(player))
                .join(',');
        }

        function buildLobbyOrderSignature(lobby) {
            return buildPlayerListSignature(resolveOrderedLobbyPlayers(lobby));
        }

        function captureTeamBoardSignature() {
            const team1Cards = dom.players?.querySelectorAll('[data-team-panel="1"] [data-team-player-card]');
            const team2Cards = dom.players?.querySelectorAll('[data-team-panel="2"] [data-team-player-card]');
            if (!team1Cards || !team2Cards) {
                return null;
            }

            return `${[...team1Cards].map((card) => card.dataset.teamPlayerCard).join(',')}|${[...team2Cards]
                .map((card) => card.dataset.teamPlayerCard)
                .join(',')}`;
        }

        function buildTeamBoardSignature(orderedPlayersOrIds, splitIndex) {
            const orderedIds = orderedPlayersOrIds.map((player) => typeof player === 'object' ? String(player.id) : String(player));
            return `${orderedIds
                .slice(0, splitIndex)
                .join(',')}|${orderedIds
                .slice(splitIndex)
                .join(',')}`;
        }

        function buildLobbyTeamSignature(lobby) {
            const teamState = resolveTeams(lobby);
            if (!teamState) {
                return null;
            }
            return buildTeamBoardSignature(
                teamState.orderedPlayers,
                teamState.team1Capacity
            );
        }

        function buildPlayerAreaRenderOptions(previousLobby, nextLobby) {
            const options = {};

            if (state.pendingPlayerAreaTransition) {
                if (state.pendingPlayerAreaTransition.kind === 'exit-team' && isTeamExitTransition(previousLobby, nextLobby)) {
                    options.playerAreaTransitionSnapshot = state.pendingPlayerAreaTransition;
                    state.pendingPlayerAreaTransition = null;
                    state.preservePlayersAreaUntil = Date.now() + 420;
                    return options;
                }

                if (state.pendingPlayerAreaTransition.kind === 'enter-team' && isTeamEntryTransition(previousLobby, nextLobby)) {
                    options.playerAreaEntrySnapshot = state.pendingPlayerAreaTransition.cardRects;
                    state.pendingPlayerAreaTransition = null;
                    state.preservePlayersAreaUntil = Date.now() + 420;
                    return options;
                }
            }

            if (Date.now() < state.preservePlayersAreaUntil && buildPlayerAreaSignature(previousLobby) === buildPlayerAreaSignature(nextLobby)) {
                options.preservePlayersArea = true;
            }

            return options;
        }

        function isTeamExitTransition(previousLobby, nextLobby) {
            return !!resolveTeams(previousLobby) && !resolveTeams(nextLobby);
        }

        function isTeamEntryTransition(previousLobby, nextLobby) {
            return !resolveTeams(previousLobby) && !!resolveTeams(nextLobby);
        }

        function buildPlayerAreaSignature(lobby) {
            if (!lobby) {
                return 'none';
            }

            const teamSignature = buildLobbyTeamSignature(lobby);
            if (teamSignature) {
                return `teams:${teamSignature}`;
            }

            const players = resolveOrderedLobbyPlayers(lobby);
            return `list:${players.map((player) => player.id).join(',')}`;
        }

        function shouldPreserveTeamBoard(nextLobby) {
            if (!isCurrentUserHost()) {
                return false;
            }

            const domSignature = captureTeamBoardSignature();
            const nextSignature = buildLobbyTeamSignature(nextLobby);
            return !!domSignature && !!nextSignature && domSignature === nextSignature;
        }

        function shouldAnimateObservedTeams(previousLobby, nextLobby) {
            if (isCurrentUserHost()) {
                return false;
            }
            return buildLobbyTeamSignature(previousLobby) !== buildLobbyTeamSignature(nextLobby);
        }

        function animateObservedTeamBoard(previousRects) {
            const board = dom.players?.querySelector('.team-board');
            if (!board) {
                return;
            }

            animateTeamCardsFromRects(previousRects);
            board.classList.remove('is-observer-update');
            void board.offsetWidth;
            board.classList.add('is-observer-update');
            window.setTimeout(() => {
                board.classList.remove('is-observer-update');
            }, 320);
        }

        function captureTeamCardRects() {
            const cards = dom.players?.querySelectorAll('[data-team-player-card]');
            if (!cards?.length) {
                return null;
            }

            const rects = new Map();
            cards.forEach((card) => {
                rects.set(card.dataset.teamPlayerCard, card.getBoundingClientRect());
            });
            return rects;
        }

        function captureTeamModeTransitionSnapshot() {
            const board = dom.players?.querySelector('[data-team-board]');
            const container = dom.players;
            const cardRects = capturePlayerCardRects();
            if (!board || !container || !cardRects) {
                return null;
            }

            const boardRect = board.getBoundingClientRect();
            const containerRect = container.getBoundingClientRect();
            const boardClone = board.cloneNode(true);
            boardClone.classList.remove('is-entering', 'is-observer-update');
            boardClone.classList.add('players-transition-overlay');

            return {
                boardClone,
                cardRects,
                frame: {
                    left: boardRect.left - containerRect.left + container.scrollLeft,
                    top: boardRect.top - containerRect.top + container.scrollTop,
                    width: boardRect.width,
                    height: boardRect.height
                }
            };
        }

        function capturePlayerCardRects() {
            const cards = dom.players?.querySelectorAll('[data-player-card]');
            if (!cards?.length) {
                return null;
            }

            const rects = new Map();
            cards.forEach((card) => {
                rects.set(card.dataset.playerCard, card.getBoundingClientRect());
            });
            return rects;
        }

        function animateTeamCardsFromRects(previousRects, skipPlayerId = null) {
            if (!previousRects) {
                return;
            }

            const cards = dom.players?.querySelectorAll('[data-team-player-card]');
            if (!cards?.length) {
                return;
            }

            cards.forEach((card) => {
                if (skipPlayerId && card.dataset.teamPlayerCard === String(skipPlayerId)) {
                    return;
                }

                const previousRect = previousRects.get(card.dataset.teamPlayerCard);
                if (!previousRect) {
                    return;
                }

                const nextRect = card.getBoundingClientRect();
                const deltaX = previousRect.left - nextRect.left;
                const deltaY = previousRect.top - nextRect.top;
                if (Math.abs(deltaX) < 1 && Math.abs(deltaY) < 1) {
                    return;
                }

                card.style.transition = 'none';
                card.style.transform = `translate3d(${deltaX}px, ${deltaY}px, 0)`;
                window.requestAnimationFrame(() => {
                    card.style.transition = 'transform 220ms cubic-bezier(0.22, 1, 0.36, 1)';
                    card.style.transform = '';
                });
                window.setTimeout(() => {
                    card.style.transition = '';
                }, 240);
            });
        }

        function animatePlayerCardsFromRects(previousRects) {
            if (!previousRects) {
                return;
            }

            const cards = dom.players?.querySelectorAll('[data-player-card]');
            if (!cards?.length) {
                return;
            }

            cards.forEach((card) => {
                const previousRect = previousRects.get(card.dataset.playerCard);
                if (!previousRect) {
                    card.animate([
                        { opacity: 0, transform: 'translateY(10px) scale(0.98)' },
                        { opacity: 1, transform: 'translateY(0) scale(1)' }
                    ], {
                        duration: 260,
                        easing: 'cubic-bezier(0.22, 1, 0.36, 1)'
                    });
                    return;
                }

                const nextRect = card.getBoundingClientRect();
                const deltaX = previousRect.left - nextRect.left;
                const deltaY = previousRect.top - nextRect.top;
                card.style.transition = 'none';
                card.style.transform = `translate3d(${deltaX}px, ${deltaY}px, 0)`;
                card.style.opacity = '0.82';
                window.requestAnimationFrame(() => {
                    card.style.transition = 'transform 280ms cubic-bezier(0.22, 1, 0.36, 1), opacity 280ms ease';
                    card.style.transform = '';
                    card.style.opacity = '';
                });
                window.setTimeout(() => {
                    card.style.transition = '';
                }, 300);
            });

        }

        function startPlayerLayoutTransition(transitionSnapshot) {
            if (!dom.players) {
                return;
            }

            dom.players.querySelectorAll('.players-transition-overlay').forEach((overlay) => overlay.remove());

            if (!transitionSnapshot) {
                dom.players.classList.remove('is-config-transition');
                return;
            }

            dom.players.classList.add('is-config-transition');

            const { boardClone, cardRects, frame } = transitionSnapshot;
            boardClone.style.left = `${frame.left}px`;
            boardClone.style.top = `${frame.top}px`;
            boardClone.style.width = `${frame.width}px`;
            boardClone.style.height = `${frame.height}px`;
            dom.players.appendChild(boardClone);

            window.requestAnimationFrame(() => {
                animatePlayerCardsFromRects(cardRects);
                boardClone.animate([
                    {
                        opacity: 1,
                        transform: 'translateY(0) scale(1)',
                        filter: 'blur(0)',
                        clipPath: 'inset(0 0 0 0 round 1.25rem)'
                    },
                    {
                        opacity: 0,
                        transform: 'translateY(-10px) scale(0.985)',
                        filter: 'blur(5px)',
                        clipPath: 'inset(12% 0 0 0 round 1.25rem)'
                    }
                ], {
                    duration: 320,
                    easing: 'cubic-bezier(0.22, 1, 0.36, 1)',
                    fill: 'forwards'
                });
            });

            window.setTimeout(() => {
                boardClone.remove();
                dom.players?.classList.remove('is-config-transition');
            }, 360);
        }

        function startTeamLayoutTransition(previousRects) {
            if (!dom.players) {
                return;
            }

            if (!previousRects) {
                return;
            }

            dom.players.classList.add('is-config-transition');
            window.requestAnimationFrame(() => {
                animateTeamCardsFromRects(previousRects);
            });

            window.setTimeout(() => {
                dom.players?.classList.remove('is-config-transition');
            }, 320);
        }

        function pointInRect(clientX, clientY, rect) {
            return clientX >= rect.left && clientX <= rect.right && clientY >= rect.top && clientY <= rect.bottom;
        }

        function distanceToRect(clientX, clientY, rect) {
            const dx = clientX < rect.left ? rect.left - clientX : (clientX > rect.right ? clientX - rect.right : 0);
            const dy = clientY < rect.top ? rect.top - clientY : (clientY > rect.bottom ? clientY - rect.bottom : 0);
            return Math.hypot(dx, dy);
        }

        function clamp(value, min, max) {
            return Math.min(Math.max(value, min), max);
        }

        function getTeamDisplayName(teamNumber) {
            return teamNumber === 1 ? t('lobbyPage.team1') : t('lobbyPage.team2');
        }

        function formatTeamsSummary(teamState) {
            return `${getTeamDisplayName(1)}: ${formatTeamNames(teamState.team1)} • ${getTeamDisplayName(2)}: ${formatTeamNames(teamState.team2)}`;
        }

        function formatTeamNames(team) {
            if (!team.length) {
                return t('lobbyPage.waitingShort');
            }
            return team.map((player) => player.name || t('lobby.userFallback', player.id)).join(', ');
        }

        function isCurrentUserHost() {
            return !!(state.currentUser && state.lobby?.host && String(state.currentUser.id) === String(state.lobby.host.id));
        }

        function resolveLobbyCapacity(lobby) {
            const configuredPlayers = Number(lobby?.gameConfig?.numberOfPlayers);
            if (Number.isFinite(configuredPlayers) && configuredPlayers > 0) {
                return configuredPlayers;
            }

            const maxPlayers = Number(lobby?.maxPlayers);
            if (Number.isFinite(maxPlayers) && maxPlayers > 0) {
                return maxPlayers;
            }

            const minPlayers = Number(lobby?.minPlayers);
            return Number.isFinite(minPlayers) && minPlayers > 0 ? minPlayers : 0;
        }

        function formatLobbyPlayerCounter(lobby, playerCount) {
            const teamState = resolveTeams(lobby);
            if (teamState) {
                return t('lobby.playerCount', teamState.team1.length + teamState.team2.length, teamState.totalCapacity);
            }

            const capacity = resolveLobbyCapacity(lobby);
            return t('lobby.playerCount', playerCount, capacity || playerCount);
        }

        function isLobbyReadyToStart(lobby, playerCount = resolveOrderedLobbyPlayers(lobby).length) {
            const requiredPlayers = Number(lobby?.minPlayers || resolveLobbyCapacity(lobby));
            return Number.isFinite(requiredPlayers) && requiredPlayers > 0 && playerCount >= requiredPlayers;
        }

        function buildStartBlockedMessage(lobby, playerCount = resolveOrderedLobbyPlayers(lobby).length) {
            const requiredPlayers = Number(lobby?.minPlayers || resolveLobbyCapacity(lobby));
            const missingPlayers = Math.max((Number.isFinite(requiredPlayers) ? requiredPlayers : 0) - playerCount, 0);
            if (missingPlayers > 0) {
                return missingPlayers === 1 ? t('lobbyPage.waitingMoreOne', missingPlayers) : t('lobbyPage.waitingMoreMany', missingPlayers);
            }
            return t('lobbyPage.notReadyYet');
        }

        function buildLobbyStatus(lobby, playerCount) {
            if (!isLobbyReadyToStart(lobby, playerCount)) {
                return buildStartBlockedMessage(lobby, playerCount);
            }
            if (playerCount < resolveLobbyCapacity(lobby)) {
                return t('lobbyPage.minReached');
            }
            return t('lobbyPage.startNow');
        }

        function updateStatus(text) {
            if (dom.status) {
                dom.status.textContent = text;
            }
        }

        function syncCloseWarning(lobby) {
            window.clearInterval(closeWarningTimer);
            closeWarningTimer = null;

            if (!lobby?.openUntil || lobby?.lobbyState === 'STARTED' || state.redirectingToGame) {
                hideCloseWarning();
                return;
            }

            renderCloseWarning(lobby.openUntil);
            closeWarningTimer = window.setInterval(() => {
                renderCloseWarning(lobby.openUntil);
            }, 1000);
        }

        function renderCloseWarning(openUntil) {
            if (!dom.closeWarning || !dom.closeWarningText) {
                return;
            }

            const closeAt = Date.parse(openUntil);
            if (!Number.isFinite(closeAt)) {
                hideCloseWarning();
                return;
            }

            const remainingMs = closeAt - Date.now();
            if (remainingMs <= 0 || remainingMs > 30000) {
                hideCloseWarning();
                return;
            }

            const secondsLeft = Math.max(1, Math.ceil(remainingMs / 1000));
            dom.closeWarning.hidden = false;
            dom.closeWarningText.textContent = secondsLeft === 1 ? t('lobbyPage.closingInOne', secondsLeft) : t('lobby.closeWarning.closingIn', secondsLeft);

            if (!state.closeWarningVisible) {
                state.closeWarningVisible = true;
                showToast(t('lobbyPage.toast.closingSoon.title'), t('lobbyPage.toast.closingSoon.body'));
            }
        }

        function hideCloseWarning() {
            if (dom.closeWarning) {
                dom.closeWarning.hidden = true;
            }
            state.closeWarningVisible = false;
        }

        function persistLobbyClosedNotice(lobby) {
            if (!wasLobbyClosedByTimeout(lobby)) {
                return;
            }

            const notice = {
                lobbyId: lobby?.id || state.lobby?.id,
                lobbyName: lobby?.name || state.lobby?.name || t('lobbies.yourLobby'),
                closedAt: Date.now()
            };

            try {
                window.sessionStorage.setItem(lobbyClosedNoticeKey, JSON.stringify(notice));
            } catch (error) {
                console.error('Unable to persist lobby close notice', error);
            }
        }

        function wasLobbyClosedByTimeout(lobby) {
            if (!lobby?.openUntil) {
                return false;
            }

            const closeAt = Date.parse(lobby.openUntil);
            if (!Number.isFinite(closeAt)) {
                return false;
            }

            return Date.now() >= closeAt - 1000;
        }

        function redirectToGame(gameId) {
            if (state.redirectingToGame || !gameId) {
                return;
            }
            state.redirectingToGame = true;
            window.clearInterval(closeWarningTimer);
            window.location.href = '/game';
        }

        async function redirectToResolvedGame() {
            if (state.redirectingToGame || !state.lobby?.id) {
                return;
            }
            try {
                const response = await fetch(`/api/games/lobby/${encodeURIComponent(state.lobby.id)}`, {
                    credentials: 'include'
                });
                if (response.ok) {
                    const game = await response.json();
                    if (game?.id) {
                        redirectToGame(game.id);
                        return;
                    }
                }
            } catch (error) {
            }
            window.setTimeout(() => {
                if (!state.redirectingToGame) {
                    state.redirectingToGame = true;
                    window.location.href = '/game';
                }
            }, 250);
        }

        function notifyLobbyEvent(type, previousLobby, nextLobby) {
            if (!nextLobby) {
                return;
            }

            const previousConfig = describeConfig(previousLobby);
            const nextConfig = describeConfig(nextLobby);
            const previousCount = Array.isArray(previousLobby?.players) ? previousLobby.players.length : null;
            const nextCount = Array.isArray(nextLobby.players) ? nextLobby.players.length : 0;

            if (type === 'UPDATED' && previousConfig !== nextConfig) {
                showToast(t('lobbyPage.toast.configUpdated.title'), t('lobbyPage.toast.configUpdated.body', nextConfig));
                return;
            }

            if (type === 'UPDATED' && previousCount !== null && previousCount !== nextCount) {
                if (nextCount > previousCount) {
                    showToast(t('lobbyPage.toast.playerJoined.title'), t('lobbyPage.toast.playerJoined.body', nextCount, nextLobby.maxPlayers));
                } else {
                    showToast(t('lobbyPage.toast.playerLeft.title'), t('lobbyPage.toast.playerLeft.body', nextCount, nextLobby.maxPlayers));
                }
            }
        }

        function showToast(title, text) {
            if (!dom.toast || !dom.toastTitle || !dom.toastText) {
                return;
            }

            window.clearTimeout(toastHideTimer);
            window.clearTimeout(toastCleanupTimer);
            dom.toastTitle.textContent = title;
            dom.toastText.textContent = text;
            dom.toast.hidden = false;
            dom.toast.classList.remove('is-visible');
            window.requestAnimationFrame(() => {
                window.requestAnimationFrame(() => {
                    dom.toast.classList.add('is-visible');
                });
            });

            toastHideTimer = window.setTimeout(() => {
                dom.toast.classList.remove('is-visible');
                toastCleanupTimer = window.setTimeout(() => {
                    dom.toast.hidden = true;
                }, 560);
            }, 2600);
        }

        async function copyToClipboard(value) {
            const text = String(value || '');
            if (navigator.clipboard?.writeText) {
                await navigator.clipboard.writeText(text);
                return;
            }

            const input = document.createElement('input');
            input.value = text;
            input.setAttribute('readonly', '');
            input.style.position = 'absolute';
            input.style.left = '-9999px';
            document.body.appendChild(input);
            input.select();
            input.setSelectionRange(0, text.length);

            try {
                if (!document.execCommand('copy')) {
                    throw new Error('Copy command failed');
                }
            } finally {
                input.remove();
            }
        }

        function startReconnectDisconnectTimer() {
            clearReconnectDisconnectTimer();
            // A backgrounded mobile tab suspends JS and drops the socket; counting
            // that as "failed to reconnect" kicked users out of the lobby. Only
            // count disconnect time while the page is visible — the visibilitychange
            // listener restarts the countdown when the user comes back.
            if (document.hidden) {
                return;
            }
            reconnectDisconnectTimer = window.setTimeout(() => {
                if (state.wsConnected || state.disconnectingForReconnectTimeout) {
                    return;
                }

                disconnectForReconnectTimeout();
            }, reconnectDisconnectDelayMs);
        }

        document.addEventListener('visibilitychange', () => {
            if (document.hidden) {
                clearReconnectDisconnectTimer();
            } else if (!state.wsConnected && !state.disconnectingForReconnectTimeout) {
                startReconnectDisconnectTimer();
            }
        });

        function clearReconnectDisconnectTimer() {
            if (!reconnectDisconnectTimer) {
                return;
            }

            window.clearTimeout(reconnectDisconnectTimer);
            reconnectDisconnectTimer = null;
        }

        async function disconnectForReconnectTimeout() {
            state.disconnectingForReconnectTimeout = true;
            updateStatus(t('lobbyPage.connGone'));

            try {
                await leaveCurrentLobby();
            } catch (error) {
                console.error('Unable to leave lobby after reconnect timeout', error);
            } finally {
                navigateToPreviousUiPage();
            }
        }

        async function leaveCurrentLobby() {
            const response = await fetch('/api/lobby/leave', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                credentials: 'include',
                body: JSON.stringify(state.lobby.id)
            });
            if (!response.ok) {
                throw new Error('Failed to leave lobby');
            }
        }

        function navigateToPreviousUiPage() {
            if (document.referrer) {
                try {
                    const referrer = new URL(document.referrer);
                    if (referrer.origin === window.location.origin && referrer.pathname !== window.location.pathname) {
                        window.history.back();
                        return;
                    }
                } catch (error) {
                    console.error('Unable to inspect previous page', error);
                }
            }

            window.location.href = '/';
        }

        function escapeHtml(value) {
            return String(value)
                .replaceAll('&', '&amp;')
                .replaceAll('<', '&lt;')
                .replaceAll('>', '&gt;')
                .replaceAll('"', '&quot;')
                .replaceAll("'", '&#39;');
        }
    })();
