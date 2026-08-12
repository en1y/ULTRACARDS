package com.ultracards.gateway.dto.games.games;

import com.ultracards.gateway.dto.games.GamePlayerDTO;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class GameResultDTO {
    private List<GamePlayerDTO> gameWinners;
    private Integer winnerPointsNum;
    private boolean draw;
    private Map<Long, Long> wagerPayouts = Map.of();
    private Map<Long, Long> wagerDeltas = Map.of();

    public GameResultDTO(List<GamePlayerDTO> gameWinners) {
        this.gameWinners = gameWinners;
    }

    public GameResultDTO(List<GamePlayerDTO> gameWinners, Integer winnerPointsNum) {
        this.gameWinners = gameWinners;
        this.winnerPointsNum = winnerPointsNum;
    }
}
