(() => {
    const storageKey = 'uc-theme';
    const savedTheme = localStorage.getItem(storageKey);
    const systemDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    const theme = savedTheme || (systemDark ? 'dark' : 'light');
    document.documentElement.setAttribute('data-theme', theme);
})();

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PROFILE_STATUS_TIMEOUT_MS = 3200;
let profileStatusTimer = null;
let sessionsStatusTimer = null;
let sessionToastTimer = null;
let cachedSessions = [];
let pendingSessionDeletion = null;
let sessionConfirmResolver = null;
let currentProfileSnapshot = {
    username: '',
    email: ''
};

function syncProfileSnapshotFromDom() {
    currentProfileSnapshot = {
        username: (document.getElementById('username')?.value || '').trim(),
        email: (document.getElementById('email')?.value || '').trim()
    };
}

function setRefreshButtonLoading(isLoading) {
    const button = document.getElementById('page-refresh');
    if (!button) {
        return;
    }

    button.classList.toggle('is-loading', isLoading);
    button.disabled = isLoading;
    button.textContent = isLoading ? 'Refreshing' : 'Refresh';
}

function setSaveButtonLoading(isLoading) {
    const button = document.getElementById('save-profile');
    if (!button) {
        return;
    }

    button.classList.toggle('is-loading', isLoading);
    button.disabled = isLoading;
    button.textContent = isLoading ? 'Saving' : 'Save';
}

function closeSessionConfirmModal(confirmed) {
    const overlay = document.getElementById('session-confirm-modal');
    if (!overlay) {
        return;
    }

    overlay.classList.remove('active');
    sessionConfirmResolver?.(confirmed);
    sessionConfirmResolver = null;
}

function confirmSessionAction(message, confirmLabel) {
    const overlay = document.getElementById('session-confirm-modal');
    const text = document.getElementById('session-confirm-text');
    const submit = document.getElementById('session-confirm-submit');

    if (!overlay || !text || !submit) {
        return Promise.resolve(false);
    }

    text.textContent = message;
    submit.textContent = confirmLabel;
    overlay.classList.add('active');

    return new Promise((resolve) => {
        sessionConfirmResolver = resolve;
    });
}

function initialiseSessionConfirmModal() {
    const overlay = document.getElementById('session-confirm-modal');
    const close = document.getElementById('session-confirm-close');
    const cancel = document.getElementById('session-confirm-cancel');
    const submit = document.getElementById('session-confirm-submit');

    if (!overlay || !close || !cancel || !submit) {
        return;
    }

    close.addEventListener('click', () => closeSessionConfirmModal(false));
    cancel.addEventListener('click', () => closeSessionConfirmModal(false));
    submit.addEventListener('click', () => closeSessionConfirmModal(true));

    overlay.addEventListener('click', (event) => {
        if (event.target === overlay) {
            closeSessionConfirmModal(false);
        }
    });

    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape' && overlay.classList.contains('active')) {
            closeSessionConfirmModal(false);
        }
    });
}

function positionSessionToast() {
    const toast = document.getElementById('session-toast');
    if (!toast) {
        return;
    }

    const overlay = document.getElementById('login-modal');
    const modal = overlay?.classList.contains('active') ? overlay.querySelector('.modal') : null;
    if (!modal) {
        toast.style.top = '';
        return;
    }

    const modalRect = modal.getBoundingClientRect();
    const toastHeight = toast.getBoundingClientRect().height || 0;
    const desiredTop = Math.max(0.75 * 16, modalRect.top - toastHeight - 12);
    toast.style.top = `${desiredTop}px`;
}

function setAnimatedStatus(elementId, message, type) {
    const status = document.getElementById(elementId);
    if (!status) {
        return;
    }

    const timerName = elementId === 'profile-status'
        ? 'profileStatusTimer'
        : elementId === 'session-toast'
            ? 'sessionToastTimer'
            : 'sessionsStatusTimer';
    const existingTimer = timerName === 'profileStatusTimer'
        ? profileStatusTimer
        : timerName === 'sessionToastTimer'
            ? sessionToastTimer
            : sessionsStatusTimer;
    if (existingTimer) {
        window.clearTimeout(existingTimer);
    }

    status.textContent = message;
    const baseClass = elementId === 'session-toast' ? 'profile-floating-status profile-status' : 'profile-status';
    status.className = `${baseClass} is-visible is-${type}`;

    if (elementId === 'session-toast') {
        window.requestAnimationFrame(() => {
            positionSessionToast();
            window.requestAnimationFrame(positionSessionToast);
        });
    }

    const nextTimer = window.setTimeout(() => hideAnimatedStatus(elementId), PROFILE_STATUS_TIMEOUT_MS);
    if (timerName === 'profileStatusTimer') {
        profileStatusTimer = nextTimer;
    } else if (timerName === 'sessionToastTimer') {
        sessionToastTimer = nextTimer;
    } else {
        sessionsStatusTimer = nextTimer;
    }
}

