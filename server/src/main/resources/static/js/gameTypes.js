function createBriskulaRequest(lobbyName, playerNum, cardsInHandNum, teamsEnabled=false, isPublic=true) {
    return JSON.stringify({
        id: "",
        name: lobbyName,
        minPlayers: playerNum,
        maxPlayers: playerNum,
        gameType: "Briskula",
        isPublic,
        gameConfig: {
            numberOfPlayers: playerNum,
            cardsInHandNum: cardsInHandNum,
            teamsEnabled: teamsEnabled
        }
    })
}

function createTresetaRequest(lobbyName, playerNum, teamsEnabled=false, isPublic=true, declarationsEnabled=false) {
    return JSON.stringify({
        id: "",
        name: lobbyName,
        minPlayers: playerNum,
        maxPlayers: playerNum,
        gameType: "Treseta",
        isPublic,
        gameConfig: {
            numberOfPlayers: playerNum,
            cardsInHandNum: playerNum === 3 ? 13 : 10,
            teamsEnabled,
            declarationsEnabled
        }
    });
}

// Durak always deals six cards; the pack, jokers, throw-in policy and passing are
// separate toggles rather than 72 dropdown entries. Server rules: a 24-card pack
// caps at 4 players, and jokers exist only in the 54-card pack.
const DURAK_DECK_SIZES = [24, 36, 54];
const DURAK_DEFAULTS = {deckSize: 36, jokersEnabled: false, throwInPolicy: 'NEIGHBORS_ONLY', passingEnabled: true};

function sanitizeDurakConfig(config) {
    const players = Math.min(6, Math.max(2, Number(config?.numberOfPlayers) || 2));
    let deckSize = DURAK_DECK_SIZES.includes(Number(config?.deckSize)) ? Number(config.deckSize) : DURAK_DEFAULTS.deckSize;
    if (deckSize === 24 && players > 4) deckSize = 36;
    return {
        numberOfPlayers: players,
        cardsInHandNum: 6,
        deckSize,
        jokersEnabled: deckSize === 54 && config?.jokersEnabled === true,
        throwInPolicy: config?.throwInPolicy === 'EVERYONE' ? 'EVERYONE' : 'NEIGHBORS_ONLY',
        passingEnabled: config?.passingEnabled === true
    };
}

function createDurakRequest(lobbyName, playerNum, isPublic = true) {
    return JSON.stringify({
        id: "",
        name: lobbyName,
        minPlayers: playerNum,
        maxPlayers: playerNum,
        gameType: "Durak",
        isPublic,
        gameConfig: sanitizeDurakConfig({numberOfPlayers: playerNum, ...DURAK_DEFAULTS})
    });
}

/** Mirrors DurakGameConfig.modeKey() — the key used by stats, history and leaderboards. */
function durakModeKey(config) {
    if (!config || typeof config !== 'object' || config.numberOfPlayers == null) {
        return '';
    }
    const c = sanitizeDurakConfig(config);
    return `P${c.numberOfPlayers}_D${c.deckSize}`
        + (c.jokersEnabled ? '_JOKERS' : '_NO_JOKERS')
        + (c.throwInPolicy === 'EVERYONE' ? '_EVERYONE' : '_NEIGHBORS')
        + (c.passingEnabled ? '_PASS' : '_NO_PASS');
}

function parseDurakModeKey(modeKey) {
    const match = /^P(\d)_D(\d+)_(JOKERS|NO_JOKERS)_(EVERYONE|NEIGHBORS)_(PASS|NO_PASS)$/
        .exec(String(modeKey || '').toUpperCase());
    if (!match) {
        return null;
    }
    return {
        numberOfPlayers: Number(match[1]),
        cardsInHandNum: 6,
        deckSize: Number(match[2]),
        jokersEnabled: match[3] === 'JOKERS',
        throwInPolicy: match[4] === 'EVERYONE' ? 'EVERYONE' : 'NEIGHBORS_ONLY',
        passingEnabled: match[5] === 'PASS'
    };
}

