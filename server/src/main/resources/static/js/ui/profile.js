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
let cachedDetailedStats = null;
let cachedFriends = [];
let cachedBlockedFriends = [];
let detailedStatsFilters = {
    gameConfig: '',
    sort: 'recent',
    user: ''
};
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
    const label = button.querySelector('span');
    if (label) {
        label.textContent = isLoading ? 'Refreshing' : 'Refresh';
    } else {
        button.textContent = isLoading ? 'Refreshing' : 'Refresh';
    }
}

function setSaveButtonLoading(isLoading) {
    const button = document.getElementById('save-profile');
    if (!button) {
        return;
    }

    button.classList.toggle('is-loading', isLoading);
    button.disabled = isLoading;
    const label = button.querySelector('span');
    if (label) {
        label.textContent = isLoading ? 'Saving' : 'Save';
    } else {
        button.textContent = isLoading ? 'Saving' : 'Save';
    }
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
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        hour12: false
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
        hour: '2-digit',
        minute: '2-digit',
        hour12: false
    }).format(date);
}

function ordinalDay(day) {
    if (day >= 11 && day <= 13) {
        return `${day}th`;
    }

    switch (day % 10) {
        case 1:
            return `${day}st`;
        case 2:
            return `${day}nd`;
        case 3:
            return `${day}rd`;
        default:
            return `${day}th`;
    }
}

function startOfMondayWeek(date) {
    const start = new Date(date.getFullYear(), date.getMonth(), date.getDate());
    const day = start.getDay();
    const mondayOffset = day === 0 ? -6 : 1 - day;
    start.setDate(start.getDate() + mondayOffset);
    return start;
}

function isSameMondayWeek(left, right) {
    return startOfMondayWeek(left).getTime() === startOfMondayWeek(right).getTime();
}

function formatLastPlayedAt(value) {
    if (!value) {
        return 'Never played';
    }

    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return 'Never played';
    }

    if (isSameMondayWeek(date, new Date())) {
        return new Intl.DateTimeFormat(undefined, { weekday: 'long' }).format(date);
    }

    const month = new Intl.DateTimeFormat(undefined, { month: 'long' }).format(date);
    return `${ordinalDay(date.getDate())} ${month}`;
}

function formatLastPlayedLabel(value) {
    return value ? `Last played on ${formatLastPlayedAt(value)}` : 'Never played';
}

function formatHistoryDate(value) {
    if (!value) {
        return 'Unknown time';
    }

    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return 'Unknown time';
    }

    return new Intl.DateTimeFormat(undefined, {
        dateStyle: 'medium',
        timeStyle: 'short',
        hour12: false
    }).format(date);
}

function formatInitialLastPlayedLabels() {
    document.querySelectorAll('[data-last-played-at]').forEach((element) => {
        element.textContent = formatLastPlayedLabel(element.dataset.lastPlayedAt);
    });
}

function lastPlayedTime(value) {
    if (!value) {
        return 0;
    }

    const time = new Date(value).getTime();
    return Number.isNaN(time) ? 0 : time;
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

function iconHtml(icon) {
    return `<img class="uc-icon" data-icon="${escapeHtml(icon)}" src="/pics/light/${escapeHtml(icon)}.svg" alt="" aria-hidden="true">`;
}

function isTypingTarget(target) {
    return target instanceof HTMLElement
        && (target.matches('input, textarea, select') || target.isContentEditable);
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
            } else if (selected === 'stats') {
                refreshDetailedStats();
            } else if (selected === 'friends') {
                refreshFriends();
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

    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape' && isTypingTarget(event.target)) {
            event.target.blur();
            return;
        }

        if ((event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') || isTypingTarget(event.target)) {
            return;
        }
        if (document.getElementById('friend-profile-modal')?.classList.contains('active')) {
            return;
        }
        if (window.ucHeader?.isUserProfilePopupOpen?.()) {
            return;
        }

        const activeIndex = tabs.findIndex((tab) => tab.classList.contains('is-active'));
        if (activeIndex < 0) {
            return;
        }

        event.preventDefault();
        const direction = event.key === 'ArrowRight' ? 1 : -1;
        const nextIndex = (activeIndex + direction + tabs.length) % tabs.length;
        const selected = tabs[nextIndex].getAttribute('data-profile-tab');
        updateHash(selected);
        setActiveTabState(selected);
        if (selected === 'sessions') {
            refreshSessions();
        } else if (selected === 'stats') {
            refreshDetailedStats();
        } else if (selected === 'friends') {
            refreshFriends();
        }
    });

    const initialTab = getHashTab();
    if (initialTab) {
        setActiveTabState(initialTab);
        if (initialTab === 'sessions') {
            refreshSessions();
        } else if (initialTab === 'stats') {
            refreshDetailedStats();
        } else if (initialTab === 'friends') {
            refreshFriends();
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
        } else if (hashTab === 'stats') {
            refreshDetailedStats();
        } else if (hashTab === 'friends') {
            refreshFriends();
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
        if (getActiveProfileTab() === 'stats') {
            await refreshDetailedStats({ clearStatus: false });
        }
        clearProfileStatus();
        clearSessionsStatus();
    } catch (error) {
        console.error(error.message);
    }
}

