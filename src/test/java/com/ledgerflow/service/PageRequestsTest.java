package com.ledgerflow.service;

import com.ledgerflow.exception.InvalidPaginationException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageRequestsTest {

    @Test
    void buildsLatestFirstPageAndRejectsInvalidBounds() {
        var pageable = PageRequests.of(0, 100);
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(100);
        assertThat(pageable.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(pageable.getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);

        assertThatThrownBy(() -> PageRequests.of(-1, 10))
                .isInstanceOf(InvalidPaginationException.class)
                .extracting(thrown -> ((InvalidPaginationException) thrown).getDetails().get(0))
                .isEqualTo("page must be zero or greater");
        assertThatThrownBy(() -> PageRequests.of(0, 0))
                .isInstanceOf(InvalidPaginationException.class)
                .extracting(thrown -> ((InvalidPaginationException) thrown).getDetails().get(0))
                .isEqualTo("size must be greater than zero");
        assertThatThrownBy(() -> PageRequests.of(0, -5))
                .isInstanceOf(InvalidPaginationException.class);
        assertThatThrownBy(() -> PageRequests.of(0, 101))
                .isInstanceOf(InvalidPaginationException.class)
                .extracting(thrown -> ((InvalidPaginationException) thrown).getDetails().get(0))
                .isEqualTo("size must not exceed 100");
    }
}
