package com.ultracards.gateway.dto.updated.games;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.ultracards.gateway.dto.updated.games.games.briskula.BriskulaGameConfigDTO;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
        property = "gameType"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = BriskulaGameConfigDTO.class, name = "Briskula")
})
public interface GameConfigDTO {
}
