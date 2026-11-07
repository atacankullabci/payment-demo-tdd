package org.example;

import org.example.entity.*;
import org.example.service.MoneyTransferService;
import org.example.service.MoneyTransferServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

public class MoneyTransferServiceTest {

    private final Balance ACCOUNT_1_INITIAL_BALANCE = new Balance(BigDecimal.valueOf(100), Currency.TURKISH_LIRA);
    private final Money ACCOUNT_1_EXACT_BALANCE = new Money(BigDecimal.valueOf(100), Currency.TURKISH_LIRA);

    private final Balance ACCOUNT_2_INITIAL_BALANCE = new Balance(BigDecimal.valueOf(50), Currency.TURKISH_LIRA);
    private final Money ACCOUNT_2_EXACT_BALANCE = new Money(BigDecimal.valueOf(50), Currency.TURKISH_LIRA);

    private final Money VALID_TRANSFER_MONEY_ACCOUNT_1 = new Money(BigDecimal.valueOf(60), Currency.TURKISH_LIRA);
    private final Money EXCEEDED_TRANSFER_MONEY_ACCOUNT_1 = new Money(BigDecimal.valueOf(150), Currency.TURKISH_LIRA);
    private final Money SMALLEST_TRANSFER_MONEY = new Money(BigDecimal.valueOf(1), Currency.TURKISH_LIRA);

    private MoneyTransferService moneyTransferService;

    private Account account1;
    private Account account2;
    private Account account3;
    private Account account4;
    private UUID transferId;

    @BeforeEach
    void setup() {
        moneyTransferService = new MoneyTransferServiceImpl(new MoneyTransferRepository());

        account1 = new Account(1L, ACCOUNT_1_INITIAL_BALANCE);
        account2 = new Account(2L, ACCOUNT_2_INITIAL_BALANCE);

        account3 = new Account(3L, ACCOUNT_1_INITIAL_BALANCE);
        account4 = new Account(4L, ACCOUNT_2_INITIAL_BALANCE);

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
        MoneyTransfer moneyTransfer = moneyTransferService.transfer(VALID_TRANSFER_MONEY_ACCOUNT_1, account1, account2, transferId);

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
        MoneyTransfer transfer = moneyTransferService.transfer(EXCEEDED_TRANSFER_MONEY_ACCOUNT_1, account1, account2, transferId);
        assertAll(
                () -> assertEquals(MoneyTransfer.Status.REJECTED, transfer.status()),
                () -> assertEquals(0, account1.getBalance().amount().compareTo(ACCOUNT_1_INITIAL_BALANCE.amount())),
                () -> assertEquals(0, account2.getBalance().amount().compareTo(ACCOUNT_2_INITIAL_BALANCE.amount()))
        );
    }

    @Test
    public void shouldReoccurringTransferReturnSamePreviousResponse() {
        MoneyTransfer moneyTransfer = moneyTransferService.transfer(VALID_TRANSFER_MONEY_ACCOUNT_1, account1, account2, transferId);

        MoneyTransfer.Status prevStatus = moneyTransfer.status();

        assertEquals(transferId, moneyTransfer.transferId());

        moneyTransfer = moneyTransferService.transfer(VALID_TRANSFER_MONEY_ACCOUNT_1, account1, account2, transferId);

        assertEquals(transferId, moneyTransfer.transferId());
        assertEquals(prevStatus, moneyTransfer.status());
    }

    @Test
    public void shouldTransferSucceedOnRecurringWhenBalanceIsValid() {
        MoneyTransfer moneyTransfer;

        moneyTransferService.transfer(EXCEEDED_TRANSFER_MONEY_ACCOUNT_1, account1, account2, transferId); // Insufficient balance - REJECTED

        moneyTransferService.transfer(ACCOUNT_2_EXACT_BALANCE, account2, account1, UUID.randomUUID()); // ADD funds

        for (int i = 0; i < 10; i++) {
            moneyTransfer = moneyTransferService.transfer(EXCEEDED_TRANSFER_MONEY_ACCOUNT_1, account1, account2, transferId); // same transfer succeeds this time

            assertEquals(transferId, moneyTransfer.transferId());
            assertEquals(MoneyTransfer.Status.SUCCESS, moneyTransfer.status());
        }

        assertEquals(0, account1.getBalance().amount().compareTo(BigDecimal.ZERO));
        assertEquals(0, account2.getBalance().amount().compareTo(BigDecimal.valueOf(150)));
    }

    @Test
    public void shouldFailWhenAccountTransfersItsOwn() {
        IllegalArgumentException ex = assertThrowsExactly(IllegalArgumentException.class,
                () -> moneyTransferService.transfer(VALID_TRANSFER_MONEY_ACCOUNT_1, account1, account1, transferId));

        assertAll(
                () -> assertEquals(0, account1.getBalance().amount().compareTo(ACCOUNT_1_INITIAL_BALANCE.amount())),
                () -> assertTrue(ex.getMessage().contains("same account"))
        );
    }

    @RepeatedTest(200)
    public void shouldHandleThreadSafe() throws InterruptedException {
        int threadCount = 100;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                ready.countDown();

                try {
                    start.await();
                    for (int j = 0; j < 100; j++) {

                        moneyTransferService.transfer(SMALLEST_TRANSFER_MONEY, account1, account2, UUID.randomUUID());
                        moneyTransferService.transfer(SMALLEST_TRANSFER_MONEY, account2, account1, UUID.randomUUID());


                        moneyTransferService.transfer(SMALLEST_TRANSFER_MONEY, account3, account4, UUID.randomUUID());
                        moneyTransferService.transfer(SMALLEST_TRANSFER_MONEY, account4, account3, UUID.randomUUID());


                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        boolean await = done.await(5, TimeUnit.SECONDS);
        executorService.shutdown();

        assertTrue(await, "Transfers did not finish in 5s — possible deadlock");

        assertEquals(0, account1.getBalance().amount().compareTo(BigDecimal.valueOf(100)));
        assertEquals(0, account2.getBalance().amount().compareTo(BigDecimal.valueOf(50)));

        assertEquals(0, account3.getBalance().amount().compareTo(BigDecimal.valueOf(100)));
        assertEquals(0, account4.getBalance().amount().compareTo(BigDecimal.valueOf(50)));

    }

    @RepeatedTest(200)
    public void shouldBeIdempotent() throws InterruptedException {
        int threadCount = 100;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                ready.countDown();

                try {
                    start.await();
                    for (int j = 0; j < 100; j++) {
                        moneyTransferService.transfer(SMALLEST_TRANSFER_MONEY, account1, account2, transferId);
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        boolean await = done.await(5, TimeUnit.SECONDS);
        executorService.shutdown();

        assertTrue(await, "Transfers did not finish in 5s — possible deadlock");

        assertEquals(0, account1.getBalance().amount().compareTo(BigDecimal.valueOf(100 - 1)));
        assertEquals(0, account2.getBalance().amount().compareTo(BigDecimal.valueOf(50 + 1)));

    }

}
