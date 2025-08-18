package com.ultracards.gateway.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class VerificationCodeDTO {
    @NotBlank
    @Pattern(regexp = "\\d{6}", message = "Code must be exactly 6 digits")
    private String code;
}