function clearAnimatedStatus(elementId) {
    const status = document.getElementById(elementId);
    if (!status) {
        return;
    }

    if (elementId === 'profile-status' && profileStatusTimer) {
        window.clearTimeout(profileStatusTimer);
        profileStatusTimer = null;
    }
    if (elementId === 'sessions-status' && sessionsStatusTimer) {
        window.clearTimeout(sessionsStatusTimer);
        sessionsStatusTimer = null;
    }
    if (elementId === 'session-toast' && sessionToastTimer) {
        window.clearTimeout(sessionToastTimer);
        sessionToastTimer = null;
    }

    status.textContent = '';
    status.className = elementId === 'session-toast' ? 'profile-floating-status profile-status' : 'profile-status';
    if (elementId === 'session-toast') {
        status.style.top = '';
    }
}

function hideAnimatedStatus(elementId) {
    const status = document.getElementById(elementId);
    if (!status || !status.textContent) {
        clearAnimatedStatus(elementId);
        return;
    }

    if (elementId === 'profile-status' && profileStatusTimer) {
        window.clearTimeout(profileStatusTimer);
        profileStatusTimer = null;
    }
    if (elementId === 'sessions-status' && sessionsStatusTimer) {
        window.clearTimeout(sessionsStatusTimer);
        sessionsStatusTimer = null;
    }
    if (elementId === 'session-toast' && sessionToastTimer) {
        window.clearTimeout(sessionToastTimer);
        sessionToastTimer = null;
    }

    status.classList.remove('is-visible');
    status.classList.add('is-hiding');

    window.setTimeout(() => {
        if (status.classList.contains('is-hiding')) {
            clearAnimatedStatus(elementId);
        }
    }, 280);
}

function setProfileStatus(message, type) {
    setAnimatedStatus('profile-status', message, type);
    setAnimatedStatus('session-toast', message, type);
}

function clearProfileStatus() {
    clearAnimatedStatus('profile-status');
    clearAnimatedStatus('session-toast');
}

function setSessionsStatus(message, type) {
    setAnimatedStatus('session-toast', message, type);
}

function clearSessionsStatus() {
    clearAnimatedStatus('session-toast');
}

function formatInstant(value) {
    if (!value) {
        return 'Unavailable';
    }

    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return 'Unavailable';
    }

    return new Intl.DateTimeFormat(undefined, {
        dateStyle: 'medium',
        timeStyle: 'short'
    }).format(date);
}

function formatSessionCardInstant(value) {
    if (!value) {
        return 'Unavailable';
    }

    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return 'Unavailable';
    }

    return new Intl.DateTimeFormat(undefined, {
        weekday: 'short',
        month: 'short',
        day: 'numeric',
        hour: 'numeric',
        minute: '2-digit'
    }).format(date);
}

function sessionLabel(session) {
    const client = session.clientType || null;
    const os = session.os || null;

    if (client && os) {
        return `${client} on ${os}`;
    }
    return client || os || 'Unknown session';
}

function sessionSubtitle(session) {
    const location = [session.region, session.country].filter(Boolean).join(', ');
    if (session.currentSession) {
        return location ? `This device • ${location}` : 'This device';
    }
    return location || 'Another signed-in device';
}

