(() => {
  window.ucHeader = window.ucHeader || {};

  window.ucHeader.createHeaderSearchPhase = () => {
    const form = document.querySelector('[data-header-search]');
    if (!form) {
      return { init: () => {} };
    }

    const input = form.querySelector('[data-header-search-input]');
    const resultsPanel = form.querySelector('[data-header-search-results]');
    if (!input || !resultsPanel) {
      return { init: () => {} };
    }

    const debounceDelay = 220;
    const isAuthenticated = form.dataset.authenticated === 'true';
    let debounceId = null;
    let abortController = null;
    let requestSequence = 0;
    let lastRenderedSignature = null;
    let currentUsers = [];
    let selectedIndex = -1;
    let profileModal = null;
    let activeProfileAction = null;
    let friendIdsCache = null;
    let friendIdsPromise = null;
    const currentUsername = document.getElementById('username-header')?.textContent?.trim() || '';
    const createIcon = (icon) => {
      if (window.createUcIcon) {
        return window.createUcIcon(icon);
      }

      const img = document.createElement('img');
      img.className = 'uc-icon';
      img.dataset.icon = icon;
      img.src = `/pics/light/${icon}.svg`;
      img.alt = '';
      img.setAttribute('aria-hidden', 'true');
      return img;
    };

    const setDropdownOpen = (isOpen) => {
      form.classList.toggle('open', isOpen);
      input.setAttribute('aria-expanded', String(isOpen));
      resultsPanel.setAttribute('aria-hidden', String(!isOpen));
    };

    const clearSearch = () => {
      if (debounceId) {
        window.clearTimeout(debounceId);
        debounceId = null;
      }
      abortController?.abort();
      abortController = null;
      requestSequence += 1;
      lastRenderedSignature = null;
      currentUsers = [];
      selectedIndex = -1;
      resultsPanel.replaceChildren();
      setDropdownOpen(false);
    };

    const toSearchUser = (item) => {
      if (item?.id === null || item?.id === undefined) {
        return null;
      }

      return {
        id: item.id,
        username: String(item.username || t('search.unnamedUser')),
        roles: Array.isArray(item.roles) ? item.roles : []
      };
    };

    const compareUsersById = (left, right) => {
      const leftId = Number(left.id);
      const rightId = Number(right.id);
      if (Number.isFinite(leftId) && Number.isFinite(rightId)) {
        return leftId - rightId;
      }

      return String(left.id).localeCompare(String(right.id));
    };

    const buildResultSignature = (state, users = []) => {
      if (state !== 'results') {
        return state;
      }

      return users
        .map((user) => `${user.id}\u0000${user.username}\u0000${(user.roles || []).join(',')}`)
        .join('\u0001') + `\u0002${selectedIndex}`;
    };

    const createStateElement = (className, message) => {
      const element = document.createElement('div');
      element.className = className;
      element.textContent = message;
      return element;
    };

    const readErrorMessage = async (response, fallback) => {
      try {
        const contentType = response.headers.get('content-type') || '';
        if (contentType.includes('application/json')) {
          const body = await response.json();
          return body?.message || body?.error || fallback;
        }

        const text = (await response.text()).trim();
        return text || fallback;
      } catch {
        return fallback;
      }
    };

    const setButtonLabel = (button, label) => {
      const text = button?.querySelector('span');
      if (text) {
        text.textContent = label;
      } else if (button) {
        button.textContent = label;
      }
    };

    const setButtonIcon = (button, icon) => {
      if (!button) {
        return;
      }

      const image = button.querySelector('[data-icon]');
      if (image) {
        image.dataset.icon = icon;
      } else {
        button.prepend(createIcon(icon));
      }
      window.syncThemeUi?.();
    };

    const setActionStatus = (statusElement, message, type = '') => {
      if (!statusElement) {
        return;
      }

      statusElement.textContent = message;
      statusElement.classList.remove('error', 'success');
      if (type) {
        statusElement.classList.add(type);
      }
    };

    const configureProfileActionButton = (action, button) => {
      if (!button) {
        return;
      }

      button.classList.toggle('btn-accent', action === 'add');
      button.classList.toggle('danger', action === 'remove');
      setButtonIcon(button, action === 'remove' ? 'user_remove' : 'user_add');
      setButtonLabel(button, action === 'remove' ? t('search.removeFriend') : t('search.addFriend'));
    };

    const addFriend = async (user, button, statusElement) => {
      if (!user?.id || !button) {
        return;
      }

      const originalText = button.querySelector('span')?.textContent || button.textContent;
      button.disabled = true;
      setButtonLabel(button, t('search.sending'));
      setActionStatus(statusElement, '');

      try {
        const response = await fetch(`/api/friends/requests/send/${encodeURIComponent(user.id)}`, {
          method: 'POST',
          credentials: 'include'
        });

        if (!response.ok) {
          throw new Error(await readErrorMessage(response, t('search.requestFailed')));
        }

        setButtonLabel(button, t('search.requestSent'));
        activeProfileAction = null;
        setActionStatus(statusElement, t('search.requestSentStatus'), 'success');
      } catch (error) {
        button.disabled = false;
        setButtonLabel(button, originalText);
        setActionStatus(statusElement, error?.message || t('search.requestSendFailed'), 'error');
      }
    };

    const removeFriend = async (user, button, statusElement) => {
      if (!user?.id || !button) {
        return;
      }

      const originalText = button.querySelector('span')?.textContent || button.textContent;
      button.disabled = true;
      setButtonLabel(button, t('search.removing'));
      setActionStatus(statusElement, '');

      try {
        const response = await fetch(`/api/friends/${encodeURIComponent(user.id)}`, {
          method: 'DELETE',
          credentials: 'include'
        });

        if (!response.ok) {
          throw new Error(await readErrorMessage(response, t('search.removeFailed')));
        }

        friendIdsCache?.delete(String(user.id));
        document.dispatchEvent(new CustomEvent('uc:friends-refresh'));
        button.disabled = false;
        configureProfileActionButton('add', button);
        activeProfileAction = { action: 'add', profile: user, button, statusElement };
        setActionStatus(statusElement, t('search.removed'), 'success');
      } catch (error) {
        button.disabled = false;
        setButtonLabel(button, originalText);
        setActionStatus(statusElement, error?.message || t('search.removeUnable'), 'error');
      }
    };

    const performProfileAction = (action) => {
      if (!action?.button || action.button.disabled) {
        return;
      }

      if (action.action === 'remove') {
        removeFriend(action.profile, action.button, action.statusElement);
      } else {
        addFriend(action.profile, action.button, action.statusElement);
      }
    };

    const isTypingTarget = (target) => target instanceof HTMLElement
        && (target.matches('input, textarea, select') || target.isContentEditable);

    const loadFriendIds = async (force = false) => {
      if (!force && friendIdsCache) {
        return friendIdsCache;
      }

      if (!force && friendIdsPromise) {
        return friendIdsPromise;
      }

      friendIdsPromise = fetch('/api/friends', {
        method: 'GET',
        credentials: 'include',
        cache: 'no-store'
      })
          .then((response) => {
            if (!response.ok) {
              throw new Error(`Friends failed: ${response.status}`);
            }
            return response.json();
          })
          .then((friends) => {
            const ids = new Set();
            if (Array.isArray(friends)) {
              for (const friend of friends) {
                if (friend?.user?.id !== null && friend?.user?.id !== undefined) {
                  ids.add(String(friend.user.id));
                }
              }
            }
            friendIdsCache = ids;
            return ids;
          })
          .catch((error) => {
            console.warn('Unable to load friends for search profile actions', error);
            return new Set();
          })
          .finally(() => {
            friendIdsPromise = null;
          });

      return friendIdsPromise;
    };

    const isCurrentUserProfile = (profile) => {
      if (!currentUsername || !profile?.username) {
        return false;
      }

      return currentUsername.toLocaleLowerCase() === String(profile.username).trim().toLocaleLowerCase();
    };

    const getProfileFriendAction = async (profile) => {
      activeProfileAction = null;
      if (!isAuthenticated || !profile?.id || isCurrentUserProfile(profile)) {
        return '';
      }

      const friendIds = await loadFriendIds(true);
      return friendIds.has(String(profile.id)) ? 'remove' : 'add';
    };

    const syncSelectedRow = () => {
      const rows = [...resultsPanel.querySelectorAll('[data-header-search-row]')];
      rows.forEach((row, index) => {
        const isSelected = index === selectedIndex;
        row.classList.toggle('is-selected', isSelected);
        row.setAttribute('aria-selected', String(isSelected));
        if (isSelected) {
          row.scrollIntoView({ block: 'nearest' });
        }
      });
    };

    const ordinalDay = (day) => {
      if (day >= 11 && day <= 13) {
        return `${day}th`;
      }

      switch (day % 10) {
        case 1:
          return `${day}st`;
        case 2:
          return `${day}nd`;
        case 3:
          return `${day}rd`;
        default:
          return `${day}th`;
      }
    };

    const startOfMondayWeek = (date) => {
      const start = new Date(date.getFullYear(), date.getMonth(), date.getDate());
      const day = start.getDay();
      const mondayOffset = day === 0 ? -6 : 1 - day;
      start.setDate(start.getDate() + mondayOffset);
      return start;
    };

    const isSameMondayWeek = (left, right) => startOfMondayWeek(left).getTime() === startOfMondayWeek(right).getTime();

    const formatLastPlayedAt = (value) => {
      if (!value) {
        return t('profile.neverPlayed');
      }

      const date = new Date(value);
      if (Number.isNaN(date.getTime())) {
        return t('profile.neverPlayed');
      }

      if (isSameMondayWeek(date, new Date())) {
        return new Intl.DateTimeFormat(document.documentElement.lang || undefined, { weekday: 'long' }).format(date);
      }

      const month = new Intl.DateTimeFormat(document.documentElement.lang || undefined, { month: 'long' }).format(date);
      return `${ordinalDay(date.getDate())} ${month}`;
    };

    const formatLastPlayedLabel = (value) => value
        ? t('search.lastPlayedOn', formatLastPlayedAt(value))
        : t('profile.neverPlayed');

    const formatHistoryDate = (value) => {
      if (!value) {
        return t('history.unknownTime');
      }
      const date = new Date(value);
      if (Number.isNaN(date.getTime())) {
        return t('history.unknownTime');
      }
      return new Intl.DateTimeFormat(document.documentElement.lang || undefined, {
        dateStyle: 'medium',
        timeStyle: 'short',
        hour12: false
      }).format(date);
    };

    const normalizeGameStats = (stats) => {
      const played = Number(stats?.played ?? 0);
      const wins = Number(stats?.wins ?? 0);
      const safePlayed = Number.isFinite(played) ? played : 0;
      const safeWins = Number.isFinite(wins) ? wins : 0;
      const losses = Math.max(0, safePlayed - safeWins);
      const winRate = safePlayed > 0 ? Math.round((safeWins / safePlayed) * 100) : 0;
      return { played: safePlayed, wins: safeWins, losses, winRate };
    };

    const fallbackDisplayName = (value) => String(value || t('search.gameFallback'))
        .replace(/([a-z])([A-Z])/g, '$1 $2')
        .toLowerCase()
        .split('_')
        .filter(Boolean)
        .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
        .join(' ') || t('search.gameFallback');

    const gameDisplayName = (gameType) => {
      if (typeof window.getGameTypeDisplayName === 'function') {
        return window.getGameTypeDisplayName(gameType);
      }
      return fallbackDisplayName(gameType);
    };

    const gameConfigDisplayName = (gameType, config) => {
      if (typeof window.getGameConfigDisplayName === 'function') {
        return window.getGameConfigDisplayName(gameType, config);
      }
      if (!config || typeof config === 'string') {
        return fallbackDisplayName(config);
      }
      return t('gameConfig.fallback');
    };

    const normalizePlayer = (value) => {
      if (!value) {
        return { name: t('history.unknownPlayer'), id: 0 };
      }
      if (typeof value === 'object') {
        return value;
      }

      try {
        return JSON.parse(value);
      } catch {
        const text = String(value);
        const nameMatch = text.match(/name=([^,\)]*)/i);
        const idMatch = text.match(/id=([^,\)]*)/i) || text.match(/(\d+)/);
        return {
          name: nameMatch ? nameMatch[1].trim() : text,
          id: idMatch ? idMatch[1].trim() : 0
        };
      }
    };

    const playerId = (player) => Number(normalizePlayer(player).id);
    const playerName = (player) => normalizePlayer(player).name || t('history.unknownPlayer');
    const gameIncludesUser = (game, userId) => {
      const normalizedUserId = Number(userId);
      if (!Number.isFinite(normalizedUserId)) {
        return false;
      }
      return (game?.playersOrder || []).some((player) => playerId(player) === normalizedUserId);
    };

    const userWonGame = (game, userId) => {
      const normalizedUserId = Number(userId);
      if (!Number.isFinite(normalizedUserId)) {
        return false;
      }
      return (game?.winners || []).some((winner) => playerId(winner) === normalizedUserId);
    };

    const createPlayerList = (players, game = null) => {
      const list = document.createElement('div');
      list.className = 'header-user-history-players';
      for (const player of players || []) {
        const item = document.createElement('span');
        item.className = 'header-user-history-player';
        if (game && userWonGame(game, playerId(player))) {
          item.classList.add('is-winner');
        }
        item.textContent = playerName(player);
        const uid = playerId(player);
        if (uid > 0) {
          item.dataset.userId = uid;
          item.dataset.userName = playerName(player);
          item.tabIndex = 0;
          item.role = 'button';
        }
        list.append(item);
      }
      list.addEventListener('click', (e) => {
        const chip = e.target.closest('[data-user-id]');
        if (!chip) return;
        document.dispatchEvent(new CustomEvent('uc:open-user-profile', {
          detail: { id: Number(chip.dataset.userId), username: chip.dataset.userName }
        }));
      });
      list.addEventListener('keydown', (e) => {
        if (e.key !== 'Enter' && e.key !== ' ') return;
        const chip = e.target.closest('[data-user-id]');
        if (!chip) return;
        e.preventDefault();
        document.dispatchEvent(new CustomEvent('uc:open-user-profile', {
          detail: { id: Number(chip.dataset.userId), username: chip.dataset.userName }
        }));
      });
      return list;
    };

    const createHistoryCard = (game, profile) => {
      const card = document.createElement('article');
      card.className = 'header-user-history-card';

      const head = document.createElement('div');
      head.className = 'header-user-history-head';

      const titleWrap = document.createElement('div');
      const title = document.createElement('strong');
      title.textContent = game?.name || gameDisplayName(game?.gameType || 'Briskula');
      const meta = document.createElement('small');
      meta.textContent = `${formatHistoryDate(game?.endedAt || game?.createdAt)} - ${gameConfigDisplayName(game?.gameType || 'Briskula', game?.gameConfig)}`;
      titleWrap.append(title, meta);

      const result = document.createElement('span');
      const won = userWonGame(game, profile?.id);
      result.className = `header-user-history-result ${won ? 'win' : 'loss'}`;
      result.textContent = won ? t('history.winLetter') : t('history.lossLetter');
      result.title = won ? t('history.win') : t('history.loss');

      head.append(titleWrap, result);

      const playersRow = document.createElement('div');
      playersRow.className = 'header-user-history-row';
      const playersLabel = document.createElement('span');
      playersLabel.textContent = t('lobby.players.chip');
      playersRow.append(playersLabel, createPlayerList(game?.playersOrder || [], game));

      const footer = document.createElement('div');
      footer.className = 'header-user-history-footer';
      const winners = document.createElement('p');
      winners.textContent = t('search.winnersLabel', (game?.winners || []).map(playerName).join(', ') || t('history.noWinner'));
      const replay = document.createElement('a');
      replay.className = 'btn header-user-history-link';
      replay.href = `/history/${encodeURIComponent(game?.id || '')}`;
      replay.append(createIcon('history'), document.createElement('span'));
      setButtonLabel(replay, t('history.replay'));
      footer.append(winners, replay);

      card.append(head, playersRow, footer);
      return card;
    };

    const friendMatchupTypeLabel = (value) => {
      if (value === 'WITH_TEAMMATE') {
        return t('search.withTeammate');
      }
      if (value === 'AGAINST_USER') {
        return t('search.againstUser');
      }
      return String(value || t('search.matchup'));
    };

    const createProfileModal = () => {
      const overlay = document.createElement('div');
      overlay.className = 'header-user-profile-overlay';
      overlay.hidden = true;

      const dialog = document.createElement('section');
      dialog.className = 'header-user-profile-dialog';
      dialog.tabIndex = -1;
      dialog.setAttribute('role', 'dialog');
      dialog.setAttribute('aria-modal', 'true');
      dialog.setAttribute('aria-labelledby', 'header-user-profile-title');

      const header = document.createElement('header');
      header.className = 'header-user-profile-head';

      const titleWrap = document.createElement('div');

      const eyebrow = document.createElement('span');
      eyebrow.className = 'chip';
      eyebrow.textContent = t('header.menu.profile');

      const title = document.createElement('h2');
      title.id = 'header-user-profile-title';
      title.textContent = t('search.userProfile');

      titleWrap.append(eyebrow, title);

      const close = document.createElement('button');
      close.className = 'header-icon-button header-icon-button-small';
      close.type = 'button';
      close.setAttribute('aria-label', t('search.closeProfile'));
      close.append(createIcon('close'));
      close.addEventListener('click', () => {
        window.ucHeader.closeUserProfilePopup?.() || closeProfileModal();
      });

      header.append(titleWrap, close);

      const content = document.createElement('div');
      content.className = 'header-user-profile-content';
      content.tabIndex = -1;
      content.dataset.userProfileContent = 'true';

      dialog.append(header, content);
      overlay.append(dialog);

      overlay.addEventListener('click', (event) => {
        if (event.target === overlay) {
          window.ucHeader.closeUserProfilePopup?.() || closeProfileModal();
        }
      });

      document.body.append(overlay);
      window.syncThemeUi?.();
      return { overlay, dialog, title, content };
    };

    const ensureProfileModal = () => {
      if (!profileModal) {
        profileModal = createProfileModal();
      }
      return profileModal;
    };

    function closeProfileModal() {
      if (!profileModal) {
        return;
      }
      activeProfileAction = null;
      profileModal.overlay.classList.remove('is-visible');
      profileModal.overlay.classList.add('is-leaving');
      window.setTimeout(() => {
        if (profileModal?.overlay.classList.contains('is-leaving')) {
          profileModal.overlay.hidden = true;
          profileModal.overlay.classList.remove('is-leaving');
        }
      }, 180);
    }

    const setProfileModalContent = (node) => {
      const modal = ensureProfileModal();
      modal.content.replaceChildren(node);
      modal.content.scrollTop = 0;
      modal.overlay.hidden = false;
      modal.overlay.classList.remove('is-leaving');
      window.syncThemeUi?.();
      window.requestAnimationFrame(() => {
        modal.overlay.classList.add('is-visible');
        modal.content.focus({ preventScroll: true });
      });
    };

    const createProfileState = (message, type = '') => {
      const state = document.createElement('p');
      state.className = `header-user-profile-state ${type}`;
      state.textContent = message;
      return state;
    };

    const createStatTile = (label, value) => {
      const tile = document.createElement('article');
      tile.className = 'header-user-profile-stat';

      const strong = document.createElement('strong');
      strong.textContent = String(value ?? 0);

      const span = document.createElement('span');
      span.textContent = label;

      tile.append(strong, span);
      return tile;
    };

    const createWinRateDonut = (normalized) => {
      const donut = document.createElement('div');
      donut.className = 'profile-stat-donut';
      donut.style.setProperty('--win-rate', String(normalized.winRate));
      donut.setAttribute('role', 'img');
      donut.setAttribute('aria-label', t('profile.winRate.aria', normalized.winRate));
      const value = document.createElement('span');
      value.textContent = `${normalized.winRate}%`;
      donut.append(value);
      return donut;
    };

    const createGameCard = (gameType, stats) => {
      const normalized = normalizeGameStats(stats);
      const card = document.createElement('article');
      card.className = 'header-user-game-card';

      const title = document.createElement('strong');
      title.textContent = gameDisplayName(gameType);

      const statsLine = document.createElement('div');
      statsLine.className = 'header-user-game-stats';
      statsLine.append(
          createStatTile(t('profile.played'), normalized.played),
          createStatTile(t('profile.won'), normalized.wins),
          createStatTile(t('search.tile.lost'), normalized.losses)
      );

      const row = document.createElement('div');
      row.className = 'profile-game-card-row';
      row.append(createWinRateDonut(normalized), statsLine);

      const footer = document.createElement('small');
      footer.textContent = formatLastPlayedLabel(stats?.lastPlayedAt);

      card.append(title, row, footer);
      return card;
    };

    const createDetailedFriendStatsSection = (detailedFriend) => {
      const section = document.createElement('section');
      section.className = 'header-user-profile-games';

      const title = document.createElement('h3');
      title.textContent = t('search.together');
      section.append(title);

      const grid = document.createElement('div');
      grid.className = 'header-user-game-grid';

      const statsByGameType = detailedFriend?.persistedStatsByGameType || {};
      const entries = [];
      for (const [gameType, stats] of Object.entries(statsByGameType)) {
        if (!Array.isArray(stats)) {
          continue;
        }
        for (const stat of stats) {
          entries.push({ gameType, stat });
        }
      }

      entries.sort((left, right) => {
        const leftTime = left.stat?.lastPlayedAt ? new Date(left.stat.lastPlayedAt).getTime() : 0;
        const rightTime = right.stat?.lastPlayedAt ? new Date(right.stat.lastPlayedAt).getTime() : 0;
        return rightTime - leftTime || Number(right.stat?.played ?? 0) - Number(left.stat?.played ?? 0);
      });

      if (!entries.length) {
        grid.append(createProfileState(t('search.noMatchupStats')));
      } else {
        for (const { gameType, stat } of entries) {
          const normalized = normalizeGameStats(stat);
          const card = document.createElement('article');
          card.className = 'header-user-game-card';

          const cardTitle = document.createElement('strong');
          cardTitle.textContent = `${gameDisplayName(gameType)} - ${gameConfigDisplayName(stat?.gameType || gameType, stat?.gameConfig)}`;

          const statsLine = document.createElement('div');
          statsLine.className = 'header-user-game-stats';
          statsLine.append(
              createStatTile(t('profile.played'), normalized.played),
              createStatTile(t('profile.won'), normalized.wins),
              createStatTile(t('search.tile.lost'), normalized.losses)
          );

          const row = document.createElement('div');
          row.className = 'profile-game-card-row';
          row.append(createWinRateDonut(normalized), statsLine);

          const footer = document.createElement('small');
          footer.textContent = `${friendMatchupTypeLabel(stat?.matchupType)} - ${formatLastPlayedLabel(stat?.lastPlayedAt)}`;

          card.append(cardTitle, row, footer);
          grid.append(card);
        }
      }

      section.append(grid);
      return section;
    };

    const switchOpenProfileTab = (direction) => {
      if (!profileModal || profileModal.overlay.hidden) {
        return false;
      }

      const tabs = Array.from(profileModal.content.querySelectorAll('.header-user-profile-tab'));
      const activeIndex = tabs.findIndex((tab) => tab.classList.contains('is-active'));
      if (tabs.length < 2 || activeIndex < 0) {
        return false;
      }

      const nextIndex = (activeIndex + direction + tabs.length) % tabs.length;
      tabs[nextIndex].click();
      tabs[nextIndex].focus({ preventScroll: true });
      return true;
    };

    const createProfileTabs = (statsPanel, historyPanel, profile) => {
      const tabs = document.createElement('div');
      tabs.className = 'header-user-profile-tabs';
      tabs.setAttribute('role', 'tablist');
      tabs.setAttribute('aria-label', t('profile.tabs.aria'));

      const statsButton = document.createElement('button');
      statsButton.className = 'header-user-profile-tab is-active';
      statsButton.type = 'button';
      statsButton.setAttribute('role', 'tab');
      statsButton.setAttribute('aria-selected', 'true');
      statsButton.textContent = t('search.stats');

      const historyButton = document.createElement('button');
      historyButton.className = 'header-user-profile-tab';
      historyButton.type = 'button';
      historyButton.setAttribute('role', 'tab');
      historyButton.setAttribute('aria-selected', 'false');
      historyButton.textContent = t('header.menu.history');

      let historyLoaded = false;
      const activate = (tab) => {
        const showHistory = tab === 'history';
        statsButton.classList.toggle('is-active', !showHistory);
        historyButton.classList.toggle('is-active', showHistory);
        statsButton.setAttribute('aria-selected', String(!showHistory));
        historyButton.setAttribute('aria-selected', String(showHistory));
        statsPanel.hidden = showHistory;
        historyPanel.hidden = !showHistory;
        profileModal?.content.focus({ preventScroll: true });

        if (showHistory && !historyLoaded) {
          historyLoaded = true;
          loadProfileHistory(profile, historyPanel);
        }
      };

      statsButton.addEventListener('click', () => activate('stats'));
      historyButton.addEventListener('click', () => activate('history'));
      tabs.append(statsButton, historyButton);
      tabs.addEventListener('keydown', (event) => {
        if ((event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') || isTypingTarget(event.target)) {
          return;
        }

        event.preventDefault();
        event.stopPropagation();
        switchOpenProfileTab(event.key === 'ArrowRight' ? 1 : -1);
      });
      return tabs;
    };

    const loadProfileHistory = async (profile, panel) => {
      panel.replaceChildren(createProfileState(t('search.loadingHistory')));
      try {
        const sharedGames = [];
        let offset = 0;
        for (let page = 0; page < 5; page += 1) {
          const params = new URLSearchParams({
            offset: String(offset),
            result: 'both',
            timeSort: 'latest'
          });
          const response = await fetch(`/api/games/history?${params}`, {
            credentials: 'include',
            cache: 'no-store'
          });
          if (!response.ok) {
            throw new Error(`History failed: ${response.status}`);
          }

          const games = await response.json();
          if (!Array.isArray(games) || games.length === 0) {
            break;
          }

          for (const game of games) {
            if (gameIncludesUser(game, profile?.id)) {
              sharedGames.push(game);
            }
          }

          if (games.length < 20 || sharedGames.length >= 20) {
            break;
          }
          offset += 20;
        }
        renderProfileHistory(profile, panel, sharedGames.slice(0, 20));
      } catch (error) {
        console.warn('Unable to load profile history', error);
        panel.replaceChildren(createProfileState(t('history.loadFailed'), 'error'));
      }
    };

    const renderProfileHistory = (profile, panel, games) => {
      panel.replaceChildren();
      const section = document.createElement('section');
      section.className = 'header-user-profile-history';

      const title = document.createElement('h3');
      title.textContent = t('history.pastGames');
      section.append(title);

      const list = document.createElement('div');
      list.className = 'header-user-history-list';
      if (!Array.isArray(games) || !games.length) {
        list.append(createProfileState(t('search.noSharedGames')));
      } else {
        for (const game of games) {
          list.append(createHistoryCard(game, profile));
        }
      }

      section.append(list);
      panel.append(section);
      window.syncThemeUi?.();
    };

    const renderProfile = async (profile, detailedFriend = null, loadedProfileFriendAction = null) => {
      const modal = ensureProfileModal();
      modal.title.textContent = profile?.username || t('search.userProfile');

      const root = document.createElement('div');
      root.className = 'header-user-profile-view is-entering';

      const summary = document.createElement('section');
      summary.className = 'header-user-profile-summary';

      const avatar = document.createElement('div');
      avatar.className = 'header-user-profile-avatar';
      avatar.textContent = (profile?.username || 'U').charAt(0).toUpperCase();

      const identity = document.createElement('div');
      identity.className = 'header-user-profile-identity';

      const name = document.createElement('h3');
      name.textContent = profile?.username || t('search.unknownUser');

      const id = document.createElement('p');
      id.textContent = `${t('profile.userId')} #${profile?.id ?? '-'}`;

      const roles = document.createElement('p');
      roles.textContent = `${t('profile.roles')}: ${(profile?.roles || ['USER']).join(', ')}`;

      identity.append(name, id, roles);

      summary.append(avatar, identity);

      const profileFriendAction = loadedProfileFriendAction ?? await getProfileFriendAction(profile);
      if (profileFriendAction) {
        const actionStatus = document.createElement('small');
        actionStatus.className = 'header-search-action-status';

        const addButton = document.createElement('button');
        addButton.className = 'btn btn-accent header-search-add-friend';
        addButton.type = 'button';
        addButton.append(createIcon('user_add'), document.createElement('span'));
        configureProfileActionButton(profileFriendAction, addButton);
        addButton.addEventListener('click', () => {
          performProfileAction(activeProfileAction);
        });

        const actions = document.createElement('div');
        actions.className = 'header-user-profile-actions';
        actions.append(addButton, actionStatus);
        summary.append(actions);
        activeProfileAction = { action: profileFriendAction, profile, button: addButton, statusElement: actionStatus };
      }

      const statsPanel = document.createElement('div');
      statsPanel.className = 'header-user-profile-panel';

      const historyPanel = document.createElement('div');
      historyPanel.className = 'header-user-profile-panel';
      historyPanel.hidden = true;

      const totals = document.createElement('section');
      totals.className = 'header-user-profile-stats';
      totals.append(
          createStatTile(t('search.tile.gamesPlayed'), profile?.gamesPlayed ?? 0),
          createStatTile(t('search.tile.gamesWon'), profile?.gamesWon ?? 0)
      );

      const games = document.createElement('section');
      games.className = 'header-user-profile-games';

      const gamesTitle = document.createElement('h3');
      gamesTitle.textContent = t('profile.tab.stats');
      games.append(gamesTitle);

      const grid = document.createElement('div');
      grid.className = 'header-user-game-grid';

      const gameStats = profile?.userGamesStats?.gameStats || {};
      const entries = Object.entries(gameStats);
      if (entries.length) {
        entries
            .sort(([leftType], [rightType]) => leftType.localeCompare(rightType))
            .forEach(([gameType, stats]) => {
              grid.append(createGameCard(gameType, stats));
            });
      } else {
        grid.append(createProfileState(t('search.noGameStats')));
      }

      games.append(grid);
      statsPanel.append(totals, games);
      if (detailedFriend) {
        statsPanel.append(createDetailedFriendStatsSection(detailedFriend));
      }
      root.append(summary, createProfileTabs(statsPanel, historyPanel, profile), statsPanel, historyPanel);
      setProfileModalContent(root);
    };

    const loadDetailedFriendProfile = async (profile, profileFriendAction) => {
      if (profileFriendAction !== 'remove' || !profile?.id) {
        return null;
      }

      try {
        const response = await fetch(`/api/friends/${encodeURIComponent(profile.id)}/details`, {
          method: 'GET',
          credentials: 'include',
          cache: 'no-store'
        });
        if (!response.ok) {
          throw new Error(`Detailed friend failed: ${response.status}`);
        }
        return response.json();
      } catch (error) {
        console.warn('Unable to load detailed friend stats for search profile', error);
        return null;
      }
    };

    const openUserProfile = async (user) => {
      if (!user?.id) {
        return;
      }

      const modal = ensureProfileModal();
      modal.title.textContent = user.username || t('search.userProfile');
      setProfileModalContent(createProfileState(t('profile.loadingProfile')));

      try {
        const response = await fetch(`/api/users/${encodeURIComponent(user.id)}/profile`, {
          method: 'GET',
          credentials: 'include',
          cache: 'no-store'
        });

        if (!response.ok) {
          throw new Error(`Profile failed: ${response.status}`);
        }

        const profile = await response.json();
        const profileFriendAction = await getProfileFriendAction(profile);
        const detailedFriend = await loadDetailedFriendProfile(profile, profileFriendAction);
        await renderProfile(profile, detailedFriend, profileFriendAction);
      } catch (error) {
        console.error('Unable to load user profile', error);
        setProfileModalContent(createProfileState(t('search.profileLoadFailed'), 'error'));
      }
    };

    window.ucHeader.registerUserProfilePopup?.({
      open: (user) => {
        setDropdownOpen(false);
        openUserProfile(user);
      },
      close: closeProfileModal,
      isOpen: () => !!profileModal && !profileModal.overlay.hidden,
      switchTab: switchOpenProfileTab
    });

    const openSelectedUser = () => {
      if (selectedIndex < 0 || selectedIndex >= currentUsers.length) {
        return;
      }

      window.ucHeader.openUserProfilePopup?.(currentUsers[selectedIndex], {
        source: 'search',
        refocusElement: input
      });
    };

    const createResultRow = (user, index) => {
      const row = document.createElement('div');
      row.className = 'header-search-row';
      row.setAttribute('role', 'option');
      row.setAttribute('aria-selected', String(index === selectedIndex));
      row.dataset.headerSearchRow = 'true';
      row.dataset.userId = String(user.id);

      const username = document.createElement('span');
      username.className = 'header-search-username';
      username.textContent = user.username;

      const id = document.createElement('span');
      id.className = 'header-search-id';
      id.textContent = `#${user.id}`;

      const button = document.createElement('button');
      button.className = 'btn header-search-view-button';
      button.type = 'button';
      button.append(createIcon('profile_icon'), document.createElement('span'));
      setButtonLabel(button, 'View');
      button.addEventListener('click', (event) => {
        event.stopPropagation();
        selectedIndex = index;
        syncSelectedRow();
        window.ucHeader.openUserProfilePopup?.(user, {
          source: 'search',
          refocusElement: input
        });
      });

      row.append(username, id, button);
      row.classList.toggle('is-selected', index === selectedIndex);
      row.addEventListener('click', () => {
        selectedIndex = index;
        syncSelectedRow();
        input.focus();
      });
      row.addEventListener('dblclick', () => {
        selectedIndex = index;
        openSelectedUser();
      });
      return row;
    };

    const renderResults = (state, users = []) => {
      const signature = buildResultSignature(state, users);
      if (signature === lastRenderedSignature) {
        setDropdownOpen(true);
        return;
      }

      lastRenderedSignature = signature;

      if (state === 'error') {
        currentUsers = [];
        selectedIndex = -1;
        resultsPanel.replaceChildren(createStateElement('header-search-state error', t('search.failed')));
        setDropdownOpen(true);
        return;
      }

      if (users.length === 0) {
        currentUsers = [];
        selectedIndex = -1;
        resultsPanel.replaceChildren(createStateElement('header-search-state', t('search.noUsers')));
        setDropdownOpen(true);
        return;
      }

      const fragment = document.createDocumentFragment();
      currentUsers = users;
      if (selectedIndex < 0 || selectedIndex >= currentUsers.length) {
        selectedIndex = 0;
      }
      for (let index = 0; index < users.length; index += 1) {
        fragment.append(createResultRow(users[index], index));
      }
      resultsPanel.replaceChildren(fragment);
      window.syncThemeUi?.();
      setDropdownOpen(true);
    };

    const fetchJson = async (url, signal) => {
      const response = await fetch(url, {
        method: 'GET',
        credentials: 'include',
        cache: 'no-store',
        signal
      });

      if (!response.ok) {
        throw new Error('Search request failed');
      }

      return response.json();
    };

    const searchUsers = async (query) => {
      abortController?.abort();
      abortController = new AbortController();
      const currentRequest = requestSequence + 1;
      requestSequence = currentRequest;

      const encodedQuery = encodeURIComponent(query);
      const bounds = 'lower=0&higher=10';
      const requests = [
        fetchJson(`/api/users/search/username/${encodedQuery}?${bounds}`, abortController.signal)
      ];

      if (/\d/.test(query)) {
        requests.push(fetchJson(`/api/users/search/id/${encodedQuery}?${bounds}`, abortController.signal));
      }

      try {
        const responses = await Promise.all(requests);
        if (currentRequest !== requestSequence || input.value.trim() !== query) {
          return;
        }

        const usersById = new Map();
        for (const response of responses) {
          if (!Array.isArray(response)) {
            continue;
          }

          for (const item of response) {
            const user = toSearchUser(item);
            if (user && !usersById.has(String(user.id))) {
              usersById.set(String(user.id), user);
            }
          }
        }

        const users = Array.from(usersById.values()).sort(compareUsersById);
        selectedIndex = users.length ? 0 : -1;
        renderResults('results', users);
      } catch (error) {
        if (error.name === 'AbortError') {
          return;
        }

        if (currentRequest === requestSequence && input.value.trim() === query) {
          renderResults('error');
        }
      }
    };

    const queueSearch = () => {
      const query = input.value.trim();
      if (query === '') {
        clearSearch();
        return;
      }

      if (debounceId) {
        window.clearTimeout(debounceId);
      }

      debounceId = window.setTimeout(() => {
        debounceId = null;
        searchUsers(query);
      }, debounceDelay);
    };

    const reopenOrSearch = () => {
      const query = input.value.trim();
      if (query === '') {
        return;
      }

      if (resultsPanel.children.length > 0) {
        setDropdownOpen(true);
        return;
      }

      queueSearch();
    };

    return {
      init() {
        input.addEventListener('input', queueSearch);
        input.addEventListener('focus', reopenOrSearch);

        input.addEventListener('keydown', (event) => {
          if (profileModal && !profileModal.overlay.hidden) {
            return;
          }

          if (!form.classList.contains('open') || !currentUsers.length) {
            return;
          }

          if (event.key === 'ArrowDown') {
            event.preventDefault();
            selectedIndex = Math.min(selectedIndex + 1, currentUsers.length - 1);
            syncSelectedRow();
            return;
          }

          if (event.key === 'ArrowUp') {
            event.preventDefault();
            selectedIndex = Math.max(selectedIndex - 1, 0);
            syncSelectedRow();
            return;
          }

          if (event.key === 'Enter') {
            event.preventDefault();
            openSelectedUser();
          }
        });

        form.addEventListener('submit', (event) => {
          event.preventDefault();
          if (form.classList.contains('open') && currentUsers.length) {
            openSelectedUser();
            return;
          }

          const query = input.value.trim();
          if (query === '') {
            clearSearch();
            return;
          }

          if (debounceId) {
            window.clearTimeout(debounceId);
            debounceId = null;
          }
          searchUsers(query);
        });

        document.addEventListener('click', (event) => {
          if (!form.contains(event.target)) {
            setDropdownOpen(false);
          }
        });

        document.addEventListener('keydown', (event) => {
          if (event.key === 'Escape' && isTypingTarget(event.target)) {
            event.target.blur();
            return;
          }

          if ((event.key === 'ArrowLeft' || event.key === 'ArrowRight') && profileModal && !profileModal.overlay.hidden) {
            if (!isTypingTarget(event.target) && switchOpenProfileTab(event.key === 'ArrowRight' ? 1 : -1)) {
              event.preventDefault();
            }
            return;
          }

          if (event.key === 'Escape') {
            if (profileModal && !profileModal.overlay.hidden) {
              window.ucHeader.closeUserProfilePopup?.();
              return;
            }
            setDropdownOpen(false);
            input.blur();
            return;
          }

          if (event.key !== 'Enter' || event.repeat || !profileModal || profileModal.overlay.hidden) {
            return;
          }

          const target = event.target;
          if (isTypingTarget(target) && target !== activeProfileAction?.button) {
            return;
          }

          if (activeProfileAction?.button && !activeProfileAction.button.disabled) {
            event.preventDefault();
            performProfileAction(activeProfileAction);
          }
        });
      }
    };
  };
})();
