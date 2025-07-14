package com.ewallet.dom.executable;

import com.ewallet.dom.exception.InsufficientFundsException;
import com.ewallet.dom.model.Transaction;
import com.ewallet.dom.model.User;
import com.ewallet.dom.model.Wallet;
import com.ewallet.dom.record.RepoRecord;
import com.ewallet.dom.record.TransactionDetailRecord;
import com.ewallet.dom.record.TransactionRequest;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class TransferFund  extends BaseExecutable {


    public TransferFund(RepoRecord repoRecord, TransactionRequest transactionRequest) {
        super(repoRecord,transactionRequest);
    }

    @Override
    public Wallet execute(final TransactionRequest transactionRequest) {
         final String senderUserName = transactionRequest.senderUserName();
         final String receiverName = transactionRequest.receiverUsername();
         final double amount = transactionRequest.amount();
         final String idempotencyKey = transactionRequest.idempotencyKey();

        validateAmount(amount);

        User senderUser = findUserByUsername(senderUserName);
        User receiverUser = findUserByUsername(receiverName);

        if (senderUser.getId().equals(receiverUser.getId())) {
            throw new IllegalArgumentException("Cannot transfer funds to yourself.");
        }

        Wallet senderWallet = findWalletByUserId(senderUser);
        // Idempotency check
        if (existsByKey(idempotencyKey)) {
            log.debug("Idempotent transfer request detected and ignored for key: {}", idempotencyKey);
            return senderWallet;
        }
        if (senderWallet.getBalance() < amount) {
            throw new InsufficientFundsException("Insufficient funds for transfer.");
        }
        Wallet receiverWallet = findWalletByUserId(receiverUser);
        // Debit sender
        double senderPreBalance = senderWallet.getBalance();
        double senderPostBalance = senderPreBalance - amount;
        senderWallet.setBalance(senderWallet.getBalance() - amount);


        // Credit receiver
        double receiverPreBalance = receiverWallet.getBalance();
        double receiverPostBalance = receiverPreBalance + amount;
        receiverWallet.setBalance(receiverWallet.getBalance() + amount);

        saveWallets(senderWallet, receiverWallet);

        // Create sender's transaction record
        Transaction senderTx = new Transaction(new TransactionDetailRecord(
                senderWallet.getId(),
                senderUser.getUsername(),
                receiverName,
                amount,
                senderPreBalance,
                senderPostBalance,
                Transaction.TransactionType.TRANSFER_SENT
        ));

        // Create receiver's transaction record
        Transaction receiverTx = new Transaction(new TransactionDetailRecord(
                receiverWallet.getId(),
                senderUser.getUsername(),
                receiverName,
                amount,
                receiverPreBalance,
                receiverPostBalance,
                Transaction.TransactionType.TRANSFER_RECEIVED
        ));

        saveTransactions(senderTx, receiverTx);


        // Record the idempotency key after successful processing
        saveIdempotencyKey(idempotencyKey, senderUser);

        return senderWallet;
    }

}
