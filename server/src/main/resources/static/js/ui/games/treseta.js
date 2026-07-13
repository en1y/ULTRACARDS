(() => {
    window.UltracardsGameRuntime?.register({
        name: 'treseta',
        cardType: 'ITALIAN',
        features: {deck: true, trump: false, teams: true, teammateHand: false, previousRound: true},
        cardStrength: {3: 0, 2: 1, 1: 2, 13: 3, 12: 4, 11: 5, 7: 6, 6: 7, 5: 8, 4: 9},
        opponentHandRotation: false,
        opponentCardSpread: 0,
        opponentCardLift: 0,
        historyCardSpread: 0,
        historyCardLift: 0,
        displayPoints(points) {
            return Math.floor((Number(points) || 0) / 3 * 10) / 10;
        },
        canPlayCard(card, game, hand) {
            const leadSuit = game?.playedCards?.[0]?.card?.charAt(0);
            if (!leadSuit) return true;
            return !hand.some((entry) => entry?.card?.charAt(0) === leadSuit)
                || card?.card?.charAt(0) === leadSuit;
        }
    });
})();
