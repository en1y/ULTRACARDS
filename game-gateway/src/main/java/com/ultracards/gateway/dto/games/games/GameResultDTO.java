package com.ultracards.gateway.dto.games.games;

import com.ultracards.gateway.dto.games.GamePlayerDTO;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class GameResultDTO {
    List<GamePlayerDTO> gameWinners;
}
