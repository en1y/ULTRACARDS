package com.ultracards.gateway.dto.admin;

/**
 * The bet fee one game or mode charges. {@code mode} is null for the game-wide row,
 * and {@code inherited} marks a fee that falls back instead of being set here.
 */
public record AdminWagerFeeDTO(String game, String mode, int feePercent, boolean inherited) {
}
