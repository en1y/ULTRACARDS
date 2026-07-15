(() => {
    function setButtonLabel(button, label) {
        const text = button?.querySelector('span');
        if (text) {
            text.textContent = label;
        } else if (button) {
            button.textContent = label;
        }
    }

    const toggleButton = document.getElementById('home-public-toggle');
    const publicCopy = document.getElementById('home-public-auth-copy');

    if (!toggleButton || !publicCopy) {
        return;
    }

    let isAnimatingOpen = false;
    let isAnimatingClosed = false;

    publicCopy.addEventListener('transitionend', (event) => {
        if (event.propertyName !== 'max-height') {
            return;
        }

        if (publicCopy.classList.contains('is-open')) {
            publicCopy.style.maxHeight = 'none';
            isAnimatingOpen = false;
            return;
        }

        if (isAnimatingClosed) {
            publicCopy.hidden = true;
            publicCopy.style.maxHeight = '';
            isAnimatingClosed = false;
            isAnimatingOpen = false;
        }
    });

    toggleButton.addEventListener('click', () => {
        const isExpanded = toggleButton.getAttribute('aria-expanded') === 'true';
        const nextExpanded = !isExpanded;

        toggleButton.setAttribute('aria-expanded', String(nextExpanded));
        toggleButton.textContent = nextExpanded ? t('home.hideAbout') : t('home.showAbout');

        if (nextExpanded) {
            isAnimatingClosed = false;
            publicCopy.hidden = false;
            publicCopy.style.maxHeight = '0px';
            publicCopy.classList.remove('is-open');
            publicCopy.getBoundingClientRect();
            publicCopy.classList.add('is-open');
            isAnimatingOpen = true;
            publicCopy.style.maxHeight = `${publicCopy.scrollHeight}px`;
            return;
        }

        if (isAnimatingOpen || publicCopy.style.maxHeight === 'none') {
            publicCopy.style.maxHeight = `${publicCopy.scrollHeight}px`;
            publicCopy.getBoundingClientRect();
        }

        publicCopy.classList.remove('is-open');
        publicCopy.style.maxHeight = '0px';
        isAnimatingClosed = true;
    });
})();

(() => {
    const toast = document.getElementById('home-toast');
    const toastTitle = document.getElementById('home-toast-title');
    const toastText = document.getElementById('home-toast-text');
    if (!toast || !toastTitle || !toastText) {
        return;
    }

    let rawNotice = null;
    try {
        rawNotice = window.sessionStorage.getItem('uc-lobby-closed-notice');
        window.sessionStorage.removeItem('uc-lobby-closed-notice');
    } catch (error) {
        return;
    }

    if (!rawNotice) {
        return;
    }

    try {
        const notice = JSON.parse(rawNotice);
        const lobbyName = notice?.lobbyName || t('lobbies.yourLobby');
        toastTitle.textContent = t('lobbies.toast.closed.title');
        toastText.textContent = t('lobbies.toast.closed.body', lobbyName);
        toast.hidden = false;
        window.requestAnimationFrame(() => toast.classList.add('is-visible'));
        window.setTimeout(() => {
            toast.classList.remove('is-visible');
            window.setTimeout(() => { toast.hidden = true; }, 260);
        }, 2800);
    } catch (error) {
        // Ignore malformed stale notices.
    }
})();

(() => {
    const joinForm = document.getElementById('join-code-form');
    const joinInput = document.getElementById('join-code-input');
    const submitButton = document.getElementById('join-code-submit');
    const statusText = document.getElementById('join-code-status');

    if (!joinForm || !joinInput || !submitButton || !statusText) {
        return;
    }

    function normalizeCode(value) {
        return String(value || '').toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 6);
    }

    function setButtonLabel(button, label) {
        const text = button?.querySelector('span');
        if (text) {
            text.textContent = label;
        } else if (button) {
            button.textContent = label;
        }
    }

    function setStatus(message, type = '') {
        statusText.textContent = message;
        statusText.classList.toggle('error', type === 'error');
        statusText.classList.toggle('success', type === 'success');
    }

    function syncInputState() {
        const code = normalizeCode(joinInput.value);
        if (joinInput.value !== code) {
            joinInput.value = code;
        }

        const isReady = /^[A-Z0-9]{6}$/.test(code);
        submitButton.disabled = !isReady;
        setStatus(isReady ? t('home.codeStatus.ready') : t('home.codeStatus.hint'), isReady ? 'success' : '');
    }

    async function joinByCode() {
        const code = normalizeCode(joinInput.value);
        joinInput.value = code;

        if (!/^[A-Z0-9]{6}$/.test(code)) {
            submitButton.disabled = true;
            setStatus(t('join.invalidCode'), 'error');
            joinInput.focus();
            return;
        }

        submitButton.disabled = true;
        setButtonLabel(submitButton, t('join.joining'));
        setStatus(t('join.joiningLobby'), 'success');

        try {
            const response = await fetch('/api/lobby/join', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                credentials: 'include',
                body: JSON.stringify({ lobbyCode: code })
            });

            if (!response.ok) {
                const message = (await response.text()).trim();
                throw new Error(message || t('join.unable'));
            }

            window.location.href = '/lobbies';
        } catch (error) {
            setStatus(error?.message || t('join.unable'), 'error');
            setButtonLabel(submitButton, t('common.join'));
            submitButton.disabled = false;
            joinInput.focus();
        }
    }

    joinInput.addEventListener('input', syncInputState);
    joinForm.addEventListener('submit', (event) => {
        event.preventDefault();
        joinByCode();
    });

    syncInputState();
})();
