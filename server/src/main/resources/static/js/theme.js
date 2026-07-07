const storageKey = 'uc-theme';
const root = document.documentElement;

const getPreferredTheme = () => {
    const saved = localStorage.getItem(storageKey);
    const systemDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    return saved || (systemDark ? 'dark' : 'light');
};

const syncThemeUi = () => {
    const theme = root.getAttribute('data-theme') || getPreferredTheme();
    const toggleButton = document.querySelector('[data-action="toggle-theme"]');
    if (toggleButton) {
        toggleButton.setAttribute('aria-pressed', String(theme === 'dark'));
    }

    const iconFolder = theme === 'dark' ? 'light' : 'dark';
    const avatarSrc = `/pics/${iconFolder}/profile_icon.svg`;
    document.querySelectorAll('[data-avatar]').forEach((img) => {
        if (img.getAttribute('src') !== avatarSrc) {
            img.setAttribute('src', avatarSrc);
        }
    });

    document.querySelectorAll('[data-icon]').forEach((img) => {
        let icon = img.dataset.icon;
        if (img.dataset.themeToggleIcon === 'true') {
            icon = theme === 'dark' ? 'light_mode' : 'dark_mode';
        }
        if (!icon) {
            return;
        }

        const iconSrc = `/pics/${iconFolder}/${icon}.svg`;
        if (img.getAttribute('src') !== iconSrc) {
            img.setAttribute('src', iconSrc);
        }
    });
};

const bindThemeToggle = () => {
    const toggleButton = document.querySelector('[data-action="toggle-theme"]');
    if (!toggleButton || toggleButton.dataset.themeBound === 'true') {
        return;
    }

    toggleButton.addEventListener('click', toggleTheme);
    toggleButton.dataset.themeBound = 'true';
};

const applyTheme = (theme) => {
    root.setAttribute('data-theme', theme);
    localStorage.setItem(storageKey, theme);
    syncThemeUi();
};

const toggleTheme = () => {
    const currentTheme = root.getAttribute('data-theme') || getPreferredTheme();
    const nextTheme = currentTheme === 'dark' ? 'light' : 'dark';
    applyTheme(nextTheme);
};

// Mobile game-table style setting ('fullscreen' default | 'classic'), stored in
// localStorage; theme-preload.js applies it before first paint, this keeps the
// header menu label in sync and lets the user flip it.
const gameUiKey = 'uc-game-ui';
const gameUiDefaultKey = 'uc-game-ui-default-fullscreen-v1';

const getGameUiMode = () => {
    if (localStorage.getItem(gameUiDefaultKey) !== '1') {
        localStorage.setItem(gameUiKey, 'fullscreen');
        localStorage.setItem(gameUiDefaultKey, '1');
    }
    return localStorage.getItem(gameUiKey) === 'classic' ? 'classic' : 'fullscreen';
};

const syncGameUiLabel = () => {
    document.querySelectorAll('[data-game-ui-label]').forEach((label) => {
        label.textContent = `Table display: ${getGameUiMode() === 'classic' ? 'Classic' : 'Fullscreen'}`;
    });
};

const applyGameUiMode = (mode) => {
    localStorage.setItem(gameUiDefaultKey, '1');
    localStorage.setItem(gameUiKey, mode);
    root.setAttribute('data-game-ui', mode);
    syncGameUiLabel();
    // Live game pages re-fan hands / re-seat players on this.
    document.dispatchEvent(new CustomEvent('uc:game-ui-changed', {detail: {mode}}));
};

const bindGameUiToggle = () => {
    const button = document.querySelector('[data-action="toggle-game-ui"]');
    if (!button || button.dataset.gameUiBound === 'true') {
        return;
    }
    button.addEventListener('click', () => {
        applyGameUiMode(getGameUiMode() === 'classic' ? 'fullscreen' : 'classic');
    });
    button.dataset.gameUiBound = 'true';
};

