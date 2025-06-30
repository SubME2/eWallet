package com.ewallet.dom.record;


import com.ewallet.dom.constant.TransactionRequestType;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

@Slf4j
public record TransactionRequest(String senderUserName, String receiverUsername, double amount, String idempotencyKey,
                                 TransactionRequestType transactionRequestType, int retryCount
){


    public TransactionRequest {
        Objects.requireNonNull(senderUserName, "senderUserName cannot be null");
        Objects.requireNonNull(idempotencyKey, "amount cannot be null");
        Objects.requireNonNull(transactionRequestType,"transactionRequestType cannot be null");
        if (amount < 0 ) throw new IllegalArgumentException("Amount cannot be less than zero");
        if (TransactionRequestType.TRANSFER.equals(transactionRequestType) )
                Objects.requireNonNull(receiverUsername, "receiverUserName cannot be null") ;
        if (retryCount < 0 || retryCount > 3 )
            throw new IllegalArgumentException("Retry attempt cannot increase more than 3 for senderUserName: " + senderUserName );
    }

    public TransactionRequest getTransactionRequestAndIncrementRetryCount(){
        try {
            log.error("Waiting to init new process");
            Thread.sleep((long) (5000L * retryCount * Math.random() * Math.random()));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return new TransactionRequest( senderUserName, receiverUsername, amount, idempotencyKey,
                 transactionRequestType, retryCount + 1);
    }

}
