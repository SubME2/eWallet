package com.ewallet.dom.mapper;

import com.ewallet.dom.constant.TransactionRequestType;
import com.ewallet.dom.dto.DepositRequest;
import com.ewallet.dom.dto.TransferRequest;
import com.ewallet.dom.dto.WithdrawRequest;
import com.ewallet.dom.model.User;
import com.ewallet.dom.record.TransactionRequest;
import jakarta.validation.Valid;


public class TransactionMappingService {

    public static TransactionRequest fromDepositRequest(User currentUser, @Valid DepositRequest depositRequest) {
        return new TransactionRequest(currentUser.getId(),null,
                depositRequest.getAmount(),depositRequest.getIdempotencyKey(), TransactionRequestType.DEPOSIT,0);
    }

    public static TransactionRequest fromTransferRequest(User currentUser, TransferRequest transferRequest){
        return new TransactionRequest(currentUser.getId(),transferRequest.getReceiverUsername(),
                transferRequest.getAmount(),transferRequest.getIdempotencyKey(), TransactionRequestType.TRANSFER,0);
    }

    public static TransactionRequest fromWithdrawRequest(User currentUser, @Valid WithdrawRequest withdrawRequest) {
        return new TransactionRequest(currentUser.getId(),null,
                withdrawRequest.getAmount(),withdrawRequest.getIdempotencyKey(), TransactionRequestType.WITHDRAW,0);
    }
}
