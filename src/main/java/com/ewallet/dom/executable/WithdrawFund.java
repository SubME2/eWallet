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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Supplier;


@Slf4j
public class WithdrawFund extends BaseExecutable {


    public WithdrawFund(RepoRecord repoRecord, TransactionRequest transactionRequest) {
        super(repoRecord, transactionRequest);
    }

    @Override
    public Wallet execute(final TransactionRequest transactionRequest) {

        final String senderUserName = transactionRequest.senderUserName();
        final double amount = transactionRequest.amount();
        final String idempotencyKey = transactionRequest.idempotencyKey();

        validateAmount(amount);

        User user = findUserByUsername(senderUserName);


        Wallet wallet = findWalletByUserId(user);
        // Idempotency check
        if (existsByKey(idempotencyKey)) {
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

        saveWallets(wallet);

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

        saveTransactions(transaction);

        // Record the idempotency key after successful processing
        saveIdempotencyKey(idempotencyKey,user);

        return wallet;
    }
}
