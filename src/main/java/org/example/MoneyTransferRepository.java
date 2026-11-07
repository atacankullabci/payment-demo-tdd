package org.example;

import org.example.entity.MoneyTransfer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MoneyTransferRepository {
    private final Map<UUID, MoneyTransfer> moneyTransferLedger = new HashMap<>();

    public MoneyTransfer get(UUID uuid) {
        return moneyTransferLedger.get(uuid);
    }

    public void save(MoneyTransfer moneyTransfer) {
        moneyTransferLedger.put(moneyTransfer.transferId(), moneyTransfer);
    }
}
