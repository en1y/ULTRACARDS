(() => {
    const balance = document.getElementById('points-balance');
    const delta = document.getElementById('points-delta');
    const claim = document.getElementById('points-claim');
    const claimStatus = document.getElementById('points-claim-status');
    const achievements = document.getElementById('points-achievements');
    const fee = document.getElementById('points-fee');
    const eventsSection = document.getElementById('points-events-section');
    const events = document.getElementById('points-events');
    const transactions = document.getElementById('points-transactions');
    const previous = document.getElementById('points-previous');
    const next = document.getElementById('points-next');
    const pageLabel = document.getElementById('points-page-label');
    let page = 0;
    const chartCanvas = document.getElementById('points-chart');
    const chartEmpty = document.getElementById('points-chart-empty');
    const rangeButtons = [...document.querySelectorAll('[data-points-range]')];
    let range = 3;
    let lastBalance = 0;

    const format = value => Number(value || 0).toLocaleString();
    const reason = type => t(`points.transaction.${String(type || '').toLowerCase()}`);
    const icon = code => ({FIRST_GAME: '◆', FIRST_WIN: '★', TEN_GAMES: '10', TEN_WINS: '♛'})[code] || '✦';

    function formatDateTime(value) {
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return '—';
        const part = number => String(number).padStart(2, '0');
        return `${part(date.getDate())}.${part(date.getMonth() + 1)}.${date.getFullYear()} ${part(date.getHours())}:${part(date.getMinutes())}`;
    }

    function renderAchievements(items) {
        achievements.replaceChildren(...items.map(item => {
            const row = document.createElement('div');
            row.className = `points-achievement${item.earned ? ' is-complete' : ''}`;

            const badge = document.createElement('span');
            badge.className = 'points-achievement-icon';
            badge.textContent = item.earned ? '✓' : icon(item.code);
            badge.setAttribute('aria-hidden', 'true');

            const copy = document.createElement('span');
            copy.className = 'points-achievement-copy';
            const title = document.createElement('strong');
            title.textContent = t(`points.achievement.${item.code.toLowerCase()}`);
            const progressText = document.createElement('small');
            progressText.textContent = item.earned
                ? t('points.achievement.complete')
                : t('points.achievement.progress', item.current, item.target);
            copy.append(title, progressText);

            const reward = document.createElement('span');
            reward.className = 'points-achievement-reward';
            reward.append(pointsDeltaNode(item.reward));

            const track = document.createElement('span');
            track.className = 'points-achievement-track';
            const fill = document.createElement('span');
            fill.style.setProperty('--achievement-progress', `${Math.min(100, item.current / Math.max(item.target, 1) * 100)}%`);
            track.append(fill);
            row.append(badge, copy, reward, track);
            return row;
        }));
    }

    function renderAccount(account) {
        balance.replaceChildren(pointsCompactNode(account.balance));
        delta.replaceChildren(pointsDeltaNode(account.changeLast24Hours), ` ${t('points.delta', '').trim()}`);
        claim.disabled = !account.dailyClaimAvailable;
        claim.querySelector(':scope > span:first-child').textContent = account.dailyClaimAvailable
            ? t('points.daily.claim') : t('points.daily.unavailable');
        renderAchievements(account.achievements || []);
    }

    function eventAchievement(item) {
        const row = document.createElement('div');
        row.className = `points-event-achievement${item.completed ? ' is-complete' : ''}`;
        const copy = document.createElement('div');
        const title = document.createElement('strong'); title.textContent = item.name;
        const description = document.createElement('small');
        const targets = [];
        if (item.gamesRequired) targets.push(t('points.events.gamesProgress', item.games, item.gamesRequired));
        if (item.winsRequired) targets.push(t('points.events.winsProgress', item.wins, item.winsRequired));
        if (item.lossesRequired) targets.push(t('points.events.lossesProgress', item.losses, item.lossesRequired));
        if (item.drawsRequired) targets.push(t('points.events.drawsProgress', item.draws, item.drawsRequired));
        description.textContent = item.completed ? t('points.events.completed') : targets.join(' · ');
        copy.append(title);
        if (item.description) copy.append(document.createTextNode(` — ${item.description}`));
        copy.append(description);
        const reward = document.createElement('span'); reward.className = 'points-event-reward';
        reward.append(pointsDeltaNode(item.rewardPoints));
        row.append(copy, reward);
        return row;
    }

    function renderEvents(items) {
        eventsSection.hidden = !items.length;
        events.replaceChildren(...items.map(item => {
            const card = document.createElement('article');
            card.className = `points-event${item.active ? ' is-active' : ' is-upcoming'}`;
            const head = document.createElement('header');
            const heading = document.createElement('div');
            const title = document.createElement('h3'); title.textContent = item.name;
            const schedule = document.createElement('small');
            schedule.textContent = item.active
                ? item.endsAt ? t('points.events.ends', formatDateTime(item.endsAt)) : t('points.events.noEnd')
                : t('points.events.starts', formatDateTime(item.startsAt));
            heading.append(title, schedule);
            const badge = document.createElement('span'); badge.className = 'points-event-state';
            badge.textContent = item.active ? t('points.events.active') : t('points.events.upcoming');
            head.append(heading, badge); card.append(head);
            if (item.description) card.append(Object.assign(document.createElement('p'), {textContent: item.description}));
            const meta = document.createElement('p'); meta.className = 'points-event-meta';
            meta.textContent = item.gameTypes.length
                ? t('points.events.games', item.gameTypes.map(game => game[0] + game.slice(1).toLowerCase()).join(', '))
                : t('points.events.allGames');
            if (item.completionRewardPoints > 0) {
                meta.append(' · ', t('points.events.completionBonus'), ' ');
                meta.append(pointsDeltaNode(item.completionRewardPoints));
            }
            card.append(meta);
            if (item.achievements?.length) {
                const goals = document.createElement('div'); goals.className = 'points-event-achievements';
                goals.append(...item.achievements.map(eventAchievement)); card.append(goals);
            }
            return card;
        }));
    }

    async function loadEvents() {
        const response = await fetch('/api/points/events', {credentials: 'same-origin'});
        if (!response.ok) throw new Error(t('points.loadFailed'));
        renderEvents(await response.json());
    }

    async function loadSettings() {
        const settings = await pointsSettings();
        fee.textContent = t('points.fee.notice', settings.wagerFeePercent);
    }

    async function loadAccount() {
        const response = await fetch('/api/points', {credentials: 'same-origin'});
        if (!response.ok) throw new Error(t('points.loadFailed'));
        const account = await response.json();
        lastBalance = account.balance;
        renderAccount(account);
    }

    async function loadChart() {
        const response = await fetch(`/api/points/series?days=${range}`, {credentials: 'same-origin'});
        if (!response.ok) throw new Error(t('points.loadFailed'));
        renderPointsChart(chartCanvas, await response.json(), {emptyElement: chartEmpty, balance: lastBalance, days: range});
    }

    /** One row per batch: four achievements unlocked together read as a single line. */
    function transactionRow(batch) {
        // Newest-first, so the group's closing balance is its first entry.
        const latest = batch[0];
        const amount = batch.reduce((total, item) => total + (Number(item.amount) || 0), 0);
        const row = document.createElement('tr');
        const reasonCell = document.createElement('td');
        reasonCell.textContent = batch.length > 1
            ? `${reason(latest.type)} ×${batch.length}`
            : reason(latest.type);
        const amountCell = document.createElement('td');
        amountCell.append(pointsDeltaNode(amount, false));
        const balanceCell = document.createElement('td');
        balanceCell.className = 'points-value';
        balanceCell.append(pointsNode(latest.balanceAfter));
        const whenCell = document.createElement('td');
        const time = document.createElement('time');
        time.dateTime = latest.createdAt;
        time.textContent = formatDateTime(latest.createdAt);
        whenCell.append(time);
        row.append(reasonCell, amountCell, balanceCell, whenCell);
        return row;
    }

    async function loadTransactions() {
        const response = await fetch(`/api/points/transactions?page=${page}&size=25`, {credentials: 'same-origin'});
        if (!response.ok) throw new Error(t('points.loadFailed'));
        const data = await response.json();
        if (data.items.length) {
            const batches = groupPointsBatches(data.items, item => item.createdAt, item => item.type);
            transactions.replaceChildren(...batches.map(group => transactionRow(group.rows)));
        } else {
            const row = document.createElement('tr');
            const cell = document.createElement('td');
            cell.colSpan = 4;
            cell.textContent = t('points.ledger.empty');
            row.append(cell);
            transactions.replaceChildren(row);
        }
        previous.disabled = data.page <= 0;
        next.disabled = data.page + 1 >= data.totalPages;
        pageLabel.textContent = t('leaderboards.page', data.page + 1, Math.max(data.totalPages, 1));
    }

    async function refresh() {
        try {
            await loadAccount();
            await Promise.all([loadTransactions(), loadChart(), loadEvents(), loadSettings()]);
        } catch (error) {
            claimStatus.textContent = error.message;
        }
    }

    rangeButtons.forEach(button => button.addEventListener('click', () => {
        range = pointsRangeDays(button.dataset.pointsRange);
        rangeButtons.forEach(other => other.classList.toggle('is-active', other === button));
        loadChart().catch(error => { claimStatus.textContent = error.message; });
    }));

    claim.addEventListener('click', async () => {
        claim.disabled = true;
        const response = await fetch('/api/points/daily-claim', {method: 'POST', credentials: 'same-origin'});
        if (!response.ok) {
            claimStatus.textContent = (await response.text()) || t('points.daily.failed');
            await loadAccount();
            return;
        }
        const result = await response.json();
        claimStatus.textContent = t('points.daily.claimed', format(result.awarded));
        renderAccount(result.account);
        page = 0;
        await loadTransactions();
    });
    document.getElementById('points-refresh').addEventListener('click', refresh);
    previous.addEventListener('click', () => { if (page > 0) { page--; loadTransactions(); } });
    next.addEventListener('click', () => { if (!next.disabled) { page++; loadTransactions(); } });
    refresh();
})();