async function refreshDetailedStats({ clearStatus = true } = {}) {
    try {
        const response = await fetch('/api/profile/stats', { credentials: 'include' });
        if (!response.ok) {
            throw new Error(`Response status: ${response.status}`);
        }

        renderDetailedStats(await response.json());
        if (clearStatus) {
            clearSessionsStatus();
        }
        return true;
    } catch (error) {
        setSessionsStatus('Could not load detailed game stats. Please refresh the page.', 'error');
        console.error(error.message);
        return false;
    }
}

async function refreshSessions({ clearStatus = true } = {}) {
    try {
        const response = await fetch('/api/profile/sessions', { credentials: 'include' });
        if (!response.ok) {
            throw new Error(`Response status: ${response.status}`);
        }

        renderSessions(await response.json());
        if (clearStatus) {
            clearSessionsStatus();
        }
        return true;
    } catch (error) {
        setSessionsStatus('Could not load sessions. Please refresh the page.', 'error');
        console.error(error.message);
        return false;
    }
}

function gameDisplayName(gameType) {
    if (typeof window.getGameTypeDisplayName === 'function') {
        return window.getGameTypeDisplayName(gameType);
    }
    return String(gameType || '')
        .toLowerCase()
        .split('_')
        .filter(Boolean)
        .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
        .join(' ') || 'Unknown game';
}

function gameConfigKey(config, gameType = 'Briskula') {
    if (typeof window.resolveGameConfigKey === 'function') {
        return window.resolveGameConfigKey(gameType, config);
    }
    if (!config) {
        return '';
    }
    if (typeof config === 'string') {
        return config;
    }
    return '';
}

function gameConfigDisplayName(config, gameType = 'Briskula') {
    if (typeof window.getGameConfigDisplayName === 'function') {
        return window.getGameConfigDisplayName(gameType, config);
    }
    if (!config || typeof config === 'string') {
        return gameDisplayName(config);
    }

    const key = gameConfigKey(config, gameType);
    if (key) {
        return gameDisplayName(key);
    }

    const players = Number(config.numberOfPlayers);
    const cards = Number(config.cardsInHandNum);
    if (Number.isFinite(players) && Number.isFinite(cards)) {
        return `${players} players, ${cards} cards${config.teamsEnabled ? ', teams' : ''}`;
    }
    return 'Game config';
}

function normalizeGameStats(stats) {
    const rawPlayed = Number(stats?.played ?? 0);
    const rawWins = Number(stats?.wins ?? 0);
    const played = Number.isFinite(rawPlayed) ? rawPlayed : 0;
    const wins = Number.isFinite(rawWins) ? rawWins : 0;
    const losses = Math.max(played - wins, 0);
    const winRate = played > 0 ? Math.round((wins / played) * 100) : 0;
    return { played, wins, losses, winRate };
}

function compareStatsEntries(a, b, sortKey = detailedStatsFilters.sort) {
    const [aType, aStats] = Array.isArray(a) ? a : [gameConfigKey(a.gameConfig), a];
    const [bType, bStats] = Array.isArray(b) ? b : [gameConfigKey(b.gameConfig), b];
    const aNormalized = normalizeGameStats(aStats);
    const bNormalized = normalizeGameStats(bStats);

    switch (sortKey) {
        case 'user':
            return matchupLabel(aStats).localeCompare(matchupLabel(bStats));
        case 'gametype':
            return gameDisplayName(aType).localeCompare(gameDisplayName(bType));
        case 'played':
            return bNormalized.played - aNormalized.played || lastPlayedTime(bStats.lastPlayedAt) - lastPlayedTime(aStats.lastPlayedAt);
        case 'wins':
            return bNormalized.wins - aNormalized.wins || lastPlayedTime(bStats.lastPlayedAt) - lastPlayedTime(aStats.lastPlayedAt);
        case 'losses':
            return bNormalized.losses - aNormalized.losses || lastPlayedTime(bStats.lastPlayedAt) - lastPlayedTime(aStats.lastPlayedAt);
        case 'winRate':
            return bNormalized.winRate - aNormalized.winRate || bNormalized.played - aNormalized.played;
        case 'recent':
        default:
            return lastPlayedTime(bStats.lastPlayedAt) - lastPlayedTime(aStats.lastPlayedAt)
                || bNormalized.played - aNormalized.played;
    }
}

function renderGameStatsCard(gameType, stats, extraClass = '') {
    const normalized = normalizeGameStats(stats);
    return `
        <article class="profile-game-card ${escapeHtml(extraClass)}">
            <span>${escapeHtml(gameDisplayName(gameType))}</span>
            <div class="profile-stat-line">
                <strong>${normalized.played}</strong>
                <small>played</small>
            </div>
            <div class="profile-stat-line">
                <strong>${normalized.wins}</strong>
                <small>won</small>
            </div>
            <div class="profile-stat-line">
                <strong>${normalized.losses}</strong>
                <small>lost</small>
            </div>
            <div class="profile-win-rate" aria-label="${normalized.winRate}% win rate">
                <span style="width: ${normalized.winRate}%"></span>
            </div>
            <p>${normalized.winRate}% win rate</p>
            <small class="profile-last-played">${escapeHtml(formatLastPlayedLabel(stats?.lastPlayedAt))}</small>
        </article>
    `;
}

function matchupLabel(matchup) {
    if (matchup.relatedUsername) {
        return matchup.relatedUsername;
    }
    if (matchup.relatedUserId) {
        return `User ${matchup.relatedUserId}`;
    }
    return 'Unknown user';
}

