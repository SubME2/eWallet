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
import lombok.extern.slf4j.Slf4j;
import org.hibernate.StaleObjectStateException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
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

    private final Executor taskExecutor;

    public WalletService(UserRepository userRepository, WalletRepository walletRepository,
                         TransactionRepository transactionRepository,
                         IdempotencyKeyRepository idempotencyKeyRepository,
                         @Qualifier("taskExecutor") Executor taskExecutor) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.taskExecutor = taskExecutor;
    }

    Map<String, CompletableFuture<Wallet>> threadMap = new ConcurrentHashMap<>();


    public CompletableFuture<Wallet> processTransaction(TransactionRequest transactionRequest,boolean b)  {
        String senderUserName = transactionRequest.senderUserName();
        populateMap(senderUserName);
        CompletableFuture<Wallet>  walletCompletableFuture = CompletableFuture.supplyAsync(concurrentTransactionProcessor( transactionRequest),taskExecutor);
        if (threadMap.containsKey(senderUserName)) {
            threadMap.get(senderUserName)
                    .thenRun(()-> threadMap.put(senderUserName,walletCompletableFuture));
        }
        return walletCompletableFuture;
    }


    private void populateMap(String senderUserName) {
        // Populating map with Complete-able future and completing it at the same time
        threadMap.putIfAbsent(senderUserName, CompletableFuture.supplyAsync(Wallet::new,taskExecutor));
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