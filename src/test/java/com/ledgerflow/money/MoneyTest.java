package com.ledgerflow.money;

import com.ledgerflow.exception.InvalidAmountException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void normalizesScaleAndRejectsNonPositiveOrTooPreciseAmounts() {
        assertThat(Money.requirePositive(new BigDecimal("10.5"))).isEqualByComparingTo("10.50");
        assertThat(Money.requirePositive(new BigDecimal("0.01"))).isEqualByComparingTo("0.01");
        assertThat(Money.ZERO).isEqualByComparingTo("0.00");
        assertThat(Money.ROUNDING).isEqualTo(java.math.RoundingMode.HALF_UP);

        assertThatThrownBy(() -> Money.requirePositive(null)).isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> Money.requirePositive(new BigDecimal("0.00"))).isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> Money.requirePositive(new BigDecimal("-1.00"))).isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> Money.requirePositive(new BigDecimal("1.001"))).isInstanceOf(InvalidAmountException.class);
    }
}
