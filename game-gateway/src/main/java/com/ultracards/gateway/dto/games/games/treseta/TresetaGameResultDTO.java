package com.ultracards.gateway.dto.games.games.treseta;

import com.ultracards.gateway.dto.games.GamePlayerDTO;
import com.ultracards.gateway.dto.games.games.GameResultDTO;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class TresetaGameResultDTO extends GameResultDTO {
    public TresetaGameResultDTO(List<GamePlayerDTO> winners, int points) {
        super(winners, points);
    }
}
