(() => {
            const storageKey = 'uc-theme';
            const savedTheme = localStorage.getItem(storageKey);
            const systemDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
            const theme = savedTheme || (systemDark ? 'dark' : 'light');
            document.documentElement.setAttribute('data-theme', theme);
        })();

const initialLobbies = Array.isArray(window.__INITIAL_LOBBIES__) ? window.__INITIAL_LOBBIES__ : [];

const gameSettingsAnimationDurationMs = 420;

        function setGameSettingsContent(gameSettings, nodes) {
            window.clearTimeout(gameSettings.hideTimeoutId);

            if (!nodes.length) {
                gameSettings.hidden = true;
                gameSettings.hideTimeoutId = window.setTimeout(() => {
                    gameSettings.innerHTML = '';
                }, gameSettingsAnimationDurationMs);
                return;
            }

            gameSettings.innerHTML = '';
            gameSettings.append(...nodes);

            if (gameSettings.hidden) {
                window.requestAnimationFrame(() => {
                    gameSettings.hidden = false;
                });
            }
        }

        function buildProperties(title, select, selectId) {
            const nodes = [];

            const h3Settings = document.createElement('h3');
            h3Settings.className = 'lobbies-settings-title';
            h3Settings.textContent = `${title} settings`;

            const div = document.createElement('div');
            div.className = 'field';

            const label = document.createElement('label');
            label.setAttribute('for', selectId);
            label.textContent = 'Game configuration';

            const properties_select = document.createElement('select');
            properties_select.setAttribute('id', selectId);

            for (let key in select) {
                const option = document.createElement('option');
                option.textContent = select[key].ui_text;
                option.value = key;
                properties_select.appendChild(option);
            }

            div.appendChild(label);
            div.appendChild(properties_select);

            nodes.push(h3Settings, div);
            return nodes;
        }

        function applyGameTypeSettings(gameType, settingsElement) {
            window.clearTimeout(settingsElement.hideTimeoutId);

            if (gameType === 'all') {
                setGameSettingsContent(settingsElement, []);
                return;
            }

            const selectedGame = gameTypes[gameType];
            if (!selectedGame) {
                setGameSettingsContent(settingsElement, []);
                return;
            }

            const title = gameType.charAt(0).toUpperCase() + gameType.slice(1);
            const selectId = settingsElement.id === 'create-game-settings' ? 'create-properties' : 'properties';
            setGameSettingsContent(settingsElement, buildProperties(title, selectedGame, selectId));
        }

        function handleGameTypeChange(select) {
            applyGameTypeSettings(select.value, document.getElementById('game-settings'));
        }

        function handleCreateGameTypeChange(select) {
            applyGameTypeSettings(select.value, document.getElementById('create-game-settings'));
        }

        async function createLobby(selectedGameType, selectedGameSetting, lobbyName) {
            const gameType = selectedGameType?.value;
            const gameSetting = selectedGameSetting?.value;

            if (!gameType || gameType === 'all') {
                window.alert('Choose a game type first.');
                return;
            }

            if (!gameSetting) {
                window.alert('Choose a game configuration first.');
                return;
            }

            if (!supportsLobbyCreation(gameType, gameSetting)) {
                window.alert('Lobby creation is not implemented for that game type yet.');
                return;
            }

            const createLobbyReq = await fetch('/api/lobby/create', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: buildLobbyCreatePayload(gameType, gameSetting, lobbyName)
            });

            if (!createLobbyReq.ok) {
                throw new Error('Failed to create new lobby');
            }

            const createdLobby = await createLobbyReq.json();
            if (createdLobby?.id)
                localStorage.setItem('lobbyId', createdLobby.id);

            window.location.href = '/lobbies';
        }

        (() => {
            const createOverlay = document.getElementById('create-div');
            const createOpenButtons = document.querySelectorAll('[data-action="open-create-lobby"]');
            const createCloseButtons = createOverlay.querySelectorAll('[data-close-create]');
            const gameTypeSelect = document.getElementById('game-type');
            const createGameType = document.getElementById('create-game-type');
            const createGameSettings = document.getElementById('create-game-settings');
            const createForm = document.getElementById('create-lobby-form');
            const createSubmitButton = document.getElementById('create-lobby-submit');

            function resetCreateModal() {
                createGameType.value = 'all';
                setGameSettingsContent(createGameSettings, []);
            }

            function openCreateModal() {
                resetCreateModal();
                createOverlay.classList.add('active');
            }

            function closeCreateModal() {
                createOverlay.classList.remove('active');
                resetCreateModal();
            }

            createOpenButtons.forEach((button) => {
                button.addEventListener('click', openCreateModal);
            });

            createCloseButtons.forEach((button) => {
                button.addEventListener('click', closeCreateModal);
            });

            gameTypeSelect?.addEventListener('change', (event) => {
                handleGameTypeChange(event.currentTarget);
            });

            createGameType?.addEventListener('change', (event) => {
                handleCreateGameTypeChange(event.currentTarget);
            });

            createOverlay.addEventListener('click', (event) => {
                if (event.target === createOverlay) {
                    closeCreateModal();
                }
            });

            createSubmitButton?.addEventListener('click', () => {
                createLobby(
                    document.getElementById('create-game-type').selectedOptions[0],
                    document.getElementById('create-properties')?.selectedOptions[0],
                    document.getElementById('create-lobby-name').value
                );
            });

            createForm?.addEventListener('submit', (event) => {
                event.preventDefault();
                createLobby(
                    document.getElementById('create-game-type').selectedOptions[0],
                    document.getElementById('create-properties')?.selectedOptions[0],
                    document.getElementById('create-lobby-name').value
                );
            });
        })();

        (() => {
            const grid = document.getElementById('lobbies-grid');
            let emptyState = document.getElementById('lobbies-empty');
            const activeCount = document.getElementById('active-lobbies-count');
            const toast = document.getElementById('lobbies-toast');
            const toastTitle = document.getElementById('lobbies-toast-title');
            const toastText = document.getElementById('lobbies-toast-text');
            let toastHideTimer = null;
            let toastCleanupTimer = null;

            if (!grid) {
                return;
            }

            function updateLobbyCount() {
                const count = grid.querySelectorAll('.lobby-card').length;
                if (activeCount) {
                    activeCount.textContent = `${count} active`;
                }
                if (!emptyState && count === 0) {
                    emptyState = document.createElement('article');
                    emptyState.id = 'lobbies-empty';
                    emptyState.className = 'card lobby-empty-state';
                    emptyState.innerHTML = `
                        <h3>No active lobbies present</h3>
                        <p>Create a room for yet another ULTRAgame</p>
                        <div class="lobby-empty-actions">
                            <button class="btn-accent" type="button" data-action="open-create-lobby">Create Lobby</button>
                        </div>
                    `;
                    grid.insertAdjacentElement('beforebegin', emptyState);
                    const button = emptyState.querySelector('[data-action="open-create-lobby"]');
                    if (button) {
                        button.addEventListener('click', () => {
                            document.getElementById('create-div')?.classList.add('active');
                        });
                    }
                }
                if (emptyState) {
                    if (count > 0) {
                        emptyState.remove();
                        emptyState = null;
                    } else {
                        emptyState.hidden = false;
                    }
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

            function describeLobbySettings(lobby) {
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
                    parts.push(config.teamsEnabled ? 'teams enabled' : 'free-for-all');
                }
                return parts.join(' • ');
            }

            function renderPlayers(players, hostId) {
                if (!players.length) {
                    return '<span class="lobby-card-subtitle">Waiting for players…</span>';
                }

                return players.map((player) => {
                    const initial = escapeHtml((player.name || 'U').charAt(0).toUpperCase());
                    const name = escapeHtml(player.name || `User ${player.id}`);
                    const isHost = hostId != null && String(player.id) === String(hostId);
                    return `
                        <span class="lobby-card-player">
                            <span class="lobby-card-avatar">${initial}</span>
                            <span>${name}</span>
                            ${isHost ? '<span class="chip">Host</span>' : ''}
                        </span>
                    `;
                }).join('');
            }

            function renderCard(lobby) {
                const players = Array.isArray(lobby.players) ? lobby.players : [];
                const openSlots = Math.max((lobby.maxPlayers ?? players.length) - players.length, 0);
                const hostId = lobby.host?.id ?? null;
                return `
                    <article class="card lobby-card" data-id="${escapeHtml(lobby.id)}">
                        <div class="lobby-card-head">
                            <div class="lobby-card-title">
                                <span class="chip">${escapeHtml(lobby.gameType || 'Unknown')}</span>
                                <h3>${escapeHtml(lobby.name || `Lobby ${lobby.id}`)}</h3>
                                <p class="lobby-card-subtitle">Host: ${escapeHtml(lobby.host?.name || 'Unknown')}</p>
                            </div>
                            <span class="chip">${players.length}/${lobby.maxPlayers ?? ''} players</span>
                        </div>

                        <div class="lobby-card-stats">
                            <span class="chip">${openSlots} open slot${openSlots === 1 ? '' : 's'}</span>
                            <span class="chip">Min ${escapeHtml(lobby.minPlayers ?? players.length)}</span>
                        </div>

                        <div class="lobby-card-settings">
                            <strong>Lobby settings</strong>
                            <p>${escapeHtml(describeLobbySettings(lobby))}</p>
                        </div>

                        <div class="lobby-card-players">
                            ${renderPlayers(players, hostId)}
                        </div>

                        <div class="lobby-card-footer">
                            <p>Join this room and continue in the live lobby view.</p>
                            <button class="btn btn-accent" type="button" data-join-lobby="${escapeHtml(lobby.id)}">Join lobby</button>
                        </div>
                    </article>
                `;
            }

            function upsertLobby(type, lobby) {
                if (!lobby || !lobby.id) {
                    return;
                }

                const existing = grid.querySelector(`[data-id="${CSS.escape(String(lobby.id))}"]`);
                if (type === 'DELETED' || type === 'STARTED') {
                    existing?.remove();
                    updateLobbyCount();
                    return;
                }

                const markup = renderCard(lobby);
                if (existing) {
                    existing.outerHTML = markup;
                } else {
                    grid.insertAdjacentHTML('beforeend', markup);
                }
                updateLobbyCount();
            }

            async function joinLobby(lobbyId) {
                const response = await fetch('/api/lobby/join', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    credentials: 'include',
                    body: JSON.stringify(lobbyId)
                });

                if (!response.ok) {
                    const message = (await response.text()).trim();
                    const error = new Error(message || 'Failed to join lobby');
                    error.status = response.status;
                    throw error;
                }

                window.location.href = '/lobbies';
            }

            function showToast(title, text) {
                if (!toast || !toastTitle || !toastText) {
                    return;
                }

                window.clearTimeout(toastHideTimer);
                window.clearTimeout(toastCleanupTimer);
                toastTitle.textContent = title;
                toastText.textContent = text;
                toast.hidden = false;
                toast.classList.remove('is-visible');
                window.requestAnimationFrame(() => {
                    window.requestAnimationFrame(() => {
                        toast.classList.add('is-visible');
                    });
                });

                toastHideTimer = window.setTimeout(() => {
                    toast.classList.remove('is-visible');
                    toastCleanupTimer = window.setTimeout(() => {
                        toast.hidden = true;
                    }, 260);
                }, 2800);
            }

            grid.addEventListener('click', async (event) => {
                const joinButton = event.target.closest('[data-join-lobby]');
                if (!joinButton) {
                    return;
                }

                try {
                    await joinLobby(joinButton.dataset.joinLobby);
                } catch (error) {
                    if (error?.status === 409) {
                        showToast('Lobby full', error.message || 'This lobby is already full.');
                        return;
                    }

                    showToast('Join failed', error?.message || 'Unable to join this lobby right now.');
                }
            });

            for (const lobby of initialLobbies) {
                upsertLobby('UPDATED', lobby);
            }
            updateLobbyCount();

            if (!window.Stomp) {
                return;
            }

            const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
            const client = Stomp.client(`${wsProtocol}//${window.location.host}/ws`);
            client.reconnect_delay = 3000;
            client.debug = null;
            client.connect({}, () => {
                client.subscribe('/topic/lobbies', (message) => {
                    try {
                        const payload = JSON.parse(message.body);
                        if (!payload || !payload.lobbyDto) {
                            return;
                        }
                        upsertLobby(payload.type, payload.lobbyDto);
                    } catch (error) {
                        console.error('Lobby list websocket parse error', error);
                    }
                });
            });
        })();
