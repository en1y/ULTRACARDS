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
    let lastBalance = Number(document.querySelector('.points-page')?.dataset.pointsBalance || 0);
    let initialSeries = null;
    try { initialSeries = JSON.parse(document.getElementById('points-initial-series')?.textContent || 'null'); }
    catch { /* fall back to the API loader below */ }

    const format = value => Number(value || 0).toLocaleString();
    const reason = type => t(`points.transaction.${String(type || '').toLowerCase()}`);
    const icon = code => ({FIRST_GAME: '◆', FIRST_WIN: '★', TEN_GAMES: '10', TEN_WINS: '♛'})[code] || '✦';

    function pointActivityNode(node) {
        const symbol = node.querySelector('.points-symbol');
        if (symbol?.previousSibling) symbol.previousSibling.textContent = `${symbol.previousSibling.textContent.trimEnd()}\u00A0`;
        return node;
    }

    function formatDateTime(value) {
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return '—';
        const part = number => String(number).padStart(2, '0');
        return `${part(date.getDate())}.${part(date.getMonth() + 1)}.${date.getFullYear()} ${part(date.getHours())}:${part(date.getMinutes())}`;
    }

    const relativeTime = new Intl.RelativeTimeFormat(document.documentElement.lang || 'en', {numeric: 'auto'});

    /** "in 5 days" reads better than a date on a countdown; the exact date stays in the tooltip. */
    function relativeDateTime(value) {
        const difference = new Date(value).getTime() - Date.now();
        if (Number.isNaN(difference)) return '—';
        const distance = Math.abs(difference);
        const [unit, size] = distance < 3600000 ? ['minute', 60000]
            : distance < 86400000 ? ['hour', 3600000] : ['day', 86400000];
        return relativeTime.format(Math.round(difference / size), unit);
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

    function eventTarget(label, current, target) {
        const goal = document.createElement('div');
        goal.className = `points-event-target${current >= target ? ' is-complete' : ''}`;
        const copy = document.createElement('span');
        copy.append(Object.assign(document.createElement('small'), {textContent: label}),
            Object.assign(document.createElement('strong'), {textContent: `${format(current)} / ${format(target)}`}));
        const track = document.createElement('span'); track.className = 'points-event-target-track';
        track.setAttribute('role', 'progressbar'); track.setAttribute('aria-label', label);
        track.setAttribute('aria-valuemin', '0'); track.setAttribute('aria-valuemax', String(target));
        track.setAttribute('aria-valuenow', String(Math.min(current, target)));
        const fill = document.createElement('span');
        fill.style.setProperty('--event-target-progress', `${Math.min(100, current / Math.max(target, 1) * 100)}%`);
        track.append(fill); goal.append(copy, track);
        return goal;
    }

    function gameChips(types) {
        const list = document.createElement('span'); list.className = 'points-event-game-list';
        list.append(...types.map(game => Object.assign(document.createElement('span'), {
            textContent: game.length > 1 ? game[0] + game.slice(1).toLowerCase() : game
        })));
        return list;
    }

    function eventAchievement(item, event) {
        const row = document.createElement('article');
        row.className = `points-event-achievement${item.completed ? ' is-complete' : ''}`;
        const head = document.createElement('header');
        const state = document.createElement('span'); state.className = 'points-event-goal-state';
        state.textContent = item.completed ? '✓' : '✦'; state.setAttribute('aria-hidden', 'true');
        const copy = document.createElement('div'); copy.className = 'points-event-goal-copy';
        const title = document.createElement('h5'); title.textContent = item.name;
        copy.append(title);
        // An unfinished goal is already described by its own progress bars, so only
        // the states that add information get a caption.
        const note = item.completed ? t('points.events.completed') : '';
        if (note) copy.append(Object.assign(document.createElement('small'), {textContent: note}));
        const reward = document.createElement('span'); reward.className = 'points-event-reward';
        reward.append(pointsDeltaNode(item.rewardPoints));
        head.append(state, copy, reward); row.append(head);
        if (item.description) row.append(Object.assign(document.createElement('p'), {textContent: item.description}));
        // Only when the goal is stricter than its event — repeating the event's own
        // list under every goal was pure noise.
        if (item.gameTypes?.length && String(item.gameTypes) !== String(event.gameTypes || [])) {
            const games = document.createElement('div'); games.className = 'points-event-goal-games';
            games.append(Object.assign(document.createElement('small'), {textContent: t('points.events.eligibleGames')}),
                gameChips(item.gameTypes));
            row.append(games);
        }
        const targets = document.createElement('div'); targets.className = 'points-event-targets';
        if (item.gamesRequired) targets.append(eventTarget(t('points.events.target.games'), item.games, item.gamesRequired));
        if (item.winsRequired) targets.append(eventTarget(t('points.events.target.wins'), item.wins, item.winsRequired));
        if (item.lossesRequired) targets.append(eventTarget(t('points.events.target.losses'), item.losses, item.lossesRequired));
        if (item.drawsRequired) targets.append(eventTarget(t('points.events.target.draws'), item.draws, item.drawsRequired));
        row.append(targets);
        return row;
    }

    function hiddenEventAchievements(count) {
        const row = document.createElement('article'); row.className = 'points-event-achievement';
        const head = document.createElement('header');
        const state = document.createElement('span'); state.className = 'points-event-goal-state';
        state.textContent = '?'; state.setAttribute('aria-hidden', 'true');
        const copy = document.createElement('div'); copy.className = 'points-event-goal-copy';
        copy.append(Object.assign(document.createElement('h5'),
                {textContent: t('points.events.hiddenAchievements', count)}),
            Object.assign(document.createElement('small'),
                {textContent: t('points.events.hiddenAchievementHint')}));
        head.append(state, copy); row.append(head);
        return row;
    }

    /** The one line that says where an event stands in time — the rest is styling. */
    function eventSchedule(model) {
        const {item, past} = model;
        const when = document.createElement('time');
        const at = past || item.active ? item.endsAt : item.startsAt;
        if (!at) {
            when.textContent = t('points.events.noEnd');
            return when;
        }
        when.dateTime = at;
        when.title = formatDateTime(at);
        // A finished event is a diary entry, so it gets a date; anything still ahead
        // reads better as a countdown.
        when.textContent = past ? t('points.events.endedOn', formatDateTime(at))
            : t(item.active ? 'points.events.ends' : 'points.events.starts', relativeDateTime(at));
        return when;
    }

    function eventCard(model) {
        const {item, goals, completeGoals, goalCount, hiddenAchievementCount, completed, totalReward, past} = model;
        // <details> carries the open state natively: a page full of events stays
        // scannable and only the one you can still work on starts expanded.
        const card = document.createElement('details');
        card.className = `points-event${completed ? ' is-complete' : past ? ' is-past' : item.active ? ' is-active' : ' is-upcoming'}`;
        card.open = item.active && !completed;

        const summary = document.createElement('summary');
        const heading = document.createElement('span'); heading.className = 'points-event-heading';
        heading.append(Object.assign(document.createElement('h4'), {textContent: item.name}), eventSchedule(model));
        const badge = document.createElement('span'); badge.className = 'points-event-state';
        badge.textContent = completed ? t('points.events.completed')
            : past ? t('points.events.expired')
                : item.active ? t('points.events.active') : t('points.events.upcoming');
        const chevron = document.createElement('span');
        chevron.className = 'points-event-chevron'; chevron.setAttribute('aria-hidden', 'true');
        summary.append(heading, badge, chevron);

        if (goalCount) {
            const progress = document.createElement('span'); progress.className = 'points-event-progress';
            const track = document.createElement('span'); track.className = 'points-event-progress-track';
            track.setAttribute('role', 'progressbar'); track.setAttribute('aria-label', t('points.events.overallProgress'));
            track.setAttribute('aria-valuemin', '0'); track.setAttribute('aria-valuemax', String(goalCount));
            track.setAttribute('aria-valuenow', String(completeGoals));
            const fill = document.createElement('span');
            fill.style.setProperty('--event-progress', `${completeGoals / goalCount * 100}%`);
            track.append(fill);
            progress.append(track, Object.assign(document.createElement('strong'),
                {textContent: t('points.events.goalProgress', completeGoals, goalCount)}));
            // What is still on the table while an event runs; what you walked away
            // with once it is over — or once you have collected the lot.
            const banked = past || completed;
            const reward = document.createElement('span'); reward.className = 'points-event-total-reward';
            reward.append(Object.assign(document.createElement('small'),
                    {textContent: t(banked ? 'points.events.earned' : 'points.events.totalReward')}),
                pointsDeltaNode(banked ? Number(item.earnedPoints || 0) : totalReward));
            summary.append(progress);
            if (banked || !hiddenAchievementCount) summary.append(reward);
        }
        card.append(summary);

        const body = document.createElement('div'); body.className = 'points-event-body';
        if (item.description) body.append(Object.assign(document.createElement('p'),
            {className: 'points-event-description', textContent: item.description}));

        const meta = document.createElement('div'); meta.className = 'points-event-meta';
        const games = document.createElement('span'); games.className = 'points-event-games';
        games.append(Object.assign(document.createElement('small'), {textContent: t('points.events.eligibleGames')}),
            gameChips(item.gameTypes.length ? item.gameTypes : [t('points.events.allGames')]));
        meta.append(games);
        if (goalCount && item.completionRewardPoints > 0) {
            const bonus = document.createElement('span'); bonus.className = 'points-event-bonus';
            bonus.append(Object.assign(document.createElement('small'), {textContent: t('points.events.completionBonus')}),
                pointsDeltaNode(item.completionRewardPoints));
            meta.append(bonus);
        }
        body.append(meta);

        if (goalCount) {
            const goalList = document.createElement('div'); goalList.className = 'points-event-achievements';
            goalList.append(...goals.map(goal => eventAchievement(goal, item)));
            if (hiddenAchievementCount) goalList.append(hiddenEventAchievements(hiddenAchievementCount));
            body.append(goalList);
        } else {
            body.append(Object.assign(document.createElement('p'),
                {className: 'points-event-notice', textContent: t('points.events.noGoals')}));
        }
        card.append(body);
        return card;
    }

    /** The rest of the list behind a disclosure — <details> keeps the open state, so
     *  there is no button to wire up and nothing to remember across a refresh. */
    function moreEvents(models) {
        const more = document.createElement('details');
        more.className = 'points-events-more';
        const summary = document.createElement('summary');
        summary.className = 'btn points-events-more-button';
        const chevron = document.createElement('span');
        chevron.className = 'points-event-chevron'; chevron.setAttribute('aria-hidden', 'true');
        summary.append(Object.assign(document.createElement('span'),
                {className: 'points-events-more-show', textContent: t('points.events.showMore', models.length)}),
            Object.assign(document.createElement('span'),
                {className: 'points-events-more-hide', textContent: t('points.events.showLess')}), chevron);
        const list = document.createElement('div');
        list.className = 'points-events-list';
        list.append(...models.map(eventCard));
        more.append(summary, list);
        return more;
    }

    function renderEvents(items) {
        eventsSection.hidden = !items.length;
        const now = Date.now();
        const models = items.map(item => {
            const goals = item.achievements || [];
            const hiddenAchievementCount = Math.max(0, Number(item.hiddenAchievementCount || 0));
            const goalCount = goals.length + hiddenAchievementCount;
            const completeGoals = goals.filter(goal => goal.completed).length;
            const ends = item.endsAt ? new Date(item.endsAt).getTime() : Infinity;
            return {
                item, goals, completeGoals, goalCount, hiddenAchievementCount, ends,
                completed: goalCount > 0 && hiddenAchievementCount === 0 && completeGoals === goals.length,
                past: ends <= now,
                totalReward: goals.length
                    ? goals.reduce((total, goal) => total + Number(goal.rewardPoints || 0), Number(item.completionRewardPoints || 0))
                    : 0
            };
        });
        const [first, ...rest] = models;
        if (!first) return events.replaceChildren();
        events.replaceChildren(...rest.length ? [eventCard(first), moreEvents(rest)] : [eventCard(first)]);
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
        amountCell.append(pointActivityNode(pointsDeltaNode(amount)));
        const balanceCell = document.createElement('td');
        balanceCell.className = 'points-value';
        balanceCell.append(pointActivityNode(pointsCompactNode(latest.balanceAfter)));
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
    if (initialSeries) renderPointsChart(chartCanvas, initialSeries,
        {emptyElement: chartEmpty, balance: lastBalance, days: range});
    else refresh();
})();
