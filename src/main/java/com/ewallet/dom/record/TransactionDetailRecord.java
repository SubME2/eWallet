package com.ewallet.dom.record;

import com.ewallet.dom.model.Transaction;

import java.util.Objects;
import java.util.UUID;

public record TransactionDetailRecord(UUID walletId,
                                      String senderUserName,
                                      String receiverUserName,
                                      double amount,
                                      double preBalance,
                                      double postBalance,
                                      Transaction.TransactionType type
) {

    public TransactionDetailRecord {
        Objects.requireNonNull(walletId, "WalletId cannot be null");
        Objects.requireNonNull(type, "Transaction type cannot be null");
        Objects.requireNonNull(senderUserName, "senderUserName cannot be null");
        Objects.requireNonNull(receiverUserName, "receiverUserName cannot be null");
        if (amount < 0 ) throw new IllegalArgumentException("Amount cannot be less than zero");
        switch (type){
            case DEPOSIT, TRANSFER_RECEIVED  -> {
                if (postBalance != (preBalance + amount)) throw new IllegalArgumentException("postBalance does not match transaction Type");
            }
            case WITHDRAWAL, TRANSFER_SENT -> {
                if (postBalance != (preBalance - amount)) throw new IllegalArgumentException("postBalance does not match transaction Type");
            }
        }
    }
}
