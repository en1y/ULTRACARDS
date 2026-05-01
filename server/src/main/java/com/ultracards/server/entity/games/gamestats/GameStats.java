package com.ultracards.server.entity.games.gamestats;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class GameStats {
    @Column(name = "played")
    private int played;
    @Column(name = "wins")
    private int wins;

    public void addPlayed() {
        this.played++;
    }

    public void addWon() {
        this.wins++;
    }
}
