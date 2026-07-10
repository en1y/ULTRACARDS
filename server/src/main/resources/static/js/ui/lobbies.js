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
            h3Settings.textContent = t('createLobby.settingsTitle', title);

            const div = document.createElement('div');
            div.className = 'field';

            const label = document.createElement('label');
            label.setAttribute('for', selectId);
            label.textContent = t('createLobby.gameConfiguration');

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
            h3Settings.textContent = t('createLobby.settingsTitle', title);

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
                    ? t('createLobby.status.gameUnavailable')
                    : t('lobbies.noSavedConfigs');
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
                setJoinCodeStatus(isReady ? t('home.codeStatus.ready') : t('lobbies.codeStatus.hint'), isReady ? 'success' : '');
            }

            function buildEmptyStateMarkup() {
                const title = filterState.gameType === 'all'
                    ? t('lobbies.empty.title')
                    : t('lobbies.empty.noMatch');
                const text = filterState.gameType === 'all'
                    ? t('lobbies.empty.copy')
                    : t('lobbies.empty.tryAnother');

                return `
                    <h3>${title}</h3>
                    <p>${text}</p>
                    <div class="lobby-empty-actions">
                        <button class="btn-accent" type="button" data-action="open-create-lobby">${t('createLobby.submit')}</button>
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
                    activeCount.textContent = t('lobbies.activeCount', count);
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
                    return t('lobby.config.standard');
                }

                const config = lobby.gameConfig;
                const n = config.numberOfPlayers;
                if (n === 2) {
                    return config.cardsInHandNum != null ? t('lobby.config.base', 2, config.cardsInHandNum) : t('history.playersCount', 2);
                }
                if (n === 3) {
                    return t('history.playersCount', 3);
                }
                if (n === 4) {
                    return config.teamsEnabled ? t('lobbies.config.4teams') : t('lobbies.config.4solo');
                }
                return t('lobby.config.standard');
            }

            function renderPlayers(players, hostId) {
                if (!players.length) {
                    return `<span class="lobby-card-subtitle">${t('lobby.status.waiting')}</span>`;
                }

                return players.map((player) => {
                    const initial = escapeHtml((player.name || 'U').charAt(0).toUpperCase());
                    const name = escapeHtml(player.name || t('lobby.userFallback', player.id));
                    const isHost = hostId != null && String(player.id) === String(hostId);
                    return `
                        <span class="lobby-card-player">
                            <span class="lobby-card-avatar">${initial}</span>
                            <span>${name}</span>
                            ${isHost ? `<span class="chip">${t('lobby.host')}</span>` : ''}
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
                                <span class="chip">${escapeHtml(lobby.gameType || t('history.unknown'))}</span>
                                <h3>${escapeHtml(lobby.name || t('lobbies.lobbyFallback', lobby.id))}</h3>
                                <p class="lobby-card-subtitle">${t('lobbies.hostLabel', escapeHtml(lobby.host?.name || t('history.unknown')))}</p>
                            </div>
                            <span class="chip">${t('lobby.playerCount', players.length, lobby.maxPlayers ?? '')}</span>
                        </div>

                        <div class="lobby-card-settings">
                            <strong>${t('lobbies.settings')}</strong>
                            <p>${escapeHtml(describeLobbySettings(lobby))}</p>
                        </div>

                        <div class="lobby-card-players">
                            ${renderPlayers(players, hostId)}
                        </div>

                        <div class="lobby-card-footer">
                            <button class="btn btn-accent" type="button" data-join-lobby-id="${escapeHtml(lobby.id || '')}">
                                <img class="uc-icon" data-icon="login" src="/pics/light/login.svg" alt="" aria-hidden="true">
                                <span>${t('lobbies.joinLobby')}</span>
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
                        throw new Error(message || t('lobbies.refreshFailed'));
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
                    showToast(t('lobbies.toast.filterFailed.title'), error?.message || t('lobbies.toast.filterFailed.body'));
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
                    showToast(t('lobbies.toast.resetFailed.title'), error?.message || t('lobbies.toast.resetFailed.body'));
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
                    const error = new Error(message || t('lobbies.joinFailedFallback'));
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
                    const lobbyName = notice?.lobbyName || t('lobbies.yourLobby');
                    showToast(t('lobbies.toast.closed.title'), t('lobbies.toast.closed.body', lobbyName));
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
                        showToast(t('lobbies.toast.fullTitle'), error.message || t('lobbies.toast.full'));
                        return;
                    }

                    showToast(t('lobbies.toast.joinFailed.title'), error?.message || t('lobbies.toast.joinFailed.body'));
                }
            });

            joinCodeInput?.addEventListener('input', syncJoinCodeState);
            joinCodeForm?.addEventListener('submit', async (event) => {
                event.preventDefault();
                const code = normalizeCode(joinCodeInput?.value);
                if (!/^[A-Z0-9]{6}$/.test(code)) {
                    setJoinCodeStatus(t('join.invalidCode'), 'error');
                    joinCodeInput?.focus();
                    return;
                }

                joinCodeSubmit.disabled = true;
                setButtonLabel(joinCodeSubmit, t('join.joining'));
                setJoinCodeStatus(t('join.joiningLobby'), 'success');

                try {
                    await joinLobby({lobbyCode: code});
                } catch (error) {
                    if (error?.status === 409) {
                        setJoinCodeStatus(error.message || t('lobbies.fullShort'), 'error');
                    } else {
                        setJoinCodeStatus(error?.message || t('join.unable'), 'error');
                    }
                    setButtonLabel(joinCodeSubmit, t('common.join'));
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
                    showToast(t('lobbies.toast.savedFilterFailed.title'), error?.message || t('lobbies.toast.savedFilterFailed.body'));
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
