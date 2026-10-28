package com.ledgerflow.service;

import com.ledgerflow.exception.InvalidPaginationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PageRequests {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private PageRequests() {
    }

    public static Pageable of(int page, int size) {
        if (page < 0) {
            throw new InvalidPaginationException("page must be zero or greater");
        }
        if (size <= 0) {
            throw new InvalidPaginationException("size must be greater than zero");
        }
        if (size > MAX_SIZE) {
            throw new InvalidPaginationException("size must not exceed " + MAX_SIZE);
        }
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }
}