function renderMatchupRows(matchups, emptyText) {
    if (!Array.isArray(matchups) || matchups.length === 0) {
        return `
            <tr>
                <td colspan="7">${escapeHtml(emptyText)}</td>
            </tr>
        `;
    }

    return matchups
        .slice()
        .sort(compareStatsEntries)
        .map((matchup) => {
            const stats = normalizeGameStats(matchup);
            return `
                <tr>
                    <td>${escapeHtml(matchupLabel(matchup))}</td>
                    <td>${escapeHtml(gameConfigDisplayName(matchup.gameConfig))}</td>
                    <td>${stats.played}</td>
                    <td>${stats.wins}</td>
                    <td>${stats.losses}</td>
                    <td>${stats.winRate}%</td>
                    <td>${escapeHtml(formatLastPlayedAt(matchup.lastPlayedAt))}</td>
                </tr>
            `;
        })
        .join('');
}

function renderMatchupTable(title, matchups, emptyText) {
    return `
        <article class="profile-stats-section">
            <div class="section-heading">
                <div>
                    <p class="section-kicker">User matchups</p>
                    <h2>${escapeHtml(title)}</h2>
                </div>
            </div>
            <div class="profile-table-wrap">
                <table class="profile-stat-table">
                    <thead>
                        <tr>
                            <th>User</th>
                            <th>Config</th>
                            <th>Played</th>
                            <th>Won</th>
                            <th>Lost</th>
                            <th>Win rate</th>
                            <th>Last played</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${renderMatchupRows(matchups, emptyText)}
                    </tbody>
                </table>
            </div>
        </article>
    `;
}

function collectBriskulaModes(briskulaStats, configStats) {
    const modeMap = new Map();

    Object.entries(configStats || {}).forEach(([gameConfig, stats]) => {
        modeMap.set(gameConfig, stats?.lastPlayedAt || null);
    });

    [...(briskulaStats.winsAgainstUser || []), ...(briskulaStats.winsWithTeammate || [])].forEach((matchup) => {
        const configKey = gameConfigKey(matchup.gameConfig);
        if (!configKey) {
            return;
        }

        const current = lastPlayedTime(modeMap.get(configKey));
        if (lastPlayedTime(matchup.lastPlayedAt) > current) {
            modeMap.set(configKey, matchup.lastPlayedAt || null);
        } else if (!modeMap.has(configKey)) {
            modeMap.set(configKey, matchup.lastPlayedAt || null);
        }
    });

    return Array.from(modeMap.entries())
        .sort((a, b) => lastPlayedTime(b[1]) - lastPlayedTime(a[1]) || gameDisplayName(a[0]).localeCompare(gameDisplayName(b[0])))
        .map(([mode]) => mode);
}

function selectDefaultBriskulaMode(modes) {
    if (!modes.length) {
        detailedStatsFilters.gameConfig = '';
        return;
    }

    if (!modes.includes(detailedStatsFilters.gameConfig)) {
        detailedStatsFilters.gameConfig = modes[0];
    }
}

function renderMatchupControls(modes) {
    const modeOptions = modes.length
        ? modes.map((mode) => `
            <option value="${escapeHtml(mode)}" ${mode === detailedStatsFilters.gameConfig ? 'selected' : ''}>
                ${escapeHtml(gameDisplayName(mode))}
            </option>
        `).join('')
        : '<option value="">No Briskula modes</option>';

    return `
        <div class="profile-stats-controls" aria-label="User matchup filters">
            <label class="profile-filter-field" for="stats-mode-filter">
                <span>Mode</span>
                <select id="stats-mode-filter" ${modes.length ? '' : 'disabled'}>
                    ${modeOptions}
                </select>
            </label>
            <label class="profile-filter-field" for="stats-sort">
                <span>Sort</span>
                <select id="stats-sort">
                    <option value="recent" ${detailedStatsFilters.sort === 'recent' ? 'selected' : ''}>Most recent</option>
                    <option value="user" ${detailedStatsFilters.sort === 'user' ? 'selected' : ''}>User</option>
                    <option value="gametype" ${detailedStatsFilters.sort === 'gametype' ? 'selected' : ''}>Game type</option>
                    <option value="played" ${detailedStatsFilters.sort === 'played' ? 'selected' : ''}>Played</option>
                    <option value="wins" ${detailedStatsFilters.sort === 'wins' ? 'selected' : ''}>Wins</option>
                    <option value="losses" ${detailedStatsFilters.sort === 'losses' ? 'selected' : ''}>Losses</option>
                    <option value="winRate" ${detailedStatsFilters.sort === 'winRate' ? 'selected' : ''}>Win rate</option>
                </select>
            </label>
            <label class="profile-filter-field" for="stats-user-filter">
                <span>User</span>
                <input id="stats-user-filter" type="search" placeholder="Enter player username" value="${escapeHtml(detailedStatsFilters.user)}">
            </label>
        </div>
    `;
}

