package com.ultracards.gateway.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.text.StringEscapeUtils;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class EmailDTO {
    @Email
    @NotBlank
    private String email;

    @Override
    public String toString() {
        return getEmail();
    }
}
