(() => {
    const storageKey = 'uc-theme';
    const savedTheme = localStorage.getItem(storageKey);
    const systemDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    document.documentElement.setAttribute('data-theme', savedTheme || (systemDark ? 'dark' : 'light'));
    // Mobile game-table style ('fullscreen' default | 'classic'), set before first
    // paint so the game board never flashes the other layout.
    const gameUi = localStorage.getItem('uc-game-ui') === 'classic' ? 'classic' : 'fullscreen';
    document.documentElement.setAttribute('data-game-ui', gameUi);
})();
