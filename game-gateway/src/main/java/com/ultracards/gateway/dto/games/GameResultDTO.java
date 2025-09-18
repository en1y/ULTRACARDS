package com.ultracards.gateway.dto.games;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GameResultDTO {
    @NotNull
    private UUID gameId;
    @NotNull
    private List<Long> winnerUserIds;
    private String finalStateJson; // optional final state
}

