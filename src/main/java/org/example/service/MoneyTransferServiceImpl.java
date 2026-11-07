package org.example.service;

import org.example.MoneyTransferRepository;
import org.example.entity.Account;
import org.example.entity.Money;
import org.example.entity.MoneyTransfer;
import org.example.exception.InsufficientBalanceException;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public class MoneyTransferServiceImpl implements MoneyTransferService {

    private final MoneyTransferRepository moneyTransferRepository;

    public MoneyTransferServiceImpl(MoneyTransferRepository moneyTransferRepository) {
        this.moneyTransferRepository = moneyTransferRepository;
    }

    @Override
    public MoneyTransfer transfer(Money money, Account from, Account to, UUID transferId) {

        if (Objects.equals(from.getId(), to.getId())) {
            throw new IllegalArgumentException("You can not send money to the same account.");
        }

        AtomicReference<MoneyTransfer> moneyTransfer = new AtomicReference<>();

        Account firstLock = from.getId().compareTo(to.getId()) < 0 ? from : to;
        Account secondLock = from.getId().compareTo(to.getId()) < 0 ? to : from;
        synchronized (firstLock) {
            synchronized (secondLock) {
                MoneyTransfer moneyTransferRecord = moneyTransferRepository.get(transferId);
                if (moneyTransferRecord != null && MoneyTransfer.Status.SUCCESS == moneyTransferRecord.status()) {
                    return moneyTransferRecord;
                }

                MoneyTransfer.Status transferStatus;
                try {

                    from.withdraw(money);
                    to.deposit(money);
                    transferStatus = MoneyTransfer.Status.SUCCESS;

                } catch (InsufficientBalanceException ise) {
                    transferStatus = MoneyTransfer.Status.REJECTED;
                } catch (Exception e) {
                    System.err.println(e);
                    throw e;
                }

                moneyTransfer.getAndSet(new MoneyTransfer(transferId, transferStatus));
                moneyTransferRepository.save(moneyTransfer.get());
            }
        }

        return moneyTransfer.get();
    }
}
