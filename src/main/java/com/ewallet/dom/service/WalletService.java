package com.ewallet.dom.service;

import com.ewallet.dom.exception.EWalletConcurrentExecutionException;
import com.ewallet.dom.exception.InsufficientFundsException;
import com.ewallet.dom.executable.DepositFund;
import com.ewallet.dom.executable.TransferFund;
import com.ewallet.dom.executable.WithdrawFund;
import com.ewallet.dom.model.Transaction;
import com.ewallet.dom.model.Wallet;
import com.ewallet.dom.record.RepoRecord;
import com.ewallet.dom.record.TransactionRequest;
import com.ewallet.dom.repository.IdempotencyKeyRepository;
import com.ewallet.dom.repository.TransactionRepository;
import com.ewallet.dom.repository.UserRepository;
import com.ewallet.dom.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.StaleObjectStateException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.BiFunction;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {


    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;

    ExecutorService service = Executors.newCachedThreadPool();
    Map<Long, CompletableFuture<Wallet>> threadMap = new ConcurrentHashMap<>();


    public CompletableFuture<Wallet> processTransaction(TransactionRequest transactionRequest,boolean b)  {
        Long userId = transactionRequest.userId();
        populateMap(userId);
        CompletableFuture<Wallet>  walletCompletableFuture = CompletableFuture.supplyAsync(concurrentTransactionProcessor( transactionRequest),service);
        if (threadMap.containsKey(userId)) {
            threadMap.get(userId)
                    .thenRun(()-> threadMap.put(userId,walletCompletableFuture));
        }
        return walletCompletableFuture;
    }


    private void populateMap(Long userId) {
        // Populating map with Complete-able future and completing it at the same time
        threadMap.putIfAbsent(userId, CompletableFuture.supplyAsync(Wallet::new,service));
    }

    public Supplier<Wallet> concurrentTransactionProcessor(TransactionRequest transactionRequest)  {
        RepoRecord repoRecord = new RepoRecord(userRepository,walletRepository,transactionRepository,idempotencyKeyRepository);
        final Long userId = transactionRequest.userId();
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
            String message = "Concurrent deposit exception for userId: " + userId;
            throw new EWalletConcurrentExecutionException(transactionRequest,message);
        }
    }


    //https://medium.com/javarevisited/solution-for-optimistic-locking-failed-database-transaction-issue-4a87880bbfd2
    //https://medium.com/@AlexanderObregon/how-to-adopt-resiliency-patterns-with-spring-boot-circuit-breaker-retries-etc-1b65e63df586

    public Wallet findWalletByUserID(Long senderUserId) {
        return walletRepository.findByUserId(senderUserId)
                .orElseThrow(() -> new RuntimeException("Wallet not found for userId: " + senderUserId));
    }

    public List<Transaction> getTransactionsForWallet(UUID walletId) {
        return transactionRepository.findByWalletIdOrderByTimestampDesc(walletId);
    }

}