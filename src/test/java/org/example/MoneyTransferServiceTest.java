package org.example;

import org.example.entity.*;
import org.example.service.MoneyTransferService;
import org.example.service.MoneyTransferServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class MoneyTransferServiceTest {

    private final Balance ACCOUNT_1_INITIAL_BALANCE = new Balance(BigDecimal.valueOf(100), Currency.TURKISH_LIRA);
    private final Money ACCOUNT_1_EXACT_BALANCE = new Money(BigDecimal.valueOf(100), Currency.TURKISH_LIRA);

    private final Balance ACCOUNT_2_INITIAL_BALANCE = new Balance(BigDecimal.valueOf(50), Currency.TURKISH_LIRA);
    private final Money ACCOUNT_2_EXACT_BALANCE = new Money(BigDecimal.valueOf(50), Currency.TURKISH_LIRA);
    private final Money VALID_TRANSFER_MONEY = new Money(BigDecimal.valueOf(60), Currency.TURKISH_LIRA);
    private final Money EXCEEDED_TRANSFER_MONEY = new Money(BigDecimal.valueOf(150), Currency.TURKISH_LIRA);

    private MoneyTransferService moneyTransferService;

    private Account account1;
    private Account account2;
    private UUID transferId;

    @BeforeEach
    void setup() {
        moneyTransferService = new MoneyTransferServiceImpl(new MoneyTransferRepository());

        account1 = new Account(1L, ACCOUNT_1_INITIAL_BALANCE);
        account2 = new Account(2L, ACCOUNT_2_INITIAL_BALANCE);

        transferId = UUID.randomUUID();
    }

    @Test
    public void shouldFailWhenTransferAmountIsNull() {
        assertThrows(IllegalArgumentException.class, () -> new Money(null, Currency.TURKISH_LIRA));
    }

    @Test
    public void shouldFailWhenTransferAmountIsNegative() {
        assertThrows(IllegalArgumentException.class, () -> new Money(BigDecimal.valueOf(-1), Currency.TURKISH_LIRA));
    }

    @Test
    public void shouldFailWhenTransferAmountIsZero() {
        assertThrows(IllegalArgumentException.class, () -> new Money(BigDecimal.ZERO, Currency.TURKISH_LIRA));
    }

    @Test
    public void shouldSuccessfullyMoneyTransfer() {
        MoneyTransfer moneyTransfer = moneyTransferService.transfer(VALID_TRANSFER_MONEY, account1, account2, transferId);

        assertAll(
                () -> assertEquals(0, account1.getBalance().amount().compareTo(BigDecimal.valueOf(40))),
                () -> assertEquals(0, account2.getBalance().amount().compareTo(BigDecimal.valueOf(110))),
                () -> assertEquals(transferId, moneyTransfer.transferId()),
                () -> assertEquals(MoneyTransfer.Status.SUCCESS, moneyTransfer.status())
        );
    }

    @Test
    public void shouldTransferWholeBalanceAndSucceed() {
        MoneyTransfer moneyTransfer = moneyTransferService.transfer(ACCOUNT_1_EXACT_BALANCE, account1, account2, transferId);

        assertAll(
                () -> assertEquals(0, account1.getBalance().amount().compareTo(BigDecimal.ZERO)),
                () -> assertEquals(0, account2.getBalance().amount().compareTo(BigDecimal.valueOf(150))),
                () -> assertEquals(transferId, moneyTransfer.transferId()),
                () -> assertEquals(MoneyTransfer.Status.SUCCESS, moneyTransfer.status())
        );
    }

    @Test
    public void shouldFailWhenFromAccountHasInsufficientAmount() {
        MoneyTransfer transfer = moneyTransferService.transfer(EXCEEDED_TRANSFER_MONEY, account1, account2, transferId);
        assertAll(
                () -> assertEquals(MoneyTransfer.Status.REJECTED, transfer.status()),
                () -> assertEquals(0, account1.getBalance().amount().compareTo(ACCOUNT_1_INITIAL_BALANCE.amount())),
                () -> assertEquals(0, account2.getBalance().amount().compareTo(ACCOUNT_2_INITIAL_BALANCE.amount()))
        );
    }

    @Test
    public void shouldReoccurringTransferReturnSamePreviousResponse() {
        MoneyTransfer moneyTransfer = moneyTransferService.transfer(VALID_TRANSFER_MONEY, account1, account2, transferId);

        MoneyTransfer.Status prevStatus = moneyTransfer.status();

        assertEquals(transferId, moneyTransfer.transferId());

        moneyTransfer = moneyTransferService.transfer(VALID_TRANSFER_MONEY, account1, account2, transferId);

        assertEquals(transferId, moneyTransfer.transferId());
        assertEquals(prevStatus, moneyTransfer.status());
    }

    @Test
    public void shouldTransferSucceedOnRecurringWhenBalanceIsValid() {
        MoneyTransfer moneyTransfer;

        moneyTransferService.transfer(EXCEEDED_TRANSFER_MONEY, account1, account2, transferId); // Insufficient balance - REJECTED

        moneyTransferService.transfer(ACCOUNT_2_EXACT_BALANCE, account2, account1, UUID.randomUUID()); // ADD funds

        for (int i = 0; i < 10; i++) {
            moneyTransfer = moneyTransferService.transfer(EXCEEDED_TRANSFER_MONEY, account1, account2, transferId); // same transfer succeeds this time

            assertEquals(transferId, moneyTransfer.transferId());
            assertEquals(MoneyTransfer.Status.SUCCESS, moneyTransfer.status());
        }

        assertEquals(0, account1.getBalance().amount().compareTo(BigDecimal.ZERO));
        assertEquals(0, account2.getBalance().amount().compareTo(BigDecimal.valueOf(150)));
    }
}
