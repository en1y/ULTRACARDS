function createBriskulaRequest(lobbyName, playerNum, cardsInHandNum, teamsEnabled=false) {
    return JSON.stringify({
        id: "",
        name: lobbyName,
        minPlayers: playerNum,
        maxPlayers: playerNum,
        players: [{name: profile.username, id: profile.id}],
        host: {name: profile.username, id: profile.id},
        gameType: "Briskula",
        gameConfig: {
            numberOfPlayers: playerNum,
            cardsInHandNum: cardsInHandNum,
            teamsEnabled: teamsEnabled
        }
    })
}

const gameTypes = {
    briskula: {
        p2: {
            ui_text: '1v1 3 cards each',
            req: (lobbyName) => createBriskulaRequest(lobbyName, 2, 3)
        },
        p2c4: {
            ui_text: '1v1 4 cards each',
            req: (lobbyName) => createBriskulaRequest(lobbyName, 2, 4)
        },
        p3: {
            ui_text: '3 players',
            req: (lobbyName) => createBriskulaRequest(lobbyName, 3, 3)
        },
        p4: {
            ui_text: '4 players',
            req: (lobbyName) => createBriskulaRequest(lobbyName, 4, 3)
        },
        p4teams: {
            ui_text: '2v2',
            req: (lobbyName) => createBriskulaRequest(lobbyName, 4, 3, true)
        }
    },
    treseta: {
        p2: {
            ui_text: '1v1',
            req: ''
        },
        p3: {
            ui_text: '3 players',
            req: ''
        },
        p4: {
            ui_text: '2v2',
            req: ''
        }
    },
    durak:{},
    poker:{}
};