package org.example.service;

import org.example.entity.Account;
import org.example.entity.MoneyTransfer;

import java.math.BigDecimal;
import java.util.UUID;

public class MoneyTransferServiceImpl implements MoneyTransferService {

    @Override
    public MoneyTransfer transfer(BigDecimal amount, Account from, Account to, UUID transferId) {
        from.withdraw(amount);
        to.deposit(amount);

        return new MoneyTransfer(transferId);
    }
}
