(() => {
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
        toggleButton.textContent = nextExpanded ? 'Hide about ULTRACARDS' : 'Show about ULTRACARDS';

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
        setStatus(isReady ? 'Ready to join.' : 'Type the lobby code to join.', isReady ? 'success' : '');
    }

    async function joinByCode() {
        const code = normalizeCode(joinInput.value);
        joinInput.value = code;

        if (!/^[A-Z0-9]{6}$/.test(code)) {
            submitButton.disabled = true;
            setStatus('Enter a valid 6-character code.', 'error');
            joinInput.focus();
            return;
        }

        submitButton.disabled = true;
        submitButton.textContent = 'Joining...';
        setStatus('Joining lobby...', 'success');

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
                throw new Error(message || 'Unable to join this lobby.');
            }

            window.location.href = '/lobbies';
        } catch (error) {
            setStatus(error?.message || 'Unable to join this lobby.', 'error');
            submitButton.textContent = 'Join';
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
