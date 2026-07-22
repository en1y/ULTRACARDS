package com.ultracards.gateway.dto.games.games.treseta;

import com.ultracards.gateway.dto.games.games.GameCardDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TresetaDeclarationRequestDTO {
    @NotEmpty
    @Valid
    private List<GameCardDTO> cards;
}
