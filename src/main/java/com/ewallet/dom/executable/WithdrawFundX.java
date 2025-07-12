package com.ewallet.dom.executable;

import com.ewallet.dom.exception.EWalletConcurrentExecutionException;
import com.ewallet.dom.exception.InsufficientFundsException;
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
import lombok.extern.slf4j.Slf4j;
import org.hibernate.StaleObjectStateException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Supplier;


@Slf4j
public record WithdrawFundX(RepoRecord repoRecord, TransactionRequest transactionRequest) implements  Callable<Wallet>, Supplier<Wallet> {


    @Transactional
    private Wallet withdrawFund(final TransactionRequest transactionRequest) {
        final String senderUserName = transactionRequest.senderUserName();
        final double amount = transactionRequest.amount();
        final String idempotencyKey = transactionRequest.idempotencyKey();

        if (amount <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive.");
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
            log.debug("Idempotent withdrawal request detected and ignored for key: {}", idempotencyKey);
            return wallet;
        }
        if (wallet.getBalance() < amount) {
            throw new InsufficientFundsException("Insufficient funds for withdrawal.");
        }

        // Debit sender
        double preBalance = wallet.getBalance();
        double postBalance = preBalance - amount;
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
                Transaction.TransactionType.WITHDRAWAL
        ));

        transactionRepository.save(transaction);

        // Record the idempotency key after successful processing

        idempotencyKeyRepository.save(new IdempotencyKey(idempotencyKey, "WITHDRAWAL", user));

        return wallet;
    }

    public Wallet withdrawFund() {
        while ( transactionRequest.retryCount() < 3) {
            TransactionRequest transactionRequestLocal = transactionRequest.retryCount() == 0  ? transactionRequest
                    : transactionRequest.getTransactionRequestAndIncrementRetryCount();
            try {
                Wallet wallet = withdrawFund(transactionRequestLocal);
                if (wallet != null) return wallet;
            } catch (ObjectOptimisticLockingFailureException | StaleObjectStateException e) {
                log.error("Error in withdraw transaction. ", e);
            }
        }
        throw new EWalletConcurrentExecutionException(transactionRequest,"FAILED::".repeat(20));
    }


    @Override
    public Wallet call() {
        return withdrawFund();
    }

    @Override
    public Wallet get() {
        return withdrawFund();
    }
}
