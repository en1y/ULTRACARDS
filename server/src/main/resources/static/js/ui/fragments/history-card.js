/**
 * The game card shown in a profile popup's history tab — used by the header search
 * results and the friends list, which open the same popup. Split out of search.js
 * so the card can change without touching the search box around it.
 *
 * `deps` carries the few helpers that still live with the popup (date wording,
 * player chips, the icon factory).
 */
(() => {
    window.ucHistoryCard = window.ucHistoryCard || {};

    /** Net Points the profile owner took from this game, when the server sent one. */
    const pointsRow = (game) => {
        const delta = Number(game?.pointsDelta);
        if (!Number.isFinite(delta) || delta === 0) return null;

        const row = document.createElement('div');
        row.className = 'header-user-history-row header-user-history-bet';
        const label = document.createElement('span');
        label.textContent = delta > 0 ? t('history.pointsWon') : t('history.pointsLost');
        const value = document.createElement('span');
        value.className = 'history-points';
        value.append(pointsDeltaNode(delta));
        row.append(label, value);
        return row;
    };

    window.ucHistoryCard.create = (game, profile, deps) => {
        const card = document.createElement('article');
        card.className = 'header-user-history-card';

        const head = document.createElement('div');
        head.className = 'header-user-history-head';

        const titleWrap = document.createElement('div');
        const title = document.createElement('strong');
        title.textContent = game?.name || getGameTypeDisplayName(game?.gameType || 'Briskula');
        const meta = document.createElement('small');
        meta.textContent = `${deps.formatDate(game?.endedAt || game?.createdAt)} - ${deps.configName(game?.gameType || 'Briskula', game?.gameConfig)}`;
        titleWrap.append(title, meta);

        const result = document.createElement('span');
        const won = deps.userWon(game, profile?.id);
        result.className = `header-user-history-result ${won ? 'win' : 'loss'}`;
        result.textContent = won ? t('history.winLetter') : t('history.lossLetter');
        result.title = won ? t('history.win') : t('history.loss');

        head.append(titleWrap, result);

        const playersRow = document.createElement('div');
        playersRow.className = 'header-user-history-row';
        const playersLabel = document.createElement('span');
        playersLabel.textContent = t('lobby.players.chip');
        playersRow.append(playersLabel, deps.playerList(game?.playersOrder || [], game));

        const footer = document.createElement('div');
        footer.className = 'header-user-history-footer';
        const winners = document.createElement('p');
        winners.textContent = t('search.winnersLabel', (game?.winners || []).map(deps.playerName).join(', ') || t('history.noWinner'));
        const replay = document.createElement('a');
        replay.className = 'btn header-user-history-link';
        replay.href = `/history/${encodeURIComponent(game?.id || '')}`;
        replay.append(deps.icon('history'), document.createElement('span'));
        deps.setLabel(replay, t('history.replay'));
        footer.append(winners, replay);

        card.append(head, playersRow);
        const bet = pointsRow(game);
        if (bet) card.append(bet);
        card.append(footer);
        return card;
    };
})();
