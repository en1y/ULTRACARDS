let profile = {
    username: '',
    id: 0
};

fetch('/api/profile')
    .then((profileResponse) => {
        if (!profileResponse.ok) {
            throw new Error(`Response status: ${profileResponse.status}`);
        }
        return profileResponse.json();
    })
    .then((profilePayload) => {
        profile = profilePayload;
    })
    .catch((error) => {
        console.error('Failed to load profile for game types.', error);
    });

function createBriskulaRequest(lobbyName, playerNum, cardsInHandNum, teamsEnabled=false, isPublic=true) {
    return JSON.stringify({
        id: "",
        name: lobbyName,
        minPlayers: playerNum,
        maxPlayers: playerNum,
        players: [{name: profile.username, id: profile.id}],
        host: {name: profile.username, id: profile.id},
        gameType: "Briskula",
        isPublic,
        gameConfig: {
            numberOfPlayers: playerNum,
            cardsInHandNum: cardsInHandNum,
            teamsEnabled: teamsEnabled,
            orderedUsers: [{name: profile.username, id: profile.id}]
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
            ui_text: '1v1 3 cards each',
            settingId: 0,
            req: (lobbyName) => createBriskulaRequest(lobbyName, 2, 3)
        },
        p2c4: {
            ui_text: '1v1 4 cards each',
            settingId: 1,
            req: (lobbyName) => createBriskulaRequest(lobbyName, 2, 4)
        },
        p3: {
            ui_text: '3 players',
            settingId: 2,
            req: (lobbyName) => createBriskulaRequest(lobbyName, 3, 3)
        },
        p4: {
            ui_text: '4 players',
            settingId: 3,
            req: (lobbyName) => createBriskulaRequest(lobbyName, 4, 3)
        },
        p4teams: {
            ui_text: '2v2',
            settingId: 4,
            req: (lobbyName) => createBriskulaRequest(lobbyName, 4, 3, true)
        }
    },
    treseta: {
        p2: {
            ui_text: '1v1',
            settingId: 0,
            req: ''
        },
        p3: {
            ui_text: '3 players',
            settingId: 1,
            req: ''
        },
        p4: {
            ui_text: '2v2',
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
