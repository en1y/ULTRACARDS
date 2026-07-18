package com.ultracards.gateway.dto.admin;

public record AdminNotificationPatchDTO(String message, Boolean read, String reason) {
}
