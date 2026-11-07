package org.example.service;

import org.example.entity.Account;
import org.example.entity.Money;
import org.example.entity.MoneyTransfer;

import java.util.UUID;

public interface MoneyTransferService {
    MoneyTransfer transfer(Money money, Account from, Account to, UUID transferId);
}