// A cool way to add methods to the window
window.getPreferredTheme = getPreferredTheme;
window.getGameUiMode = getGameUiMode;
window.applyGameUiMode = applyGameUiMode;
window.syncThemeUi = syncThemeUi;
window.applyTheme = applyTheme;
window.toggleTheme = toggleTheme;
window.createUcIcon = (icon) => {
    const img = document.createElement('img');
    const theme = root.getAttribute('data-theme') || getPreferredTheme();
    const iconFolder = theme === 'dark' ? 'light' : 'dark';
    img.className = 'uc-icon';
    img.dataset.icon = icon;
    img.src = `/pics/${iconFolder}/${icon}.svg`;
    img.alt = '';
    img.setAttribute('aria-hidden', 'true');
    return img;
};

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        bindThemeToggle();
        syncThemeUi();
        bindGameUiToggle();
        syncGameUiLabel();
    }, { once: true });
} else {
    bindThemeToggle();
    syncThemeUi();
    bindGameUiToggle();
    syncGameUiLabel();
}

// Mobile keyboard handling. The keyboard OVERLAYS the page (default viewport
// behavior -- the layout must not shrink, the header stays put). Two parts:
// 1) publish the keyboard height as --kb-offset so fixed chat panels can lift
//    themselves above it, and
// 2) nudge whichever in-flow text box gained focus into the visible area.
if (window.visualViewport) {
    // rAF-throttled so the burst of viewport events during the keyboard's own
    // slide collapses to one update per frame — the panels track it 1:1.
    let kbFrame = null;
    const updateKeyboardOffset = () => {
        if (kbFrame) return;
        kbFrame = requestAnimationFrame(() => {
            kbFrame = null;
            const vv = window.visualViewport;
            // The viewport heights also diverge transiently when the URL bar
            // collapses or the page is pinch-zoomed; only trust the difference
            // as "keyboard" while a text field is actually focused.
            const editing = document.activeElement?.matches?.('input, textarea, [contenteditable]');
            // documentElement.clientHeight is the layout viewport fixed
            // elements anchor to; innerHeight drifts with the URL bar and
            // overshoots the lift on scrolled pages.
            const base = document.documentElement.clientHeight;
            const offset = editing ? Math.max(0, base - vv.height - vv.offsetTop) : 0;
            document.documentElement.style.setProperty('--kb-offset', `${Math.round(offset)}px`);
        });
    };
    window.visualViewport.addEventListener('resize', updateKeyboardOffset);
    window.visualViewport.addEventListener('scroll', updateKeyboardOffset);
    document.addEventListener('focusin', updateKeyboardOffset);
    document.addEventListener('focusout', updateKeyboardOffset);
    updateKeyboardOffset();
}

document.addEventListener('focusin', (event) => {
    if (!event.target.matches('input, textarea')) return;
    if (!window.matchMedia('(max-width: 900px)').matches) return;
    // Inputs inside FIXED panels (floating chats, friends drawer) are lifted by
    // --kb-offset; scrolling the page for them just jerks the background.
    if (event.target.closest('.chat-sidebar, .lobby-chat-panel, .friends-drawer')) return;
    setTimeout(() => event.target.scrollIntoView({ block: 'center', behavior: 'smooth' }), 300);
});

// Keep the phone screen awake during games (game + replay pages). Each grant
// auto-releases after 5 minutes; any interaction or returning to the tab
// re-acquires it. No-op where the Wake Lock API is unavailable.
if (navigator.wakeLock && document.body?.classList.contains('game-page')) {
    let wakeLock = null;
    let wakeLockTimer = null;
    const acquireWakeLock = async () => {
        if (document.hidden || (wakeLock && !wakeLock.released)) return;
        try {
            wakeLock = await navigator.wakeLock.request('screen');
            window.clearTimeout(wakeLockTimer);
            wakeLockTimer = window.setTimeout(() => wakeLock?.release(), 5 * 60 * 1000);
        } catch (_) { /* denied (e.g. battery saver) — nothing to do */ }
    };
    document.addEventListener('visibilitychange', () => { if (!document.hidden) acquireWakeLock(); });
    document.addEventListener('pointerdown', acquireWakeLock, { passive: true });
    acquireWakeLock();
}
