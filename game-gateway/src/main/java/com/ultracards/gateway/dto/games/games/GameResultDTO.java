package com.ultracards.gateway.dto.games.games;

import com.ultracards.gateway.dto.games.GamePlayerDTO;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class GameResultDTO {
    private List<GamePlayerDTO> gameWinners;
    private Integer winnerPointsNum;

    public GameResultDTO(List<GamePlayerDTO> gameWinners) {
        this.gameWinners = gameWinners;
    }

    public GameResultDTO(List<GamePlayerDTO> gameWinners, Integer winnerPointsNum) {
        this.gameWinners = gameWinners;
        this.winnerPointsNum = winnerPointsNum;
    }
}