function escapeHtml(value) {
    return String(value ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');
}

function initialiseTabs() {
    const tabs = Array.from(document.querySelectorAll('[data-profile-tab]'));
    const panels = Array.from(document.querySelectorAll('[data-profile-panel]'));
    let switching = false;
    const validTabs = new Set(tabs.map((tab) => tab.getAttribute('data-profile-tab')));

    const setActiveTabState = (selected) => {
        tabs.forEach((candidate) => {
            candidate.classList.toggle('is-active', candidate.getAttribute('data-profile-tab') === selected);
        });
        panels.forEach((panel) => {
            panel.classList.toggle('is-active', panel.getAttribute('data-profile-panel') === selected);
            panel.classList.remove('is-entering', 'is-leaving');
        });
    };

    const getHashTab = () => {
        const hash = window.location.hash.replace('#', '');
        return validTabs.has(hash) ? hash : null;
    };

    const updateHash = (selected) => {
        const nextHash = `#${selected}`;
        if (window.location.hash !== nextHash) {
            window.history.replaceState(null, '', nextHash);
        }
    };

    tabs.forEach((tab) => {
        tab.addEventListener('click', () => {
            if (switching || tab.classList.contains('is-active')) {
                return;
            }

            const selected = tab.getAttribute('data-profile-tab');
            const currentPanel = panels.find((panel) => panel.classList.contains('is-active'));
            const nextPanel = panels.find((panel) => panel.getAttribute('data-profile-panel') === selected);

            if (!nextPanel) {
                return;
            }

            switching = true;
            updateHash(selected);
            tabs.forEach((candidate) => {
                candidate.classList.toggle('is-active', candidate === tab);
            });

            if (selected === 'sessions') {
                refreshSessions();
            }

            if (currentPanel && currentPanel !== nextPanel) {
                currentPanel.classList.add('is-leaving');
                window.setTimeout(() => {
                    currentPanel.classList.remove('is-active', 'is-leaving');
                    nextPanel.classList.add('is-active', 'is-entering');
                    window.setTimeout(() => {
                        nextPanel.classList.remove('is-entering');
                        switching = false;
                    }, 280);
                }, 200);
                return;
            }

            nextPanel.classList.add('is-active', 'is-entering');
            window.setTimeout(() => {
                nextPanel.classList.remove('is-entering');
                switching = false;
            }, 280);
        });
    });

    const initialTab = getHashTab();
    if (initialTab) {
        setActiveTabState(initialTab);
        if (initialTab === 'sessions') {
            refreshSessions();
        }
    } else {
        updateHash(getActiveProfileTab());
    }

    window.addEventListener('hashchange', () => {
        if (switching) {
            return;
        }

        const hashTab = getHashTab();
        if (!hashTab) {
            updateHash(getActiveProfileTab());
            return;
        }

        setActiveTabState(hashTab);
        if (hashTab === 'sessions') {
            refreshSessions();
        }
    });
}

function getActiveProfileTab() {
    return document.querySelector('[data-profile-tab].is-active')?.getAttribute('data-profile-tab') || 'overview';
}

async function verifyRecentSession(onVerified) {
    if (!window.ucAuthModal?.requestRecentVerification) {
        throw new Error('Recent verification is not available on this page.');
    }

    const verified = await window.ucAuthModal.requestRecentVerification(
        onVerified ? { onVerified } : undefined
    );
    if (!verified) {
        throw new Error('Verification was cancelled.');
    }
}

function rememberPendingSessionDeletion(sessionId, isCurrentSession) {
    pendingSessionDeletion = { sessionId, isCurrentSession };
}

function clearPendingSessionDeletion() {
    pendingSessionDeletion = null;
}

async function save({ allowRetry = true } = {}) {
    const username = document.getElementById('username').value.trim();
    const email = document.getElementById('email').value.trim();

    clearProfileStatus();

    if (!username) {
        setProfileStatus('Username cannot be blank.', 'error');
        return;
    }

    if (!EMAIL_PATTERN.test(email)) {
        setProfileStatus('Wrong email format.', 'error');
        return;
    }

    if (username === currentProfileSnapshot.username && email === currentProfileSnapshot.email) {
        setProfileStatus('No changes to save.', 'error');
        return;
    }

    try {
        setSaveButtonLoading(true);
        const response = await fetch('/api/profile', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            credentials: 'include',
            body: JSON.stringify({
                username,
                email
            })
        });

        if (response.status === 403 && allowRetry) {
            setProfileStatus('Recent verification is required. Check your email for the code.', 'error');
            await verifyRecentSession();
            await save({ allowRetry: false });
            return;
        }

        if (!response.ok) {
            if (response.status === 400) {
                setProfileStatus('Could not save profile. Check the email and username format.', 'error');
                return;
            }

            setProfileStatus('Could not save profile. Please try again.', 'error');
            throw new Error(`Response status: ${response.status}`);
        }

        await updateProfile(await response.json());
        setProfileStatus('Profile saved successfully.', 'success');
    } catch (error) {
        if (error.message === 'Verification was cancelled.') {
            setProfileStatus('Profile update cancelled.', 'error');
            return;
        }

        if (!document.getElementById('profile-status')?.textContent) {
            setProfileStatus('Network error while saving profile.', 'error');
        }
        console.error(error.message);
    } finally {
        setSaveButtonLoading(false);
    }
}

