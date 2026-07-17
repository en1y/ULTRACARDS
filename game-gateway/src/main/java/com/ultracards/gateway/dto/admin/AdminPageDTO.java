package com.ultracards.gateway.dto.admin;

import java.util.List;

public record AdminPageDTO<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
}
