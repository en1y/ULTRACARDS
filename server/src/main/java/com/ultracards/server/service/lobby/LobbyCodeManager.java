package com.ultracards.server.service.lobby;

import com.ultracards.server.entity.lobby.LobbyCode;
import com.ultracards.server.entity.lobby.LobbyEntity;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.HashMap;

@Service
public class LobbyCodeManager {
    private final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private final int LENGTH = 6;
    private final SecureRandom random = new SecureRandom();

    private final HashMap<LobbyCode, LobbyEntity> lobbyByCode = new HashMap<>();

    public LobbyEntity getLobbyByCode(LobbyCode code) {
        return lobbyByCode.get(code);
    }

    public void addLobbyCode(LobbyEntity lobby) {
        var code = generateCode();
        lobby.setLobbyCode(code);
        lobbyByCode.put(code, lobby);
    }

    public void removeLobbyCode(LobbyEntity lobby) {
        lobbyByCode.remove(lobby.getLobbyCode());
    }

    private LobbyCode generateCode() {
        var code = new StringBuilder(LENGTH);

        for (int i = 0; i < LENGTH; i++) {
            int index = random.nextInt(CHARACTERS.length());
            code.append(CHARACTERS.charAt(index));
        }

        return new LobbyCode(code.toString());
    }
}
