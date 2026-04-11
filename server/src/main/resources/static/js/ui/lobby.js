(() => {
            const storageKey = 'uc-theme';
            const savedTheme = localStorage.getItem(storageKey);
            const systemDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
            const theme = savedTheme || (systemDark ? 'dark' : 'light');
            document.documentElement.setAttribute('data-theme', theme);
        })();

(() => {
        const lobbyPage = document.getElementById('lobby-page');
        const initialLobby = window.__INITIAL_LOBBY__ ?? null;
        const initialChat = window.__INITIAL_LOBBY_CHAT__ ?? null;
        const username = lobbyPage?.dataset.username || '';
        if (!lobbyPage || !initialLobby || !initialLobby.id) {
            return;
        }

        const state = {
            lobby: initialLobby,
            currentUser: null,
            wsConnected: false,
            redirectingToGame: false
        };

        const dom = {
            name: document.getElementById('lobby-name'),
            gameType: document.getElementById('lobby-type-chip'),
            playerChip: document.getElementById('lobby-player-chip'),
            host: document.getElementById('lobby-host'),
            config: document.getElementById('lobby-config'),
            configEditor: document.getElementById('lobby-config-editor'),
            configSelect: document.getElementById('lobby-config-select'),
            wsStatus: document.getElementById('ws-status'),
            status: document.getElementById('lobby-status'),
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
            emptyText: 'Too scared to send a message?'
        });

        renderLobby(state.lobby);
        bindActions();
        loadProfile();
        connectLobbyWs();

        function loadProfile() {
            fetch('/api/auth/profile', { credentials: 'include' })
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
                    updateStatus('Unable to load current profile.');
                });
        }

        function connectLobbyWs() {
            if (!window.Stomp) {
                setWsStatus('Unavailable');
                return;
            }

            const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
            const wsUrl = `${wsProtocol}//${window.location.host}/ws`;
            const client = Stomp.client(wsUrl);
            client.reconnect_delay = 3000;
            client.debug = null;

            client.connect({}, () => {
                state.wsConnected = true;
                setWsStatus('Connected');
                client.subscribe(`/topic/lobbies/${state.lobby.id}`, (message) => {
                    try {
                        const payload = JSON.parse(message.body);
                        if (payload && payload.gameId) {
                            updateStatus(`Game started. Game id: ${payload.gameId}`);
                            redirectToGame(payload.gameId);
                            return;
                        }
                        if (!payload || !payload.type) {
                            return;
                        }
                        if (payload.type === 'DELETED') {
                            window.location.href = '/lobbies';
                            return;
                        }
                        if (payload.lobbyDto) {
                            const previousLobby = previousLobbySnapshot;
                            state.lobby = payload.lobbyDto;
                            previousLobbySnapshot = payload.lobbyDto;
                        renderLobby(state.lobby);
                        notifyLobbyEvent(payload.type, previousLobby, payload.lobbyDto);
                        if (payload.type === 'STARTED') {
                                showToast('Game started', 'The lobby has started a game.');
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
                setWsStatus('Reconnecting…');
            });
        }

        function bindActions() {
            dom.start?.addEventListener('click', async () => {
                try {
                    const response = await fetch('/api/lobby/start', {
                        method: 'POST',
                        credentials: 'include'
                    });
                    if (!response.ok) {
                        throw new Error('Failed to start lobby');
                    }
                    updateStatus('Starting game…');
                    redirectToResolvedGame();
                } catch (error) {
                    updateStatus('Unable to start the lobby.');
                }
            });

            dom.leave?.addEventListener('click', async () => {
                try {
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
                    window.location.href = '/lobbies';
                } catch (error) {
                    updateStatus('Unable to leave the lobby.');
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
                    window.location.href = '/lobbies';
                } catch (error) {
                    updateStatus('Unable to delete the lobby.');
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
                    updateStatus('Unable to kick that player.');
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
                    showToast('Update failed', 'Unable to update the lobby configuration.');
                }
            });
        }

        function renderLobby(lobby) {
            const players = sortPlayers(lobby.players || [], lobby.host);
            const currentUserId = state.currentUser?.id != null ? String(state.currentUser.id) : null;
            const isHost = currentUserId && lobby.host && String(lobby.host.id) === currentUserId;
            const isPlayer = currentUserId
                ? players.some((player) => String(player.id) === currentUserId)
                : true;

            if (!isPlayer) {
                window.location.href = '/lobbies';
                return;
            }

            if (dom.name) dom.name.textContent = lobby.name || 'ULTRAlobby';
            if (dom.gameType) dom.gameType.textContent = lobby.gameType || 'Lobby';
            if (dom.playerChip) dom.playerChip.textContent = `${players.length} / ${lobby.maxPlayers} players`;
            if (dom.host) dom.host.textContent = lobby.host?.name || 'Unknown';
            if (dom.config) dom.config.textContent = describeConfig(lobby);
            if (dom.status) dom.status.textContent = buildLobbyStatus(lobby, players.length);
            renderConfigEditor(lobby, !!(currentUserId && lobby.host && String(lobby.host.id) === currentUserId));

            if (dom.start) {
                dom.start.hidden = !isHost;
                dom.start.disabled = players.length < Number(lobby.minPlayers || lobby.maxPlayers || 0);
            }
            if (dom.delete) {
                dom.delete.hidden = !isHost;
            }
            if (dom.leave) {
                dom.leave.hidden = !!isHost;
            }

            renderPlayers(players, isHost, currentUserId, lobby.host);
        }

        function renderPlayers(players, isHost, currentUserId, host) {
            if (!dom.players) {
                return;
            }

            if (!players.length) {
                dom.players.innerHTML = '<div class="lobby-empty">No players in this lobby.</div>';
                return;
            }

            dom.players.innerHTML = players.map((player) => {
                const playerId = String(player.id);
                const owner = host && String(host.id) === playerId;
                const current = currentUserId && currentUserId === playerId;
                const canKick = isHost && !owner;
                const initial = (player.name || 'U').charAt(0).toUpperCase();
                const role = owner ? 'Host' : (current ? 'You' : 'Player');

                return `
                    <div class="player-row">
                        <div class="player-main">
                            <div class="player-avatar">${escapeHtml(initial)}</div>
                            <div>
                                <div class="player-name">${escapeHtml(player.name || `User ${player.id}`)}</div>
                                <div class="player-role">${escapeHtml(role)}</div>
                            </div>
                        </div>
                        ${canKick ? `<button class="btn danger" type="button" data-kick-player="${player.id}">Kick</button>` : ''}
                    </div>
                `;
            }).join('');
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
            if (!lobby || lobby.gameType !== 'Briskula' || !lobby.gameConfig) {
                return 'Standard rules';
            }

            const config = lobby.gameConfig;
            const parts = [];
            if (config.numberOfPlayers != null) {
                parts.push(`${config.numberOfPlayers} players`);
            }
            if (config.cardsInHandNum != null) {
                parts.push(`${config.cardsInHandNum} cards each`);
            }
            if (config.numberOfPlayers === 4) {
                parts.push(config.teamsEnabled ? 'Teams enabled' : 'No teams');
            }
            return parts.join(' • ') || 'Standard rules';
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

        function resolveGameSettingKey(lobby) {
            if (!lobby || String(lobby.gameType) !== 'Briskula' || !lobby.gameConfig) {
                return '';
            }

            const config = lobby.gameConfig;
            if (config.numberOfPlayers === 2 && config.cardsInHandNum === 3) {
                return 'p2';
            }
            if (config.numberOfPlayers === 2 && config.cardsInHandNum === 4) {
                return 'p2c4';
            }
            if (config.numberOfPlayers === 3) {
                return 'p3';
            }
            if (config.numberOfPlayers === 4 && config.teamsEnabled) {
                return 'p4teams';
            }
            if (config.numberOfPlayers === 4) {
                return 'p4';
            }
            return '';
        }

        async function updateLobbyConfiguration(settingKey) {
            const gameTypeKey = String(state.lobby.gameType || '').toLowerCase();
            if (!supportsLobbyCreation(gameTypeKey, settingKey)) {
                throw new Error('Unsupported game configuration');
            }

            const updatedLobby = JSON.parse(buildLobbyCreatePayload(gameTypeKey, settingKey, state.lobby.name));
            updatedLobby.id = state.lobby.id;
            updatedLobby.name = state.lobby.name;
            updatedLobby.players = state.lobby.players;
            updatedLobby.host = state.lobby.host;

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
                state.lobby = lobby;
                previousLobbySnapshot = lobby;
                renderLobby(state.lobby);
            }
        }

        function buildLobbyStatus(lobby, playerCount) {
            if (playerCount < Number(lobby.minPlayers || 0)) {
                return `Waiting for ${Number(lobby.minPlayers) - playerCount} more player(s) to start.`;
            }
            if (playerCount < Number(lobby.maxPlayers || 0)) {
                return 'Minimum player count reached. The host can start the lobby now.';
            }
            return 'Lobby is full and ready to start.';
        }

        function updateStatus(text) {
            if (dom.status) {
                dom.status.textContent = text;
            }
        }

        function redirectToGame(gameId) {
            if (state.redirectingToGame || !gameId) {
                return;
            }
            state.redirectingToGame = true;
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
                showToast('Configuration updated', `New setup: ${nextConfig}`);
                return;
            }

            if (type === 'UPDATED' && previousCount !== null && previousCount !== nextCount) {
                if (nextCount > previousCount) {
                    showToast('Player joined', `${nextCount}/${nextLobby.maxPlayers} players are now in the lobby.`);
                } else {
                    showToast('Player left', `${nextCount}/${nextLobby.maxPlayers} players remain in the lobby.`);
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

        function setWsStatus(text) {
            if (dom.wsStatus) {
                dom.wsStatus.textContent = text;
            }
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
