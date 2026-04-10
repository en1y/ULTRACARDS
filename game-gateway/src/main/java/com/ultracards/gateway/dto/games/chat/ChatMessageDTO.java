package com.ultracards.gateway.dto.games.chat;


import com.ultracards.gateway.dto.games.GamePlayerDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO {
    private GamePlayerDTO sender;
    @NotBlank private String message;
    private Instant timestamp;
}
