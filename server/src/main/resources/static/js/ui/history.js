(() => {
  const list = document.getElementById('history-list');
  const gameTypeSelect = document.getElementById('history-game-type');
  const resultSelect = document.getElementById('history-result');
  const timeSortSelect = document.getElementById('history-time-sort');
  const loadMoreButton = document.getElementById('history-load-more');
  const refreshButton = document.getElementById('history-refresh');
  const summary = document.getElementById('history-summary');
  const currentUserId = Number(window.__CURRENT_USER_ID__);
  const initialHistory = Array.isArray(window.__INITIAL_HISTORY__) ? window.__INITIAL_HISTORY__ : null;

  if (!list || !gameTypeSelect || !resultSelect || !timeSortSelect || !loadMoreButton || !refreshButton) return;

  const gamesById = new Map();
  let offset = 0;
  let loading = false;

  const escapeHtml = (value) => String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');

  const formatDate = (value) => {
    if (!value) return t('history.unknownTime');
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return t('history.unknownTime');
    return new Intl.DateTimeFormat(document.documentElement.lang || undefined, {
      dateStyle: 'medium',
      timeStyle: 'short',
      hour12: false
    }).format(date);
  };

  const normalizePlayer = (value) => {
    if (!value) return { name: t('history.unknownPlayer'), id: 0 };
    if (typeof value === 'object') return value;

    try {
      return JSON.parse(value);
    } catch {
      const nameMatch = String(value).match(/name=([^,\)]*)/i);
      const idMatch = String(value).match(/id=([^,\)]*)/i) || String(value).match(/(\d+)/);
      return {
        name: nameMatch ? nameMatch[1].trim() : String(value),
        id: idMatch ? idMatch[1].trim() : 0
      };
    }
  };

  const playerName = (player) => escapeHtml(normalizePlayer(player).name || t('history.unknownPlayer'));
  const playerId = (player) => Number(normalizePlayer(player).id);
  const isCurrentUser = (player) => playerId(player) === currentUserId;

  const isWinner = (game, player) => Array.isArray(game.winners)
    && game.winners.some((winner) => playerId(winner) === playerId(player));

  const currentUserWon = (game) => Array.isArray(game.winners)
    && game.winners.some((winner) => playerId(winner) === currentUserId);

  const settingsText = (config = {}) => {
    const parts = [t('history.playersCount', config.numberOfPlayers || '?')];
    if (config.numberOfPlayers === 2) parts.push(t('history.cardsCount', config.cardsInHandNum || '?'));
    if (config.numberOfPlayers === 4) parts.push(config.teamsEnabled ? t('history.teams') : t('history.solo'));
    return parts.join(' - ');
  };

  const playerList = (players, game = null) => (players || [])
    .map((player) => {
      const winner = game && isWinner(game, player);
      const uid = playerId(player);
      const interactive = uid > 0 ? ` data-user-id="${uid}" data-user-name="${playerName(player)}" tabindex="0" role="button"` : '';
      return `<span class="history-player ${winner ? 'is-winner' : ''}"${interactive}>${playerName(player)}</span>`;
    })
    .join('');

  const scoreEntries = (pointsByUser = {}) => Object.entries(pointsByUser)
    .map(([key, points]) => ({
      player: normalizePlayer(key),
      points: Number(points) || 0
    }));

  const scores = (game, pointsByUser = game.pointsByUser) => scoreEntries(pointsByUser)
    .map(({ player, points }) => {
      const winner = isWinner(game, player);
      const uid = playerId(player);
      const interactive = uid > 0 ? ` data-user-id="${uid}" data-user-name="${playerName(player)}" tabindex="0" role="button"` : '';
      return `<span class="history-score ${winner ? 'is-winner' : ''}"${interactive}>${playerName(player)} <strong>${points}</strong></span>`;
    }).join('');

  const renderGame = (game) => {
    const won = currentUserWon(game);
    const winnerLabel = Array.isArray(game.winners) && game.winners.length === 1 ? t('history.winner') : t('history.winners');
    return `
      <article class="card history-card" data-game-id="${escapeHtml(game.id)}">
        <div class="history-card-head">
          <div class="history-card-title">
          <span class="chip">${escapeHtml(getGameTypeDisplayName(game.gameType) || t('history.unknown'))}</span>
            <h3>${escapeHtml(game.name || 'Briskula')}</h3>
            <div class="history-meta">
              <span>${formatDate(game.endedAt || game.createdAt)}</span>
              <span>${escapeHtml(settingsText(game.gameConfig))}</span>
            </div>
          </div>
          <span class="history-result ${won ? 'win' : 'loss'}" title="${won ? t('history.win') : t('history.loss')}">${won ? t('history.winLetter') : t('history.lossLetter')}</span>
        </div>
        <div class="history-card-row">
          <strong>${t('lobby.players.chip')}</strong>
          <div class="history-score-list">${scores(game)}</div>
        </div>
        <div class="history-card-footer">
          <p>${winnerLabel}: ${playerList(game.winners, game) || t('history.noWinner')}</p>
          <a class="btn history-details-button" href="/history/${encodeURIComponent(game.id)}" data-history-details="${escapeHtml(game.id)}">
            <img class="uc-icon" data-icon="history" src="/pics/light/history.svg" alt="" aria-hidden="true">
            <span>${t('history.replay')}</span>
          </a>
        </div>
      </article>
    `;
  };

  const updateSummary = () => {
    const gameType = gameTypeSelect.options[gameTypeSelect.selectedIndex]?.textContent || t('common.allGames');
    const result = resultSelect.options[resultSelect.selectedIndex]?.textContent || t('history.winsAndLosses');
    const time = timeSortSelect.options[timeSortSelect.selectedIndex]?.textContent || t('history.latestFirst');
    if (summary) summary.textContent = `${gameType}, ${result.toLowerCase()}, ${time.toLowerCase()}.`;
  };

  const renderGames = (games, reset) => {
    if (reset) {
      offset = 0;
      gamesById.clear();
      list.innerHTML = '';
    }

    if (!Array.isArray(games) || games.length === 0) {
      if (offset === 0) {
        list.innerHTML = `<article class="card history-card history-empty">${t('history.noMatches')}</article>`;
      }
      loadMoreButton.hidden = true;
      return;
    }

    games.forEach((game) => gamesById.set(String(game.id), game));
    list.insertAdjacentHTML('beforeend', games.map(renderGame).join(''));
    window.syncThemeUi?.();
    offset += 20;
    loadMoreButton.hidden = games.length < 20;
  };

  const setListLoading = (isLoading) => {
    loading = isLoading;
    list.setAttribute('aria-busy', String(isLoading));
    loadMoreButton.disabled = isLoading;
    refreshButton.disabled = isLoading;
    loadMoreButton.textContent = isLoading ? t('common.loading') : t('history.loadMore');
    const refreshLabel = refreshButton.querySelector('span');
    if (refreshLabel) {
      refreshLabel.textContent = isLoading ? t('history.refreshing') : t('common.refresh');
    }
  };

  const loadHistory = async (reset = false) => {
    if (loading) return;
    setListLoading(true);
    if (reset) {
      offset = 0;
      list.innerHTML = '';
    }
    updateSummary();

    const params = new URLSearchParams({
      offset: String(offset),
      result: resultSelect.value,
      timeSort: timeSortSelect.value
    });

    try {
      const response = await fetch(`/api/games/history?${params}`, { credentials: 'include' });
      if (!response.ok) throw new Error('Could not load history');
      renderGames(await response.json(), reset);
    } catch (error) {
      if (offset === 0) {
        list.innerHTML = `<article class="card history-card history-error">${t('history.loadFailed')}</article>`;
      }
    } finally {
      setListLoading(false);
    }
  };

  resultSelect.addEventListener('change', () => loadHistory(true));
  timeSortSelect.addEventListener('change', () => loadHistory(true));
  gameTypeSelect.addEventListener('change', () => loadHistory(true));
  loadMoreButton.addEventListener('click', () => loadHistory(false));
  refreshButton.addEventListener('click', () => loadHistory(true));
  const openPlayerProfile = (el) => {
    document.dispatchEvent(new CustomEvent('uc:open-user-profile', {
      detail: { id: Number(el.dataset.userId), username: el.dataset.userName }
    }));
  };

  list.addEventListener('click', (event) => {
    const player = event.target.closest('[data-user-id]');
    if (player) { openPlayerProfile(player); return; }

    const link = event.target.closest('[data-history-details]');
    if (!link) return;
    const game = gamesById.get(String(link.dataset.historyDetails));
    if (game) {
      sessionStorage.setItem(`ultracards:history:${game.id}`, JSON.stringify(game));
    }
  });

  list.addEventListener('keydown', (event) => {
    if (event.key !== 'Enter' && event.key !== ' ') return;
    const player = event.target.closest('[data-user-id]');
    if (!player) return;
    event.preventDefault();
    openPlayerProfile(player);
  });

  updateSummary();
  if (initialHistory) {
    renderGames(initialHistory, true);
  } else {
    loadHistory(true);
  }
})();
