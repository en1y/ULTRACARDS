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

// A cool way to add methods to the window
window.getPreferredTheme = getPreferredTheme;
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
    }, { once: true });
} else {
    bindThemeToggle();
    syncThemeUi();
}