function bindMatchupControls(container) {
    const modeSelect = container.querySelector('#stats-mode-filter');
    const sortSelect = container.querySelector('#stats-sort');
    const userInput = container.querySelector('#stats-user-filter');

    modeSelect?.addEventListener('change', () => {
        detailedStatsFilters.gameConfig = modeSelect.value;
        renderDetailedStats(cachedDetailedStats);
    });

    sortSelect?.addEventListener('change', () => {
        detailedStatsFilters.sort = sortSelect.value || 'recent';
        renderDetailedStats(cachedDetailedStats);
    });

    userInput?.addEventListener('input', () => {
        detailedStatsFilters.user = userInput.value || '';
        renderDetailedStats(cachedDetailedStats);
        const nextInput = document.getElementById('stats-user-filter');
        nextInput?.focus();
        nextInput?.setSelectionRange(nextInput.value.length, nextInput.value.length);
    });
}

function filterMatchupsByControls(matchups) {
    const userFilter = detailedStatsFilters.user.trim().toLowerCase();
    return (Array.isArray(matchups) ? matchups : [])
        .filter((matchup) => !detailedStatsFilters.gameConfig || gameConfigKey(matchup.gameConfig) === detailedStatsFilters.gameConfig)
        .filter((matchup) => !userFilter || matchupLabel(matchup).toLowerCase().includes(userFilter));
}

function renderDetailedStats(data) {
    cachedDetailedStats = data || null;

    const container = document.getElementById('detailed-game-stats');
    if (!container) {
        return;
    }

    const gameStats = cachedDetailedStats?.userGamesStats?.gameStats || {};
    const briskulaStats = cachedDetailedStats?.userBriskulaStats || {};
    const configStats = briskulaStats.configStats || {};
    const modes = collectBriskulaModes(briskulaStats, configStats);
    selectDefaultBriskulaMode(modes);

    const gameEntries = Object.entries(gameStats).sort((a, b) => compareStatsEntries(a, b, 'recent'));
    const configEntries = Object.entries(configStats).sort((a, b) => compareStatsEntries(a, b, 'recent'));
    const selectedModeName = detailedStatsFilters.gameConfig
        ? gameDisplayName(detailedStatsFilters.gameConfig)
        : 'No mode selected';
    const winsAgainstUser = filterMatchupsByControls(briskulaStats.winsAgainstUser);
    const winsWithTeammate = filterMatchupsByControls(briskulaStats.winsWithTeammate);
    const teammateTable = winsWithTeammate.length
        ? renderMatchupTable(`With teammates - ${selectedModeName}`, winsWithTeammate, 'No teammate stats for this mode.')
        : '';

    container.innerHTML = `
        <article class="profile-stats-section">
            <div class="section-heading">
                <div>
                    <p class="section-kicker">All games</p>
                    <h2>Game statistics</h2>
                </div>
            </div>
            <div class="profile-game-grid">
                ${gameEntries.map(([gameType, stats]) => renderGameStatsCard(gameType, stats)).join('') || `
                    <article class="profile-session-card profile-session-card--empty">
                        <p>No game stats found for this account.</p>
                    </article>
                `}
            </div>
        </article>
        <article class="profile-stats-section">
            <div class="section-heading">
                <div>
                    <p class="section-kicker">Briskula</p>
                    <h2>By game setting</h2>
                </div>
            </div>
            <div class="profile-game-grid">
                ${configEntries.map(([gameType, stats]) => renderGameStatsCard(gameType, stats, 'profile-game-card--config')).join('') || `
                    <article class="profile-session-card profile-session-card--empty">
                        <p>No Briskula configuration stats yet.</p>
                    </article>
                `}
            </div>
        </article>
        ${renderMatchupControls(modes)}
        ${renderMatchupTable(`Against users - ${selectedModeName}`, winsAgainstUser, 'No opponent stats for this mode.')}
        ${teammateTable}
    `;

    bindMatchupControls(container);
}

function friendUserId(friend) {
    return friend?.user?.id != null ? String(friend.user.id) : '';
}

function friendName(friend) {
    return friend?.user?.name || 'Unknown user';
}

function friendPresence(friend) {
    return String(friend?.presenceStatus || 'OFFLINE');
}

function presenceLabel(presence) {
    if (presence === 'IN_GAME') {
        return 'In game';
    }
    if (presence === 'IN_LOBBY') {
        return 'In lobby';
    }
    if (presence === 'ONLINE') {
        return 'Online';
    }
    return 'Offline';
}

function renderFriendPlayCounts(friend) {
    const counts = Array.isArray(friend?.playedTogetherByGameType) ? friend.playedTogetherByGameType : [];
    if (!counts.length) {
        return '<span class="profile-friend-count">No game-specific history</span>';
    }

    return counts.map((item) => `
        <span class="profile-friend-count">
            ${escapeHtml(gameDisplayName(item.gameType || item.gameConfig || 'Game'))}: ${escapeHtml(item.playedTogether ?? item.played ?? item.count ?? 0)}
        </span>
    `).join('');
}

function friendMatchupTypeLabel(value) {
    if (value === 'WITH_TEAMMATE') {
        return 'With teammate';
    }
    if (value === 'AGAINST_USER') {
        return 'Against user';
    }
    return String(value || 'Matchup');
}

