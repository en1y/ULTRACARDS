(() => {
    const gameSettingsAnimationDurationMs = 420;
    const overlay = document.getElementById('create-div');
    const closeButtons = overlay?.querySelectorAll('[data-close-create]') || [];
    const form = document.getElementById('create-lobby-form');
    const lobbyNameInput = document.getElementById('create-lobby-name');
    const gameTypeSelect = document.getElementById('create-game-type');
    const publicInput = document.getElementById('create-lobby-public');
    const visibilityText = document.getElementById('create-lobby-visibility-text');
    const visibilityLabel = document.getElementById('create-lobby-public-toggle-label');
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

    function setAnimatedText(element, text) {
        if (!element || element.textContent === text) {
            return;
        }

        element.textContent = text;
        element.classList.remove('create-lobby-text-swap');
        element.getBoundingClientRect();
        element.classList.add('create-lobby-text-swap');
    }

    function syncVisibilityText() {
        if (!publicInput) {
            return;
        }

        setAnimatedText(visibilityText, publicInput.checked ? t('createLobby.visibility.publicLobby') : t('createLobby.visibility.privateLobby'));
        setAnimatedText(visibilityLabel, publicInput.checked ? t('createLobby.visibility.public') : t('createLobby.visibility.private'));
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
        titleElement.textContent = t('createLobby.settingsTitle', title);

        const field = document.createElement('div');
        field.className = 'field';

        const label = document.createElement('label');
        label.setAttribute('for', 'create-properties');
        label.textContent = t('createLobby.gameConfiguration');

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
        titleElement.textContent = t('createLobby.settingsTitle', title);

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
            setStatus(t('createLobby.status.ready'));
            return;
        }

        if (gameType === 'all') {
            setStatus(t('createLobby.status.pickGame'));
            return;
        }

        setStatus(t('createLobby.status.unavailable'), 'error');
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
            setSettingsContent(buildSettingsNotice(title, t('createLobby.status.gameUnavailable')));
            syncCreateState();
            return;
        }

        setSettingsContent(buildSettingsSelect(title, selectedGame));
        document.getElementById('create-properties')?.addEventListener('change', syncCreateState);
        syncCreateState();
    }

    function resetCreateMenu() {
        form.reset();
        if (publicInput) {
            publicInput.checked = true;
        }
        setSettingsContent([]);
        submitButton.textContent = t('createLobby.submit');
        syncVisibilityText();
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
            setStatus(t('createLobby.status.chooseType'), 'error');
            gameTypeSelect.focus();
            return;
        }

        if (!settingKey) {
            setStatus(t('createLobby.status.chooseConfig'), 'error');
            return;
        }

        if (!supportsLobbyCreation(gameType, settingKey)) {
            setStatus(t('createLobby.status.notImplemented'), 'error');
            return;
        }

        submitButton.disabled = true;
        submitButton.textContent = t('createLobby.creating');
        setStatus(t('createLobby.creatingLobby'), 'success');

        try {
            const response = await fetch('/api/lobby/create', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                credentials: 'include',
                body: buildLobbyCreatePayload(gameType, settingKey, lobbyNameInput?.value, publicInput?.checked !== false)
            });

            if (!response.ok) {
                const message = (await response.text()).trim();
                throw new Error(message || t('createLobby.failed'));
            }

            const createdLobby = await response.json();
            if (createdLobby?.id) {
                localStorage.setItem('lobbyId', createdLobby.id);
            }

            window.location.href = '/lobbies';
        } catch (error) {
            setStatus(error?.message || t('createLobby.failed'), 'error');
            submitButton.textContent = t('createLobby.submit');
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
    publicInput?.addEventListener('change', syncVisibilityText);
    form.addEventListener('submit', (event) => {
        event.preventDefault();
        createLobby();
    });

    syncVisibilityText();
    syncCreateState();
})();
