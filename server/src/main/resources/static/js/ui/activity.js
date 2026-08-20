/**
 * The GitHub-style activity graph: one square per day, 53 columns of Monday-first weeks.
 *
 * Shared by the profile page and the header's user popup, so it takes a container and the
 * day rows the Points API returns and owns nothing else.
 */
(() => {
  const WEEKS = 53;
  const DAY_MS = 86400000;
  // Squares per level. Four buckets is what makes a quiet week still readable next to a busy one.
  const LEVELS = [1, 3, 6, 10];

  // Same contract as admin.js's `tr`: the fallback is already interpolated by its caller,
  // so a missing key still reads as a sentence instead of a raw "{0}".
  const t = (key, fallback, ...args) => (typeof window.t === 'function' && (window.__I18N__ || {})[key]
    ? window.t(key, ...args)
    : fallback);

  const pad = (number) => String(number).padStart(2, '0');
  // Keys must match the server's LocalDate strings, and `new Date('2026-08-19')` parses as
  // UTC midnight — which is the day before in western zones. Build the key by hand instead.
  const isoKey = (date) => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
  const displayDate = (date) => `${pad(date.getDate())}.${pad(date.getMonth() + 1)}.${date.getFullYear()}`;
  const addDays = (date, days) => new Date(date.getFullYear(), date.getMonth(), date.getDate() + days);

  /** Monday of the first column, and the day count that covers the grid. */
  const gridRange = (today = new Date()) => {
    const anchor = new Date(today.getFullYear(), today.getMonth(), today.getDate());
    const mondayFirst = (anchor.getDay() + 6) % 7;
    const weekEnd = addDays(anchor, 6 - mondayFirst);
    const start = addDays(weekEnd, -(WEEKS * 7) + 1);
    return { start, days: Math.round((anchor - start) / DAY_MS) + 1 };
  };

  const levelOf = (games) => {
    if (!games) return 0;
    for (let index = 0; index < LEVELS.length; index += 1) if (games <= LEVELS[index]) return index + 1;
    return LEVELS.length;
  };

  const monthLabels = () => {
    const names = t('activity.months', 'Jan,Feb,Mar,Apr,May,Jun,Jul,Aug,Sep,Oct,Nov,Dec');
    return String(names).split(',');
  };

  const weekdayLabels = () => {
    const names = t('activity.weekdays', 'Mon,Tue,Wed,Thu,Fri,Sat,Sun');
    return String(names).split(',');
  };

  /**
   * Follows the pointer across the grid and parks a styled bubble above the hovered
   * square. Delegated from the grid so 371 squares cost two listeners, and positioned
   * against the graph root so the bubble is never clipped by the scroll container.
   */
  const attachTooltip = (root, grid) => {
    const bubble = document.createElement('div');
    bubble.className = 'activity-tooltip';
    bubble.setAttribute('role', 'tooltip');
    bubble.hidden = true;

    const show = (cell) => {
      bubble.textContent = cell.dataset.tooltip || '';
      bubble.hidden = false;
      const square = cell.getBoundingClientRect();
      const bounds = root.getBoundingClientRect();
      const width = bubble.offsetWidth;
      // Clamp inside the graph so the first and last columns stay readable.
      const left = Math.min(
        Math.max(square.left - bounds.left + square.width / 2 - width / 2, 0),
        Math.max(bounds.width - width, 0));
      bubble.style.left = `${left}px`;
      bubble.style.top = `${square.top - bounds.top - bubble.offsetHeight - 6}px`;
    };

    grid.addEventListener('mouseover', (event) => {
      const cell = event.target instanceof Element ? event.target.closest('.activity-day') : null;
      if (cell) show(cell);
    });
    grid.addEventListener('mouseleave', () => { bubble.hidden = true; });
    return bubble;
  };

  /**
   * @param {Element} container  emptied and filled with the graph
   * @param {Array}   days       [{date:'yyyy-mm-dd', games, wins}], as returned by the Points API
   */
  const renderActivityGraph = (container, days) => {
    if (!container) return;
    const byDate = new Map();
    let total = 0;
    for (const day of Array.isArray(days) ? days : []) {
      if (!day?.date) continue;
      byDate.set(String(day.date), day);
      total += Number(day.games) || 0;
    }

    const { start } = gridRange();
    const months = monthLabels();
    const weekdays = weekdayLabels();

    const root = document.createElement('div');
    root.className = 'activity-graph';

    const scroll = document.createElement('div');
    scroll.className = 'activity-graph-scroll';

    const monthsRow = document.createElement('div');
    monthsRow.className = 'activity-graph-months';
    monthsRow.setAttribute('aria-hidden', 'true');

    const body = document.createElement('div');
    body.className = 'activity-graph-body';

    const weekdayColumn = document.createElement('div');
    weekdayColumn.className = 'activity-graph-weekdays';
    weekdayColumn.setAttribute('aria-hidden', 'true');
    // Every other row, the way GitHub does it: seven stacked labels do not fit.
    weekdays.forEach((label, index) => {
      const cell = document.createElement('span');
      if (index % 2 === 1) cell.textContent = label;
      weekdayColumn.append(cell);
    });

    const grid = document.createElement('div');
    grid.className = 'activity-graph-grid';
    grid.setAttribute('role', 'img');
    grid.setAttribute('aria-label',
      t('activity.aria', `Activity graph. Games in the last year: ${total}`, total));

    let lastMonth = -1;
    for (let week = 0; week < WEEKS; week += 1) {
      const columnStart = addDays(start, week * 7);
      const label = document.createElement('span');
      // Label a column only when its month is new, so each month is named once.
      if (columnStart.getMonth() !== lastMonth) {
        lastMonth = columnStart.getMonth();
        label.textContent = months[lastMonth] || '';
      }
      monthsRow.append(label);

      for (let weekday = 0; weekday < 7; weekday += 1) {
        const date = addDays(columnStart, weekday);
        const cell = document.createElement('span');
        cell.className = 'activity-day';
        const entry = byDate.get(isoKey(date));
        const games = Number(entry?.games) || 0;
        cell.dataset.level = String(levelOf(games));
        const wins = Number(entry?.wins) || 0;
        // Held in a data attribute rather than `title`: the native tooltip waits about a
        // second, cannot be styled, and would double up with the one below.
        cell.dataset.tooltip = games
          ? t('activity.tooltip', `${displayDate(date)} · ${games} played, ${wins} won`,
            games, wins, displayDate(date))
          : t('activity.tooltipEmpty', `No games on ${displayDate(date)}`, displayDate(date));
        grid.append(cell);
      }
    }

    body.append(weekdayColumn, grid);
    scroll.append(monthsRow, body);

    const legend = document.createElement('div');
    legend.className = 'activity-graph-legend';
    const totalLabel = document.createElement('span');
    totalLabel.className = 'activity-graph-total';
    totalLabel.textContent = t('activity.total', `Games in the last year: ${total}`, total);
    const less = document.createElement('small');
    less.textContent = t('activity.less', 'Less');
    const more = document.createElement('small');
    more.textContent = t('activity.more', 'More');
    const scale = document.createElement('span');
    scale.className = 'activity-graph-scale';
    scale.setAttribute('aria-hidden', 'true');
    for (let level = 0; level <= LEVELS.length; level += 1) {
      const key = document.createElement('span');
      key.className = 'activity-day';
      key.dataset.level = String(level);
      scale.append(key);
    }
    legend.append(totalLabel, less, scale, more);

    root.append(scroll, legend, attachTooltip(root, grid));
    container.replaceChildren(root);
    // The newest week sits on the right; nobody wants to scroll there by hand.
    scroll.scrollLeft = scroll.scrollWidth;
  };

  /** Fetches and renders in one call. `userId` omitted means the signed-in player. */
  const loadActivityGraph = async (container, userId) => {
    if (!container) return;
    const { days } = gridRange();
    const path = userId
      ? `/api/points/users/${encodeURIComponent(userId)}/activity?days=${days}`
      : `/api/points/activity?days=${days}`;
    try {
      const response = await fetch(path, { credentials: 'include', cache: 'no-store' });
      if (!response.ok) throw new Error(`Activity failed: ${response.status}`);
      renderActivityGraph(container, await response.json());
    } catch (error) {
      console.warn('Unable to load the activity graph', error);
      const failed = document.createElement('p');
      failed.className = 'activity-graph-state';
      failed.textContent = t('activity.loadFailed', 'Activity could not be loaded.');
      container.replaceChildren(failed);
    }
  };

  // ---------------------------------------------------------------- achievements

  // Rendered as three groups so a wall of streak milestones does not bury the rest.
  const GROUPS = [
    ['STREAK', 'achievements.group.streak', 'Play streak'],
    ['GAMES', 'achievements.group.games', 'Games played'],
    ['WINS', 'achievements.group.wins', 'Games won']
  ];

  const pointsNode = (value) => (window.pointsCompactNode
    ? window.pointsCompactNode(value)
    : document.createTextNode(String(value ?? 0)));

  const percentOf = (achievement) => {
    const target = Number(achievement?.target) || 0;
    const current = Number(achievement?.current) || 0;
    return target > 0 ? Math.min(100, Math.round((current / target) * 100)) : 0;
  };

  const progressBar = (achievement, className) => {
    const bar = document.createElement('div');
    bar.className = className || 'achievement-bar';
    bar.setAttribute('role', 'progressbar');
    bar.setAttribute('aria-valuemin', '0');
    bar.setAttribute('aria-valuemax', String(Number(achievement?.target) || 0));
    bar.setAttribute('aria-valuenow', String(Math.min(
      Number(achievement?.current) || 0,
      Number(achievement?.target) || 0)));
    const fill = document.createElement('span');
    fill.style.setProperty('--progress', `${percentOf(achievement)}%`);
    bar.append(fill);
    return bar;
  };

  const tile = (value, label, className) => {
    const node = document.createElement('div');
    node.className = `streak-tile${className ? ` ${className}` : ''}`;
    const strong = document.createElement('strong');
    strong.textContent = String(value);
    const small = document.createElement('small');
    small.textContent = label;
    node.append(strong, small);
    return node;
  };

  /** Current streak, longest, and three ice tokens — the header of the whole tab. */
  const renderStreak = (streak) => {
    const section = document.createElement('section');
    section.className = 'streak-summary';
    const current = Number(streak?.current) || 0;
    const longest = Number(streak?.longest) || 0;
    const freezes = Number(streak?.freezes) || 0;

    const currentTile = tile(current, t('achievements.streak.current', 'Day streak'),
      `streak-tile--hero${streak?.atRisk ? ' is-at-risk' : ''}${current > 0 ? ' is-alive' : ''}`);
    section.append(currentTile, tile(longest, t('achievements.streak.longest', 'Longest streak')));

    const freezeTile = tile(`${Math.min(freezes, 3)} / 3`,
      t('achievements.streak.freezes', 'Streak freezes'), 'streak-tile--freezes');
    const dots = document.createElement('span');
    dots.className = 'streak-freezes';
    dots.setAttribute('aria-hidden', 'true');
    for (let index = 0; index < 3; index += 1) {
      const dot = document.createElement('span');
      dot.className = `streak-freeze${index < freezes ? ' is-held' : ''}`;
      dot.textContent = '❄';
      dots.append(dot);
    }
    freezeTile.append(dots);
    section.append(freezeTile);
    return section;
  };

  /**
   * The nearest unearned milestone, given its own banner. One clear "you are N away
   * from X" beats twenty identical cards for telling a player what to do next.
   */
  const renderNextUp = (achievements) => {
    let next = null;
    for (const achievement of achievements) {
      if (achievement.earned) continue;
      const remaining = (Number(achievement.target) || 0) - (Number(achievement.current) || 0);
      if (remaining <= 0) continue;
      if (!next || remaining < next.remaining) next = { achievement, remaining };
    }
    if (!next) return null;

    const banner = document.createElement('section');
    banner.className = 'achievement-next';

    const head = document.createElement('div');
    head.className = 'achievement-next-head';
    const kicker = document.createElement('p');
    kicker.className = 'achievement-next-kicker';
    kicker.textContent = t('achievements.nextUp', 'Up next');
    const name = document.createElement('h4');
    name.textContent = next.achievement.name || '';
    const copy = document.createElement('div');
    copy.append(kicker, name);
    const reward = document.createElement('span');
    reward.className = 'achievement-reward points-value';
    reward.append(pointsNode(next.achievement.reward));
    head.append(copy, reward);

    const foot = document.createElement('div');
    foot.className = 'achievement-next-foot';
    const progress = document.createElement('small');
    const current = Number(next.achievement.current) || 0;
    const target = Number(next.achievement.target) || 0;
    progress.textContent = t('points.achievement.progress', `${current} of ${target}`, current, target);
    const remaining = document.createElement('small');
    remaining.className = 'achievement-next-remaining';
    remaining.textContent = next.achievement.metric === 'STREAK'
      ? t('achievements.daysToGo', `${next.remaining} days to go`, next.remaining)
      : t('achievements.toGo', `${next.remaining} to go`, next.remaining);
    foot.append(progress, remaining);

    banner.append(head, progressBar(next.achievement, 'achievement-bar achievement-bar--next'), foot);
    return banner;
  };

  /** "8 of 20 earned · 24.5K P" — the at-a-glance answer to "how am I doing". */
  const renderScore = (achievements) => {
    const earned = achievements.filter((achievement) => achievement.earned);
    let total = 0;
    for (const achievement of earned) total += Number(achievement.reward) || 0;

    const score = document.createElement('section');
    score.className = 'achievement-score';

    const count = document.createElement('span');
    count.className = 'achievement-score-count';
    count.textContent = t('achievements.earnedCount',
      `${earned.length} of ${achievements.length} earned`, earned.length, achievements.length);

    const rail = document.createElement('div');
    rail.className = 'achievement-bar achievement-bar--score';
    const fill = document.createElement('span');
    const percent = achievements.length ? Math.round((earned.length / achievements.length) * 100) : 0;
    fill.style.setProperty('--progress', `${percent}%`);
    rail.append(fill);

    const points = document.createElement('span');
    points.className = 'achievement-score-points points-value';
    points.append(pointsNode(total));

    score.append(count, rail, points);
    return score;
  };

  const achievementRows = (list) => Array.from(list.querySelectorAll('.achievement-row'));

  const selectedAchievement = (list) => list.querySelector('.achievement-row.is-selected');

  const nearestAchievement = (list) => {
    const rows = achievementRows(list);
    if (!rows.length) return null;
    const centre = list.getBoundingClientRect().left + list.clientWidth / 2;
    let nearest = rows[0];
    let nearestDistance = Number.POSITIVE_INFINITY;
    for (const row of rows) {
      const bounds = row.getBoundingClientRect();
      const distance = Math.abs(bounds.left + bounds.width / 2 - centre);
      if (distance >= nearestDistance) continue;
      nearest = row;
      nearestDistance = distance;
    }
    return nearest;
  };

  const syncRailControls = (list, selected = selectedAchievement(list)) => {
    const group = list.closest('.achievement-group');
    if (!group || !selected) return;
    const rows = achievementRows(list);
    const index = rows.indexOf(selected);
    const previous = group.querySelector('.achievement-rail-control--previous');
    const next = group.querySelector('.achievement-rail-control--next');
    const position = group.querySelector('.achievement-carousel-position');
    if (previous) previous.disabled = index <= 0;
    if (next) next.disabled = index >= rows.length - 1;
    if (position) {
      position.textContent = (index + 1) + ' / ' + rows.length;
      position.setAttribute('aria-label',
        t('achievements.position', 'Achievement ' + (index + 1) + ' of ' + rows.length,
          index + 1, rows.length));
    }
  };

  const selectAchievement = (list, target) => {
    if (!target) return;
    for (const row of achievementRows(list)) {
      row.classList.toggle('is-selected', row === target);
    }
    syncRailControls(list, target);
  };

  /** Centres one rung without using scrollIntoView, which would also move the profile page. */
  const scrollToAchievement = (list, target, behavior = 'auto') => {
    if (!target || !list.clientWidth) return false;
    // Rect deltas rather than offsetLeft: the list is not a positioned ancestor,
    // so offsetLeft would be measured against something further up the tree.
    const requestedLeft = list.scrollLeft + target.getBoundingClientRect().left
      - list.getBoundingClientRect().left - (list.clientWidth - target.offsetWidth) / 2;
    const maximum = Math.max(0, list.scrollWidth - list.clientWidth);
    const left = Math.max(0, Math.min(maximum, requestedLeft));
    if (Math.abs(list.scrollLeft - left) < 1) return true;
    const reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    if (behavior === 'smooth' && !reducedMotion && typeof list.scrollTo === 'function') {
      list.scrollTo({ left, behavior });
    } else {
      list.classList.add('is-positioning');
      list.scrollLeft = left;
      list.classList.remove('is-positioning');
    }
    return true;
  };

  const settleAchievementRail = (list, behavior = 'smooth') => {
    const target = nearestAchievement(list);
    if (!target) return;
    selectAchievement(list, target);
    scrollToAchievement(list, target, behavior);
  };

  const railSettleTimers = new WeakMap();
  const scheduleRailSettle = (list) => {
    clearTimeout(railSettleTimers.get(list));
    railSettleTimers.set(list, setTimeout(() => settleAchievementRail(list), 90));
  };

  /** Moves exactly one milestone left or right from the card currently in focus. */
  const moveAlongRail = (list, direction) => {
    const rows = achievementRows(list);
    if (!rows.length) return;
    const active = selectedAchievement(list) || nearestAchievement(list);
    const activeIndex = Math.max(0, rows.indexOf(active));
    const nextIndex = Math.max(0, Math.min(rows.length - 1, activeIndex + direction));
    const target = rows[nextIndex];
    selectAchievement(list, target);
    scrollToAchievement(list, target, 'smooth');
  };

  /**
   * Scrolls a group so the rung the player is on sits in the middle, rather than
   * leaving them at milestone one with their progress off-screen.
   */
  const centreOnProgress = (list) => {
    const target = list.querySelector('.achievement-row.is-current') || list.lastElementChild;
    if (!target) return;
    const scrollToTarget = () => {
      selectAchievement(list, target);
      if (!scrollToAchievement(list, target)) return false;
      return true;
    };
    if (scrollToTarget()) return;
    // A tab panel can still be display:none when its content renders; centre once it is shown.
    const observer = new ResizeObserver(() => { if (scrollToTarget()) observer.disconnect(); });
    observer.observe(list);
  };

  const railControl = (list, direction) => {
    const previous = direction < 0;
    const button = document.createElement('button');
    button.className = `achievement-rail-control achievement-rail-control--${previous ? 'previous' : 'next'}`;
    button.type = 'button';
    button.setAttribute('aria-label', previous
      ? t('achievements.previous', 'Previous achievement')
      : t('achievements.next', 'Next achievement'));
    button.textContent = previous ? '‹' : '›';
    button.addEventListener('click', () => moveAlongRail(list, direction));
    return button;
  };

  /** Adds mouse dragging while leaving touch scrolling native and momentum-friendly. */
  const attachAchievementRail = (list) => {
    let drag = null;
    list.addEventListener('scroll', () => scheduleRailSettle(list), { passive: true });
    list.addEventListener('keydown', (event) => {
      if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
      event.preventDefault();
      moveAlongRail(list, event.key === 'ArrowLeft' ? -1 : 1);
    });
    list.addEventListener('pointerdown', (event) => {
      if (event.pointerType !== 'mouse' || event.button !== 0) return;
      drag = { id: event.pointerId, startX: event.clientX, startScroll: list.scrollLeft };
      list.classList.add('is-dragging');
      list.setPointerCapture?.(event.pointerId);
    });
    list.addEventListener('pointermove', (event) => {
      if (!drag || event.pointerId !== drag.id) return;
      event.preventDefault();
      list.scrollLeft = drag.startScroll - (event.clientX - drag.startX);
    });
    const finishDrag = (event) => {
      if (!drag || event.pointerId !== drag.id) return;
      list.releasePointerCapture?.(event.pointerId);
      list.classList.remove('is-dragging');
      drag = null;
      settleAchievementRail(list);
    };
    list.addEventListener('pointerup', finishDrag);
    list.addEventListener('pointercancel', finishDrag);
  };

  /** One compact row per milestone: a ladder reads better than a grid of boxes. */
  const achievementRow = (achievement, isCurrent = false) => {
    const row = document.createElement('article');
    row.className = `achievement-row${achievement.earned ? ' is-earned' : ''}${isCurrent ? ' is-current' : ''}`;
    row.setAttribute('role', 'listitem');
    if (isCurrent) row.setAttribute('aria-current', 'step');

    const mark = document.createElement('span');
    mark.className = 'achievement-mark';
    mark.setAttribute('aria-hidden', 'true');
    mark.textContent = achievement.earned ? '✓' : String(achievement.target ?? '');

    const body = document.createElement('div');
    body.className = 'achievement-row-body';
    if (isCurrent) {
      const current = document.createElement('span');
      current.className = 'achievement-current-badge';
      current.textContent = t('achievements.currentGoal', 'Current goal');
      body.append(current);
    }
    const name = document.createElement('strong');
    name.textContent = achievement.name || '';
    const status = document.createElement('small');
    status.className = 'achievement-progress';
    const current = Number(achievement.current) || 0;
    const target = Number(achievement.target) || 0;
    status.textContent = achievement.earned
      ? t('achievements.earned', 'Earned')
      : t('points.achievement.progress', `${current} of ${target}`, current, target);
    body.append(name, progressBar(achievement), status);

    const reward = document.createElement('span');
    reward.className = 'achievement-reward points-value';
    reward.append(pointsNode(achievement.reward));

    row.append(mark, body, reward);
    return row;
  };

  /**
   * @param {Element} container emptied and filled with the streak and achievement list
   * @param {Object}  data      the PointsAchievementsDTO the Points API returns
   */
  const renderAchievements = (container, data) => {
    if (!container) return;
    const root = document.createElement('div');
    root.className = 'achievements-view';
    root.append(renderStreak(data?.streak));

    if (data?.streak?.atRisk) {
      const note = document.createElement('p');
      note.className = 'streak-note';
      note.setAttribute('role', 'status');
      // A gap only costs freezes when a day was actually missed; playing the very next
      // day continues the streak for free.
      const spend = Number(data.streak.freezesToKeep) || 0;
      note.textContent = spend > 0
        ? t('achievements.streak.atRiskFreeze',
          `Play today to keep this streak - ${spend} streak freeze(s) will be spent.`, spend)
        : t('achievements.streak.atRisk', 'Play today to keep this streak going.');
      root.append(note);
    }

    // How freezes work is not guessable from a count of dots, so say it outright.
    const help = document.createElement('p');
    help.className = 'streak-help';
    help.textContent = t('achievements.streak.help',
      'Play 7 days in a row to earn a streak freeze, up to 3 at once. If you miss a day, '
      + 'one freeze is spent automatically the next time you play and the streak carries on.');
    root.append(help);

    const lists = [];
    const achievements = Array.isArray(data?.achievements) ? data.achievements : [];
    if (!achievements.length) {
      const empty = document.createElement('p');
      empty.className = 'activity-graph-state';
      empty.textContent = t('achievements.empty', 'No achievements are configured.');
      root.append(empty);
      container.replaceChildren(root);
      return;
    }

    const next = renderNextUp(achievements);
    if (next) root.append(next);
    root.append(renderScore(achievements));

    for (const [metric, key, fallback] of GROUPS) {
      const matching = achievements.filter((achievement) => achievement?.metric === metric);
      if (!matching.length) continue;
      // Ascending target: a milestone ladder only reads as a ladder in order.
      matching.sort((left, right) => (Number(left.target) || 0) - (Number(right.target) || 0));
      const done = matching.filter((achievement) => achievement.earned).length;

      const group = document.createElement('section');
      group.className = 'achievement-group';
      const head = document.createElement('div');
      head.className = 'achievement-group-head';
      const meta = document.createElement('div');
      meta.className = 'achievement-group-meta';
      const title = document.createElement('h4');
      title.textContent = t(key, fallback);
      const count = document.createElement('small');
      count.textContent = t('achievements.earnedCount',
        done + ' of ' + matching.length + ' earned', done, matching.length);
      meta.append(title, count);

      const list = document.createElement('div');
      list.className = 'achievement-list';
      list.setAttribute('role', 'list');
      list.setAttribute('aria-label', title.textContent);
      list.tabIndex = 0;
      // The first unearned rung is where the player actually is.
      const firstUnearned = matching.findIndex((achievement) => !achievement.earned);
      const currentIndex = firstUnearned < 0 ? matching.length - 1 : firstUnearned;
      matching.forEach((achievement, index) => list.append(achievementRow(achievement, index === currentIndex)));

      const actions = document.createElement('div');
      actions.className = 'achievement-carousel-actions';
      const position = document.createElement('span');
      position.className = 'achievement-carousel-position';
      position.setAttribute('aria-live', 'polite');
      actions.append(railControl(list, -1), position, railControl(list, 1));
      head.append(meta, actions);

      const rail = document.createElement('div');
      rail.className = 'achievement-rail';
      rail.append(list);
      attachAchievementRail(list);

      group.append(head, rail);
      root.append(group);
      lists.push(list);
    }

    container.replaceChildren(root);
    // Reading clientWidth inside forces the layout we need, so this runs now rather than
    // on an animation frame — rAF never fires in a background or hidden tab, which would
    // leave the ladder parked at rung one.
    lists.forEach(centreOnProgress);
  };

  /** Fetches and renders in one call. `userId` omitted means the signed-in player. */
  const loadAchievements = async (container, userId) => {
    if (!container) return;
    const path = userId
      ? `/api/points/users/${encodeURIComponent(userId)}/achievements`
      : '/api/points/achievements';
    try {
      const response = await fetch(path, { credentials: 'include', cache: 'no-store' });
      if (!response.ok) throw new Error(`Achievements failed: ${response.status}`);
      renderAchievements(container, await response.json());
    } catch (error) {
      console.warn('Unable to load achievements', error);
      const failed = document.createElement('p');
      failed.className = 'activity-graph-state';
      failed.textContent = t('achievements.loadFailed', 'Achievements could not be loaded.');
      container.replaceChildren(failed);
    }
  };

  window.renderActivityGraph = renderActivityGraph;
  window.loadActivityGraph = loadActivityGraph;
  window.renderAchievements = renderAchievements;
  window.loadAchievements = loadAchievements;
})();