function renderDetailedFriendStats(detailedFriend) {
    const statsByGameType = detailedFriend?.persistedStatsByGameType || {};
    const entries = Object.entries(statsByGameType)
        .flatMap(([gameType, stats]) => (Array.isArray(stats) ? stats : []).map((stat) => ({ gameType, stat })))
        .sort((left, right) => compareStatsEntries(left.stat, right.stat, 'recent'));

    if (!entries.length) {
        return `
            <section class="profile-friends-section">
                <div class="section-heading">
                    <div>
                        <p class="section-kicker">Together</p>
                        <h2>Persisted stats</h2>
                    </div>
                </div>
                <article class="profile-session-card profile-session-card--empty">
                    <p>No persisted friend matchup stats yet.</p>
                </article>
            </section>
        `;
    }

    return `
        <section class="profile-friends-section">
            <div class="section-heading">
                <div>
                    <p class="section-kicker">Together</p>
                    <h2>Persisted stats</h2>
                </div>
            </div>
            <div class="profile-game-grid">
                ${entries.map(({ gameType, stat }) => {
                    const normalized = normalizeGameStats(stat);
                    return `
                        <article class="profile-game-card profile-game-card--config">
                            <span>${escapeHtml(gameDisplayName(gameType))} - ${escapeHtml(gameConfigDisplayName(stat.gameConfig, stat.gameType || gameType))}</span>
                            <div class="profile-stat-line">
                                <strong>${normalized.played}</strong>
                                <small>played</small>
                            </div>
                            <div class="profile-stat-line">
                                <strong>${normalized.wins}</strong>
                                <small>won</small>
                            </div>
                            <div class="profile-stat-line">
                                <strong>${normalized.losses}</strong>
                                <small>lost</small>
                            </div>
                            <div class="profile-win-rate" aria-label="${normalized.winRate}% win rate">
                                <span style="width: ${normalized.winRate}%"></span>
                            </div>
                            <p>${normalized.winRate}% win rate - ${escapeHtml(friendMatchupTypeLabel(stat.matchupType))}</p>
                            <small class="profile-last-played">${escapeHtml(formatLastPlayedLabel(stat.lastPlayedAt))}</small>
                        </article>
                    `;
                }).join('')}
            </div>
        </section>
    `;
}

function normalizeHistoryPlayer(value) {
    if (!value) {
        return { name: 'Unknown player', id: 0 };
    }
    if (typeof value === 'object') {
        return value;
    }

    try {
        return JSON.parse(value);
    } catch {
        const text = String(value);
        const nameMatch = text.match(/name=([^,\)]*)/i);
        const idMatch = text.match(/id=([^,\)]*)/i) || text.match(/(\d+)/);
        return {
            name: nameMatch ? nameMatch[1].trim() : text,
            id: idMatch ? idMatch[1].trim() : 0
        };
    }
}

function historyPlayerId(player) {
    return Number(normalizeHistoryPlayer(player).id);
}

function historyPlayerName(player) {
    return normalizeHistoryPlayer(player).name || 'Unknown player';
}

function historyGameIncludesUser(game, userId) {
    const normalizedUserId = Number(userId);
    if (!Number.isFinite(normalizedUserId)) {
        return false;
    }
    return (game?.playersOrder || []).some((player) => historyPlayerId(player) === normalizedUserId);
}

function historyUserWonGame(game, userId) {
    const normalizedUserId = Number(userId);
    if (!Number.isFinite(normalizedUserId)) {
        return false;
    }
    return (game?.winners || []).some((winner) => historyPlayerId(winner) === normalizedUserId);
}

function renderFriendHistoryPlayers(players, game) {
    return (players || []).map((player) => {
        const winner = historyUserWonGame(game, historyPlayerId(player));
        return `<span class="profile-friend-history-player ${winner ? 'is-winner' : ''}">${escapeHtml(historyPlayerName(player))}</span>`;
    }).join('');
}

function renderFriendHistoryCard(game, profile) {
    const won = historyUserWonGame(game, profile?.id);
    return `
        <article class="profile-friend-history-card">
            <div class="profile-friend-history-head">
                <div>
                    <strong>${escapeHtml(game?.name || gameDisplayName(game?.gameType || 'Briskula'))}</strong>
                    <small>${escapeHtml(formatHistoryDate(game?.endedAt || game?.createdAt))} - ${escapeHtml(gameConfigDisplayName(game?.gameConfig, game?.gameType || 'Briskula'))}</small>
                </div>
                <span class="profile-friend-history-result ${won ? 'win' : 'loss'}">${won ? 'Win' : 'Loss'}</span>
            </div>
            <div class="profile-friend-history-row">
                <span>Players</span>
                <div class="profile-friend-history-players">${renderFriendHistoryPlayers(game?.playersOrder || [], game)}</div>
            </div>
            <div class="profile-friend-history-footer">
                <p>Winners: ${escapeHtml((game?.winners || []).map(historyPlayerName).join(', ') || 'No winner recorded')}</p>
                <a class="btn" href="/history/${encodeURIComponent(game?.id || '')}">
                    ${iconHtml('history')}
                    <span>Replay</span>
                </a>
            </div>
        </article>
    `;
}