function getDurakConfigDisplayName(config) {
    const resolved = typeof config === 'string' ? parseDurakModeKey(config) : config;
    if (!resolved || !Number.isFinite(Number(resolved.numberOfPlayers))) {
        return t('gameConfig.fallback');
    }
    const parts = [
        t('gameConfig.players', resolved.numberOfPlayers),
        t('durak.deck.short', resolved.deckSize)
    ];
    if (resolved.jokersEnabled) parts.push(t('durak.jokers.short'));
    parts.push(t(resolved.throwInPolicy === 'EVERYONE' ? 'durak.throwIn.everyone' : 'durak.throwIn.neighbors'));
    if (resolved.passingEnabled) parts.push(t('durak.passing.short'));
    return parts.join(' · ');
}

function normalizeLobbyName(lobbyName) {
    return typeof lobbyName === 'string' && lobbyName.trim().length
        ? lobbyName.trim()
        : 'ULTRAlobby';
}

const gameTypes = {
    briskula: {
        p2: {
            ui_text: t('gameConfig.1v1x3'),
            settingId: 0,
            req: (lobbyName) => createBriskulaRequest(lobbyName, 2, 3)
        },
        p2c4: {
            ui_text: t('gameConfig.1v1x4'),
            settingId: 1,
            req: (lobbyName) => createBriskulaRequest(lobbyName, 2, 4)
        },
        p3: {
            ui_text: t('gameConfig.3p'),
            settingId: 2,
            req: (lobbyName) => createBriskulaRequest(lobbyName, 3, 3)
        },
        p4: {
            ui_text: t('gameConfig.4p'),
            settingId: 3,
            req: (lobbyName) => createBriskulaRequest(lobbyName, 4, 3)
        },
        p4teams: {
            ui_text: t('gameConfig.2v2'),
            settingId: 4,
            req: (lobbyName) => createBriskulaRequest(lobbyName, 4, 3, true)
        }
    },
    treseta: {
        p2: {
            ui_text: t('gameConfig.1v1'),
            settingId: 0,
            req: (lobbyName) => createTresetaRequest(lobbyName, 2)
        },
        p3: {
            ui_text: t('gameConfig.3p'),
            settingId: 1,
            req: (lobbyName) => createTresetaRequest(lobbyName, 3)
        },
        p4teams: {
            ui_text: t('gameConfig.2v2'),
            settingId: 2,
            req: (lobbyName) => createTresetaRequest(lobbyName, 4, true)
        },
        p4: {
            ui_text: t('gameConfig.4p'),
            settingId: 3,
            req: (lobbyName) => createTresetaRequest(lobbyName, 4)
        }
    },
    // Durak modes have no fixed settingId: the pack/jokers/throw-in/passing toggles
    // multiply out to 72 server configs, so the lobbies list filters client-side.
    durak: {
        p2: {ui_text: t('gameConfig.2p'), settingId: null, req: (lobbyName) => createDurakRequest(lobbyName, 2)},
        p3: {ui_text: t('gameConfig.3p'), settingId: null, req: (lobbyName) => createDurakRequest(lobbyName, 3)},
        p4: {ui_text: t('gameConfig.4p'), settingId: null, req: (lobbyName) => createDurakRequest(lobbyName, 4)},
        p5: {ui_text: t('gameConfig.5p'), settingId: null, req: (lobbyName) => createDurakRequest(lobbyName, 5)},
        p6: {ui_text: t('gameConfig.6p'), settingId: null, req: (lobbyName) => createDurakRequest(lobbyName, 6)}
    },
    poker:{}
};

function getGameTypeSettings(gameType) {
    return gameTypes[gameType] || null;
}

function getGameTypeSetting(gameType, settingKey) {
    const settings = getGameTypeSettings(gameType);
    if (!settings) {
        return null;
    }
    return settings[settingKey] || null;
}

function getGameTypeSettingId(gameType, settingKey) {
    const setting = getGameTypeSetting(gameType, settingKey);
    return Number.isInteger(setting?.settingId) ? setting.settingId : null;
}

