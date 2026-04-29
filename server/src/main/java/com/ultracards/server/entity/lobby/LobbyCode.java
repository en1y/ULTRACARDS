package com.ultracards.server.entity.lobby;

import java.util.Objects;

public record LobbyCode(String lobbyCode) {

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        LobbyCode lobbyCode1 = (LobbyCode) o;
        return Objects.equals(lobbyCode, lobbyCode1.lobbyCode);
    }

}
