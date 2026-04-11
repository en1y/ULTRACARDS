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

    const avatarSrc = theme === 'dark' ? '/pics/profile_icon_light.svg' : '/pics/profile_icon_dark.svg';
    document.querySelectorAll('[data-avatar]').forEach((img) => {
        if (img.getAttribute('src') !== avatarSrc) {
            img.setAttribute('src', avatarSrc);
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

// A cool way to add methods to the window
window.getPreferredTheme = getPreferredTheme;
window.syncThemeUi = syncThemeUi;
window.applyTheme = applyTheme;
window.toggleTheme = toggleTheme;

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        bindThemeToggle();
        syncThemeUi();
    }, { once: true });
} else {
    bindThemeToggle();
    syncThemeUi();
}