function titleCaseGameName(value) {
    return String(value || '')
        .replace(/([a-z])([A-Z])/g, '$1 $2')
        .toLowerCase()
        .split('_')
        .filter(Boolean)
        .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
        .join(' ') || t('game.unknown');
}

function getGameTypeDisplayName(gameType) {
    const key = String(gameType || '').toLowerCase();
    return t(`game.${key}`) === `game.${key}` ? titleCaseGameName(gameType) : t(`game.${key}`);
}

function resolveBriskulaGameConfigKey(config) {
    if (!config) {
        return '';
    }
    if (typeof config === 'string') {
        return config;
    }

    const players = Number(config.numberOfPlayers);
    const cards = Number(config.cardsInHandNum);
    const teams = Boolean(config.teamsEnabled);
    if (players === 2 && cards === 3 && !teams) {
        return 'TWO_PLAYERS';
    }
    if (players === 2 && cards === 4 && !teams) {
        return 'TWO_PLAYERS_FOUR_CARDS_IN_HAND_EACH';
    }
    if (players === 3 && cards === 3 && !teams) {
        return 'THREE_PLAYERS';
    }
    if (players === 4 && cards === 3 && teams) {
        return 'FOUR_PLAYERS_WITH_TEAMS';
    }
    if (players === 4 && cards === 3 && !teams) {
        return 'FOUR_PLAYERS_NO_TEAMS';
    }
    return '';
}

function resolveBriskulaGameSettingKey(config) {
    switch (resolveBriskulaGameConfigKey(config)) {
        case 'TWO_PLAYERS':
            return 'p2';
        case 'TWO_PLAYERS_FOUR_CARDS_IN_HAND_EACH':
            return 'p2c4';
        case 'THREE_PLAYERS':
            return 'p3';
        case 'FOUR_PLAYERS_WITH_TEAMS':
            return 'p4teams';
        case 'FOUR_PLAYERS_NO_TEAMS':
            return 'p4';
        default:
            return '';
    }
}

function resolveGameConfigKey(gameType, config) {
    if (String(gameType || '').toLowerCase() === 'briskula') {
        return resolveBriskulaGameConfigKey(config);
    }
    if (String(gameType || '').toLowerCase() === 'treseta' && config && typeof config === 'object') {
        const players = Number(config.numberOfPlayers);
        if (players === 2) return 'TWO_PLAYERS';
        if (players === 3) return 'THREE_PLAYERS';
        if (players === 4 && config.teamsEnabled) return 'FOUR_PLAYERS_WITH_TEAMS';
        if (players === 4) return 'FOUR_PLAYERS_NO_TEAMS';
    }
    if (String(gameType || '').toLowerCase() === 'durak' && config && typeof config === 'object') {
        return config.modeKey || durakModeKey(config);
    }
    return typeof config === 'string' ? config : '';
}

function getGameConfigDisplayName(gameType, config) {
    const normalizedGameType = String(gameType || '').toLowerCase();
    if (normalizedGameType === 'briskula') {
        const settingKey = resolveBriskulaGameSettingKey(config);
        const setting = getGameTypeSetting('briskula', settingKey);
        if (setting?.ui_text) {
            return setting.ui_text;
        }
    }
    if (normalizedGameType === 'treseta') {
        const setting = getGameTypeSetting('treseta', resolveLobbyGameSettingKey({gameType: 'Treseta', gameConfig: config}));
        const declarationsSuffix = config && typeof config === 'object' && config.declarationsEnabled
            ? ' · ' + t('gameConfig.withDeclarations')
            : '';
        if (setting?.ui_text) return setting.ui_text + declarationsSuffix;
    }
    if (normalizedGameType === 'durak') {
        return getDurakConfigDisplayName(config);
    }

    const configKey = resolveGameConfigKey(gameType, config);
    if (configKey) {
        return titleCaseGameName(configKey);
    }

    if (config && typeof config === 'object') {
        const players = Number(config.numberOfPlayers);
        const cards = Number(config.cardsInHandNum);
        if (Number.isFinite(players) && Number.isFinite(cards)) {
            return t('gameConfig.custom', players, cards) + (config.teamsEnabled ? t('gameConfig.customTeams') : '');
        }
    }
    return t('gameConfig.fallback');
}

