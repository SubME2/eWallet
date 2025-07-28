package com.ewallet.dom.executable;

import com.ewallet.dom.exception.EWalletConcurrentExecutionException;
import com.ewallet.dom.model.IdempotencyKey;
import com.ewallet.dom.model.Transaction;
import com.ewallet.dom.model.User;
import com.ewallet.dom.model.Wallet;
import com.ewallet.dom.record.RepoRecord;
import com.ewallet.dom.record.TransactionRequest;
import com.ewallet.dom.repository.IdempotencyKeyRepository;
import com.ewallet.dom.repository.TransactionRepository;
import com.ewallet.dom.repository.UserRepository;
import com.ewallet.dom.repository.WalletRepository;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.StaleObjectStateException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

@Slf4j
public sealed abstract class BaseExecutable
        implements Runnable, Callable<Wallet>, Supplier<Wallet>
        permits DepositFund, TransferFund, WithdrawFund {

    private final TransactionRequest transactionRequest;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final TransactionRepository transactionRepository;

    private final CompletableFuture<Wallet> result = new CompletableFuture<>();

    public Wallet getResult() {
        try {
            return result.get();
        } catch (InterruptedException | ExecutionException e) {
            return null;
        }
    }

    protected BaseExecutable(RepoRecord repoRecord, TransactionRequest transactionRequest) {
        this.transactionRequest = transactionRequest;
        userRepository = repoRecord.userRepository();
        walletRepository = repoRecord.walletRepository();
        idempotencyKeyRepository = repoRecord.idempotencyKeyRepository();
        transactionRepository = repoRecord.transactionRepository();
    }

    @Transactional
    public abstract Wallet execute(final TransactionRequest transactionRequest);

    User findUserByUsername(String senderUserName) {
        return userRepository.findByUsername(senderUserName).orElseThrow();
    }
    Wallet findWalletByUserId(User user){
        return  walletRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Wallet not found for userId: " + user.getId())); // This will fetch the sender's wallet as part of the transaction
    }

    void validateAmount(double amount){
        if (amount <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive.");
        }
    }

    void saveWallets(Wallet... wallets){
        walletRepository.saveAll(Arrays.stream(wallets).toList());
    }

    void saveTransactions(Transaction... transactions){
        transactionRepository.saveAll(Arrays.stream(transactions).toList());
    }

    boolean existsByKey(String idempotencyKey) {
        return idempotencyKeyRepository.existsByKey(idempotencyKey);
    }
    void saveIdempotencyKey(String idempotencyKey,User user){
        idempotencyKeyRepository.save(new IdempotencyKey(idempotencyKey, transactionRequest.transactionRequestType().toString(), user));
    }

    public Wallet execute() {
        while ( transactionRequest.retryCount() < 3 ) {
            TransactionRequest transactionRequestLocal = transactionRequest.retryCount() == 0  ? transactionRequest
                    : transactionRequest.getTransactionRequestAndIncrementRetryCount();
            try {
                Wallet wallet = execute(transactionRequestLocal);
                if (wallet != null){
                    result.complete(wallet);
                    return wallet;
                }
            } catch (ObjectOptimisticLockingFailureException | StaleObjectStateException e) {
                log.error("Error in withdraw transaction. ", e);
            }
        }
        throw new EWalletConcurrentExecutionException(transactionRequest,"FAILED::".repeat(20));
    }



    @Override
    public void run() {
        execute();
    }

    @Override
    public Wallet call() {
        return execute();
    }

    @Override
    public Wallet get() {
        return execute();
    }
}
