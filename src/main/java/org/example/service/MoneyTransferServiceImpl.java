package org.example.service;

import org.example.MoneyTransferRepository;
import org.example.entity.Account;
import org.example.entity.Money;
import org.example.entity.MoneyTransfer;
import org.example.exception.InsufficientBalanceException;

import java.util.UUID;

public class MoneyTransferServiceImpl implements MoneyTransferService {

    private final MoneyTransferRepository moneyTransferRepository;

    public MoneyTransferServiceImpl(MoneyTransferRepository moneyTransferRepository) {
        this.moneyTransferRepository = moneyTransferRepository;
    }

    @Override
    public MoneyTransfer transfer(Money money, Account from, Account to, UUID transferId) {

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

        MoneyTransfer moneyTransfer = new MoneyTransfer(transferId, transferStatus);
        moneyTransferRepository.save(moneyTransfer);

        return moneyTransfer;
    }
}