function resolveLobbyGameSettingKey(lobby) {
    if (!lobby) {
        return '';
    }

    const gameType = String(lobby.gameType || '').toLowerCase();
    if (!['briskula', 'treseta', 'durak'].includes(gameType) || !lobby.gameConfig) {
        return '';
    }

    const config = lobby.gameConfig;
    if (gameType === 'durak') {
        const players = Number(config.numberOfPlayers);
        return players >= 2 && players <= 6 ? `p${players}` : '';
    }
    if (gameType === 'treseta') {
        if (config.numberOfPlayers === 2) return 'p2';
        if (config.numberOfPlayers === 3) return 'p3';
        if (config.numberOfPlayers === 4 && config.teamsEnabled) return 'p4teams';
        if (config.numberOfPlayers === 4) return 'p4';
        return '';
    }
    if (config.numberOfPlayers === 2 && config.cardsInHandNum === 3) {
        return 'p2';
    }
    if (config.numberOfPlayers === 2 && config.cardsInHandNum === 4) {
        return 'p2c4';
    }
    if (config.numberOfPlayers === 3) {
        return 'p3';
    }
    if (config.numberOfPlayers === 4 && config.teamsEnabled) {
        return 'p4teams';
    }
    if (config.numberOfPlayers === 4) {
        return 'p4';
    }
    return '';
}

function resolveLobbyGameSettingId(lobby) {
    const gameType = String(lobby?.gameType || '').toLowerCase();
    const settingKey = resolveLobbyGameSettingKey(lobby);
    if (!gameType || !settingKey) {
        return null;
    }
    return getGameTypeSettingId(gameType, settingKey);
}

function buildLobbyCreatePayload(gameType, settingKey, lobbyName, isPublic=true, gameConfigExtras=null) {
    const setting = getGameTypeSetting(gameType, settingKey);
    if (!setting || typeof setting.req !== 'function') {
        throw new Error(`Unsupported game type setting: ${gameType}/${settingKey}`);
    }
    const payload = JSON.parse(setting.req(normalizeLobbyName(lobbyName)));
    payload.isPublic = isPublic;
    if (gameConfigExtras && payload.gameConfig) {
        Object.assign(payload.gameConfig, gameConfigExtras);
    }
    if (String(gameType || '').toLowerCase() === 'durak') {
        // The server rejects an impossible combination (24-card pack with 5+ players,
        // jokers without the 54-card pack); fold it back to a legal one here.
        payload.gameConfig = sanitizeDurakConfig(payload.gameConfig);
    }
    return JSON.stringify(payload);
}

function supportsLobbyCreation(gameType, settingKey) {
    const setting = getGameTypeSetting(gameType, settingKey);
    return !!setting && typeof setting.req === 'function';
}

/* ---------- Durak rule toggles, shared by the create-lobby modal and the lobby page ---------- */

function durakOptionId(idPrefix, name) {
    return `${idPrefix}durak-${name}`;
}

/**
 * A bare segmented row of toggle buttons, no label. The group carries the
 * selected value (readable/writable via `.value`, aliasing `dataset.value`)
 * and emits a bubbling `change`, so callers treat it exactly like the
 * `<select>` it replaces — one tap per option instead of opening a dropdown.
 */
function buildChoiceGroup(id, options, value) {
    const group = document.createElement('div');
    group.className = 'durak-choice';
    group.id = id;
    group.setAttribute('role', 'radiogroup');
    group.dataset.value = String(value);
    options.forEach(([optionValue, optionText]) => {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'durak-choice-button';
        button.dataset.value = String(optionValue);
        button.textContent = optionText;
        button.setAttribute('role', 'radio');
        button.addEventListener('click', () => {
            if (button.disabled || group.dataset.readonly === '1') return;
            setDurakChoice(group, optionValue);
            group.dispatchEvent(new Event('change', {bubbles: true}));
        });
        group.appendChild(button);
    });
    setDurakChoice(group, value);
    Object.defineProperty(group, 'value', {
        get: () => group.dataset.value,
        set: (newValue) => setDurakChoice(group, newValue)
    });
    return group;
}

