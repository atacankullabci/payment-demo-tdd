package org.example;

import org.example.entity.MoneyTransfer;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MoneyTransferRepository {
    private final ConcurrentHashMap<UUID, MoneyTransfer> moneyTransferLedger = new ConcurrentHashMap<>();

    public MoneyTransfer get(UUID uuid) {
        return moneyTransferLedger.get(uuid);
    }

    public void save(MoneyTransfer moneyTransfer) {
        moneyTransferLedger.put(moneyTransfer.transferId(), moneyTransfer);
    }
}
