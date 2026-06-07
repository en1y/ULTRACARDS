package com.ultracards.gateway.dto.friends;

import com.ultracards.gateway.dto.games.GameTypeDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DetailedFriendDTO {
    private FriendDTO friend;
    private Map<GameTypeDTO, List<FriendPersistedStatsDTO>> persistedStatsByGameType;
}
