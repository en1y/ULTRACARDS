package com.ultracards.gateway.dto.points;

import java.util.List;

public record PointTransactionPageDTO(
        List<PointTransactionDTO> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