async function refresh() {
    try {
        const [profileResponse, sessionsResponse] = await Promise.all([
            fetch('/api/profile', { credentials: 'include' }),
            fetch('/api/profile/sessions', { credentials: 'include' })
        ]);

        if (!profileResponse.ok || !sessionsResponse.ok) {
            throw new Error(`Refresh failed: ${profileResponse.status}/${sessionsResponse.status}`);
        }

        await updateProfile(await profileResponse.json());
        renderSessions(await sessionsResponse.json());
        clearProfileStatus();
        clearSessionsStatus();
    } catch (error) {
        console.error(error.message);
    }
}

async function refreshSessions() {
    try {
        const response = await fetch('/api/profile/sessions', { credentials: 'include' });
        if (!response.ok) {
            throw new Error(`Response status: ${response.status}`);
        }

        renderSessions(await response.json());
        clearSessionsStatus();
    } catch (error) {
        setSessionsStatus('Could not load sessions. Please refresh the page.', 'error');
        console.error(error.message);
    }
}

async function animateSessionRemoval(sessionId) {
    const card = document.querySelector(`[data-session-card="${CSS.escape(sessionId)}"]`);
    if (!card) {
        return;
    }

    card.classList.add('is-removing');
    await new Promise((resolve) => window.setTimeout(resolve, 280));
}

async function deleteSession(sessionId, isCurrentSession, { allowRetry = true } = {}) {
    clearSessionsStatus();

    try {
        const response = await fetch(`/api/profile/sessions?id=${encodeURIComponent(sessionId)}`, {
            method: 'DELETE',
            credentials: 'include',
            redirect: 'manual'
        });

        if (response.status === 403 && allowRetry) {
            rememberPendingSessionDeletion(sessionId, isCurrentSession);
            setSessionsStatus('Recent verification is required before removing a session.', 'error');
            await verifyRecentSession();
            const pending = pendingSessionDeletion;
            if (!pending) {
                throw new Error('Pending session removal was lost.');
            }
            clearPendingSessionDeletion();
            await deleteSession(pending.sessionId, pending.isCurrentSession, { allowRetry: false });
            return;
        }

        if (response.type === 'opaqueredirect' || response.status === 302) {
            await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' }).catch(() => {});
            window.location.href = '/';
            return;
        }

        if (!response.ok) {
            setSessionsStatus('Could not remove session. Please try again.', 'error');
            throw new Error(`Response status: ${response.status}`);
        }

        if (isCurrentSession) {
            await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' }).catch(() => {});
            window.location.href = '/';
            return;
        }

        clearPendingSessionDeletion();
        await animateSessionRemoval(sessionId);
        setSessionsStatus('Session removed.', 'success');
        await refreshSessions();
    } catch (error) {
        if (error.message === 'Verification was cancelled.') {
            clearPendingSessionDeletion();
            setSessionsStatus('Session removal cancelled.', 'error');
            return;
        }

        setSessionsStatus('Network error while removing the session.', 'error');
        console.error(error.message);
    }
}

