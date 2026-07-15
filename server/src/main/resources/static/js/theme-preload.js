(() => {
    const storageKey = 'uc-theme';
    const savedTheme = localStorage.getItem(storageKey);
    const systemDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    document.documentElement.setAttribute('data-theme', savedTheme || (systemDark ? 'dark' : 'light'));
    document.documentElement.setAttribute('data-game-ui', 'fullscreen');
})();
