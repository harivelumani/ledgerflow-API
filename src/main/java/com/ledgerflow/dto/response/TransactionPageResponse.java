package com.ledgerflow.dto.response;

import java.util.List;

public record TransactionPageResponse(
        List<TransactionItemResponse> content,
        int page,
        int size,
        long totalRecords,
        int totalPages
) {
}
