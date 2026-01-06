package com.ultracards.gateway.dto.updated.games.games;

import com.ultracards.gateway.dto.updated.games.GamePlayerDTO;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class GameResultDTO {
    List<GamePlayerDTO> gameWinners;
}
