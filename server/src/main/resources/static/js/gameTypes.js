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
            req: ''
        },
        p3: {
            ui_text: t('gameConfig.3p'),
            settingId: 1,
            req: ''
        },
        p4: {
            ui_text: t('gameConfig.2v2'),
            settingId: 2,
            req: ''
        }
    },
    durak:{},
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
    return titleCaseGameName(gameType);
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
    if (gameType !== 'briskula' || !lobby.gameConfig) {
        return '';
    }

    const config = lobby.gameConfig;
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

function buildLobbyCreatePayload(gameType, settingKey, lobbyName, isPublic=true) {
    const setting = getGameTypeSetting(gameType, settingKey);
    if (!setting || typeof setting.req !== 'function') {
        throw new Error(`Unsupported game type setting: ${gameType}/${settingKey}`);
    }
    const payload = JSON.parse(setting.req(normalizeLobbyName(lobbyName)));
    payload.isPublic = isPublic;
    return JSON.stringify(payload);
}

function supportsLobbyCreation(gameType, settingKey) {
    const setting = getGameTypeSetting(gameType, settingKey);
    return !!setting && typeof setting.req === 'function';
}
