/**
 * Balance-over-time chart, shared by the Points page and the admin user page.
 * Chart.js comes off the classpath webjar, so no network is involved.
 *
 * A plain linear axis over epoch millis rather than Chart.js's time scale: the
 * time scale needs a separate date-adapter package, while this gives the same
 * day-proportional spacing — a week with two quiet days draws two flat days.
 */
(() => {
    const charts = new WeakMap();
    const DAY = 86_400_000;
    const HOUR = 3_600_000;

    const pad = number => String(number).padStart(2, '0');
    const startOfDay = value => { const date = new Date(value); date.setHours(0, 0, 0, 0); return date.getTime(); };

    // The axis drops the year — it costs width and every visible range is either
    // recent or self-evident. The tooltip below still spells the full date out.
    // A window of a day or two is labelled by hour instead; "09.08" seven times
    // over says nothing.
    const label = (value, byHour) => {
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return '';
        return byHour
            ? `${pad(date.getHours())}:${pad(date.getMinutes())}`
            : `${pad(date.getDate())}.${pad(date.getMonth() + 1)}`;
    };

    const fullDate = value => {
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return '';
        return `${pad(date.getDate())}.${pad(date.getMonth() + 1)}.${date.getFullYear()} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
    };

    /** One plotted stop per batch: the balance it ended on, the change it summed to. */
    const collapse = points => groupPointsBatches(points, row => row.at, row => row.reason)
        .map(group => {
            const last = group.rows[group.rows.length - 1];
            return {
                at: last.at,
                balance: last.balance,
                change: group.rows.reduce((total, row) => total + (Number(row.change) || 0), 0),
                reason: last.reason,
                count: group.rows.length
            };
        });

    /** Ticks on whole hours or whole midnights, roughly seven of them. */
    const axisTicks = (min, max, byHour) => {
        const ticks = [];
        if (byHour) {
            const step = Math.max(1, Math.ceil((max - min) / HOUR / 7)) * HOUR;
            const cursor = new Date(min);
            cursor.setMinutes(0, 0, 0);
            for (let value = cursor.getTime(); value <= max; value += step)
                if (value >= min) ticks.push({value});
            return ticks;
        }
        // Stepped by calendar day rather than by 86.4e6 ms, so a DST change cannot
        // walk the labels off midnight.
        const step = Math.max(1, Math.ceil((max - min) / DAY / 7));
        const cursor = new Date(min);
        cursor.setHours(0, 0, 0, 0);
        while (cursor.getTime() <= max) {
            if (cursor.getTime() >= min) ticks.push({value: cursor.getTime()});
            cursor.setDate(cursor.getDate() + step);
        }
        return ticks;
    };

    const cssColor = (name, fallback) =>
        getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback;

    // Nothing to plot yet: draw a calm placeholder curve so the card still reads as
    // a chart instead of a hole in the page.
    const placeholder = balance => {
        const base = Number(balance) || 0;
        const shape = [0.35, 0.5, 0.42, 0.62, 0.55, 0.75, 0.68];
        return shape.map((factor, index) => ({
            at: null,
            label: '',
            balance: base ? Math.round(base * factor) : Math.round(1000 * factor),
            index
        }));
    };

    window.renderPointsChart = (canvas, series, options = {}) => {
        if (!canvas || typeof Chart === 'undefined') return null;
        const points = collapse(Array.isArray(series) ? series : []);
        // Only a user with no ledger at all gets the decorative stand-in; one entry
        // is enough to draw an honest flat line across the window.
        const empty = !points.length && options.balance == null;
        const days = Number(options.days) || 0;
        const now = Date.now();
        // Whole days, so a 7-day range is exactly seven slots wide.
        const windowStart = days > 0
            ? startOfDay(now) - (days - 1) * DAY
            : startOfDay(points[0]?.at ?? now);
        // Ends at "now", not at midnight: the rest of today has not happened, and
        // drawing it as empty canvas reads like missing data.
        const windowEnd = now;
        const span = windowEnd - windowStart;
        const byHour = span <= 2 * DAY;

        // The fetch window can reach further back than the drawn one, so the opening
        // balance is whatever the last entry before the window left behind.
        const before = points.filter(row => new Date(row.at).getTime() < windowStart);
        const inWindow = points.filter(row => new Date(row.at).getTime() >= windowStart);
        const opening = before.length
            ? before[before.length - 1].balance
            : inWindow.length
                ? inWindow[0].balance - (Number(inWindow[0].change) || 0)
                : Number(options.balance) || 0;
        const closing = inWindow.length ? inWindow[inWindow.length - 1].balance : opening;
        // Anchors at both ends: without them a quiet week would draw as a dot in the
        // middle of an empty box instead of a line running the width of the range.
        const rows = empty ? placeholder(options.balance) : [
            ...(days > 0 ? [{at: windowStart, balance: opening, anchor: true}] : []),
            ...inWindow,
            {at: now, balance: closing, anchor: true}
        ];
        const accent = cssColor('--color-points', '#f2c14e');
        const muted = cssColor('--color-text-muted', '#8b8b8b');
        const success = cssColor('--color-success', '#2f9e44');
        const danger = cssColor('--color-danger', '#c92a2a');
        const line = empty ? muted : accent;

        if (options.emptyElement) options.emptyElement.hidden = !empty;

        const config = {
            type: 'line',
            data: {
                labels: empty ? rows.map(() => '') : undefined,
                datasets: [{
                    data: empty
                        ? rows.map(row => row.balance)
                        : rows.map(row => ({x: new Date(row.at).getTime(), y: row.balance})),
                    borderColor: line,
                    backgroundColor: `color-mix(in oklab, ${line} 18%, transparent)`,
                    borderWidth: 2,
                    fill: true,
                    // A balance holds until something moves it, so quiet days are flat
                    // and a change is a step — not a slope between two distant dots.
                    stepped: empty ? false : 'after',
                    tension: empty ? 0.25 : 0,
                    // A dot on every ledger entry — that is where the balance moved.
                    // The window anchors are not changes, so they stay bare.
                    pointRadius: empty ? 0 : rows.map(row => (row.anchor ? 0 : 3)),
                    pointHoverRadius: empty ? 0 : rows.map(row => (row.anchor ? 0 : 5)),
                    pointBackgroundColor: line
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: false,
                interaction: {mode: 'index', intersect: false},
                // Room for the end dots and the first/last tick, which otherwise sit
                // right on the canvas edge.
                layout: {padding: {top: 12, right: 16, bottom: 4, left: 4}},
                plugins: {
                    legend: {display: false},
                    tooltip: {
                        enabled: !empty,
                        callbacks: {
                            title: items => fullDate(rows[items[0]?.dataIndex]?.at),
                            labelTextColor: item => {
                                const change = Number(rows[item.dataIndex]?.change) || 0;
                                return change > 0 ? success : change < 0 ? danger : muted;
                            },
                            label: item => {
                                const row = rows[item.dataIndex] || {};
                                const change = Number(row.change) || 0;
                                const sign = change > 0 ? '+' : change < 0 ? '−' : '';
                                const reason = row.reason ? t(`points.transaction.${String(row.reason).toLowerCase()}`) : '';
                                const batch = row.count > 1 ? ` ×${row.count}` : '';
                                return `${pointsText(row.balance)}${change ? ` (${sign}${pointsText(Math.abs(change))})` : ''}${reason ? ` · ${reason}${batch}` : ''}`;
                            }
                        }
                    }
                },
                scales: {
                    x: {
                        display: !empty,
                        type: empty ? 'category' : 'linear',
                        min: windowStart,
                        max: windowEnd,
                        // Built by hand: Chart.js picks its own "nice" spacing for a
                        // linear axis, which lands labels on 02:06 instead of 02:00.
                        afterBuildTicks: axis => { axis.ticks = axisTicks(axis.min, axis.max, byHour); },
                        ticks: {color: muted, padding: 8, callback: value => label(value, byHour)},
                        grid: {display: false},
                        border: {display: false}
                    },
                    y: {
                        display: !empty,
                        beginAtZero: true,
                        ticks: {color: muted, maxTicksLimit: 5, padding: 8,
                            callback: value => pointsCompactAmount(value).trim()},
                        grid: {color: `color-mix(in oklab, ${muted} 25%, transparent)`},
                        border: {display: false}
                    }
                }
            }
        };

        charts.get(canvas)?.destroy();
        const chart = new Chart(canvas, config);
        charts.set(canvas, chart);
        return chart;
    };
})();
