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
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.StaleObjectStateException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Supplier;


@Slf4j
@RequiredArgsConstructor
public class TransferFund implements Runnable, Callable<Wallet>, Supplier<Wallet> {

    private final RepoRecord repoRecord;
    private final TransactionRequest transactionRequest;
    private TransactionRequest transactionRequestRetry;

    @Getter
    private Wallet result;

    private Wallet transferFund(final TransactionRequest transactionRequest) {
         final String senderUserName = transactionRequest.senderUserName();
         final String receiverName = transactionRequest.receiverUsername();
         final double amount = transactionRequest.amount();
         final String idempotencyKey = transactionRequest.idempotencyKey();

        UserRepository userRepository = repoRecord.userRepository();
        WalletRepository walletRepository = repoRecord.walletRepository();
        TransactionRepository transactionRepository = repoRecord.transactionRepository();
        IdempotencyKeyRepository idempotencyKeyRepository = repoRecord.idempotencyKeyRepository();
        User senderUser = userRepository.findByUsername(senderUserName).orElseThrow();
        User receiverUser = userRepository.findByUsername(receiverName).orElseThrow();

        if (senderUser.getId().equals(receiverUser.getId())) {
            throw new IllegalArgumentException("Cannot transfer funds to yourself.");
        }

        Wallet senderWallet = walletRepository.findByUserId(senderUser.getId())
                .orElseThrow(() -> new RuntimeException("Wallet not found for userId: " + senderUserName)); // This will fetch the sender's wallet as part of the transaction
        // Idempotency check
        if (idempotencyKeyRepository.existsByKey(idempotencyKey)) {
            log.debug("Idempotent transfer request detected and ignored for key: {}", idempotencyKey);
            return senderWallet;
        }
        if (senderWallet.getBalance() < amount) {
            throw new InsufficientFundsException("Insufficient funds for transfer.");
        }
        Wallet receiverWallet = walletRepository.findByUserId(receiverUser.getId())
                .orElseThrow(() -> new RuntimeException("Wallet not found for userId: " + receiverUser.getId())); // This will fetch the receiver's wallet as part of the transaction

        // Debit sender
        double senderPreBalance = senderWallet.getBalance();
        double senderPostBalance = senderPreBalance - amount;
        senderWallet.setBalance(senderWallet.getBalance() - amount);
        //saveWallet(senderWallet); // Saves and increments version for senderWallet

        // Credit receiver
        double receiverPreBalance = receiverWallet.getBalance();
        double receiverPostBalance = receiverPreBalance + amount;
        receiverWallet.setBalance(receiverWallet.getBalance() + amount);
        //saveWallet(receiverWallet); // Saves and increments version for receiverWallet

        walletRepository.saveAll(List.of(senderWallet, receiverWallet));

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
        //senderWallet.addTransaction(senderTx);
        //transactionRepository.save(senderTx);

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
        //receiverWallet.addTransaction(receiverTx);
        //transactionRepository.save(receiverTx);
        transactionRepository.saveAll(List.of(senderTx, receiverTx));


        // Record the idempotency key after successful processing
        idempotencyKeyRepository.save(new IdempotencyKey(idempotencyKey, "TRANSFER", senderUser));

//            tx.commit();
        result = senderWallet;
        return senderWallet;
    }


    private <T extends JpaRepository<?, ?>> JpaRepository<?, ?> getRepository(Class<T> t, RepositoryFactorySupport repositoryFactorySupport) {
        // Example of obtaining EntityManagerFactory and EntityManager programmatically
        // (This might vary depending on your setup, e.g., using Hibernate's API directly)
        return repositoryFactorySupport.getRepository(t);
    }

    public Wallet transferFund() {
        TransactionRequest transactionRequestLocal = transactionRequestRetry != null ? transactionRequestRetry : transactionRequest;
        try {
            return transferFund(transactionRequestLocal);
        } catch (ObjectOptimisticLockingFailureException | StaleObjectStateException e) {
            if (transactionRequest.retryCount() < 3){
                transactionRequestRetry = transactionRequestLocal.getTransactionRequestAndIncrementRetryCount();
                return transferFund();
            }
            else throw new EWalletConcurrentExecutionException(transactionRequest,"FAILEDDDDDDDDDDDD");
        }
    }

    @Override
    public void run() {
        transferFund();
    }

    @Override
    public Wallet call() {
        return transferFund();
    }

    @Override
    public Wallet get() {
        return transferFund();
    }
}