function renderSessions(sessions) {
    cachedSessions = Array.isArray(sessions) ? sessions : [];

    const container = document.getElementById('sessions-list');
    if (!container) {
        return;
    }

    if (!cachedSessions.length) {
        container.innerHTML = `
            <article class="profile-session-card profile-session-card--empty">
                <p>No sessions found for this account.</p>
            </article>
        `;
        return;
    }

    const createMetaItem = (label, value, modifier = '') => {
        if (!value || value === 'Unavailable') {
            return '';
        }

        return `
            <div class="profile-session-meta-item ${modifier}">
                <strong>${escapeHtml(label)}</strong>
                <span>${escapeHtml(value)}</span>
            </div>
        `;
    };

    container.innerHTML = cachedSessions.map((session) => `
        <article class="profile-session-card ${session.currentSession ? 'is-current' : ''}" data-session-card="${escapeHtml(session.id)}">
            <div class="profile-session-primary">
                <div>
                    <strong>${escapeHtml(sessionLabel(session))}</strong>
                    <p>${escapeHtml(sessionSubtitle(session))}</p>
                </div>
            </div>
            <div class="profile-session-meta">
                ${createMetaItem('Last active', formatSessionCardInstant(session.lastSeenAt))}
                ${createMetaItem('Verified', formatSessionCardInstant(session.lastAuthenticatedAt))}
                ${createMetaItem('Started', formatSessionCardInstant(session.firstSeenAt))}
                ${createMetaItem('Device ID', session.deviceId)}
            </div>
            <div class="profile-session-actions">
                ${session.currentSession ? '<span class="profile-session-badge profile-session-badge--action">Current session</span>' : ''}
                <button
                    class="btn ${session.currentSession ? 'danger' : ''}"
                    type="button"
                    data-delete-session="${escapeHtml(session.id)}"
                    data-current-session="${session.currentSession ? 'true' : 'false'}">
                    ${session.currentSession ? 'Log out this device' : 'Remove session'}
                </button>
            </div>
        </article>
    `).join('');

    container.querySelectorAll('[data-delete-session]').forEach((button) => {
        button.addEventListener('click', async () => {
            const sessionId = button.getAttribute('data-delete-session');
            const isCurrentSession = button.getAttribute('data-current-session') === 'true';
            const confirmed = await confirmSessionAction(
                isCurrentSession
                    ? 'This will log out your current device. Continue?'
                    : 'Remove this session from your account?',
                isCurrentSession ? 'Log out' : 'Remove session'
            );

            if (!confirmed) {
                return;
            }

            await deleteSession(sessionId, isCurrentSession);
        });
    });
}

async function updateProfile(data) {
    currentProfileSnapshot = {
        username: (data.username || '').trim(),
        email: (data.email || '').trim()
    };

    document.getElementById('username').value = data.username || '';
    const usernameHeader = document.getElementById('username-header');
    if (usernameHeader) {
        usernameHeader.textContent = data.username || '';
    }
    document.getElementById('email').value = data.email || '';
    document.getElementById('roles').innerText = (data.roles || []).join(', ');
    document.getElementById('id').innerText = data.id || '';
    document.getElementById('games_played').innerText = data.gamesPlayed ?? 0;
    document.getElementById('games_won').innerText = data.gamesWon ?? 0;

    const perGameType = document.getElementById('per-game-type');
    perGameType.innerText = '';

    Object.entries(data.playedAndWonGames || {}).forEach(([gameType, arr]) => {
        const gameCard = document.createElement('div');
        gameCard.className = 'profile-game-card';

        const title = document.createElement('span');
        title.textContent = gameType;

        const br1 = document.createElement('br');

        const playedGames = document.createElement('span');
        playedGames.textContent = `${arr[0]}`;

        const br2 = document.createElement('br');

        const wonGames = document.createElement('span');
        wonGames.textContent = `${arr[1]}`;

        gameCard.appendChild(title);
        gameCard.appendChild(br1);
        gameCard.append('Games played: ');
        gameCard.appendChild(playedGames);
        gameCard.appendChild(br2);
        gameCard.append('Games won: ');
        gameCard.appendChild(wonGames);

        perGameType.appendChild(gameCard);
    });
}

document.addEventListener('DOMContentLoaded', async () => {
    initialiseTabs();
    initialiseSessionConfirmModal();
    syncProfileSnapshotFromDom();
    window.addEventListener('resize', positionSessionToast);
    document.getElementById('page-refresh')?.addEventListener('click', async () => {
        setRefreshButtonLoading(true);
        try {
            if (getActiveProfileTab() === 'sessions') {
                await refreshSessions();
                return;
            }
            await refresh();
        } finally {
            setRefreshButtonLoading(false);
        }
    });
    document.getElementById('save-profile')?.addEventListener('click', () => save());
    await refreshSessions();
});
