/**
 * Points economy helpers shared by every page: the `{amount}P` rendering and the
 * bet ladder that the lobby sliders and the lobby-list filter both run on.
 */
(() => {
    // 100…900 by 100, 1K…9K by 1K, and so on up to the 1M ceiling. Nine stops per
    // decade means the 100 / 1K / 10K / 100K / 1M ticks land evenly along the track.
    const steps = [100, 1_000, 10_000, 100_000]
        .flatMap(decade => Array.from({length: 9}, (_, offset) => decade * (offset + 1)))
        .concat(1_000_000);
    const ticks = [100, 1_000, 10_000, 100_000, 1_000_000];

    window.WAGER_MIN = steps[0];
    window.WAGER_MAX = steps[steps.length - 1];
    window.WAGER_RAKE_PERCENT = 4;

    // Admins can charge a different fee per game and per mode, so the answer is cached
    // per game/mode pair rather than once for the whole page.
    const settingsRequests = new Map();
    window.pointsSettings = (game, mode) => {
        const key = `${game || ''}|${mode || ''}`;
        if (!settingsRequests.has(key)) {
            const params = new URLSearchParams();
            if (game) params.set('game', game);
            if (game && mode) params.set('mode', mode);
            const search = params.toString();
            settingsRequests.set(key, fetch(`/api/points/settings${search ? `?${search}` : ''}`, {credentials: 'same-origin'})
                .then(response => response.ok ? response.json() : {wagerFeePercent: WAGER_RAKE_PERCENT})
                .catch(() => ({wagerFeePercent: WAGER_RAKE_PERCENT})));
        }
        return settingsRequests.get(key);
    };

    const renderWagerFee = settings => {
        const percent = Number(settings?.wagerFeePercent ?? 4);
        window.WAGER_RAKE_PERCENT = percent;
        document.querySelectorAll('[data-wager-fee]').forEach(node => {
            node.textContent = t('points.wager.help', percent);
        });
        document.querySelectorAll('[data-wager-guide-fee]').forEach(node => {
            node.textContent = t('guides.bet.intro', percent);
        });
        document.querySelectorAll('[data-wager-example-losers]').forEach(node => {
            const losingPool = 1_000 * Number(node.dataset.wagerExampleLosers || 0);
            const winners = Math.max(1, Number(node.dataset.wagerExampleWinners || 1));
            const distributable = Math.floor(losingPool * (100 - percent) / 100);
            node.textContent = pointsText(Math.floor(distributable / winners), true);
        });
    };
    // Lobby pages call this once they know which game and mode they are quoting.
    window.refreshWagerFee = (game, mode) => pointsSettings(game, mode).then(renderWagerFee);
    const loadWagerFee = () => refreshWagerFee();
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', loadWagerFee, {once: true});
    else loadWagerFee();

    window.wagerStep = index => steps[Math.min(steps.length - 1, Math.max(0, Math.round(Number(index) || 0)))];

    window.wagerIndex = value => {
        const target = Number(value) || 0;
        let best = 0;
        steps.forEach((step, index) => {
            if (Math.abs(step - target) < Math.abs(steps[best] - target)) best = index;
        });
        return best;
    };

    window.roundWager = value => steps[wagerIndex(value)];

    /** Largest stop at or below `value`; null when even the smallest bet is out of reach. */
    window.floorWager = value => {
        const target = Number(value) || 0;
        let affordable = null;
        steps.forEach(step => { if (step <= target) affordable = step; });
        return affordable;
    };

    /** Balances for a list of accounts, so a lobby can size its bet to the table. */
    window.pointsBalances = ids => {
        const unique = [...new Set((ids || []).filter(id => id != null))];
        if (!unique.length) return Promise.resolve({});
        return fetch(`/api/points/balances?ids=${unique.join(',')}`, {credentials: 'same-origin'})
            .then(response => response.ok ? response.json() : {})
            .catch(() => ({}));
    };

    const compactFormat = new Intl.NumberFormat(undefined, {notation: 'compact', maximumFractionDigits: 1});

    /** A shortened amount followed by a non-breaking space, ready for the P symbol. */
    window.pointsCompactAmount = value => {
        const text = compactFormat.format(Number(value) || 0);
        return text + '\u00A0';
    };

    window.pointsText = (value, signed = false) => {
        const number = Number(value) || 0;
        const sign = signed && number > 0 ? '+' : signed && number < 0 ? '−' : '';
        return `${sign}${Math.abs(number).toLocaleString()}${t('points.symbol')}`;
    };

    const symbolNode = () => {
        const symbol = document.createElement('span');
        symbol.className = 'points-symbol';
        symbol.textContent = t('points.symbol');
        return symbol;
    };

    window.pointsNode = (value, signed = false) => {
        const number = Number(value) || 0;
        const fragment = document.createDocumentFragment();
        if (signed && number > 0) fragment.append('+');
        if (signed && number < 0) fragment.append('−');
        fragment.append(Math.abs(number).toLocaleString(), symbolNode());
        return fragment;
    };

    window.pointsCompactText = value => `${pointsCompactAmount(value)}${t('points.symbol')}`;

    window.pointsRangeDays = value => value === 'year'
        ? Math.max(1, Math.ceil((Date.now() - new Date(new Date().getFullYear(), 0, 1).getTime()) / 86_400_000))
        : Math.max(0, Number(value) || 0);

    /**
     * Folds a run of same-type entries stamped at the same second into one group —
     * the four achievements a single game unlocks are one event, not four.
     * Works on either sort order, since same-second entries are always adjacent.
     */
    window.groupPointsBatches = (rows, at, type) => {
        const groups = [];
        (rows || []).forEach(row => {
            const signature = `${type(row)}|${Math.floor(new Date(at(row)).getTime() / 1000)}`;
            const last = groups[groups.length - 1];
            if (last && last.signature === signature) last.rows.push(row);
            else groups.push({signature, rows: [row]});
        });
        return groups;
    };

    window.pointsCompactNode = value => {
        const fragment = document.createDocumentFragment();
        fragment.append(pointsCompactAmount(value), symbolNode());
        return fragment;
    };

    /** A balance change with one consistent sign and color on every Points surface. */
    window.pointsDeltaNode = (value, compact = true) => {
        const number = Number(value) || 0;
        const delta = document.createElement('span');
        delta.className = `points-delta ${number > 0 ? 'is-up' : number < 0 ? 'is-down' : 'is-zero'}`;
        if (number > 0) delta.append('+');
        if (number < 0) delta.append('−');
        delta.append(compact ? pointsCompactNode(Math.abs(number)) : pointsNode(Math.abs(number)));
        return delta;
    };

    // "1K" by hand rather than Intl compact notation: German and friends spell
    // thousands out in full, which is far too wide for a tick under the slider.
    const tickLabel = step => {
        if (step >= 1_000_000) return `${step / 1_000_000}M`;
        return step >= 1_000 ? `${step / 1_000}K` : String(step);
    };

    window.renderWagerTicks = element => {
        if (!element) return;
        element.replaceChildren(...ticks.map(step => {
            const tick = document.createElement('span');
            tick.textContent = tickLabel(step);
            return tick;
        }));
    };

    /**
     * Wires a `.wager-editor` block: the range slides over ladder indices, the
     * readout and preset chips follow it. `onChange(stake, committed)` fires on
     * every move, with `committed` true only once the user lets go.
     */
    window.initWagerControl = (root, onChange) => {
        if (!root) return null;
        const range = root.querySelector('.wager-range');
        const amount = root.querySelector('[data-wager-amount]');
        const presets = [...root.querySelectorAll('[data-wager-preset]')];
        renderWagerTicks(root.querySelector('.wager-ticks'));
        // Labelled here rather than in the templates so a chip can never disagree
        // with the readout it sets.
        presets.forEach(preset => preset.replaceChildren(pointsCompactNode(preset.dataset.wagerPreset)));
        if (range) {
            range.min = '0';
            range.max = String(steps.length - 1);
            range.step = '1';
        }

        let cap = WAGER_MAX;
        let off = false;
        const syncPresets = stake => presets.forEach(preset => {
            const value = Number(preset.dataset.wagerPreset);
            preset.classList.toggle('is-active', value === stake);
            preset.disabled = off || value > cap;
        });

        const control = {
            value: () => Math.min(wagerStep(range?.value), cap),
            set(value) {
                const stake = Math.min(roundWager(value), cap);
                if (range) range.value = String(wagerIndex(stake));
                if (amount) amount.textContent = pointsCompactAmount(stake);
                syncPresets(stake);
                return stake;
            },
            disable(disabled) {
                off = disabled;
                root.setAttribute('aria-disabled', String(disabled));
                if (range) range.disabled = disabled;
                syncPresets(control.value());
            },
            /** Stops the thumb at the largest stop the given balance covers. */
            limit(balance) {
                cap = floorWager(balance) ?? WAGER_MIN;
                control.set(control.value());
            }
        };

        control.set(WAGER_MIN);
        // set() runs on its own line: `onChange?.(control.set(…))` would skip the
        // argument entirely whenever no callback was handed in.
        const commit = (value, committed) => {
            const stake = control.set(value);
            onChange?.(stake, committed);
        };
        range?.addEventListener('input', () => commit(wagerStep(range.value), false));
        range?.addEventListener('change', () => commit(wagerStep(range.value), true));
        presets.forEach(preset => preset.addEventListener('click', () => commit(preset.dataset.wagerPreset, true)));
        return control;
    };
})();
