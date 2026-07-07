function setButtonLabel(button, label) {
    const text = button?.querySelector('span');
    if (text) {
        text.textContent = label;
    } else if (button) {
        button.textContent = label;
    }
}

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

        function buildSettingsNotice(title, text) {
            const nodes = [];

            const h3Settings = document.createElement('h3');
            h3Settings.className = 'lobbies-settings-title';
            h3Settings.textContent = `${title} settings`;

            const notice = document.createElement('p');
            notice.className = 'lobbies-settings-note';
            notice.textContent = text;

            nodes.push(h3Settings, notice);
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
            if (!Object.keys(selectedGame).length) {
                const text = settingsElement.id === 'create-game-settings'
                    ? 'Lobby creation is not available for this game yet.'
                    : 'No saved configurations are available for this game yet.';
                setGameSettingsContent(settingsElement, buildSettingsNotice(title, text));
                return;
            }
            setGameSettingsContent(settingsElement, buildProperties(title, selectedGame, selectId));
        }

        function handleGameTypeChange(select) {
            applyGameTypeSettings(select.value, document.getElementById('game-settings'));
        }

(() => {
            const grid = document.getElementById('lobbies-grid');
            let emptyState = document.getElementById('lobbies-empty');
            const activeCount = document.getElementById('active-lobbies-count');
            const activeFilterLabel = document.getElementById('active-filter-label');
            const gameTypeSelect = document.getElementById('game-type');
            const applyFiltersButton = document.getElementById('apply-filters-button');
            const clearFiltersButton = document.getElementById('clear-filters-button');
            const joinCodeForm = document.getElementById('join-code-form');
            const joinCodeInput = document.getElementById('join-code-input');
            const joinCodeSubmit = document.getElementById('join-code-submit');
            const joinCodeStatus = document.getElementById('join-code-status');
            const toast = document.getElementById('lobbies-toast');
            const toastTitle = document.getElementById('lobbies-toast-title');
            const toastText = document.getElementById('lobbies-toast-text');
            const lobbyClosedNoticeKey = 'uc-lobby-closed-notice';
            const lobbyFilterStorageKey = 'uc-lobbies-filter';
            const filterState = {
                gameType: 'all',
                settingKey: '',
                settingId: null
            };
            let latestFilterRequestId = 0;
            let toastHideTimer = null;
            let toastCleanupTimer = null;

            if (!grid) {
                return;
            }

            function normalizeCode(value) {
                return String(value || '').toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 6);
            }

            function setJoinCodeStatus(message, type = '') {
                if (!joinCodeStatus) {
                    return;
                }

                joinCodeStatus.textContent = message;
                joinCodeStatus.classList.toggle('error', type === 'error');
                joinCodeStatus.classList.toggle('success', type === 'success');
            }

            function syncJoinCodeState() {
                if (!joinCodeInput || !joinCodeSubmit) {
                    return;
                }

                const code = normalizeCode(joinCodeInput.value);
                if (joinCodeInput.value !== code) {
                    joinCodeInput.value = code;
                }

                const isReady = /^[A-Z0-9]{6}$/.test(code);
                joinCodeSubmit.disabled = !isReady;
                setJoinCodeStatus(isReady ? 'Ready to join.' : 'Enter a 6-character lobby code.', isReady ? 'success' : '');
            }

            function buildEmptyStateMarkup() {
                const title = filterState.gameType === 'all'
                    ? 'No active lobbies present'
                    : 'No lobbies match this filter';
                const text = filterState.gameType === 'all'
                    ? 'Create a room for yet another ULTRAgame'
                    : 'Try another game setup or create a lobby for this configuration.';

                return `
                    <h3>${title}</h3>
                    <p>${text}</p>
                    <div class="lobby-empty-actions">
                        <button class="btn-accent" type="button" data-action="open-create-lobby">Create Lobby</button>
                    </div>
                `;
            }

            function wireEmptyStateActions() {
                const button = emptyState?.querySelector('[data-action="open-create-lobby"]');
                if (button) {
                    button.addEventListener('click', () => {
                        document.getElementById('create-div')?.classList.add('active');
                    });
                }
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
                    emptyState.innerHTML = buildEmptyStateMarkup();
                    grid.insertAdjacentElement('beforebegin', emptyState);
                    wireEmptyStateActions();
                }
                if (emptyState) {
                    if (count > 0) {
                        emptyState.remove();
                        emptyState = null;
                    } else {
                        emptyState.innerHTML = buildEmptyStateMarkup();
                        wireEmptyStateActions();
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
                const n = config.numberOfPlayers;
                if (n === 2) {
                    return config.cardsInHandNum != null ? `2 players • ${config.cardsInHandNum} cards each` : '2 players';
                }
                if (n === 3) {
                    return '3 players';
                }
                if (n === 4) {
                    return config.teamsEnabled ? '4 players with teams' : '4 players no teams';
                }
                return 'Standard rules';
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

                        <div class="lobby-card-settings">
                            <strong>Lobby settings</strong>
                            <p>${escapeHtml(describeLobbySettings(lobby))}</p>
                        </div>

                        <div class="lobby-card-players">
                            ${renderPlayers(players, hostId)}
                        </div>

                        <div class="lobby-card-footer">
                            <button class="btn btn-accent" type="button" data-join-lobby-id="${escapeHtml(lobby.id || '')}">
                                <img class="uc-icon" data-icon="login" src="/pics/light/login.svg" alt="" aria-hidden="true">
                                <span>Join lobby</span>
                            </button>
                        </div>
                    </article>
                `;
            }

            function getSelectedFilter() {
                const gameType = gameTypeSelect?.value || 'all';
                if (gameType === 'all') {
                    return {
                        gameType: 'all',
                        settingKey: '',
                        settingId: null
                    };
                }

                const settings = getGameTypeSettings(gameType) || {};
                const selectedSettingKey = document.getElementById('properties')?.value || Object.keys(settings)[0] || '';
                const selectedSettingId = getGameTypeSettingId(gameType, selectedSettingKey);

                return {
                    gameType,
                    settingKey: selectedSettingKey,
                    settingId: selectedSettingId ?? 0
                };
            }

            function buildLobbyFetchUrl(nextFilter) {
                if (nextFilter.gameType === 'all') {
                    return '/api/lobby/get-lobbies';
                }

                return `/api/lobby/get-lobbies/${encodeURIComponent(nextFilter.gameType)}/${encodeURIComponent(nextFilter.settingId)}`;
            }

            function persistFilter(nextFilter) {
                try {
                    window.localStorage.setItem(lobbyFilterStorageKey, JSON.stringify({
                        gameType: nextFilter.gameType,
                        settingKey: nextFilter.settingKey
                    }));
                } catch (error) {
                    console.error('Unable to persist lobby filter', error);
                }
            }

            function readSavedFilter() {
                try {
                    const rawFilter = window.localStorage.getItem(lobbyFilterStorageKey);
                    if (!rawFilter) {
                        return null;
                    }

                    const parsedFilter = JSON.parse(rawFilter);
                    if (!parsedFilter || parsedFilter.gameType === 'all') {
                        return {
                            gameType: 'all',
                            settingKey: '',
                            settingId: null
                        };
                    }

                    const gameType = String(parsedFilter.gameType || '').toLowerCase();
                    const settingKey = String(parsedFilter.settingKey || '');
                    const settingId = getGameTypeSettingId(gameType, settingKey);
                    if (!gameType || !settingKey || settingId == null) {
                        return null;
                    }

                    return {
                        gameType,
                        settingKey,
                        settingId
                    };
                } catch (error) {
                    console.error('Unable to read saved lobby filter', error);
                    return null;
                }
            }

            function syncFilterControls(nextFilter) {
                if (!gameTypeSelect) {
                    return;
                }

                gameTypeSelect.value = nextFilter.gameType;
                handleGameTypeChange(gameTypeSelect);

                if (nextFilter.gameType === 'all') {
                    return;
                }

                const propertiesSelect = document.getElementById('properties');
                if (propertiesSelect && getGameTypeSetting(nextFilter.gameType, nextFilter.settingKey)) {
                    propertiesSelect.value = nextFilter.settingKey;
                }
            }

            function lobbyMatchesFilter(lobby) {
                if (!lobby || filterState.gameType === 'all') {
                    return true;
                }

                if (String(lobby.gameType || '').toLowerCase() !== filterState.gameType) {
                    return false;
                }

                const lobbySettingId = resolveLobbyGameSettingId(lobby);
                return lobbySettingId != null && lobbySettingId === filterState.settingId;
            }

            function replaceLobbies(lobbies) {
                grid.innerHTML = '';
                for (const lobby of lobbies) {
                    grid.insertAdjacentHTML('beforeend', renderCard(lobby));
                }
                window.syncThemeUi?.();
                updateLobbyCount();
            }

            function applyFilterState(nextFilter) {
                filterState.gameType = nextFilter.gameType;
                filterState.settingKey = nextFilter.settingKey;
                filterState.settingId = nextFilter.settingId;
                persistFilter(nextFilter);
            }

            async function refreshLobbies(nextFilter) {
                const requestId = ++latestFilterRequestId;
                applyFiltersButton?.setAttribute('disabled', 'disabled');
                clearFiltersButton?.setAttribute('disabled', 'disabled');

                try {
                    const response = await fetch(buildLobbyFetchUrl(nextFilter), {
                        method: 'GET',
                        credentials: 'include'
                    });
                    if (!response.ok) {
                        const message = (await response.text()).trim();
                        throw new Error(message || 'Failed to refresh lobbies.');
                    }

                    const lobbies = await response.json();
                    if (requestId !== latestFilterRequestId) {
                        return;
                    }

                    applyFilterState(nextFilter);
                    replaceLobbies(Array.isArray(lobbies) ? lobbies : []);
                } finally {
                    if (requestId === latestFilterRequestId) {
                        applyFiltersButton?.removeAttribute('disabled');
                        clearFiltersButton?.removeAttribute('disabled');
                    }
                }
            }

            async function applySelectedFilters() {
                const nextFilter = getSelectedFilter();
                try {
                    await refreshLobbies(nextFilter);
                } catch (error) {
                    showToast('Filter failed', error?.message || 'Unable to refresh lobbies right now.');
                }
            }

            async function clearFilters() {
                if (gameTypeSelect) {
                    gameTypeSelect.value = 'all';
                    handleGameTypeChange(gameTypeSelect);
                }

                try {
                    await refreshLobbies({
                        gameType: 'all',
                        settingKey: '',
                        settingId: null
                    });
                } catch (error) {
                    showToast('Reset failed', error?.message || 'Unable to reset filters right now.');
                }
            }

            function upsertLobby(type, lobby) {
                if (!lobby || !lobby.id) {
                    return;
                }

                const existing = grid.querySelector(`[data-id="${CSS.escape(String(lobby.id))}"]`);
                if (type === 'DELETED' || type === 'STARTED' || lobby.isPublic === false) {
                    existing?.remove();
                    updateLobbyCount();
                    return;
                }

                if (!lobbyMatchesFilter(lobby)) {
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

            async function joinLobby(joinRequest) {
                const response = await fetch('/api/lobby/join', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    credentials: 'include',
                    body: JSON.stringify(joinRequest)
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

            function showPendingLobbyClosedNotice() {
                let rawNotice = null;
                try {
                    rawNotice = window.sessionStorage.getItem(lobbyClosedNoticeKey);
                    window.sessionStorage.removeItem(lobbyClosedNoticeKey);
                } catch (error) {
                    console.error('Unable to read lobby close notice', error);
                    return;
                }

                if (!rawNotice) {
                    return;
                }

                try {
                    const notice = JSON.parse(rawNotice);
                    const lobbyName = notice?.lobbyName || 'Your lobby';
                    showToast('Lobby closed', `${lobbyName} was closed because its timer ran out.`);
                } catch (error) {
                    console.error('Unable to parse lobby close notice', error);
                }
            }

            grid.addEventListener('click', async (event) => {
                const joinButton = event.target.closest('[data-join-lobby-id]');
                if (!joinButton) {
                    return;
                }

                try {
                    await joinLobby({lobbyId: joinButton.dataset.joinLobbyId});
                } catch (error) {
                    if (error?.status === 409) {
                        showToast('Lobby full', error.message || 'This lobby is already full.');
                        return;
                    }

                    showToast('Join failed', error?.message || 'Unable to join this lobby right now.');
                }
            });

            joinCodeInput?.addEventListener('input', syncJoinCodeState);
            joinCodeForm?.addEventListener('submit', async (event) => {
                event.preventDefault();
                const code = normalizeCode(joinCodeInput?.value);
                if (!/^[A-Z0-9]{6}$/.test(code)) {
                    setJoinCodeStatus('Enter a valid 6-character lobby code.', 'error');
                    joinCodeInput?.focus();
                    return;
                }

                joinCodeSubmit.disabled = true;
                setButtonLabel(joinCodeSubmit, 'Joining...');
                setJoinCodeStatus('Joining lobby...', 'success');

                try {
                    await joinLobby({lobbyCode: code});
                } catch (error) {
                    if (error?.status === 409) {
                        setJoinCodeStatus(error.message || 'Lobby is full.', 'error');
                    } else {
                        setJoinCodeStatus(error?.message || 'Unable to join this lobby.', 'error');
                    }
                    setButtonLabel(joinCodeSubmit, 'Join');
                    joinCodeSubmit.disabled = false;
                    joinCodeInput?.focus();
                }
            });

            applyFiltersButton?.addEventListener('click', applySelectedFilters);
            clearFiltersButton?.addEventListener('click', clearFilters);
            gameTypeSelect?.addEventListener('change', (event) => {
                handleGameTypeChange(event.currentTarget);
            });

            const savedFilter = readSavedFilter();
            if (savedFilter) {
                syncFilterControls(savedFilter);
                refreshLobbies(savedFilter).catch((error) => {
                    replaceLobbies(initialLobbies);
                    syncFilterSummary();
                    showToast('Saved filter failed', error?.message || 'Unable to restore your saved lobby filter.');
                });
            } else {
                replaceLobbies(initialLobbies);
                syncFilterSummary();
            }
            syncJoinCodeState();
            showPendingLobbyClosedNotice();

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
