package com.ewallet.dom.executable;

import com.ewallet.dom.exception.EWalletConcurrentExecutionException;
import com.ewallet.dom.model.IdempotencyKey;
import com.ewallet.dom.model.Transaction;
import com.ewallet.dom.model.User;
import com.ewallet.dom.model.Wallet;
import com.ewallet.dom.record.RepoRecord;
import com.ewallet.dom.record.TransactionDetailRecord;
import com.ewallet.dom.record.TransactionRequest;
import com.ewallet.dom.repository.IdempotencyKeyRepository;
import com.ewallet.dom.repository.TransactionRepository;
import com.ewallet.dom.repository.UserRepository;
import com.ewallet.dom.repository.WalletRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.StaleObjectStateException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Supplier;


@Slf4j
@RequiredArgsConstructor
public class DepositFund implements Runnable, Callable<Wallet>, Supplier<Wallet> {

    private final RepoRecord repoRecord;
    private final TransactionRequest transactionRequest;
    private TransactionRequest transactionRequestRetry;

    @Getter
    private Wallet result;

    private Wallet depositFund(final TransactionRequest transactionRequest) {

        final String senderUserName = transactionRequest.senderUserName();
        final double amount = transactionRequest.amount();
        final String idempotencyKey = transactionRequest.idempotencyKey();

        if (amount <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive.");
        }

        UserRepository userRepository = repoRecord.userRepository(); //(UserRepository) getRepository(UserRepository.class,rfs);
        WalletRepository walletRepository = repoRecord.walletRepository(); //(WalletRepository) getRepository(WalletRepository.class,rfs);
        TransactionRepository transactionRepository = repoRecord.transactionRepository();//(TransactionRepository) getRepository(TransactionRepository.class,rfs);
        IdempotencyKeyRepository idempotencyKeyRepository = repoRecord.idempotencyKeyRepository();//(IdempotencyKeyRepository) getRepository(IdempotencyKeyRepository.class,rfs);
        User user = userRepository.findByUsername(senderUserName).orElseThrow();


        Wallet wallet = walletRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Wallet not found for userId: " + senderUserName)); // This will fetch the sender's wallet as part of the transaction
        // Idempotency check
        if (idempotencyKeyRepository.existsByKey(idempotencyKey)) {
            log.debug("Idempotent transfer request detected and ignored for key: {}", idempotencyKey);
            return wallet;
        }

        // Debit sender
        double preBalance = wallet.getBalance();
        double postBalance = preBalance + amount;
        wallet.setBalance(postBalance);
        //saveWallet(senderWallet); // Saves and increments version for senderWallet

        walletRepository.saveAll(List.of(wallet));

        // Create sender's transaction record
        Transaction transaction = new Transaction(new TransactionDetailRecord(
                wallet.getId(),
                user.getUsername(),
                user.getUsername(),
                amount,
                preBalance,
                postBalance,
                Transaction.TransactionType.DEPOSIT
        ));

        transactionRepository.save(transaction);
        // Record the idempotency key after successful processing
        idempotencyKeyRepository.save(new IdempotencyKey(idempotencyKey, "DEPOSIT", user));

//            tx.commit();
        result = wallet;
        return wallet;
    }


    public Wallet depositFund() {
        TransactionRequest transactionRequestLocal = transactionRequestRetry != null ? transactionRequestRetry : transactionRequest;
        try {
            return depositFund(transactionRequestLocal);
        } catch (ObjectOptimisticLockingFailureException | StaleObjectStateException e) {
            if (transactionRequest.retryCount() < 3){
                transactionRequestRetry = transactionRequestLocal.getTransactionRequestAndIncrementRetryCount();
                return depositFund();
            }
            else throw new EWalletConcurrentExecutionException(transactionRequest,"FAILEDDDDDDDDDDDD");
        }
    }

    @Override
    public void run() {
        result = depositFund();
    }

    @Override
    public Wallet call() {
        return depositFund();
    }

    @Override
    public Wallet get() {
        return depositFund();
    }
}
