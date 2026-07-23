(() => {
    const PAGE_SIZE = 25;
    const state = { metric: 'GAMES_PLAYED', gameType: '', mode: '', page: 0, request: null };
    const gameSelect = document.getElementById('leaderboard-game');
    const modeSelect = document.getElementById('leaderboard-mode');
    const status = document.getElementById('leaderboard-status');
    const chart = document.getElementById('leaderboard-chart');
    const chartBars = document.getElementById('leaderboard-chart-bars');
    const chartSummary = document.getElementById('leaderboard-chart-summary');
    const chartGuideTop = document.getElementById('leaderboard-chart-guide-top');
    const chartGuideMiddle = document.getElementById('leaderboard-chart-guide-middle');
    const chartGuideBottom = document.getElementById('leaderboard-chart-guide-bottom');
    const tableShell = document.getElementById('leaderboard-table-shell');
    const rows = document.getElementById('leaderboard-rows');
    const summary = document.getElementById('leaderboard-summary');
    const threshold = document.getElementById('leaderboard-threshold');
    const currentUser = document.getElementById('leaderboard-current-user');
    const currentPosition = document.getElementById('leaderboard-current-position');
    const pagination = document.getElementById('leaderboard-pagination');
    const previous = document.getElementById('leaderboard-previous');
    const next = document.getElementById('leaderboard-next');
    const pageLabel = document.getElementById('leaderboard-page-label');

    const labels = {
        rank: t('leaderboards.column.rank'),
        games: t('leaderboards.column.games'),
        wins: t('leaderboards.column.wins'),
        winRate: t('leaderboards.column.winRate')
    };

    function selectedMetricButton() {
        return document.querySelector(`[data-metric="${state.metric}"]`);
    }

    function readUrl() {
        const params = new URLSearchParams(location.search);
        const metric = params.get('metric');
        if (['GAMES_PLAYED', 'WIN_RATE', 'WINS'].includes(metric)) state.metric = metric;
        state.gameType = params.get('gameType') || '';
        state.mode = params.get('mode') || '';
        state.page = Math.max(0, Number.parseInt(params.get('page') || '0', 10) || 0);
        gameSelect.value = [...gameSelect.options].some(option => option.value === state.gameType)
            ? state.gameType : '';
        document.querySelectorAll('[data-metric]').forEach(button => {
            const active = button.dataset.metric === state.metric;
            button.classList.toggle('is-active', active);
            button.setAttribute('aria-pressed', String(active));
        });
    }

    function writeUrl() {
        const params = new URLSearchParams();
        if (state.metric !== 'GAMES_PLAYED') params.set('metric', state.metric);
        if (state.gameType) params.set('gameType', state.gameType);
        if (state.mode) params.set('mode', state.mode);
        if (state.page) params.set('page', String(state.page));
        const query = params.toString();
        history.replaceState(null, '', `${location.pathname}${query ? `?${query}` : ''}`);
    }

    function showStatus(message, kind = '') {
        status.hidden = false;
        status.className = `leaderboard-status card${kind ? ` is-${kind}` : ''}`;
        status.replaceChildren();
        if (!kind) {
            const loader = document.createElement('span');
            loader.className = 'leaderboard-loader';
            loader.setAttribute('aria-hidden', 'true');
            status.append(loader);
        }
        const text = document.createElement('span');
        text.textContent = message;
        status.append(text);
        chart.hidden = true;
        tableShell.hidden = true;
        pagination.hidden = true;
    }

    function gameName() {
        return gameSelect.selectedOptions[0]?.textContent || t('leaderboards.game.all');
    }

    function modeLabel(mode) {
        const declarations = mode.endsWith('_WITH_DECLARATIONS');
        const base = declarations ? mode.slice(0, -'_WITH_DECLARATIONS'.length) : mode;
        const keys = {
            TWO_PLAYERS: state.gameType === 'Briskula' ? 'gameConfig.1v1x3' : 'gameConfig.1v1',
            TWO_PLAYERS_FOUR_CARDS_IN_HAND_EACH: 'gameConfig.1v1x4',
            THREE_PLAYERS: 'gameConfig.3p',
            FOUR_PLAYERS_NO_TEAMS: 'gameConfig.4p',
            FOUR_PLAYERS_WITH_TEAMS: 'gameConfig.2v2'
        };
        const label = keys[base] ? t(keys[base]) : titleCaseGameName(base);
        return declarations ? `${label} · ${t('gameConfig.withDeclarations')}` : label;
    }

    function updateModes(availableModes) {
        const oldMode = state.mode;
        modeSelect.replaceChildren();
        const all = document.createElement('option');
        all.value = '';
        all.textContent = t('leaderboards.mode.all');
        modeSelect.append(all);
        availableModes.forEach(mode => {
            const option = document.createElement('option');
            option.value = mode;
            option.textContent = modeLabel(mode);
            modeSelect.append(option);
        });
        const valid = availableModes.includes(oldMode);
        state.mode = valid ? oldMode : '';
        modeSelect.value = state.mode;
        modeSelect.disabled = availableModes.length === 0;
        if (!valid && oldMode) writeUrl();
    }

    function cell(value, className, label) {
        const output = document.createElement('td');
        output.className = className;
        output.dataset.label = label;
        output.textContent = value;
        return output;
    }

    function metricLabel() {
        return selectedMetricButton()?.querySelector('span')?.textContent || state.metric;
    }

    function metricValue(entry) {
        if (state.metric === 'WIN_RATE') return entry.winRate;
        if (state.metric === 'WINS') return entry.wins;
        return entry.gamesPlayed;
    }

    function metricDisplay(entry) {
        return state.metric === 'WIN_RATE'
            ? `${entry.winRate.toFixed(1)}%`
            : metricValue(entry).toLocaleString();
    }

    function openProfile(entry, source) {
        if (entry.userId == null) return;
        window.ucHeader?.openUserProfilePopup?.({ id: entry.userId, username: entry.username }, {
            source: 'leaderboard',
            refocusElement: source
        });
    }

    function chartHeight(entry, maximum) {
        if (state.metric === 'WIN_RATE') return Math.min(100, Math.max(0, entry.winRate));
        return metricValue(entry) / maximum * 100;
    }

    function updateChartGuides(maximum) {
        const top = state.metric === 'WIN_RATE' ? '100%' : maximum.toLocaleString();
        const middle = state.metric === 'WIN_RATE' ? '50%' : Math.round(maximum / 2).toLocaleString();
        chartGuideTop.textContent = top;
        chartGuideMiddle.textContent = middle;
        chartGuideBottom.textContent = state.metric === 'WIN_RATE' ? '0%' : '0';
    }

    function renderChart(data) {
        const entries = data.items.slice(0, 10);
        if (entries.length === 0) {
            chart.hidden = true;
            chartBars.replaceChildren();
            return;
        }
        const maximum = Math.max(...entries.map(metricValue), 1);
        const bars = entries.map(entry => {
            const item = document.createElement('button');
            item.className = 'leaderboard-chart-item';
            item.type = 'button';
            const tooltip = `${entry.username} · ${metricLabel()}: ${metricDisplay(entry)}`;
            item.dataset.tooltip = tooltip;
            item.setAttribute('aria-label', `${tooltip}. ${t('leaderboards.openProfile', entry.username)}`);
            item.addEventListener('click', () => openProfile(entry, item));

            const bar = document.createElement('span');
            bar.className = 'leaderboard-chart-bar';
            bar.style.setProperty('--bar-height', String(chartHeight(entry, maximum)));
            const label = document.createElement('span');
            label.className = 'leaderboard-chart-label';
            label.textContent = `#${entry.position}`;
            item.append(bar, label);
            return item;
        });
        chartBars.replaceChildren(...bars);
        updateChartGuides(maximum);
        chartSummary.textContent = t('leaderboards.summary', data.totalElements.toLocaleString(), metricLabel());
        chart.hidden = false;
    }

    function renderRow(entry) {
        const row = document.createElement('tr');
        if (entry.position <= 3) row.classList.add('is-podium', `is-rank-${entry.position}`);
        if (entry.currentUser) row.classList.add('is-current-user');

        const rank = document.createElement('td');
        rank.className = 'leaderboard-rank';
        rank.dataset.label = labels.rank;
        const badge = document.createElement('span');
        badge.className = 'leaderboard-rank-badge';
        badge.textContent = `#${entry.position}`;
        rank.append(badge);

        const player = document.createElement('td');
        player.className = 'leaderboard-player';
        const playerButton = document.createElement('button');
        playerButton.className = 'leaderboard-player-button';
        playerButton.type = 'button';
        playerButton.textContent = entry.username;
        playerButton.setAttribute('aria-label', t('leaderboards.openProfile', entry.username));
        playerButton.addEventListener('click', () => openProfile(entry, playerButton));
        player.append(playerButton);
        if (entry.currentUser) {
            const you = document.createElement('span');
            you.className = 'leaderboard-you';
            you.textContent = t('leaderboards.you');
            player.append(you);
        }

        const games = cell(entry.gamesPlayed.toLocaleString(), 'leaderboard-number', labels.games);
        const wins = cell(entry.wins.toLocaleString(), 'leaderboard-number', labels.wins);
        const winRate = cell(`${entry.winRate.toFixed(1)}%`, 'leaderboard-number', labels.winRate);
        const primary = state.metric === 'GAMES_PLAYED' ? games : state.metric === 'WINS' ? wins : winRate;
        primary.classList.add('leaderboard-primary-value');
        row.append(rank, player, games, wins, winRate);
        return row;
    }

    function render(data) {
        updateModes(data.availableModes || []);
        if (data.items.length === 0 && data.totalElements > 0 && state.page > 0) {
            state.page = Math.max(0, data.totalPages - 1);
            writeUrl();
            load();
            return;
        }
        rows.replaceChildren(...data.items.map(renderRow));
        status.hidden = true;
        tableShell.hidden = data.items.length === 0;
        renderChart(data);

        const scope = state.mode ? `${gameName()} · ${modeLabel(state.mode)}` : gameName();
        summary.textContent = t('leaderboards.summary', data.totalElements.toLocaleString(), scope);
        threshold.hidden = data.metric !== 'WIN_RATE';
        threshold.textContent = data.metric === 'WIN_RATE'
            ? t('leaderboards.threshold', data.minimumGames) : '';

        currentUser.hidden = data.currentUserPosition == null;
        if (data.currentUserPosition != null) currentPosition.textContent = `#${data.currentUserPosition}`;

        if (data.items.length === 0) {
            showStatus(t('leaderboards.empty'), 'empty');
            return;
        }

        pagination.hidden = data.totalPages <= 1;
        previous.disabled = data.page <= 0;
        next.disabled = data.page + 1 >= data.totalPages;
        pageLabel.textContent = t('leaderboards.page', data.page + 1, data.totalPages);
    }

    async function load() {
        state.request?.abort();
        state.request = new AbortController();
        showStatus(t('leaderboards.loading'));
        currentUser.hidden = true;
        const params = new URLSearchParams({ metric: state.metric, page: state.page, size: PAGE_SIZE });
        if (state.gameType) params.set('gameType', state.gameType);
        if (state.mode) params.set('mode', state.mode);
        try {
            const response = await fetch(`/api/leaderboards?${params}`, {
                credentials: 'same-origin',
                signal: state.request.signal
            });
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            render(await response.json());
        } catch (error) {
            if (error.name !== 'AbortError') showStatus(t('leaderboards.error'), 'error');
        }
    }

    document.querySelectorAll('[data-metric]').forEach(button => button.addEventListener('click', () => {
        if (button === selectedMetricButton()) return;
        state.metric = button.dataset.metric;
        state.page = 0;
        document.querySelectorAll('[data-metric]').forEach(item => {
            const active = item === button;
            item.classList.toggle('is-active', active);
            item.setAttribute('aria-pressed', String(active));
        });
        writeUrl();
        load();
    }));

    gameSelect.addEventListener('change', () => {
        state.gameType = gameSelect.value;
        state.mode = '';
        state.page = 0;
        modeSelect.disabled = true;
        writeUrl();
        load();
    });
    modeSelect.addEventListener('change', () => {
        state.mode = modeSelect.value;
        state.page = 0;
        writeUrl();
        load();
    });
    previous.addEventListener('click', () => {
        if (state.page === 0) return;
        state.page--;
        writeUrl();
        load();
        document.getElementById('leaderboard-results-title').scrollIntoView({ behavior: 'smooth' });
    });
    next.addEventListener('click', () => {
        state.page++;
        writeUrl();
        load();
        document.getElementById('leaderboard-results-title').scrollIntoView({ behavior: 'smooth' });
    });

    readUrl();
    load();
})();
