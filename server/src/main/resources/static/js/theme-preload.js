(() => {
    // Remove values left by UI features that no longer read from localStorage.
    try {
        localStorage.removeItem('lobbyId');
        localStorage.removeItem('uc-game-ui');
        localStorage.removeItem('uc-game-ui-default-fullscreen-v1');
    } catch (_) {}

    const storageKey = 'uc-theme';
    const savedTheme = localStorage.getItem(storageKey);
    const systemDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    document.documentElement.setAttribute('data-theme', savedTheme || (systemDark ? 'dark' : 'light'));
    document.documentElement.setAttribute('data-game-ui', 'fullscreen');
})();
