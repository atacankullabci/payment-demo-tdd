package org.example.service;

import org.example.entity.Account;
import org.example.entity.MoneyTransfer;

import java.math.BigDecimal;
import java.util.UUID;

public interface MoneyTransferService {
    MoneyTransfer transfer(BigDecimal amount, Account from, Account to, UUID transferId);
}
