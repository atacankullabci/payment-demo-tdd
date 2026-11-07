package org.example.entity;

import java.util.Objects;

public class Account {
    private final Long id;
    private Balance balance;

    public Account(Long id, Balance balance) {
        this.id = Objects.requireNonNull(id);
        this.balance = balance;
    }

    public Long getId() {
        return id;
    }

    public Balance getBalance() {
        return balance;
    }

    public void deposit(Money money) {
        balance = balance.add(money);
    }

    public void withdraw(Money money) {
        balance = balance.subtract(money);
    }
}
