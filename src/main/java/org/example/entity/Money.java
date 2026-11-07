package org.example.entity;

import java.math.BigDecimal;

public record Money(BigDecimal amount, Currency currency) {
    public Money {
        if(amount == null) throw new IllegalArgumentException("Price is null");
        if (amount.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("Price cannot be negative");
        if (amount.compareTo(BigDecimal.ZERO) == 0) throw new IllegalArgumentException("Price cannot be zero");
    }
}
