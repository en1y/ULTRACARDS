(() => {
    window.UltracardsGameRuntime?.register({
        name: 'briskula',
        cardType: 'ITALIAN',
        features: {deck: true, trump: true, teams: true, teammateHand: true, previousRound: true},
        cardStrength: {1: 0, 3: 1, 13: 2, 12: 3, 11: 4, 7: 5, 6: 6, 5: 7, 4: 8, 2: 9},
        copy(key, ...args) { return t(`briskula.${key}`, ...args); }
    });
})();
