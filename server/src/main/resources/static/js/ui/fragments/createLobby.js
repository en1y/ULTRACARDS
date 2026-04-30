(() => {
    const gameSettingsAnimationDurationMs = 420;
    const overlay = document.getElementById('create-div');
    const closeButtons = overlay?.querySelectorAll('[data-close-create]') || [];
    const form = document.getElementById('create-lobby-form');
    const lobbyNameInput = document.getElementById('create-lobby-name');
    const gameTypeSelect = document.getElementById('create-game-type');
    const settingsElement = document.getElementById('create-game-settings');
    const submitButton = document.getElementById('create-lobby-submit');
    const statusText = document.getElementById('create-lobby-status');

    if (!overlay || !form || !gameTypeSelect || !settingsElement || !submitButton || !statusText) {
        return;
    }

    function setStatus(message, type = '') {
        statusText.textContent = message;
        statusText.classList.toggle('error', type === 'error');
        statusText.classList.toggle('success', type === 'success');
    }

    function setSettingsContent(nodes) {
        window.clearTimeout(settingsElement.hideTimeoutId);

        if (!nodes.length) {
            settingsElement.hidden = true;
            settingsElement.hideTimeoutId = window.setTimeout(() => {
                settingsElement.innerHTML = '';
            }, gameSettingsAnimationDurationMs);
            return;
        }

        settingsElement.innerHTML = '';
        settingsElement.append(...nodes);

        if (settingsElement.hidden) {
            window.requestAnimationFrame(() => {
                settingsElement.hidden = false;
            });
        }
    }

    function buildSettingsSelect(title, settings) {
        const titleElement = document.createElement('h3');
        titleElement.className = 'create-lobby-settings-title';
        titleElement.textContent = `${title} settings`;

        const field = document.createElement('div');
        field.className = 'field';

        const label = document.createElement('label');
        label.setAttribute('for', 'create-properties');
        label.textContent = 'Game configuration';

        const select = document.createElement('select');
        select.id = 'create-properties';

        Object.entries(settings).forEach(([key, setting]) => {
            const option = document.createElement('option');
            option.textContent = setting.ui_text;
            option.value = key;
            select.appendChild(option);
        });

        field.append(label, select);
        return [titleElement, field];
    }

    function buildSettingsNotice(title, text) {
        const titleElement = document.createElement('h3');
        titleElement.className = 'create-lobby-settings-title';
        titleElement.textContent = `${title} settings`;

        const notice = document.createElement('p');
        notice.className = 'create-lobby-settings-note';
        notice.textContent = text;

        return [titleElement, notice];
    }

    function syncCreateState() {
        const gameType = gameTypeSelect.value;
        const settingKey = document.getElementById('create-properties')?.value || '';
        const canCreate = gameType !== 'all' && !!settingKey && supportsLobbyCreation(gameType, settingKey);

        submitButton.disabled = !canCreate;
        if (canCreate) {
            setStatus('Create the lobby!');
            return;
        }

        if (gameType === 'all') {
            setStatus('Pick a game to continue.');
            return;
        }

        setStatus('Lobby creation is not available for this setup.', 'error');
    }

    function applyGameTypeSettings() {
        const gameType = gameTypeSelect.value;
        if (gameType === 'all') {
            setSettingsContent([]);
            syncCreateState();
            return;
        }

        const selectedGame = getGameTypeSettings(gameType);
        const title = gameType.charAt(0).toUpperCase() + gameType.slice(1);
        if (!selectedGame || !Object.keys(selectedGame).length) {
            setSettingsContent(buildSettingsNotice(title, 'Lobby creation is not available for this game yet.'));
            syncCreateState();
            return;
        }

        setSettingsContent(buildSettingsSelect(title, selectedGame));
        document.getElementById('create-properties')?.addEventListener('change', syncCreateState);
        syncCreateState();
    }

    function resetCreateMenu() {
        form.reset();
        setSettingsContent([]);
        submitButton.textContent = 'Create Lobby';
        syncCreateState();
    }

    function openCreateMenu() {
        resetCreateMenu();
        overlay.classList.add('active');
        window.setTimeout(() => lobbyNameInput?.focus(), 0);
    }

    function closeCreateMenu() {
        overlay.classList.remove('active');
        resetCreateMenu();
    }

    async function createLobby() {
        const gameType = gameTypeSelect.value;
        const settingKey = document.getElementById('create-properties')?.value || '';

        if (!gameType || gameType === 'all') {
            setStatus('Choose a game type first.', 'error');
            gameTypeSelect.focus();
            return;
        }

        if (!settingKey) {
            setStatus('Choose a game configuration first.', 'error');
            return;
        }

        if (!supportsLobbyCreation(gameType, settingKey)) {
            setStatus('Lobby creation is not implemented for that game type yet.', 'error');
            return;
        }

        submitButton.disabled = true;
        submitButton.textContent = 'Creating...';
        setStatus('Creating lobby...', 'success');

        try {
            const response = await fetch('/api/lobby/create', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                credentials: 'include',
                body: buildLobbyCreatePayload(gameType, settingKey, lobbyNameInput?.value)
            });

            if (!response.ok) {
                const message = (await response.text()).trim();
                throw new Error(message || 'Failed to create new lobby.');
            }

            const createdLobby = await response.json();
            if (createdLobby?.id) {
                localStorage.setItem('lobbyId', createdLobby.id);
            }

            window.location.href = '/lobbies';
        } catch (error) {
            setStatus(error?.message || 'Failed to create new lobby.', 'error');
            submitButton.textContent = 'Create Lobby';
            submitButton.disabled = !supportsLobbyCreation(gameType, settingKey);
        }
    }

    document.addEventListener('click', (event) => {
        const openButton = event.target.closest('[data-action="open-create-lobby"]');
        if (!openButton) {
            return;
        }

        event.preventDefault();
        openCreateMenu();
    });

    closeButtons.forEach((button) => {
        button.addEventListener('click', closeCreateMenu);
    });

    overlay.addEventListener('click', (event) => {
        if (event.target === overlay) {
            closeCreateMenu();
        }
    });

    gameTypeSelect.addEventListener('change', applyGameTypeSettings);
    form.addEventListener('submit', (event) => {
        event.preventDefault();
        createLobby();
    });

    syncCreateState();
})();
