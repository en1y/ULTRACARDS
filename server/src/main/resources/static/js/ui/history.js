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
    if (!value) return 'Unknown time';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return 'Unknown time';
    return new Intl.DateTimeFormat(undefined, {
      dateStyle: 'medium',
      timeStyle: 'short',
      hour12: false
    }).format(date);
  };

  const normalizePlayer = (value) => {
    if (!value) return { name: 'Unknown player', id: 0 };
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

  const playerName = (player) => escapeHtml(normalizePlayer(player).name || 'Unknown player');
  const playerId = (player) => Number(normalizePlayer(player).id);
  const isCurrentUser = (player) => playerId(player) === currentUserId;

  const isWinner = (game, player) => Array.isArray(game.winners)
    && game.winners.some((winner) => playerId(winner) === playerId(player));

  const currentUserWon = (game) => Array.isArray(game.winners)
    && game.winners.some((winner) => playerId(winner) === currentUserId);

  const settingsText = (config = {}) => {
    const players = config.numberOfPlayers || '?';
    const cards = config.cardsInHandNum || '?';
    const mode = config.teamsEnabled ? 'teams' : 'solo';
    return `${players} players - ${cards} cards - ${mode}`;
  };

  const playerList = (players, game = null) => (players || [])
    .map((player) => {
      const winner = game && isWinner(game, player);
      return `<span class="history-player ${winner ? 'is-winner' : ''}">${playerName(player)}</span>`;
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
      return `<span class="history-score ${winner ? 'is-winner' : ''}">${playerName(player)} <strong>${points}</strong></span>`;
    }).join('');

  const renderGame = (game) => {
    const won = currentUserWon(game);
    const winnerLabel = Array.isArray(game.winners) && game.winners.length === 1 ? 'Winner' : 'Winners';
    return `
      <article class="card history-card" data-game-id="${escapeHtml(game.id)}">
        <div class="history-card-head">
          <div class="history-card-title">
            <h3>${escapeHtml(game.name || 'Briskula')}</h3>
            <div class="history-meta">
              <span>${formatDate(game.endedAt || game.createdAt)}</span>
              <span>${escapeHtml(settingsText(game.gameConfig))}</span>
            </div>
          </div>
          <span class="history-result ${won ? 'win' : 'loss'}">${won ? 'Win' : 'Loss'}</span>
        </div>
        <div class="history-card-row">
          <strong>Players</strong>
          <div class="history-player-list">${playerList(game.playersOrder, game)}</div>
        </div>
        <div class="history-card-row">
          <strong>Points</strong>
          <div class="history-score-list">${scores(game)}</div>
        </div>
        <div class="history-card-footer">
          <p>${winnerLabel}: ${playerList(game.winners, game) || 'No winner recorded'}</p>
          <a class="btn history-details-button" href="/history/${encodeURIComponent(game.id)}" data-history-details="${escapeHtml(game.id)}">Replay</a>
        </div>
      </article>
    `;
  };

  const updateSummary = () => {
    const gameType = gameTypeSelect.options[gameTypeSelect.selectedIndex]?.textContent || 'All games';
    const result = resultSelect.options[resultSelect.selectedIndex]?.textContent || 'Wins and losses';
    const time = timeSortSelect.options[timeSortSelect.selectedIndex]?.textContent || 'Latest first';
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
        list.innerHTML = '<article class="card history-card history-empty">No games match these filters.</article>';
      }
      loadMoreButton.hidden = true;
      return;
    }

    games.forEach((game) => gamesById.set(String(game.id), game));
    list.insertAdjacentHTML('beforeend', games.map(renderGame).join(''));
    offset += 20;
    loadMoreButton.hidden = games.length < 20;
  };

  const setListLoading = (isLoading) => {
    loading = isLoading;
    list.setAttribute('aria-busy', String(isLoading));
    loadMoreButton.disabled = isLoading;
    refreshButton.disabled = isLoading;
    loadMoreButton.textContent = isLoading ? 'Loading' : 'Load More';
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
        list.innerHTML = '<article class="card history-card history-error">History could not be loaded.</article>';
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
  list.addEventListener('click', (event) => {
    const link = event.target.closest('[data-history-details]');
    if (!link) return;
    const game = gamesById.get(String(link.dataset.historyDetails));
    if (game) {
      sessionStorage.setItem(`ultracards:history:${game.id}`, JSON.stringify(game));
    }
  });

  updateSummary();
  if (initialHistory) {
    renderGames(initialHistory, true);
  } else {
    loadHistory(true);
  }
})();