async function loadFriendProfileHistory(profile) {
    const panel = document.querySelector('[data-friend-profile-history-panel]');
    if (!panel) {
        return;
    }

    panel.innerHTML = '<p class="section-copy">Loading history...</p>';
    try {
        const sharedGames = [];
        let offset = 0;
        for (let page = 0; page < 5; page += 1) {
            const params = new URLSearchParams({
                offset: String(offset),
                result: 'both',
                timeSort: 'latest'
            });
            const response = await fetch(`/api/games/history?${params}`, {
                credentials: 'include',
                cache: 'no-store'
            });
            if (!response.ok) {
                throw new Error(`History failed: ${response.status}`);
            }

            const games = await response.json();
            if (!Array.isArray(games) || games.length === 0) {
                break;
            }
            for (const game of games) {
                if (historyGameIncludesUser(game, profile?.id)) {
                    sharedGames.push(game);
                }
            }
            if (games.length < 20 || sharedGames.length >= 20) {
                break;
            }
            offset += 20;
        }

        panel.innerHTML = `
            <section class="profile-friend-history-section">
                <div class="section-heading">
                    <div>
                        <p class="section-kicker">Past games</p>
                        <h2>History</h2>
                    </div>
                </div>
                <div class="profile-friend-history-list">
                    ${sharedGames.length ? sharedGames.slice(0, 20).map((game) => renderFriendHistoryCard(game, profile)).join('') : `
                        <article class="profile-session-card profile-session-card--empty">
                            <p>No shared past games found.</p>
                        </article>
                    `}
                </div>
            </section>
        `;
        window.syncThemeUi?.();
    } catch (error) {
        console.error(error.message);
        panel.innerHTML = '<p class="section-copy">History could not be loaded.</p>';
    }
}

function bindFriendProfileTabs(profile) {
    const content = document.getElementById('friend-profile-content');
    if (!content) {
        return;
    }

    const tabs = content.querySelectorAll('[data-friend-profile-tab]');
    const statsPanel = content.querySelector('[data-friend-profile-stats-panel]');
    const historyPanel = content.querySelector('[data-friend-profile-history-panel]');
    let historyLoaded = false;

    tabs.forEach((tab) => {
        tab.addEventListener('click', () => {
            const target = tab.getAttribute('data-friend-profile-tab');
            const showHistory = target === 'history';
            tabs.forEach((item) => {
                const active = item === tab;
                item.classList.toggle('is-active', active);
                item.setAttribute('aria-selected', String(active));
            });
            if (statsPanel) {
                statsPanel.hidden = showHistory;
            }
            if (historyPanel) {
                historyPanel.hidden = !showHistory;
            }
            if (showHistory && !historyLoaded) {
                historyLoaded = true;
                loadFriendProfileHistory(profile);
            }
        });
    });

    content.addEventListener('keydown', (event) => {
        if ((event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') || isTypingTarget(event.target)) {
            return;
        }

        const tabList = Array.from(tabs);
        const activeIndex = tabList.findIndex((tab) => tab.classList.contains('is-active'));
        if (activeIndex < 0) {
            return;
        }

        event.preventDefault();
        const direction = event.key === 'ArrowRight' ? 1 : -1;
        const nextIndex = (activeIndex + direction + tabList.length) % tabList.length;
        tabList[nextIndex].click();
        tabList[nextIndex].focus({ preventScroll: true });
    });
}

function renderFriendSection(title, friends, emptyText, blocked = false) {
    const content = friends.length
        ? friends.map((friend) => {
            const id = friendUserId(friend);
            const name = friendName(friend);
            const presence = friendPresence(friend);
            return `
                <article class="profile-friend-card" data-friend-card="${escapeHtml(id)}">
                    <div class="profile-friend-main">
                        <span class="profile-friend-avatar" aria-hidden="true">${escapeHtml(name.charAt(0).toUpperCase() || 'U')}</span>
                        <div class="profile-friend-copy">
                            <strong>${escapeHtml(name)}</strong>
                            <p>${escapeHtml(blocked ? 'Blocked user' : `${presenceLabel(presence)} - ${friend.totalPlayedTogether || 0} games together`)}</p>
                            <div class="profile-friend-counts">
                                ${blocked ? '' : renderFriendPlayCounts(friend)}
                            </div>
                        </div>
                    </div>
                    <div class="profile-friend-actions">
                        ${blocked ? '' : `<span class="profile-friend-presence profile-friend-presence-${escapeHtml(presence.toLowerCase().replace('_', '-'))}">${escapeHtml(presenceLabel(presence))}</span>`}
                        <button class="btn" type="button" data-view-friend="${escapeHtml(id)}">
                            ${iconHtml('profile_icon')}
                            <span>View stats</span>
                        </button>
                        <button class="btn danger" type="button" data-${blocked ? 'unblock' : 'remove'}-friend="${escapeHtml(id)}">
                            ${iconHtml(blocked ? 'block' : 'user_remove')}
                            <span>${blocked ? 'Unblock' : 'Remove'}</span>
                        </button>
                    </div>
                </article>
            `;
        }).join('')
        : `
            <article class="profile-session-card profile-session-card--empty">
                <p>${escapeHtml(emptyText)}</p>
            </article>
        `;

    return `
        <section class="profile-friends-section">
            <div class="section-heading">
                <div>
                    <p class="section-kicker">${escapeHtml(blocked ? 'Blocked' : 'Connected')}</p>
                    <h2>${escapeHtml(title)}</h2>
                </div>
            </div>
            <div class="profile-friends-list">
                ${content}
            </div>
        </section>
    `;
}

function bindFriendActions(container) {
    container.querySelectorAll('[data-view-friend]').forEach((button) => {
        button.addEventListener('click', () => {
            openFriendProfile(button.getAttribute('data-view-friend'));
        });
    });

    container.querySelectorAll('[data-remove-friend]').forEach((button) => {
        button.addEventListener('click', async () => {
            const id = button.getAttribute('data-remove-friend');
            const name = friendName(cachedFriends.find((friend) => friendUserId(friend) === id));
            const confirmed = await confirmSessionAction(`Remove ${name} from your friends?`, 'Remove friend');
            if (confirmed) {
                await removeFriend(id);
            }
        });
    });

    container.querySelectorAll('[data-unblock-friend]').forEach((button) => {
        button.addEventListener('click', async () => {
            const id = button.getAttribute('data-unblock-friend');
            const name = friendName(cachedBlockedFriends.find((friend) => friendUserId(friend) === id));
            const confirmed = await confirmSessionAction(`Unblock ${name}?`, 'Unblock');
            if (confirmed) {
                await unblockFriend(id);
            }
        });
    });
}

function renderFriends() {
    const container = document.getElementById('friends-management');
    if (!container) {
        return;
    }

    container.innerHTML = `
        ${renderFriendSection('Friends', cachedFriends, 'No friends yet.')}
        ${renderFriendSection('Blocked users', cachedBlockedFriends, 'No blocked users.', true)}
    `;
    window.syncThemeUi?.();
    bindFriendActions(container);
}

async function refreshFriends({ clearStatus = true } = {}) {
    const container = document.getElementById('friends-management');
    if (container && !cachedFriends.length && !cachedBlockedFriends.length) {
        container.innerHTML = `
            <article class="profile-session-card profile-session-card--placeholder">
                <p>Loading friends...</p>
            </article>
        `;
    }

    try {
        const [friendsResponse, blockedResponse] = await Promise.all([
            fetch('/api/friends', { credentials: 'include', cache: 'no-store' }),
            fetch('/api/friends/blocked', { credentials: 'include', cache: 'no-store' })
        ]);

        if (!friendsResponse.ok || !blockedResponse.ok) {
            throw new Error(`Friends failed: ${friendsResponse.status}/${blockedResponse.status}`);
        }

        cachedFriends = await friendsResponse.json();
        cachedBlockedFriends = await blockedResponse.json();
        if (!Array.isArray(cachedFriends)) {
            cachedFriends = [];
        }
        if (!Array.isArray(cachedBlockedFriends)) {
            cachedBlockedFriends = [];
        }
        renderFriends();
        if (clearStatus) {
            clearSessionsStatus();
        }
    } catch (error) {
        console.error(error.message);
        setSessionsStatus('Could not load friends. Please refresh the page.', 'error');
    }
}

async function removeFriend(friendId) {
    if (!friendId) {
        return;
    }

    try {
        const response = await fetch(`/api/friends/${encodeURIComponent(friendId)}`, {
            method: 'DELETE',
            credentials: 'include'
        });
        if (!response.ok) {
            throw new Error(`Remove friend failed: ${response.status}`);
        }
        document.dispatchEvent(new CustomEvent('uc:friends-refresh'));
        await refreshFriends({ clearStatus: false });
        setSessionsStatus('Friend removed.', 'success');
    } catch (error) {
        console.error(error.message);
        setSessionsStatus('Could not remove friend.', 'error');
    }
}

async function unblockFriend(friendId) {
    if (!friendId) {
        return;
    }

    try {
        const response = await fetch(`/api/friends/blocks/${encodeURIComponent(friendId)}`, {
            method: 'DELETE',
            credentials: 'include'
        });
        if (!response.ok) {
            throw new Error(`Unblock friend failed: ${response.status}`);
        }
        document.dispatchEvent(new CustomEvent('uc:friends-refresh'));
        await refreshFriends({ clearStatus: false });
        setSessionsStatus('User unblocked.', 'success');
    } catch (error) {
        console.error(error.message);
        setSessionsStatus('Could not unblock user.', 'error');
    }
}

function closeFriendProfileModal() {
    const overlay = document.getElementById('friend-profile-modal');
    overlay?.classList.remove('active');
}

function initialiseFriendProfileModal() {
    const overlay = document.getElementById('friend-profile-modal');
    const close = document.getElementById('friend-profile-close');
    if (!overlay || !close) {
        return;
    }

    close.addEventListener('click', closeFriendProfileModal);
    overlay.addEventListener('click', (event) => {
        if (event.target === overlay) {
            closeFriendProfileModal();
        }
    });
    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape' && overlay.classList.contains('active')) {
            closeFriendProfileModal();
        }
    });
}

