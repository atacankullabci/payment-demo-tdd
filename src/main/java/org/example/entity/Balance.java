package org.example.entity;

import org.example.exception.InsufficientBalanceException;

import java.math.BigDecimal;

public record Balance(BigDecimal amount, Currency currency) {
    public Balance {
        if(amount == null) throw new IllegalArgumentException("Balance can not be null");
        if (amount.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("Balance amount cannot be negative");
    }

    public Balance add(Money money) {
        return new Balance(this.amount.add(money.amount()), currency);
    }

    public Balance subtract(Money money) {
        if (this.amount.compareTo(money.amount()) < 0) {
            throw new InsufficientBalanceException("Insufficient balance.");
        }
        return new Balance(this.amount.subtract(money.amount()),currency);
    }
}