/** Same choice group, with a field label above it (Durak's Deck/Throw-in/Players rows). */
function buildDurakChoice(id, labelText, options, value) {
    const field = document.createElement('div');
    field.className = 'durak-option durak-option-choice';
    const label = document.createElement('span');
    label.className = 'durak-option-label';
    label.id = `${id}-label`;
    label.textContent = labelText;

    const group = buildChoiceGroup(id, options, value);
    group.setAttribute('aria-labelledby', label.id);

    field.append(label, group);
    return field;
}

// Durak setting keys are p2..p6, so the player count is the key's digit.
function durakPlayerCountFromKey(key) {
    return Number(String(key).slice(1)) || 2;
}

/**
 * Same segmented-button control as buildDurakChoice, specialized for a settings
 * map whose keys are plain player counts (Durak's p2..p6).
 */
function buildDurakPlayerCountChoice(id, settings, selectedKey, isDisabled) {
    const options = Object.keys(settings).map((key) => [key, String(durakPlayerCountFromKey(key))]);
    const field = buildDurakChoice(id, t('durak.players.label'), options, selectedKey);
    if (typeof isDisabled === 'function') {
        const group = field.querySelector('.durak-choice');
        group.querySelectorAll('.durak-choice-button').forEach((button) => {
            button.disabled = isDisabled(button.dataset.value);
            button.classList.toggle('is-unavailable', button.disabled);
        });
    }
    return field;
}

function setDurakChoice(group, value) {
    if (!group) return;
    group.dataset.value = String(value);
    group.querySelectorAll('.durak-choice-button').forEach((button) => {
        const active = button.dataset.value === String(value);
        button.classList.toggle('is-active', active);
        button.setAttribute('aria-checked', String(active));
    });
}

function buildDurakToggle(id, labelText) {
    const row = document.createElement('label');
    row.className = 'durak-option durak-option-toggle';
    row.setAttribute('for', id);
    const text = document.createElement('span');
    text.className = 'durak-option-label';
    text.textContent = labelText;
    const control = document.createElement('span');
    control.className = 'durak-option-switch';
    const input = document.createElement('input');
    input.type = 'checkbox';
    input.id = id;
    const switchElement = document.createElement('span');
    switchElement.className = 'visibility-switch';
    switchElement.setAttribute('aria-hidden', 'true');
    control.append(input, switchElement);
    row.append(text, control);
    return row;
}

/**
 * The pack / jokers / throw-in / passing controls, pre-filled from an existing
 * config. `readOnly` renders the same controls for everyone who is not the host,
 * so every player can see the rules they are about to play under.
 */
function buildDurakSettingsNodes(idPrefix, config, readOnly = false) {
    const resolved = sanitizeDurakConfig(config || DURAK_DEFAULTS);

    const deck = buildDurakChoice(
        durakOptionId(idPrefix, 'deck'),
        t('durak.deck.label'),
        DURAK_DECK_SIZES.map((size) => [size, String(size)]),
        resolved.deckSize
    );
    const policy = buildDurakChoice(
        durakOptionId(idPrefix, 'throwin'),
        t('durak.throwIn.label'),
        [['NEIGHBORS_ONLY', t('durak.throwIn.neighbors')], ['EVERYONE', t('durak.throwIn.everyone')]],
        resolved.throwInPolicy
    );

    const jokers = buildDurakToggle(durakOptionId(idPrefix, 'jokers'), t('durak.jokers.label'));
    jokers.querySelector('input').checked = resolved.jokersEnabled;
    const passing = buildDurakToggle(durakOptionId(idPrefix, 'passing'), t('durak.passing.label'));
    passing.querySelector('input').checked = resolved.passingEnabled;

    const wrap = document.createElement('div');
    wrap.className = 'durak-options';
    wrap.classList.toggle('is-readonly', !!readOnly);
    wrap.append(deck, policy, jokers, passing);
    if (readOnly) {
        wrap.querySelectorAll('button, input').forEach((control) => {
            control.disabled = true;
            control.setAttribute('aria-disabled', 'true');
        });
        wrap.querySelectorAll('.durak-choice').forEach((group) => {
            group.dataset.readonly = '1';
        });
    }
    return [wrap];
}

