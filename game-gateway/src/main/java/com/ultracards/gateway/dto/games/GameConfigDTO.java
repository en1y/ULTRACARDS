package com.ultracards.gateway.dto.games;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.ultracards.gateway.dto.games.games.briskula.BriskulaGameConfigDTO;
import com.ultracards.gateway.dto.games.games.treseta.TresetaGameConfigDTO;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
        property = "gameType"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = BriskulaGameConfigDTO.class, name = "Briskula"),
        @JsonSubTypes.Type(value = TresetaGameConfigDTO.class, name = "Treseta")
})
public interface GameConfigDTO {
}
