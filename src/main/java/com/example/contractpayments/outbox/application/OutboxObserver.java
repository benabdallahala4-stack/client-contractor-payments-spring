package com.example.contractpayments.outbox.application;

public interface OutboxObserver {
    void published();
    void failed();

    OutboxObserver NOOP = new OutboxObserver() {
        @Override public void published() { }
        @Override public void failed() { }
    };
}