/**
 * Greys out combinations the server would reject, so the toggles can never produce
 * an invalid config: no 24-card pack above four players, no jokers outside the 54.
 */
function syncDurakSettingsAvailability(idPrefix, numberOfPlayers) {
    const deck = document.getElementById(durakOptionId(idPrefix, 'deck'));
    const jokersInput = document.getElementById(durakOptionId(idPrefix, 'jokers'));
    if (!deck) {
        return;
    }
    const readOnly = deck.dataset.readonly === '1';
    const smallPackAllowed = Number(numberOfPlayers) <= 4;
    const smallPack = deck.querySelector('.durak-choice-button[data-value="24"]');
    if (smallPack) {
        smallPack.disabled = readOnly || !smallPackAllowed;
        smallPack.classList.toggle('is-unavailable', !smallPackAllowed);
        if (!smallPackAllowed && deck.dataset.value === '24') setDurakChoice(deck, 36);
    }
    if (jokersInput) {
        const jokersAllowed = deck.dataset.value === '54';
        jokersInput.disabled = readOnly || !jokersAllowed;
        if (!jokersAllowed) jokersInput.checked = false;
        jokersInput.closest('.durak-option')?.classList.toggle('is-disabled', !jokersAllowed);
    }
}

function readDurakSettings(idPrefix) {
    const control = (name) => document.getElementById(durakOptionId(idPrefix, name));
    return {
        deckSize: Number(control('deck')?.dataset.value) || DURAK_DEFAULTS.deckSize,
        jokersEnabled: control('jokers')?.checked === true,
        throwInPolicy: control('throwin')?.dataset.value === 'EVERYONE' ? 'EVERYONE' : 'NEIGHBORS_ONLY',
        passingEnabled: control('passing')?.checked === true
    };
}

/* ---------- Durak rule filters, shared by the lobby list and the leaderboards ----------
   Durak has 72 legal rule combinations, so "pick a mode from a list" does not work.
   The filter is the same segmented rows a lobby uses, with an extra "Any" on each: any
   row left on Any simply does not narrow anything. */

const DURAK_FILTER_ANY = '';

const DURAK_FILTER_ROWS = [
    {name: 'players', labelKey: 'durak.players.label',
        options: () => [2, 3, 4, 5, 6].map((count) => [String(count), String(count)])},
    {name: 'deck', labelKey: 'durak.deck.label',
        options: () => DURAK_DECK_SIZES.map((size) => [String(size), String(size)])},
    {name: 'throwin', labelKey: 'durak.throwIn.label',
        options: () => [['NEIGHBORS_ONLY', t('durak.throwIn.neighbors')], ['EVERYONE', t('durak.throwIn.everyone')]]},
    {name: 'jokers', labelKey: 'durak.jokers.label',
        options: () => [['true', t('common.yes')], ['false', t('common.no')]]},
    {name: 'passing', labelKey: 'durak.passing.label',
        options: () => [['true', t('common.yes')], ['false', t('common.no')]]}
];

/** Builds the five filter rows into `container`, pre-selected from `filter`. */
function renderDurakFilter(container, idPrefix, filter) {
    if (!container) return;
    const chosen = filter || {};
    container.replaceChildren(...DURAK_FILTER_ROWS.map((row) => buildDurakChoice(
        durakOptionId(idPrefix, `filter-${row.name}`),
        t(row.labelKey),
        [[DURAK_FILTER_ANY, t('durak.filter.any')], ...row.options()],
        chosen[row.name] ?? DURAK_FILTER_ANY
    )));
    container.classList.add('durak-options', 'durak-filter');
}

