package com.ultracards.gateway.dto.games.games.treseta;

import com.ultracards.gateway.dto.games.GamePlayerDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TresetaDeclarationDTO {
    private GamePlayerDTO player;
    private String type;
    private List<String> suits;
    private int points;
}
