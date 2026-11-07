package org.example.entity;

public enum Currency {
    TURKISH_LIRA("949"),
    AMERICAN_DOLLAR("840"),
    ;

    private final String currencyCode;

    Currency(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }
}
