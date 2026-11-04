package org.example.entity;

import java.util.UUID;

public record MoneyTransfer(UUID transferId) {
    public enum Status {
        SUCCESS,
        PENDING,
        REJECTED,
        CANCELLED,
        ALREADY_SUCCEEDED
    }
}