function renderFriendProfile(profile, detailedFriend = null) {
    const title = document.getElementById('friend-profile-title');
    const content = document.getElementById('friend-profile-content');
    if (!content) {
        return;
    }

    if (title) {
        title.textContent = profile?.username || 'Friend profile';
    }

    const gameStats = profile?.userGamesStats?.gameStats || {};
    const gameCards = Object.entries(gameStats)
        .sort((a, b) => compareStatsEntries(a, b, 'recent'))
        .map(([gameType, stats]) => renderGameStatsCard(gameType, stats))
        .join('');

    content.innerHTML = `
        <section class="profile-friend-profile-summary">
            <span class="profile-friend-avatar profile-friend-profile-avatar" aria-hidden="true">
                ${escapeHtml((profile?.username || 'U').charAt(0).toUpperCase())}
            </span>
            <div>
                <h2>${escapeHtml(profile?.username || 'Unknown user')}</h2>
                <p class="section-copy">User ID #${escapeHtml(profile?.id ?? '-')}</p>
                <p class="section-copy">Roles: ${escapeHtml((profile?.roles || ['USER']).join(', '))}</p>
            </div>
        </section>
        <div class="profile-friend-profile-tabs" role="tablist" aria-label="Friend profile sections">
            <button class="profile-friend-profile-tab is-active" type="button" role="tab" aria-selected="true" data-friend-profile-tab="stats">Stats</button>
            <button class="profile-friend-profile-tab" type="button" role="tab" aria-selected="false" data-friend-profile-tab="history">History</button>
        </div>
        <div class="profile-friend-profile-panel" data-friend-profile-stats-panel>
            <section class="profile-history-stats">
                <div class="profile-summary-card profile-summary-stack">
                    <span class="summary-label">Games played</span>
                    <span class="summary-value">${escapeHtml(profile?.gamesPlayed ?? 0)}</span>
                </div>
                <div class="profile-summary-card profile-summary-stack">
                    <span class="summary-label">Games won</span>
                    <span class="summary-value">${escapeHtml(profile?.gamesWon ?? 0)}</span>
                </div>
            </section>
            <section class="profile-friends-section">
                <div class="section-heading">
                    <div>
                        <p class="section-kicker">Game stats</p>
                        <h2>Stats</h2>
                    </div>
                </div>
                <div class="profile-game-grid">
                    ${gameCards || `
                        <article class="profile-session-card profile-session-card--empty">
                            <p>No game stats found for this user.</p>
                        </article>
                    `}
                </div>
            </section>
            ${renderDetailedFriendStats(detailedFriend)}
        </div>
        <div class="profile-friend-profile-panel" data-friend-profile-history-panel hidden></div>
    `;
    bindFriendProfileTabs(profile);
    content.focus({ preventScroll: true });
}

