package com.ewallet.dom.exception;

import com.ewallet.dom.record.TransactionRequest;
import lombok.Getter;

@Getter
public class EWalletConcurrentExecutionException extends RuntimeException {
    final TransactionRequest transactionRequest;

    public EWalletConcurrentExecutionException(TransactionRequest transactionRequest, String message) {
        super(message);
        this.transactionRequest = transactionRequest;
    }


}