function readDurakFilter(idPrefix) {
    const filter = {};
    DURAK_FILTER_ROWS.forEach((row) => {
        const group = document.getElementById(durakOptionId(idPrefix, `filter-${row.name}`));
        filter[row.name] = group?.dataset.value ?? DURAK_FILTER_ANY;
    });
    return filter;
}

function setDurakFilter(idPrefix, filter) {
    DURAK_FILTER_ROWS.forEach((row) => {
        setDurakChoice(document.getElementById(durakOptionId(idPrefix, `filter-${row.name}`)),
            filter?.[row.name] ?? DURAK_FILTER_ANY);
    });
}

function durakFilterIsEmpty(filter) {
    return !filter || DURAK_FILTER_ROWS.every((row) => !filter[row.name]);
}

/** True when a config satisfies every row the filter actually constrains. */
function durakConfigMatchesFilter(config, filter) {
    const resolved = typeof config === 'string' ? parseDurakModeKey(config) : config;
    if (!resolved) return false;
    if (durakFilterIsEmpty(filter)) return true;
    const matches = (value, expected) => !expected || String(value) === String(expected);
    return matches(resolved.numberOfPlayers, filter.players)
        && matches(resolved.deckSize, filter.deck)
        && matches(resolved.throwInPolicy === 'EVERYONE' ? 'EVERYONE' : 'NEIGHBORS_ONLY', filter.throwin)
        && matches(!!resolved.jokersEnabled, filter.jokers)
        && matches(!!resolved.passingEnabled, filter.passing);
}

/** Short human summary of the constrained rows, for the "active filter" line. */
function describeDurakFilter(filter) {
    if (durakFilterIsEmpty(filter)) return t('durak.filter.allModes');
    const parts = [];
    if (filter.players) parts.push(t('gameConfig.players', filter.players));
    if (filter.deck) parts.push(t('durak.filter.deck', filter.deck));
    if (filter.throwin) parts.push(filter.throwin === 'EVERYONE'
        ? t('durak.throwIn.everyone') : t('durak.throwIn.neighbors'));
    if (filter.jokers) parts.push(filter.jokers === 'true' ? t('durak.filter.jokers') : t('durak.filter.noJokers'));
    if (filter.passing) parts.push(filter.passing === 'true' ? t('durak.filter.passing') : t('durak.filter.noPassing'));
    return parts.join(' · ');
}

/**
 * Every legal Durak rule set, as canonical mode keys. Built through the same
 * sanitiser the lobby uses, so impossible combinations (Jokers without the 54-card
 * pack, a 24-card pack above four players) simply collapse onto the legal one.
 */
function durakAllModeKeys() {
    const keys = new Set();
    [2, 3, 4, 5, 6].forEach((numberOfPlayers) => {
        DURAK_DECK_SIZES.forEach((deckSize) => {
            [false, true].forEach((jokersEnabled) => {
                ['NEIGHBORS_ONLY', 'EVERYONE'].forEach((throwInPolicy) => {
                    [false, true].forEach((passingEnabled) => {
                        keys.add(durakModeKey(sanitizeDurakConfig({
                            numberOfPlayers, deckSize, jokersEnabled, throwInPolicy, passingEnabled
                        })));
                    });
                });
            });
        });
    });
    return [...keys];
}

/** The mode keys a filter covers — what the leaderboards ask the server to add up. */
function durakFilterQuery(filter) {
    const params = new URLSearchParams();
    if (filter?.players) params.set('durakPlayers', filter.players);
    if (filter?.deck) params.set('durakDeck', filter.deck);
    if (filter?.throwin) params.set('durakThrowIn', filter.throwin);
    if (filter?.jokers) params.set('durakJokers', filter.jokers);
    if (filter?.passing) params.set('durakPassing', filter.passing);
    return params;
}