async function openFriendProfile(friendId) {
    if (!friendId) {
        return;
    }

    const overlay = document.getElementById('friend-profile-modal');
    const content = document.getElementById('friend-profile-content');
    if (!overlay || !content) {
        return;
    }

    content.innerHTML = '<p class="section-copy">Loading profile...</p>';
    overlay.classList.add('active');

    try {
        const [profileResponse, detailsResponse] = await Promise.all([
            fetch(`/api/users/${encodeURIComponent(friendId)}/profile`, {
                method: 'GET',
                credentials: 'include',
                cache: 'no-store'
            }),
            fetch(`/api/friends/${encodeURIComponent(friendId)}/details`, {
                method: 'GET',
                credentials: 'include',
                cache: 'no-store'
            }).catch(() => null)
        ]);

        if (!profileResponse.ok) {
            throw new Error(`Friend profile failed: ${profileResponse.status}`);
        }

        const detailedFriend = detailsResponse?.ok ? await detailsResponse.json() : null;
        renderFriendProfile(await profileResponse.json(), detailedFriend);
    } catch (error) {
        console.error(error.message);
        content.innerHTML = '<p class="section-copy">Unable to load this profile.</p>';
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
        if (await refreshSessions({ clearStatus: false })) {
            setSessionsStatus('Session removed.', 'success');
        }
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
                    ${iconHtml(session.currentSession ? 'logout' : 'close')}
                    <span>${session.currentSession ? 'Log out this device' : 'Remove session'}</span>
                </button>
            </div>
        </article>
    `).join('');
    window.syncThemeUi?.();

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

    Object.entries(data.userGamesStats?.gameStats || {})
        .sort((a, b) => compareStatsEntries(a, b, 'recent'))
        .forEach(([gameType, stats]) => {
        const normalized = normalizeGameStats(stats);
        const gameCard = document.createElement('div');
        gameCard.className = 'profile-game-card';

        const title = document.createElement('span');
        title.textContent = gameDisplayName(gameType);

        const br1 = document.createElement('br');

        const playedGames = document.createElement('span');
        playedGames.textContent = `${normalized.played}`;

        const br2 = document.createElement('br');

        const wonGames = document.createElement('span');
        wonGames.textContent = `${normalized.wins}`;

        const lastPlayed = document.createElement('small');
        lastPlayed.className = 'profile-last-played';
        lastPlayed.textContent = formatLastPlayedLabel(stats.lastPlayedAt);

        gameCard.appendChild(title);
        gameCard.appendChild(br1);
        gameCard.append('Games played: ');
        gameCard.appendChild(playedGames);
        gameCard.appendChild(br2);
        gameCard.append('Games won: ');
        gameCard.appendChild(wonGames);
        gameCard.appendChild(lastPlayed);

        perGameType.appendChild(gameCard);
    });
}

document.addEventListener('DOMContentLoaded', async () => {
    initialiseTabs();
    initialiseSessionConfirmModal();
    initialiseFriendProfileModal();
    formatInitialLastPlayedLabels();
    syncProfileSnapshotFromDom();
    window.addEventListener('resize', positionSessionToast);
    document.getElementById('page-refresh')?.addEventListener('click', async () => {
        setRefreshButtonLoading(true);
        try {
            if (getActiveProfileTab() === 'sessions') {
                await refreshSessions();
                return;
            }
            if (getActiveProfileTab() === 'friends') {
                await refreshFriends();
                return;
            }
            await refresh();
        } finally {
            setRefreshButtonLoading(false);
        }
    });
    document.getElementById('save-profile')?.addEventListener('click', () => save());
    await refreshSessions();
    if (getActiveProfileTab() === 'stats') {
        await refreshDetailedStats();
    }
    if (getActiveProfileTab() === 'friends') {
        await refreshFriends();
    }
});
