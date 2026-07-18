package com.ultracards.gateway.dto.admin;

import java.util.Map;

public record AdminStatsDTO(
        Long userId,
        Map<String, AdminStatLineDTO> overall,
        Map<String, AdminStatLineDTO> briskulaModes,
        Map<String, AdminStatLineDTO> tresetaModes,
        int briskulaOpponentRows,
        int briskulaTeammateRows,
        int tresetaOpponentRows,
        int tresetaTeammateRows
) {
}
