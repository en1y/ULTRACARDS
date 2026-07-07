(() => {
    const storageKey = 'uc-theme';
    const savedTheme = localStorage.getItem(storageKey);
    const systemDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    document.documentElement.setAttribute('data-theme', savedTheme || (systemDark ? 'dark' : 'light'));
    // Mobile game-table style ('fullscreen' default | 'classic'), set before first
    // paint so the game board never flashes the other layout.
    const gameUiKey = 'uc-game-ui';
    const gameUiDefaultKey = 'uc-game-ui-default-fullscreen-v1';
    if (localStorage.getItem(gameUiDefaultKey) !== '1') {
        localStorage.setItem(gameUiKey, 'fullscreen');
        localStorage.setItem(gameUiDefaultKey, '1');
    }
    const gameUi = localStorage.getItem(gameUiKey) === 'classic' ? 'classic' : 'fullscreen';
    document.documentElement.setAttribute('data-game-ui', gameUi);
})();
