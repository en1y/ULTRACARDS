package com.ultracards.gateway.dto.games.games.briskula;

import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.games.games.GameResultDTO;
import lombok.Data;

import java.util.List;

@Data
public class BriskulaGameResultDTO extends GameResultDTO {
    int winnerPointsNum;

    public BriskulaGameResultDTO(List<GamePlayerDTO> gameWinners, int winnerPointsNum) {
        super(gameWinners);
        this.winnerPointsNum = winnerPointsNum;
    }
}
