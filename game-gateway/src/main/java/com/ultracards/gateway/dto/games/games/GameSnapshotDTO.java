package com.ultracards.gateway.dto.games.games;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameSnapshotDTO<T extends GameEntityDTO> {
    private T game;
    private List<GameCardDTO> hand;
}
