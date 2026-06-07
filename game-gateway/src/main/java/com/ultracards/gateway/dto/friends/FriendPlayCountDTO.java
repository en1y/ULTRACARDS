package com.ultracards.gateway.dto.friends;

import com.ultracards.gateway.dto.games.GameTypeDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FriendPlayCountDTO {
    private GameTypeDTO gameType;
    private int playedTogether;
}
