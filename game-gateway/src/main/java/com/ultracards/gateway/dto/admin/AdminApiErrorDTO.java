package com.ultracards.gateway.dto.admin;

import java.util.List;
import java.util.Map;

public record AdminApiErrorDTO(int status, String message, Map<String, String> errors, List<String> globalErrors) {
}
