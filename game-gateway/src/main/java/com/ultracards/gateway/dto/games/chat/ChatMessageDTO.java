package com.ultracards.gateway.dto.games.chat;


import com.ultracards.gateway.dto.games.GamePlayerDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO {
    private GamePlayerDTO sender;
    @Size(min = 1, max = 200)
    @NotBlank private String message;
    private Instant timestamp;
}
