package com.ultracards.gateway.dto.games.briskula;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BriskulaCardDTO {
    private String suit;
    private String value;
    private int number;
    private int points;
    private String code;
}
