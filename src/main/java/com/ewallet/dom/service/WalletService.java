package com.ewallet.dom.service;

import com.ewallet.dom.exception.EWalletConcurrentExecutionException;
import com.ewallet.dom.executable.DepositFund;
import com.ewallet.dom.executable.TransferFund;
import com.ewallet.dom.executable.WithdrawFund;
import com.ewallet.dom.model.Transaction;
import com.ewallet.dom.model.User;
import com.ewallet.dom.model.Wallet;
import com.ewallet.dom.record.RepoRecord;
import com.ewallet.dom.record.TransactionRequest;
import com.ewallet.dom.repository.IdempotencyKeyRepository;
import com.ewallet.dom.repository.TransactionRepository;
import com.ewallet.dom.repository.UserRepository;
import com.ewallet.dom.repository.WalletRepository;
import com.ewallet.dom.util.LogExecution;
import com.ewallet.dom.util.LogExecutionTime;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.StaleObjectStateException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;

@Slf4j
@Service
public class WalletService {


    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;

    //ExecutorService service = Executors.newCachedThreadPool();

    private final ThreadPoolTaskExecutor taskExecutor;

    public WalletService(UserRepository userRepository, WalletRepository walletRepository,
                         TransactionRepository transactionRepository,
                         IdempotencyKeyRepository idempotencyKeyRepository,
                         @Qualifier("taskExecutor") ThreadPoolTaskExecutor taskExecutor) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.taskExecutor = taskExecutor;
    }

    @LogExecution
    @LogExecutionTime
    public CompletableFuture<Wallet> processTransaction(TransactionRequest transactionRequest,boolean b)  {
        CompletableFuture<Wallet>  walletCompletableFuture = CompletableFuture.supplyAsync(concurrentTransactionProcessor( transactionRequest),taskExecutor);
        walletCompletableFuture.orTimeout(5, TimeUnit.SECONDS);
        return walletCompletableFuture;
    }


    public Supplier<Wallet> concurrentTransactionProcessor(TransactionRequest transactionRequest)  {
        RepoRecord repoRecord = getRepoRecord();
        try {
            switch (transactionRequest.transactionRequestType()){
                case DEPOSIT -> {
                    return new DepositFund(repoRecord, transactionRequest);
                }
                case WITHDRAW -> {
                    return new WithdrawFund(repoRecord, transactionRequest);
                }
                case TRANSFER -> {
                    return new TransferFund(repoRecord,transactionRequest);
                }
                default -> throw new IllegalArgumentException("Unknow transaction type.");
            }
        }catch (StaleObjectStateException | ObjectOptimisticLockingFailureException e){
            String message = "Concurrent deposit exception for userId: " + transactionRequest.senderUserName();
            throw new EWalletConcurrentExecutionException(transactionRequest,message);
        }
    }

    private RepoRecord getRepoRecord() {
        return new RepoRecord(userRepository, walletRepository, transactionRepository, idempotencyKeyRepository);
    }


    //https://medium.com/javarevisited/solution-for-optimistic-locking-failed-database-transaction-issue-4a87880bbfd2
    //https://medium.com/@AlexanderObregon/how-to-adopt-resiliency-patterns-with-spring-boot-circuit-breaker-retries-etc-1b65e63df586

    public Wallet findWalletByUserID(String senderUserName) {
        User user = userRepository.findByUsername(senderUserName).orElseThrow();
        return walletRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Wallet not found for userId: " + senderUserName));
    }

    public List<Transaction> getTransactionsForWallet(UUID walletId) {
        return transactionRepository.findByWalletIdOrderByTimestampDesc(walletId);
    }

}