package com.ultracards.gateway.dto.games.games.durak;

import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.games.games.GameResultDTO;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Durak has no score: everyone but the {@code durak} wins, and a game where the last players run
 * out together is a draw with no loser and no winners.
 */
@Data
@NoArgsConstructor
public class DurakGameResultDTO extends GameResultDTO {
    private GamePlayerDTO loser;
    private List<GamePlayerDTO> finishOrder;
    private boolean draw;

    public DurakGameResultDTO(List<GamePlayerDTO> gameWinners, GamePlayerDTO loser,
                              List<GamePlayerDTO> finishOrder, boolean draw) {
        super(gameWinners);
        this.loser = loser;
        this.finishOrder = finishOrder;
        this.draw = draw;
    }
}
