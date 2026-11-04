package org.example;

import org.example.entity.Account;
import org.example.entity.MoneyTransfer;
import org.example.service.MoneyTransferService;
import org.example.service.MoneyTransferServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class MoneyTransferServiceTest {

    private MoneyTransferService moneyTransferService;

    private Account account1;
    private Account account2;
    private UUID transferId;

    @BeforeEach
    void setup() {
        moneyTransferService = new MoneyTransferServiceImpl();

        account1 = new Account(1L, BigDecimal.valueOf(100));
        account2 = new Account(2L, BigDecimal.valueOf(50));

        transferId = UUID.randomUUID();
    }

    @Test
    public void shouldSuccessfullyMoneyTransfer() {
        MoneyTransfer moneyTransfer = moneyTransferService.transfer(BigDecimal.valueOf(30), account1, account2, transferId);

        assertAll(
                () -> assertEquals(0, account1.getBalance().compareTo(BigDecimal.valueOf(70))),
                () -> assertEquals(0, account2.getBalance().compareTo(BigDecimal.valueOf(80))),
                () -> assertEquals(transferId, moneyTransfer.transferId())
        );
    }

    @Test
    public void shouldFailWhenInsufficientAmount() {
        BigDecimal prevAccount1Balance = account1.getBalance();
        BigDecimal prevAccount2Balance = account2.getBalance();

        moneyTransferService.transfer(BigDecimal.valueOf(150), account1, account2, transferId);

        assertAll(
                () -> assertEquals(prevAccount1Balance, account1.getBalance()),
                () -> assertEquals(prevAccount2Balance, account2.getBalance())
        );
    }

}
